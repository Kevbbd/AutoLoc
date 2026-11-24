/*
 * AutoLoc : vitrine interactive.
 *   GET  /api/public/vehicules[?debut&fin]            flotte (et disponibilité sur une période)
 *   GET  /api/public/vehicules/{id}/devis?debut&fin   prix et disponibilité calculés par le serveur
 *   GET  /api/public/vehicules/{id}/periodes-reservees
 *   POST /api/public/reservations                     réservation sans compte
 *   POST /api/client/reservations                     réservation d'un client connecté (via compte.js)
 *   POST /api/public/reservations/consultation        consultation (code + e-mail)
 *   POST /api/public/reservations/annulation          annulation par le client (code + e-mail)
 *   GET  /api/public/agences, /actuator/health
 */
(function ($) {
  'use strict';

  var A = window.AutoLoc;
  var element = A.element;
  var dates = A.dates;

  var CATEGORIES = {
    CITADINE: { libelle: 'Citadine', pluriel: 'Citadines', images: ['car-11.jpg', 'car-12.jpg'] },
    BERLINE: { libelle: 'Berline', pluriel: 'Berlines', images: ['car-5.jpg', 'car-7.jpg', 'car-9.jpg'] },
    SUV: { libelle: 'SUV', pluriel: 'SUV', images: ['car-2.jpg', 'car-8.jpg'] },
    UTILITAIRE: { libelle: 'Utilitaire', pluriel: 'Utilitaires', images: ['car-8.jpg', 'car-4.jpg'] }
  };
  var STATUTS_VEHICULE = { DISPONIBLE: 'Disponible', LOUE: 'Loué', MAINTENANCE: 'En maintenance' };
  var DUREE_MAX_JOURS = 30;
  var DELAI_MAX_JOURS = 365;
  var INTERVALLE_SANTE_MS = 15000;
  var formatHeure = new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

  var etat = {
    vehicules: [],
    agences: [],
    filtre: 'TOUS',
    periode: null,
    images: {},
    vehiculeEnCours: null,
    numeroDevis: 0,
    derniereReservation: null
  };

  function $id(id) {
    return document.getElementById(id);
  }

  /* =====================================================================
     Contrôles de dates côté navigateur (mêmes règles que ReglesLocation)
     ===================================================================== */

  /** Renvoie null si la période est valide, sinon { champ, message }. */
  function erreurPeriode(debut, fin) {
    var aujourdhui = dates.aujourdhui();
    if (!debut) { return { champ: 'dateDebut', message: 'Choisissez une date de départ.' }; }
    if (!fin) { return { champ: 'dateFin', message: 'Choisissez une date de retour.' }; }
    if (debut < aujourdhui) { return { champ: 'dateDebut', message: 'La date de départ ne peut pas être dans le passé.' }; }
    if (debut > dates.ajouterJours(aujourdhui, DELAI_MAX_JOURS)) {
      return { champ: 'dateDebut', message: 'Les réservations sont ouvertes ' + DELAI_MAX_JOURS + ' jours à l\'avance au maximum.' };
    }
    if (fin <= debut) { return { champ: 'dateFin', message: 'La date de retour doit être postérieure à la date de départ.' }; }
    if (dates.joursEntre(debut, fin) > DUREE_MAX_JOURS) {
      return { champ: 'dateFin', message: 'La durée d\'une location est limitée à ' + DUREE_MAX_JOURS + ' jours.' };
    }
    return null;
  }

  function lierDates(champDebut, champFin) {
    var aujourdhui = dates.aujourdhui();
    champDebut.min = aujourdhui;
    champDebut.max = dates.ajouterJours(aujourdhui, DELAI_MAX_JOURS);
    function ajusterFin() {
      if (!champDebut.value) { return; }
      champFin.min = dates.ajouterJours(champDebut.value, 1);
      champFin.max = dates.ajouterJours(champDebut.value, DUREE_MAX_JOURS);
      if (!champFin.value || champFin.value <= champDebut.value) {
        champFin.value = dates.ajouterJours(champDebut.value, 3);
      }
    }
    champDebut.addEventListener('change', ajusterFin);
    ajusterFin();
  }

  function libellePeriode(periode) {
    var jours = dates.joursEntre(periode.debut, periode.fin);
    return 'Du ' + dates.longue(periode.debut) + ' au ' + dates.longue(periode.fin) + ' · ' + jours + ' jour' + (jours > 1 ? 's' : '');
  }

  /* =====================================================================
     Erreurs de formulaire (affichées sous chaque champ)
     ===================================================================== */

  function effacerErreurs(formulaire, alerte) {
    formulaire.querySelectorAll('.is-invalid').forEach(function (champ) { champ.classList.remove('is-invalid'); });
    if (alerte) { alerte.hidden = true; alerte.textContent = ''; }
  }

  function marquerChamp(formulaire, nom, message) {
    var champ = formulaire.querySelector('[name="' + nom + '"]');
    if (!champ) { return false; }
    champ.classList.add('is-invalid');
    var retour = champ.parentNode.querySelector('.invalid-feedback');
    if (retour) { retour.textContent = message; }
    return true;
  }

  function afficherErreurServeur(formulaire, alerte, erreur) {
    var nonPlacees = [];
    Object.keys(erreur.erreurs || {}).forEach(function (nom) {
      if (!marquerChamp(formulaire, nom, erreur.erreurs[nom])) { nonPlacees.push(erreur.erreurs[nom]); }
    });
    var resteAffiche = Object.keys(erreur.erreurs || {}).length > 0 && nonPlacees.length === 0;
    if (!resteAffiche) {
      alerte.textContent = nonPlacees.length ? nonPlacees.join(' ') : erreur.detail;
      alerte.hidden = false;
    }
    var premier = formulaire.querySelector('.is-invalid');
    if (premier) { premier.focus(); }
  }

  /* =====================================================================
     Flotte
     ===================================================================== */

  function attribuerImages() {
    var compteurs = {};
    etat.vehicules.forEach(function (v) {
      var categorie = CATEGORIES[v.categorie] || CATEGORIES.BERLINE;
      var rang = compteurs[v.categorie] || 0;
      compteurs[v.categorie] = rang + 1;
      etat.images[v.idVehicule] = 'images/' + categorie.images[rang % categorie.images.length];
    });
  }

  function estLibre(v) {
    if (etat.periode) { return v.disponibleSurPeriode === true; }
    return v.statut === 'DISPONIBLE';
  }

  function chargerFlotte() {
    var url = '/api/public/vehicules';
    if (etat.periode) {
      url += '?debut=' + etat.periode.debut + '&fin=' + etat.periode.fin;
    }
    return A.api(url).then(function (vehicules) {
      etat.vehicules = vehicules;
      attribuerImages();
      afficherFiltres();
      afficherFlotte();
      afficherBandeauPeriode();
      afficherChiffres();
    });
  }

  function afficherBandeauPeriode() {
    var bandeau = $id('bandeau-periode');
    if (!etat.periode) { bandeau.hidden = true; return; }
    var libres = etat.vehicules.filter(estLibre).length;
    $id('bandeau-periode-texte').textContent = libellePeriode(etat.periode) + ' — '
      + libres + ' véhicule' + (libres > 1 ? 's' : '') + ' libre' + (libres > 1 ? 's' : '') + ' sur ces dates';
    bandeau.hidden = false;
  }

  function carteVehicule(v) {
    var libre = estLibre(v);
    var colonne = element('div', 'col-md-6 col-lg-4');
    var carte = element('div', 'car-wrap rounded carte-vehicule' + (libre ? '' : ' indisponible'));

    var visuel = element('div', 'img rounded d-flex align-items-end');
    visuel.style.backgroundImage = 'url(' + etat.images[v.idVehicule] + ')';
    visuel.setAttribute('role', 'img');
    visuel.setAttribute('aria-label', v.marque + ' ' + v.modele);
    if (etat.periode) {
      visuel.appendChild(element('span', 'badge-statut ' + (libre ? 'statut-DISPONIBLE' : 'statut-MAINTENANCE'),
        libre ? 'Libre sur vos dates' : 'Indisponible sur vos dates'));
    } else {
      visuel.appendChild(element('span', 'badge-statut statut-' + v.statut, STATUTS_VEHICULE[v.statut] || v.statut));
    }
    visuel.appendChild(element('span', 'photo-nc', 'Photo non contractuelle'));

    var texte = element('div', 'text');
    texte.appendChild(element('h2', 'mb-1', v.marque + ' ' + v.modele));

    var ligne = element('div', 'd-flex align-items-center mb-2');
    var categorie = CATEGORIES[v.categorie];
    ligne.appendChild(element('span', 'cat mr-2', categorie ? categorie.libelle : v.categorie));
    ligne.appendChild(element('span', 'immatriculation', v.immatriculation));
    var prix = element('p', 'price ml-auto', A.prix(v.tarifJournalier) + ' ');
    prix.appendChild(element('span', null, '/jour'));
    ligne.appendChild(prix);
    texte.appendChild(ligne);

    if (etat.periode && libre) {
      var jours = dates.joursEntre(etat.periode.debut, etat.periode.fin);
      texte.appendChild(element('p', 'total-periode',
        'Soit ' + A.prix(Number(v.tarifJournalier) * jours) + ' pour ' + jours + ' jour' + (jours > 1 ? 's' : '')));
    }

    if (v.equipements && v.equipements.length) {
      var liste = element('ul', 'equipements');
      v.equipements.forEach(function (libelle) { liste.appendChild(element('li', null, libelle)); });
      texte.appendChild(liste);
    }

    if (v.agence) {
      var agence = element('p', 'agence');
      agence.appendChild(element('span', 'ion-ios-pin'));
      agence.appendChild(document.createTextNode(v.agence + (v.ville ? ' · ' + v.ville : '')));
      texte.appendChild(agence);
    }

    var bouton = element('button', 'btn btn-primary py-2 mt-3 w-100 bouton-reserver');
    bouton.type = 'button';
    if (v.statut === 'MAINTENANCE') {
      bouton.disabled = true;
      bouton.textContent = 'En maintenance';
    } else if (etat.periode && !libre) {
      bouton.disabled = true;
      bouton.textContent = 'Indisponible sur ces dates';
    } else {
      bouton.textContent = 'Réserver';
      bouton.addEventListener('click', function () { ouvrirReservation(v); });
    }
    texte.appendChild(bouton);

    carte.appendChild(visuel);
    carte.appendChild(texte);
    colonne.appendChild(carte);
    return colonne;
  }

  function correspondAuFiltre(v) {
    if (etat.filtre === 'TOUS') { return true; }
    if (etat.filtre === 'DISPONIBLE') { return estLibre(v); }
    return v.categorie === etat.filtre;
  }

  function afficherFlotte() {
    var grille = $id('grille-vehicules');
    if (!etat.vehicules.length) {
      grille.replaceChildren(element('div', 'col-12 etat-message',
        'Aucun véhicule en base pour le moment. Démarrez l\'application avec le profil « dev » pour charger la démonstration.'));
      return;
    }
    var cartes = etat.vehicules.filter(correspondAuFiltre).map(carteVehicule);
    if (!cartes.length) {
      grille.replaceChildren(element('div', 'col-12 etat-message', 'Aucun véhicule ne correspond à ce filtre.'));
      return;
    }
    grille.replaceChildren.apply(grille, cartes);
  }

  function compter(predicat) {
    return etat.vehicules.filter(predicat).length;
  }

  function afficherFiltres() {
    var filtres = [
      { code: 'TOUS', libelle: 'Tous', compte: etat.vehicules.length },
      { code: 'DISPONIBLE', libelle: etat.periode ? 'Libres sur vos dates' : 'Disponibles', compte: compter(estLibre) }
    ];
    Object.keys(CATEGORIES).forEach(function (code) {
      var compte = compter(function (v) { return v.categorie === code; });
      if (compte) { filtres.push({ code: code, libelle: CATEGORIES[code].pluriel, compte: compte }); }
    });
    if (!filtres.some(function (f) { return f.code === etat.filtre; })) { etat.filtre = 'TOUS'; }

    var boutons = filtres.map(function (f) {
      var bouton = element('button', 'filtre' + (f.code === etat.filtre ? ' actif' : ''), f.libelle);
      bouton.type = 'button';
      bouton.setAttribute('aria-pressed', String(f.code === etat.filtre));
      bouton.appendChild(element('span', 'compte', '(' + f.compte + ')'));
      bouton.addEventListener('click', function () {
        etat.filtre = f.code;
        afficherFiltres();
        afficherFlotte();
      });
      return bouton;
    });
    $id('filtres-flotte').replaceChildren.apply($id('filtres-flotte'), boutons);
  }

  /* =====================================================================
     Recherche par dates
     ===================================================================== */

  function initialiserRecherche() {
    var formulaire = $id('form-recherche');
    var debut = $id('recherche-debut');
    var fin = $id('recherche-fin');
    var erreur = $id('recherche-erreur');
    lierDates(debut, fin);

    formulaire.addEventListener('submit', function (evenement) {
      evenement.preventDefault();
      erreur.hidden = true;
      var probleme = erreurPeriode(debut.value, fin.value);
      if (probleme) {
        erreur.textContent = probleme.message;
        erreur.hidden = false;
        return;
      }
      etat.periode = { debut: debut.value, fin: fin.value };
      var categorie = $id('recherche-categorie').value;
      etat.filtre = categorie === 'TOUS' ? 'DISPONIBLE' : categorie;
      chargerFlotte()
        .then(function () { document.getElementById('flotte').scrollIntoView({ behavior: 'smooth' }); })
        .catch(function (e) {
          etat.periode = null;
          erreur.textContent = e.detail;
          erreur.hidden = false;
        });
    });

    $id('effacer-periode').addEventListener('click', function () {
      etat.periode = null;
      etat.filtre = 'TOUS';
      debut.value = '';
      fin.value = '';
      chargerFlotte().catch(afficherErreurFlotte);
    });
  }

  /* =====================================================================
     Réservation
     ===================================================================== */

  /** periode (facultative) : dates à reprendre, par exemple au retour de la connexion au compte. */
  function ouvrirReservation(v, periode) {
    etat.vehiculeEnCours = v;
    var formulaire = $id('form-reservation');
    formulaire.reset();
    effacerErreurs(formulaire, $id('reservation-erreur'));
    appliquerCompte();
    $id('reservation-etape-formulaire').hidden = false;
    $id('reservation-etape-confirmation').hidden = true;
    $id('reservation-pied').hidden = true;

    $id('modale-reservation-titre').textContent = 'Réserver : ' + v.marque + ' ' + v.modele;
    $id('reservation-visuel').style.backgroundImage = 'url(' + etat.images[v.idVehicule] + ')';
    var categorie = CATEGORIES[v.categorie];
    $id('reservation-fiche-vehicule').textContent = (categorie ? categorie.libelle : v.categorie)
      + ' · ' + v.immatriculation + ' · ' + A.prix(v.tarifJournalier) + ' / jour';
    $id('reservation-fiche-agence').textContent = v.agence ? 'Départ : ' + v.agence + (v.ville ? ', ' + v.ville : '') : '';

    var debut = $id('resa-dateDebut');
    var fin = $id('resa-dateFin');
    var demain = dates.ajouterJours(dates.aujourdhui(), 1);
    var choisie = periode || etat.periode;
    debut.value = choisie ? choisie.debut : demain;
    fin.value = choisie ? choisie.fin : dates.ajouterJours(demain, 3);
    if (!debut.dataset.lie) {
      lierDates(debut, fin);
      debut.addEventListener('change', actualiserDevis);
      fin.addEventListener('change', actualiserDevis);
      debut.dataset.lie = 'oui';
    } else {
      debut.dispatchEvent(new Event('change'));
    }

    chargerPeriodesReservees(v.idVehicule);
    actualiserDevis();
    $('#modale-reservation').modal('show');
  }

  var CHAMPS_IDENTITE = ['prenom', 'nom', 'email', 'telephone', 'numPermis'];

  /**
   * Client connecté : son identité est reprise du compte et verrouillée. Le serveur ne la lit de toute
   * façon pas : il réserve au nom de l'adresse authentifiée.
   */
  function appliquerCompte() {
    var profil = A.compte.profil();
    CHAMPS_IDENTITE.forEach(function (nom) {
      var champ = $id('resa-' + nom);
      champ.readOnly = Boolean(profil);
      if (profil) {
        champ.value = profil[nom] || '';
        champ.classList.remove('is-invalid');
      }
    });
    var zone = A.vider($id('reservation-compte'));
    if (profil) {
      zone.textContent = 'Réservation au nom de votre compte (' + profil.email + ') : elle apparaîtra dans « Mon compte ».';
      return;
    }
    zone.appendChild(document.createTextNode('Vous avez un compte ? '));
    var lien = element('button', 'btn btn-link p-0 align-baseline', 'Connectez-vous');
    lien.type = 'button';
    lien.addEventListener('click', function () {
      var v = etat.vehiculeEnCours;
      var periode = { debut: $id('resa-dateDebut').value, fin: $id('resa-dateFin').value };
      A.compte.ouvrir('connexion', function () { ouvrirReservation(v, periode); });
    });
    zone.appendChild(lien);
    zone.appendChild(document.createTextNode(' pour pré-remplir vos informations et suivre vos réservations.'));
  }

  function chargerPeriodesReservees(idVehicule) {
    var liste = $id('reservation-periodes');
    liste.replaceChildren(element('li', 'text-muted', 'Chargement…'));
    A.api('/api/public/vehicules/' + idVehicule + '/periodes-reservees').then(function (periodes) {
      if (!periodes.length) {
        liste.replaceChildren(element('li', 'text-muted', 'Aucune réservation à venir : toutes les dates sont libres.'));
        return;
      }
      liste.replaceChildren.apply(liste, periodes.map(function (p) {
        return element('li', null, 'Du ' + dates.courte(p.dateDebut) + ' au ' + dates.courte(p.dateFin) + ' (retour)');
      }));
    }).catch(function () {
      liste.replaceChildren(element('li', 'text-muted', 'Indisponible pour le moment.'));
    });
  }

  function actualiserDevis() {
    var v = etat.vehiculeEnCours;
    var zone = $id('reservation-devis');
    var bouton = $id('reservation-valider');
    var formulaire = $id('form-reservation');
    var debut = $id('resa-dateDebut').value;
    var fin = $id('resa-dateFin').value;
    var numero = ++etat.numeroDevis;

    ['dateDebut', 'dateFin'].forEach(function (nom) {
      formulaire.querySelector('[name="' + nom + '"]').classList.remove('is-invalid');
    });
    bouton.disabled = true;
    var probleme = erreurPeriode(debut, fin);
    if (probleme) {
      marquerChamp(formulaire, probleme.champ, probleme.message);
      zone.className = 'devis';
      zone.replaceChildren();
      return;
    }

    zone.className = 'devis devis-chargement';
    zone.replaceChildren(element('span', null, 'Calcul du prix…'));
    A.api('/api/public/vehicules/' + v.idVehicule + '/devis?debut=' + debut + '&fin=' + fin)
      .then(function (devis) {
        if (numero !== etat.numeroDevis) { return; }
        if (!devis.disponible) {
          zone.className = 'devis devis-indisponible';
          zone.replaceChildren(element('strong', null, 'Indisponible : '), document.createTextNode(devis.motifIndisponibilite));
          return;
        }
        zone.className = 'devis devis-ok';
        zone.replaceChildren(
          element('span', 'devis-calcul', devis.jours + ' jour' + (devis.jours > 1 ? 's' : '') + ' × ' + A.prix(devis.tarifJournalier)),
          element('span', 'devis-total', A.prix(devis.montantTotal))
        );
        bouton.disabled = false;
      })
      .catch(function (erreur) {
        if (numero !== etat.numeroDevis) { return; }
        zone.className = 'devis devis-indisponible';
        zone.replaceChildren(document.createTextNode(erreur.detail));
      });
  }

  function validerFormulaireReservation(formulaire) {
    var valide = true;
    var regles = [
      ['prenom', function (x) { return x.trim() ? null : 'Le prénom est obligatoire.'; }],
      ['nom', function (x) { return x.trim() ? null : 'Le nom est obligatoire.'; }],
      ['email', function (x) { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(x.trim()) ? null : 'Saisissez une adresse e-mail valide.'; }],
      ['telephone', function (x) { return /^[0-9+ .()-]*$/.test(x) ? null : 'Chiffres, espaces, points, tirets ou + uniquement.'; }],
      ['numPermis', function (x) {
        if (!x.trim()) { return 'Le numéro de permis est obligatoire.'; }
        return /^[A-Za-z0-9 -]+$/.test(x) ? null : 'Lettres, chiffres, espaces ou tirets uniquement.';
      }]
    ];
    regles.forEach(function (regle) {
      var message = regle[1](formulaire.querySelector('[name="' + regle[0] + '"]').value);
      if (message) {
        marquerChamp(formulaire, regle[0], message);
        valide = false;
      }
    });
    var certification = $id('resa-certification');
    if (!certification.checked) {
      certification.classList.add('is-invalid');
      valide = false;
    }
    var probleme = erreurPeriode($id('resa-dateDebut').value, $id('resa-dateFin').value);
    if (probleme) {
      marquerChamp(formulaire, probleme.champ, probleme.message);
      valide = false;
    }
    return valide;
  }

  function soumettreReservation(evenement) {
    evenement.preventDefault();
    var formulaire = evenement.target;
    var alerte = $id('reservation-erreur');
    var bouton = $id('reservation-valider');
    effacerErreurs(formulaire, alerte);
    $id('resa-certification').classList.remove('is-invalid');
    if (!validerFormulaireReservation(formulaire)) {
      var premier = formulaire.querySelector('.is-invalid');
      if (premier) { premier.focus(); }
      return;
    }

    var demande = {
      idVehicule: etat.vehiculeEnCours.idVehicule,
      dateDebut: $id('resa-dateDebut').value,
      dateFin: $id('resa-dateFin').value,
      prenom: $id('resa-prenom').value.trim(),
      nom: $id('resa-nom').value.trim(),
      email: $id('resa-email').value.trim(),
      telephone: $id('resa-telephone').value.trim(),
      numPermis: $id('resa-numPermis').value.trim()
    };
    bouton.disabled = true;
    bouton.textContent = 'Réservation en cours…';
    var envoi = A.compte.actif()
      ? A.compte.reserver({ idVehicule: demande.idVehicule, dateDebut: demande.dateDebut, dateFin: demande.dateFin })
      : A.api('/api/public/reservations', { corps: demande });
    envoi
      .then(function (reservation) {
        etat.derniereReservation = { code: reservation.codeAcces, email: reservation.client.email };
        afficherConfirmation(reservation);
        chargerFlotte().catch(afficherErreurFlotte);
      })
      .catch(function (erreur) {
        afficherErreurServeur(formulaire, alerte, erreur);
        if (erreur.status === 409) {
          chargerPeriodesReservees(etat.vehiculeEnCours.idVehicule);
          actualiserDevis();
        } else {
          bouton.disabled = false;
        }
      })
      .finally(function () {
        bouton.textContent = 'Confirmer la réservation';
      });
  }

  function ajouterLigne(liste, terme, valeur) {
    liste.appendChild(element('dt', null, terme));
    liste.appendChild(element('dd', null, valeur));
  }

  function afficherConfirmation(r) {
    $id('reservation-etape-formulaire').hidden = true;
    $id('reservation-etape-confirmation').hidden = false;
    $id('reservation-pied').hidden = false;
    $id('modale-reservation-titre').textContent = 'Merci ' + r.client.prenom + ' !';
    $id('confirmation-numero').textContent = r.idReservation;
    $id('confirmation-code').textContent = A.codeLisible(r.codeAcces);
    var recap = A.vider($id('confirmation-recap'));
    ajouterLigne(recap, 'Véhicule', r.vehicule.marque + ' ' + r.vehicule.modele + ' (' + r.vehicule.immatriculation + ')');
    ajouterLigne(recap, 'Départ', dates.longue(r.dateDebut));
    ajouterLigne(recap, 'Retour', dates.longue(r.dateFin));
    ajouterLigne(recap, 'Durée', r.jours + ' jour' + (r.jours > 1 ? 's' : ''));
    ajouterLigne(recap, 'Montant total', A.prix(r.montantTotal));
    ajouterLigne(recap, 'À régler en agence', A.prix(r.resteAPayer));
    if (r.vehicule.agence) { ajouterLigne(recap, 'Agence', r.vehicule.agence + (r.vehicule.ville ? ', ' + r.vehicule.ville : '')); }
    ajouterLigne(recap, 'E-mail', r.client.email);
    if (A.compte.actif()) { ajouterLigne(recap, 'Suivi', 'Dans « Mon compte », avec toutes vos réservations'); }
  }

  /* =====================================================================
     Ma réservation (consultation et annulation)
     ===================================================================== */

  function ouvrirMaReservation(prerempli) {
    var formulaire = $id('form-consultation');
    formulaire.reset();
    effacerErreurs(formulaire, $id('consultation-erreur'));
    $id('consultation-resultat').hidden = true;
    formulaire.hidden = false;
    if (prerempli) {
      $id('consult-code').value = A.codeLisible(prerempli.code);
      $id('consult-email').value = prerempli.email;
    }
    $('#modale-ma-reservation').modal('show');
    if (prerempli) { consulter(prerempli.code, prerempli.email); }
  }

  function soumettreConsultation(evenement) {
    evenement.preventDefault();
    var formulaire = evenement.target;
    effacerErreurs(formulaire, $id('consultation-erreur'));
    var code = A.normaliserCode($id('consult-code').value);
    var email = $id('consult-email').value.trim();
    var valide = true;
    if (!code) {
      marquerChamp(formulaire, 'code', 'Le code comporte 10 lettres et chiffres : il figure sur votre confirmation.');
      valide = false;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      marquerChamp(formulaire, 'email', 'Saisissez une adresse e-mail valide.');
      valide = false;
    }
    if (valide) { consulter(code, email); }
  }

  function consulter(code, email) {
    var formulaire = $id('form-consultation');
    A.api('/api/public/reservations/consultation', { corps: { code: code, email: email } })
      .then(function (reservation) { afficherMaReservation(reservation, email); })
      .catch(function (erreur) { afficherErreurServeur(formulaire, $id('consultation-erreur'), erreur); });
  }

  function afficherMaReservation(r, email, messageSucces) {
    $id('form-consultation').hidden = true;
    var zone = A.vider($id('consultation-resultat'));
    zone.hidden = false;

    if (messageSucces) { zone.appendChild(element('div', 'alert alert-success', messageSucces)); }

    var entete = element('div', 'd-flex align-items-center justify-content-between mb-3');
    entete.appendChild(element('h4', 'mb-0', 'Réservation n° ' + r.idReservation));
    entete.appendChild(A.badgeStatut(r.statut));
    zone.appendChild(entete);

    var recap = element('dl', 'recapitulatif');
    ajouterLigne(recap, 'Titulaire', r.client.prenom + ' ' + r.client.nom);
    ajouterLigne(recap, 'Véhicule', r.vehicule.marque + ' ' + r.vehicule.modele + ' (' + r.vehicule.immatriculation + ')');
    ajouterLigne(recap, 'Période', 'Du ' + dates.courte(r.dateDebut) + ' au ' + dates.courte(r.dateFin) + ' · ' + r.jours + ' jour' + (r.jours > 1 ? 's' : ''));
    ajouterLigne(recap, 'Montant total', A.prix(r.montantTotal));
    ajouterLigne(recap, 'Déjà réglé', A.prix(r.montantPaye));
    ajouterLigne(recap, 'Reste à payer', A.prix(r.resteAPayer));
    if (r.vehicule.agence) { ajouterLigne(recap, 'Agence', r.vehicule.agence + (r.vehicule.ville ? ', ' + r.vehicule.ville : '')); }
    zone.appendChild(recap);

    if (r.paiements.length) {
      zone.appendChild(element('h6', 'mt-3', 'Paiements'));
      var liste = element('ul', 'list-unstyled liste-paiements');
      r.paiements.forEach(function (p) {
        liste.appendChild(element('li', null, dates.heure(p.datePaiement) + ' · ' + (A.MODES_PAIEMENT[p.modePaiement] || p.modePaiement) + ' · ' + A.prix(p.montant)));
      });
      zone.appendChild(liste);
    }

    var actions = element('div', 'actions-reservation mt-3');
    if (r.annulableParClient) {
      var annuler = element('button', 'btn btn-outline-danger', 'Annuler ma réservation');
      annuler.type = 'button';
      annuler.addEventListener('click', function () { demanderConfirmationAnnulation(actions, r, email); });
      actions.appendChild(annuler);
    }
    var autre = element('button', 'btn btn-link', 'Consulter une autre réservation');
    autre.type = 'button';
    autre.addEventListener('click', function () { ouvrirMaReservation(null); });
    actions.appendChild(autre);
    zone.appendChild(actions);
  }

  function demanderConfirmationAnnulation(actions, r, email) {
    A.vider(actions);
    actions.appendChild(element('p', 'mb-2 font-weight-bold', 'Confirmer l\'annulation de la réservation n° ' + r.idReservation + ' ?'));
    var oui = element('button', 'btn btn-danger mr-2', 'Oui, annuler');
    oui.type = 'button';
    var non = element('button', 'btn btn-outline-secondary', 'Non, la conserver');
    non.type = 'button';
    non.addEventListener('click', function () { afficherMaReservation(r, email); });
    oui.addEventListener('click', function () {
      oui.disabled = true;
      oui.textContent = 'Annulation…';
      A.api('/api/public/reservations/annulation', { corps: { code: r.codeAcces, email: email } })
        .then(function (annulee) {
          afficherMaReservation(annulee, email, 'Votre réservation est annulée. Le véhicule est de nouveau proposé à la location.');
          chargerFlotte().catch(afficherErreurFlotte);
        })
        .catch(function (erreur) {
          afficherMaReservation(r, email);
          $id('consultation-resultat').insertBefore(element('div', 'alert alert-danger', erreur.detail), $id('consultation-resultat').firstChild);
        });
    });
    actions.appendChild(oui);
    actions.appendChild(non);
  }

  /* =====================================================================
     Agences, chiffres clés, santé de l'API
     ===================================================================== */

  function adresseComplete(agence) {
    var adresse = agence.adresse || '';
    var dejaDansAdresse = agence.ville && adresse.toLowerCase().indexOf(agence.ville.toLowerCase()) !== -1;
    return agence.ville && !dejaDansAdresse ? adresse + ' · ' + agence.ville : adresse;
  }

  function afficherAgences() {
    var conteneur = $id('liste-agences');
    if (!etat.agences.length) {
      conteneur.replaceChildren(element('p', 'etat-message etat-message-clair', 'Aucune agence enregistrée pour le moment.'));
      return;
    }
    conteneur.replaceChildren.apply(conteneur, etat.agences.map(function (a) {
      var carte = element('div', 'agence-carte');
      carte.appendChild(element('h3', null, a.nom));
      carte.appendChild(element('p', null, adresseComplete(a)));
      if (a.telephone) { carte.appendChild(element('p', null, 'Tél. ' + a.telephone)); }
      var flotte = etat.vehicules.filter(function (v) { return v.idAgence === a.idAgence; });
      var disponibles = flotte.filter(function (v) { return v.statut === 'DISPONIBLE'; }).length;
      carte.appendChild(element('span', 'flotte-agence',
        flotte.length + ' véhicule' + (flotte.length > 1 ? 's' : '') + ' · ' + disponibles + ' disponible' + (disponibles > 1 ? 's' : '') + ' aujourd\'hui'));
      return carte;
    }));
    var principale = etat.agences[0];
    $id('contact-adresse').textContent = adresseComplete(principale);
    if (principale.telephone) { $id('contact-telephone').textContent = principale.telephone; }
  }

  function afficherChiffres() {
    var equipements = {};
    etat.vehicules.forEach(function (v) { (v.equipements || []).forEach(function (e) { equipements[e] = true; }); });
    var disponibles = compter(function (v) { return v.statut === 'DISPONIBLE'; });
    var valeurs = {
      vehicules: etat.vehicules.length,
      disponibles: disponibles,
      agences: etat.agences.length,
      equipements: Object.keys(equipements).length
    };
    document.querySelectorAll('[data-stat]').forEach(function (el) {
      var valeur = valeurs[el.getAttribute('data-stat')] || 0;
      if ($ && $.fn.animateNumber) { $(el).animateNumber({ number: valeur }, 1200); } else { el.textContent = valeur; }
    });
    $id('statut-disponibles').textContent = disponibles + ' / ' + etat.vehicules.length;
    if (etat.agences.length) { afficherAgences(); }
  }

  function afficherErreurFlotte(erreur) {
    $id('grille-vehicules').replaceChildren(element('div', 'col-12 etat-message erreur', 'Impossible de charger la flotte : ' + erreur.detail));
  }

  function verifierSante() {
    var pastille = $id('statut-api');
    fetch('/actuator/health', { headers: { Accept: 'application/json' } })
      .then(function (reponse) { return reponse.json(); })
      .then(function (sante) {
        var enLigne = sante.status === 'UP';
        pastille.className = 'pastille ' + (enLigne ? 'pastille-up' : 'pastille-down');
        pastille.textContent = enLigne ? 'En ligne' : 'Dégradée (' + sante.status + ')';
      })
      .catch(function () {
        pastille.className = 'pastille pastille-down';
        pastille.textContent = 'Injoignable';
      })
      .finally(function () { $id('statut-heure').textContent = formatHeure.format(new Date()); });
  }

  /* =====================================================================
     Démarrage
     ===================================================================== */

  function demarrer() {
    $id('annee').textContent = String(new Date().getFullYear());
    verifierSante();
    setInterval(verifierSante, INTERVALLE_SANTE_MS);
    initialiserRecherche();

    $id('form-reservation').addEventListener('submit', soumettreReservation);
    $id('form-consultation').addEventListener('submit', soumettreConsultation);
    $id('confirmation-voir').addEventListener('click', function () {
      if (A.compte.actif()) {
        A.compte.ouvrir('espace');
        return;
      }
      $('#modale-reservation').modal('hide');
      ouvrirMaReservation(etat.derniereReservation);
    });
    // Connexion ou déconnexion pendant que le formulaire est ouvert : on le met à jour.
    document.addEventListener('autoloc:compte', function () {
      if (etat.vehiculeEnCours && !$id('reservation-etape-formulaire').hidden) { appliquerCompte(); }
    });
    // Annulation depuis « Mon compte » : le véhicule redevient disponible dans la flotte.
    document.addEventListener('autoloc:reservations', function () { chargerFlotte().catch(afficherErreurFlotte); });
    document.addEventListener('click', function (evenement) {
      var declencheur = evenement.target.closest('[data-action="ma-reservation"]');
      if (declencheur) {
        evenement.preventDefault();
        ouvrirMaReservation(etat.derniereReservation);
      }
    });
    $('#modale-reservation').on('shown.bs.modal', function () {
      if (!$id('reservation-etape-formulaire').hidden) {
        $id(A.compte.actif() ? 'resa-certification' : 'resa-prenom').focus();
      }
    });

    A.api('/api/public/agences')
      .then(function (agences) { etat.agences = agences; })
      .catch(function () { etat.agences = []; })
      .then(chargerFlotte)
      .then(afficherAgences)
      .catch(function (erreur) {
        afficherErreurFlotte(erreur);
        $id('liste-agences').replaceChildren(element('p', 'etat-message etat-message-clair', 'Agences indisponibles pour le moment.'));
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', demarrer);
  } else {
    demarrer();
  }
})(window.jQuery);
