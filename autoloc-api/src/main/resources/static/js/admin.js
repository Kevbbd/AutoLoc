/*
 * AutoLoc : espace agence (rôle ADMIN).
 *   GET  /api/admin/reservations
 *   POST /api/admin/reservations/{id}/paiements    { montant?, modePaiement }
 *   POST /api/admin/reservations/{id}/terminaison
 *   POST /api/admin/reservations/{id}/annulation
 *   POST /api/public/mot-de-passe/oubli               { email }
 * Les identifiants restent dans une variable : ni cookie, ni stockage local.
 */
(function ($) {
  'use strict';

  var A = window.AutoLoc;
  var element = A.element;
  var dates = A.dates;
  var formatHeure = new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

  var FILTRES = [
    { code: 'TOUTES', libelle: 'Toutes' },
    { code: 'ACTIVES', libelle: 'En cours et à venir' },
    { code: 'A_ENCAISSER', libelle: 'À encaisser' },
    { code: 'TERMINEE', libelle: 'Terminées' },
    { code: 'ANNULEE', libelle: 'Annulées' }
  ];

  var etat = {
    identifiants: null,
    utilisateur: null,
    reservations: [],
    vehicules: [],
    filtre: 'ACTIVES',
    recherche: '',
    action: null,
    encaissement: null
  };

  function $id(id) {
    return document.getElementById(id);
  }

  function estActive(r) {
    return r.statut === 'EN_ATTENTE' || r.statut === 'CONFIRMEE';
  }

  function aEncaisser(r) {
    return r.contratValide && Number(r.resteAPayer) > 0;
  }

  function appel(url, corps) {
    var options = { identifiants: etat.identifiants };
    if (corps !== undefined) { options.corps = corps; }
    return A.api(url, options).catch(function (erreur) {
      if (erreur.status === 401) { deconnecter('Session expirée : reconnectez-vous.'); }
      throw erreur;
    });
  }

  /* ---------- Connexion ---------- */

  function seConnecter(evenement) {
    evenement.preventDefault();
    var utilisateur = $id('connexion-utilisateur').value.trim();
    var motDePasse = $id('connexion-motdepasse').value;
    var erreur = $id('connexion-erreur');
    erreur.hidden = true;
    if (!utilisateur || !motDePasse) {
      erreur.textContent = 'Saisissez votre identifiant et votre mot de passe.';
      erreur.hidden = false;
      return;
    }
    var identifiants = utilisateur + ':' + motDePasse;
    A.api('/api/admin/reservations', { identifiants: identifiants })
      .then(function (reservations) {
        etat.identifiants = identifiants;
        etat.utilisateur = utilisateur;
        $id('connexion-motdepasse').value = '';
        $id('connexion-info').hidden = true;
        $id('admin-connexion').hidden = true;
        $id('admin-tableau').hidden = false;
        $id('admin-utilisateur').textContent = 'Connecté : ' + utilisateur;
        $id('admin-utilisateur').hidden = false;
        $id('admin-deconnexion').hidden = false;
        etat.reservations = reservations;
        return chargerVehicules();
      })
      .then(afficherTout)
      .catch(function (e) {
        erreur.textContent = e.status === 401 ? 'Identifiant ou mot de passe incorrect.'
          : e.status === 403 ? 'Ce compte n\'a pas accès à l\'espace agence (rôle ADMIN requis).'
          : e.detail;
        erreur.hidden = false;
      });
  }

  function deconnecter(message) {
    etat.identifiants = null;
    etat.utilisateur = null;
    etat.reservations = [];
    $id('admin-tableau').hidden = true;
    $id('admin-connexion').hidden = false;
    $id('admin-utilisateur').hidden = true;
    $id('admin-deconnexion').hidden = true;
    var erreur = $id('connexion-erreur');
    erreur.hidden = !message;
    erreur.textContent = message || '';
    $id('connexion-utilisateur').focus();
  }

  /* ---------- Mot de passe oublié ---------- */

  function afficherPanneau(nom) {
    $id('panneau-connexion').hidden = nom !== 'connexion';
    $id('panneau-oubli').hidden = nom !== 'oubli';
    if (nom === 'oubli') {
      $id('oubli-resultat').hidden = true;
      $id('oubli-erreur').hidden = true;
      $id('oubli-email').focus();
    } else {
      $id('connexion-utilisateur').focus();
    }
  }

  function demanderLien(evenement) {
    evenement.preventDefault();
    var champ = $id('oubli-email');
    var email = champ.value.trim();
    var resultat = $id('oubli-resultat');
    var erreur = $id('oubli-erreur');
    var bouton = $id('oubli-envoyer');
    resultat.hidden = true;
    erreur.hidden = true;
    if (!/^[^\s@]+@[^\s@]+$/.test(email)) {
      champ.classList.add('is-invalid');
      $id('oubli-email-erreur').textContent = 'Saisissez une adresse e-mail valide.';
      return;
    }
    bouton.disabled = true;
    A.api('/api/public/mot-de-passe/oubli', { corps: { email: email } })
      .then(function (reponse) {
        resultat.textContent = reponse.message;
        resultat.hidden = false;
      })
      .catch(function (e) {
        if (e.erreurs && e.erreurs.email) {
          champ.classList.add('is-invalid');
          $id('oubli-email-erreur').textContent = e.erreurs.email;
        } else {
          erreur.textContent = e.detail;
          erreur.hidden = false;
        }
      })
      .then(function () { bouton.disabled = false; });
  }

  /** admin.html#oubli ouvre directement le formulaire ; #reinitialise vient de la page de réinitialisation. */
  function lireAncre() {
    var ancre = window.location.hash;
    if (ancre === '#oubli') {
      afficherPanneau('oubli');
    } else if (ancre === '#reinitialise') {
      var info = $id('connexion-info');
      info.textContent = 'Mot de passe modifié : connectez-vous avec le nouveau.';
      info.hidden = false;
    }
    if (ancre) { history.replaceState(null, '', window.location.pathname); }
  }

  /* ---------- Chargement ---------- */

  function chargerVehicules() {
    return A.api('/api/public/vehicules').then(function (vehicules) { etat.vehicules = vehicules; });
  }

  function recharger(message) {
    return appel('/api/admin/reservations')
      .then(function (reservations) { etat.reservations = reservations; return chargerVehicules(); })
      .then(function () {
        afficherTout();
        if (message) { afficherMessage(message, 'success'); }
      })
      .catch(function (erreur) { afficherMessage(erreur.detail, 'danger'); });
  }

  function afficherMessage(texte, type) {
    var zone = $id('admin-message');
    zone.className = 'alert alert-' + type;
    zone.textContent = texte;
    zone.hidden = false;
    clearTimeout(afficherMessage.minuteur);
    afficherMessage.minuteur = setTimeout(function () { zone.hidden = true; }, 6000);
  }

  function afficherTout() {
    $id('admin-maj').textContent = etat.reservations.length + ' réservation' + (etat.reservations.length > 1 ? 's' : '')
      + ' · mis à jour à ' + formatHeure.format(new Date());
    afficherIndicateurs();
    afficherFiltres();
    afficherLignes();
  }

  /* ---------- Indicateurs ---------- */

  function somme(liste, champ) {
    return liste.reduce(function (total, r) { return total + Number(r[champ]); }, 0);
  }

  function indicateur(titre, valeur, detail, classe) {
    var colonne = element('div', 'col-6 col-lg-3 mb-3');
    var carte = element('div', 'indicateur ' + (classe || ''));
    carte.appendChild(element('span', 'indicateur-titre', titre));
    carte.appendChild(element('strong', 'indicateur-valeur', valeur));
    carte.appendChild(element('span', 'indicateur-detail', detail));
    colonne.appendChild(carte);
    return colonne;
  }

  function afficherIndicateurs() {
    var aujourdhui = dates.aujourdhui();
    var actives = etat.reservations.filter(estActive);
    var enCours = actives.filter(function (r) { return r.dateDebut <= aujourdhui && aujourdhui < r.dateFin; });
    var valides = etat.reservations.filter(function (r) { return r.contratValide; });
    var flotte = etat.vehicules.length;
    var loues = etat.vehicules.filter(function (v) { return v.statut === 'LOUE'; }).length;
    var taux = flotte ? Math.round(100 * loues / flotte) : 0;

    $id('admin-indicateurs').replaceChildren(
      indicateur('Réservations actives', String(actives.length), enCours.length + ' en cours aujourd\'hui', 'indicateur-bleu'),
      indicateur('Chiffre d\'affaires', A.prix(somme(valides, 'montantTotal')), 'contrats valides', 'indicateur-vert'),
      indicateur('Encaissé', A.prix(somme(valides, 'montantPaye')), 'reste ' + A.prix(somme(valides, 'resteAPayer')), 'indicateur-ambre'),
      indicateur('Taux d\'occupation', taux + ' %', loues + ' véhicule' + (loues > 1 ? 's' : '') + ' loué' + (loues > 1 ? 's' : '') + ' sur ' + flotte, 'indicateur-gris')
    );
  }

  /* ---------- Filtres et tableau ---------- */

  function correspond(r) {
    var filtre = etat.filtre;
    var okFiltre = filtre === 'TOUTES'
      || (filtre === 'ACTIVES' && estActive(r))
      || (filtre === 'A_ENCAISSER' && aEncaisser(r))
      || r.statut === filtre;
    if (!okFiltre) { return false; }
    if (!etat.recherche) { return true; }
    var texte = [r.idReservation, r.client.prenom, r.client.nom, r.client.email, r.vehicule.immatriculation, r.vehicule.marque, r.vehicule.modele]
      .join(' ').toLowerCase();
    return texte.indexOf(etat.recherche) !== -1;
  }

  function afficherFiltres() {
    var boutons = FILTRES.map(function (f) {
      var nombre = etat.reservations.filter(function (r) {
        return f.code === 'TOUTES' || (f.code === 'ACTIVES' && estActive(r)) || (f.code === 'A_ENCAISSER' && aEncaisser(r)) || r.statut === f.code;
      }).length;
      var bouton = element('button', 'filtre' + (f.code === etat.filtre ? ' actif' : ''), f.libelle);
      bouton.type = 'button';
      bouton.setAttribute('aria-pressed', String(f.code === etat.filtre));
      bouton.appendChild(element('span', 'compte', '(' + nombre + ')'));
      bouton.addEventListener('click', function () {
        etat.filtre = f.code;
        afficherFiltres();
        afficherLignes();
      });
      return bouton;
    });
    $id('admin-filtres').replaceChildren.apply($id('admin-filtres'), boutons);
  }

  function cellule(contenu, classe) {
    var td = element('td', classe);
    (Array.isArray(contenu) ? contenu : [contenu]).forEach(function (noeud) {
      td.appendChild(typeof noeud === 'string' ? document.createTextNode(noeud) : noeud);
    });
    return td;
  }

  function bouton(libelle, classe, action) {
    var b = element('button', 'btn btn-sm ' + classe, libelle);
    b.type = 'button';
    b.addEventListener('click', action);
    return b;
  }

  function lignePaiement(r) {
    var total = Number(r.montantTotal);
    var paye = Number(r.montantPaye);
    var pourcentage = total > 0 ? Math.min(100, Math.round(100 * paye / total)) : 0;
    var conteneur = element('div', 'paiement-cellule');
    var barre = element('div', 'barre-paiement');
    var remplissage = element('div', 'barre-paiement-remplie' + (pourcentage === 100 ? ' complete' : ''));
    remplissage.style.width = pourcentage + '%';
    barre.appendChild(remplissage);
    conteneur.appendChild(barre);
    conteneur.appendChild(element('small', null, A.prix(paye) + ' payé · reste ' + A.prix(r.resteAPayer)));
    return conteneur;
  }

  function afficherLignes() {
    var corps = $id('admin-lignes');
    var lignes = etat.reservations.filter(correspond).map(function (r) {
      var tr = element('tr', r.statut === 'ANNULEE' ? 'ligne-annulee' : '');
      tr.appendChild(cellule(String(r.idReservation), 'font-weight-bold'));

      var client = element('div');
      client.appendChild(element('div', 'font-weight-500', r.client.prenom + ' ' + r.client.nom));
      client.appendChild(element('small', 'd-block text-muted', r.client.email));
      if (r.client.telephone) { client.appendChild(element('small', 'd-block text-muted sans-coupure', r.client.telephone)); }
      tr.appendChild(cellule(client));

      var vehicule = element('div');
      vehicule.appendChild(element('div', 'sans-coupure', r.vehicule.marque + ' ' + r.vehicule.modele));
      vehicule.appendChild(element('span', 'immatriculation', r.vehicule.immatriculation));
      tr.appendChild(cellule(vehicule));

      var periode = element('div');
      periode.appendChild(element('div', 'sans-coupure', dates.courte(r.dateDebut) + ' → ' + dates.courte(r.dateFin)));
      periode.appendChild(element('small', 'text-muted', r.jours + ' jour' + (r.jours > 1 ? 's' : '')));
      tr.appendChild(cellule(periode));

      tr.appendChild(cellule(A.prix(r.montantTotal), 'text-right colonne-montant'));
      tr.appendChild(cellule(lignePaiement(r), 'colonne-paiement'));
      tr.appendChild(cellule(A.badgeStatut(r.statut)));

      var actions = element('div', 'actions-admin');
      if (aEncaisser(r)) {
        actions.appendChild(bouton('Encaisser', 'btn-primary', function () { ouvrirEncaissement(r); }));
      }
      if (r.statut === 'CONFIRMEE') {
        actions.appendChild(bouton('Terminer', 'btn-secondary', function () {
          demanderConfirmation(r, 'terminaison', 'Terminer la location n° ' + r.idReservation,
            'Le véhicule ' + r.vehicule.immatriculation + ' a été restitué par ' + r.client.prenom + ' ' + r.client.nom
            + '. Il redeviendra disponible s\'il n\'est pas réservé aujourd\'hui par un autre client.');
        }));
      }
      if (estActive(r)) {
        actions.appendChild(bouton('Annuler', 'btn-outline-danger', function () {
          demanderConfirmation(r, 'annulation', 'Annuler la réservation n° ' + r.idReservation,
            'Le contrat de ' + A.prix(r.montantTotal) + ' sera invalidé et la période libérée. Cette action est définitive.');
        }));
      }
      tr.appendChild(cellule(actions, 'text-right'));
      return tr;
    });

    if (!lignes.length) {
      var vide = element('tr');
      var td = element('td', 'etat-message', 'Aucune réservation ne correspond.');
      td.colSpan = 8;
      vide.appendChild(td);
      lignes = [vide];
    }
    corps.replaceChildren.apply(corps, lignes);
  }

  /* ---------- Encaissement ---------- */

  function ouvrirEncaissement(r) {
    etat.encaissement = r;
    var formulaire = $id('form-encaissement');
    formulaire.querySelectorAll('.is-invalid').forEach(function (c) { c.classList.remove('is-invalid'); });
    $id('encaissement-erreur').hidden = true;
    $id('encaissement-resume').textContent = 'Réservation n° ' + r.idReservation + ' · ' + r.client.prenom + ' ' + r.client.nom
      + ' · total ' + A.prix(r.montantTotal) + ', reste ' + A.prix(r.resteAPayer) + '.';
    var montant = $id('encaissement-montant');
    montant.value = Number(r.resteAPayer).toFixed(2);
    montant.max = Number(r.resteAPayer).toFixed(2);
    $id('encaissement-mode').value = 'CARTE';
    $('#modale-encaissement').modal('show');
  }

  function soumettreEncaissement(evenement) {
    evenement.preventDefault();
    var r = etat.encaissement;
    var formulaire = evenement.target;
    var champ = $id('encaissement-montant');
    var erreur = $id('encaissement-erreur');
    var valider = $id('encaissement-valider');
    champ.classList.remove('is-invalid');
    erreur.hidden = true;

    var montant = Number(champ.value);
    var reste = Number(r.resteAPayer);
    var message = null;
    if (!/^\d+(\.\d{1,2})?$/.test(champ.value)) { message = 'Saisissez un montant positif avec deux décimales au maximum.'; }
    else if (!(montant > 0)) { message = 'Le montant doit être strictement positif.'; }
    else if (Math.round(montant * 100) > Math.round(reste * 100)) { message = 'Le montant dépasse le reste à payer (' + A.prix(reste) + ').'; }
    if (message) {
      champ.classList.add('is-invalid');
      formulaire.querySelector('[name="montant"] ~ .invalid-feedback').textContent = message;
      champ.focus();
      return;
    }

    valider.disabled = true;
    appel('/api/admin/reservations/' + r.idReservation + '/paiements', {
      montant: Number(montant.toFixed(2)),
      modePaiement: $id('encaissement-mode').value
    })
      .then(function (maj) {
        $('#modale-encaissement').modal('hide');
        return recharger('Paiement de ' + A.prix(montant) + ' enregistré pour la réservation n° ' + maj.idReservation
          + (Number(maj.resteAPayer) === 0 ? ' : contrat soldé.' : ' : reste ' + A.prix(maj.resteAPayer) + '.'));
      })
      .catch(function (e) {
        erreur.textContent = (e.erreurs && (e.erreurs.montant || e.erreurs.modePaiement)) || e.detail;
        erreur.hidden = false;
      })
      .finally(function () { valider.disabled = false; });
  }

  /* ---------- Terminer / annuler ---------- */

  function demanderConfirmation(r, action, titre, texte) {
    etat.action = { reservation: r, action: action };
    $id('modale-confirmation-titre').textContent = titre;
    $id('confirmation-texte').textContent = texte;
    $id('confirmation-erreur').hidden = true;
    var valider = $id('confirmation-valider');
    valider.className = 'btn ' + (action === 'annulation' ? 'btn-danger' : 'btn-primary');
    valider.textContent = action === 'annulation' ? 'Annuler la réservation' : 'Terminer la location';
    valider.disabled = false;
    $('#modale-confirmation').modal('show');
  }

  function executerAction() {
    var en = etat.action;
    var valider = $id('confirmation-valider');
    valider.disabled = true;
    appel('/api/admin/reservations/' + en.reservation.idReservation + '/' + en.action, {})
      .then(function (maj) {
        $('#modale-confirmation').modal('hide');
        return recharger(en.action === 'annulation'
          ? 'Réservation n° ' + maj.idReservation + ' annulée.'
          : 'Location n° ' + maj.idReservation + ' terminée.');
      })
      .catch(function (e) {
        var erreur = $id('confirmation-erreur');
        erreur.textContent = e.detail;
        erreur.hidden = false;
        valider.disabled = false;
      });
  }

  /* ---------- Démarrage ---------- */

  function demarrer() {
    $id('form-connexion').addEventListener('submit', seConnecter);
    $id('form-oubli').addEventListener('submit', demanderLien);
    Array.prototype.forEach.call(document.querySelectorAll('[data-panneau]'), function (lien) {
      lien.addEventListener('click', function () { afficherPanneau(lien.getAttribute('data-panneau')); });
    });
    $id('admin-deconnexion').addEventListener('click', function () { deconnecter(null); });
    $id('admin-rafraichir').addEventListener('click', function () { recharger(null); });
    $id('form-encaissement').addEventListener('submit', soumettreEncaissement);
    $id('confirmation-valider').addEventListener('click', executerAction);
    $id('admin-recherche').addEventListener('input', function (evenement) {
      etat.recherche = evenement.target.value.trim().toLowerCase();
      afficherLignes();
    });
    $('#modale-encaissement').on('shown.bs.modal', function () { $id('encaissement-montant').select(); });
    $id('connexion-utilisateur').focus();
    lireAncre();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', demarrer);
  } else {
    demarrer();
  }
})(window.jQuery);
