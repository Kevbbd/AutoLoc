/*
 * AutoLoc : compte client sur le site (fenêtre « Mon compte »).
 *   GET  /api/client/profil
 *   GET  /api/client/reservations
 *   POST /api/client/reservations                    { idVehicule, dateDebut, dateFin }
 *   POST /api/client/reservations/{id}/annulation
 *   POST /api/public/clients/inscription              { email }
 *   POST /api/public/mot-de-passe/oubli               { email }
 *
 * Les identifiants restent dans une variable : ni cookie, ni stockage local. Fermer ou recharger
 * la page déconnecte. Le reste du site (autoloc.js) passe par window.AutoLoc.compte et réagit aux
 * événements « autoloc:compte » (connexion, déconnexion) et « autoloc:reservations » (annulation).
 */
(function ($) {
  'use strict';

  var A = window.AutoLoc;
  var element = A.element;
  var dates = A.dates;
  var EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  var PANNEAUX = {
    connexion: 'Mon compte',
    inscription: 'Créer un compte',
    oubli: 'Mot de passe oublié',
    espace: 'Mon espace'
  };
  var GROUPES = [
    { code: 'enCours', titre: 'En cours' },
    { code: 'aVenir', titre: 'À venir' },
    { code: 'passees', titre: 'Passées et annulées' }
  ];
  var ANCRES = { '#connexion': 'connexion', '#inscription': 'inscription', '#mot-de-passe-oublie': 'oubli' };

  var etat = {
    identifiants: null,
    profil: null,
    reservations: [],
    apresConnexion: null
  };

  function $id(id) {
    return document.getElementById(id);
  }

  function notifier(nom) {
    document.dispatchEvent(new CustomEvent('autoloc:' + nom));
  }

  /** Appel authentifié. Un 401 signifie que le mot de passe a changé entre-temps : on déconnecte. */
  function appel(url, corps) {
    var options = { identifiants: etat.identifiants };
    if (corps !== undefined) { options.corps = corps; }
    return A.api(url, options).catch(function (erreur) {
      if (erreur.status === 401) {
        deconnecter('Votre session a expiré : reconnectez-vous.');
        erreur.detail = erreur.message = 'Votre session a expiré : reconnectez-vous depuis « Mon compte ».';
      }
      throw erreur;
    });
  }

  /* ---------- Fenêtre et panneaux ---------- */

  function afficherPanneau(nom) {
    Object.keys(PANNEAUX).forEach(function (p) { $id('compte-panneau-' + p).hidden = p !== nom; });
    $id('modale-compte-titre').textContent = PANNEAUX[nom];
    $id('modale-compte').querySelector('.modal-dialog').classList.toggle('modal-lg', nom === 'espace');
    if (nom === 'inscription' || nom === 'oubli') {
      var formulaire = $id('form-compte-' + nom);
      formulaire.hidden = false;
      $id(nom + '-resultat').hidden = true;
      $id(nom + '-erreur').hidden = true;
      var champ = $id(nom + '-email');
      if (!champ.value) { champ.value = $id('compte-email').value.trim(); }
    }
    var focus = { connexion: 'compte-email', inscription: 'inscription-email', oubli: 'oubli-email', espace: 'compte-reserver' }[nom];
    if ($('#modale-compte').hasClass('show')) { $id(focus).focus(); }
  }

  /**
   * Ouvre la fenêtre « Mon compte » sur le panneau demandé (l'espace si le client est connecté).
   * Une autre fenêtre ouverte est d'abord refermée : Bootstrap n'empile pas les fenêtres modales.
   */
  function ouvrir(panneau, apresConnexion) {
    etat.apresConnexion = apresConnexion || null;
    var nom = etat.identifiants ? 'espace' : (panneau && panneau !== 'espace' ? panneau : 'connexion');
    var autre = $('.modal.show').not('#modale-compte');
    var montrer = function () {
      afficherPanneau(nom);
      $('#modale-compte').modal('show');
      if (nom === 'espace') { chargerReservations(); }
    };
    if (autre.length) {
      autre.one('hidden.bs.modal', montrer).modal('hide');
    } else {
      montrer();
    }
  }

  function majNavigation() {
    var lien = $id('nav-compte');
    lien.textContent = etat.profil ? 'Mon compte · ' + etat.profil.prenom : 'Mon compte';
  }

  /* ---------- Connexion ---------- */

  function seConnecter(evenement) {
    evenement.preventDefault();
    var email = $id('compte-email').value.trim();
    var motDePasse = $id('compte-motdepasse').value;
    var erreur = $id('compte-connexion-erreur');
    var bouton = $id('compte-connexion-valider');
    erreur.hidden = true;
    $id('compte-connexion-info').hidden = true;
    if (!EMAIL.test(email) || !motDePasse) {
      erreur.textContent = 'Saisissez votre adresse e-mail et votre mot de passe.';
      erreur.hidden = false;
      return;
    }
    var identifiants = email + ':' + motDePasse;
    bouton.disabled = true;
    A.api('/api/client/profil', { identifiants: identifiants })
      .then(function (profil) {
        etat.identifiants = identifiants;
        etat.profil = profil;
        $id('compte-motdepasse').value = '';
        majNavigation();
        afficherProfil();
        notifier('compte');
        var suite = etat.apresConnexion;
        etat.apresConnexion = null;
        if (suite) {
          // Connexion demandée depuis le formulaire de réservation : on y retourne, pré-rempli.
          $('#modale-compte').one('hidden.bs.modal', suite).modal('hide');
        } else {
          afficherPanneau('espace');
          chargerReservations();
        }
      })
      .catch(function (e) {
        erreur.textContent = e.status === 401 ? 'Adresse e-mail ou mot de passe incorrect.'
          : e.status === 403 ? 'Ce compte appartient au personnel de l\'agence : connectez-vous depuis l\'espace agence.'
          : e.detail;
        erreur.hidden = false;
      })
      .finally(function () { bouton.disabled = false; });
  }

  function deconnecter(message) {
    etat.identifiants = null;
    etat.profil = null;
    etat.reservations = [];
    A.vider($id('compte-reservations'));
    majNavigation();
    notifier('compte');
    afficherPanneau('connexion');
    var info = $id('compte-connexion-info');
    info.textContent = message || '';
    info.hidden = !message;
  }

  /* ---------- Inscription et mot de passe oublié (même principe : un lien par e-mail) ---------- */

  function demanderLien(nom, url) {
    return function (evenement) {
      evenement.preventDefault();
      var champ = $id(nom + '-email');
      var email = champ.value.trim();
      var erreur = $id(nom + '-erreur');
      var bouton = $id(nom + '-envoyer');
      erreur.hidden = true;
      if (!EMAIL.test(email)) {
        champ.classList.add('is-invalid');
        champ.parentNode.querySelector('.invalid-feedback').textContent = 'Saisissez une adresse e-mail valide.';
        champ.focus();
        return;
      }
      bouton.disabled = true;
      A.api(url, { corps: { email: email } })
        .then(function (reponse) {
          $id('form-compte-' + nom).hidden = true;
          var resultat = $id(nom + '-resultat');
          resultat.textContent = reponse.message;
          resultat.hidden = false;
        })
        .catch(function (e) {
          erreur.textContent = e.erreurs.email || e.detail;
          erreur.hidden = false;
        })
        .finally(function () { bouton.disabled = false; });
    };
  }

  /* ---------- Espace client ---------- */

  function afficherProfil() {
    var p = etat.profil;
    $id('compte-nom').textContent = 'Bonjour ' + p.prenom + ' ' + p.nom;
    $id('compte-details').textContent = p.email + ' · Permis ' + p.numPermis + (p.telephone ? ' · ' + p.telephone : '');
  }

  function groupeDe(r, aujourdhui) {
    var active = r.statut === 'EN_ATTENTE' || r.statut === 'CONFIRMEE';
    if (!active || r.dateFin < aujourdhui) { return 'passees'; }
    return r.dateDebut <= aujourdhui ? 'enCours' : 'aVenir';
  }

  function chargerReservations(messageSucces) {
    var zone = $id('compte-reservations');
    if (!zone.firstChild) { zone.appendChild(element('p', 'etat-message', 'Chargement de vos réservations…')); }
    return appel('/api/client/reservations')
      .then(function (reservations) {
        etat.reservations = reservations;
        afficherReservations(messageSucces);
      })
      .catch(function (e) {
        if (etat.identifiants) {
          A.vider(zone).appendChild(element('p', 'etat-message erreur', e.detail));
        }
      });
  }

  function afficherReservations(messageSucces) {
    var zone = A.vider($id('compte-reservations'));
    if (messageSucces) { zone.appendChild(element('div', 'alert alert-success', messageSucces)); }
    if (!etat.reservations.length) {
      zone.appendChild(element('p', 'etat-message etat-message-clair', 'Aucune réservation pour l\'instant. Choisissez un véhicule dans la flotte pour commencer.'));
      return;
    }
    var aujourdhui = dates.aujourdhui();
    var groupes = { enCours: [], aVenir: [], passees: [] };
    etat.reservations.forEach(function (r) { groupes[groupeDe(r, aujourdhui)].push(r); });
    groupes.enCours.sort(function (a, b) { return a.dateDebut < b.dateDebut ? -1 : 1; });
    groupes.aVenir.sort(function (a, b) { return a.dateDebut < b.dateDebut ? -1 : 1; });
    groupes.passees.sort(function (a, b) { return a.dateDebut < b.dateDebut ? 1 : -1; });

    GROUPES.forEach(function (g) {
      var liste = groupes[g.code];
      var section = element('section', 'groupe-reservations');
      section.appendChild(element('h6', 'groupe-reservations-titre', g.titre + ' (' + liste.length + ')'));
      if (!liste.length) {
        section.appendChild(element('p', 'groupe-reservations-vide', g.code === 'aVenir' ? 'Aucune réservation à venir.' : 'Aucune.'));
      }
      liste.forEach(function (r) { section.appendChild(carteReservation(r)); });
      zone.appendChild(section);
    });
  }

  function carteReservation(r) {
    var carte = element('article', 'resa-compte' + (r.statut === 'ANNULEE' ? ' resa-compte-annulee' : ''));
    var entete = element('div', 'resa-compte-entete');
    entete.appendChild(element('strong', null, r.vehicule.marque + ' ' + r.vehicule.modele));
    entete.appendChild(A.badgeStatut(r.statut));
    carte.appendChild(entete);

    carte.appendChild(element('p', 'resa-compte-ligne', 'Du ' + dates.courte(r.dateDebut) + ' au ' + dates.courte(r.dateFin)
      + ' · ' + r.jours + ' jour' + (r.jours > 1 ? 's' : '')
      + (r.vehicule.agence ? ' · ' + r.vehicule.agence + (r.vehicule.ville ? ', ' + r.vehicule.ville : '') : '')));
    var montants = 'N° ' + r.idReservation + ' · Code ' + A.codeLisible(r.codeAcces) + ' · Total ' + A.prix(r.montantTotal);
    if (r.statut !== 'ANNULEE') { montants += ' · Reste à payer ' + A.prix(r.resteAPayer); }
    carte.appendChild(element('p', 'resa-compte-ligne text-muted', montants));

    if (r.annulableParClient) {
      var actions = element('div', 'resa-compte-actions');
      actions.appendChild(boutonAnnuler(actions, r));
      carte.appendChild(actions);
    }
    return carte;
  }

  function boutonAnnuler(actions, r) {
    var bouton = element('button', 'btn btn-outline-danger btn-sm', 'Annuler');
    bouton.type = 'button';
    bouton.addEventListener('click', function () {
      A.vider(actions);
      actions.appendChild(element('span', 'mr-2 font-weight-bold', 'Annuler cette réservation ?'));
      var oui = element('button', 'btn btn-danger btn-sm mr-2', 'Oui, annuler');
      oui.type = 'button';
      var non = element('button', 'btn btn-outline-secondary btn-sm', 'Non');
      non.type = 'button';
      non.addEventListener('click', function () { A.vider(actions).appendChild(boutonAnnuler(actions, r)); });
      oui.addEventListener('click', function () {
        oui.disabled = non.disabled = true;
        oui.textContent = 'Annulation…';
        appel('/api/client/reservations/' + r.idReservation + '/annulation', {})
          .then(function () {
            notifier('reservations');
            return chargerReservations('La réservation n° ' + r.idReservation + ' est annulée. Le véhicule est de nouveau proposé à la location.');
          })
          .catch(function (e) {
            A.vider(actions).appendChild(element('span', 'text-danger mr-2', e.detail));
            actions.appendChild(boutonAnnuler(actions, r));
          });
      });
      actions.appendChild(oui);
      actions.appendChild(non);
      non.focus();
    });
    return bouton;
  }

  /* ---------- Liens directs : index.html#connexion, #inscription, #mot-de-passe-oublie ---------- */

  function lireAncre() {
    var panneau = ANCRES[window.location.hash];
    if (!panneau) { return; }
    history.replaceState(null, '', window.location.pathname + window.location.search);
    ouvrir(panneau);
  }

  /* ---------- Démarrage ---------- */

  function demarrer() {
    $id('form-compte-connexion').addEventListener('submit', seConnecter);
    $id('form-compte-inscription').addEventListener('submit', demanderLien('inscription', '/api/public/clients/inscription'));
    $id('form-compte-oubli').addEventListener('submit', demanderLien('oubli', '/api/public/mot-de-passe/oubli'));
    $id('compte-deconnexion').addEventListener('click', function () { deconnecter('Vous êtes déconnecté.'); });
    $id('compte-reserver').addEventListener('click', function () {
      $('#modale-compte').one('hidden.bs.modal', function () {
        $id('flotte').scrollIntoView({ behavior: 'smooth' });
      }).modal('hide');
    });
    $id('modale-compte').addEventListener('click', function (evenement) {
      var bouton = evenement.target.closest('[data-panneau]');
      if (bouton) { afficherPanneau(bouton.getAttribute('data-panneau')); }
    });
    document.addEventListener('click', function (evenement) {
      var declencheur = evenement.target.closest('[data-action="mon-compte"]');
      if (declencheur) {
        evenement.preventDefault();
        ouvrir('connexion');
      }
    });
    $('#modale-compte').on('shown.bs.modal', function () {
      var visible = $(this).find('[id^="compte-panneau-"]:not([hidden])').find('input:not([hidden]), button.btn-primary').filter(':visible').first();
      if (visible.length) { visible.trigger('focus'); }
    });
    // Une connexion abandonnée (fenêtre fermée) ne doit pas rouvrir plus tard le formulaire de réservation.
    $('#modale-compte').on('hidden.bs.modal', function () {
      if (!etat.identifiants) { etat.apresConnexion = null; }
    });
    window.addEventListener('hashchange', lireAncre);
    lireAncre();
  }

  A.compte = {
    /** Vrai si un client est connecté. */
    actif: function () { return etat.identifiants !== null; },
    /** Fiche du client connecté ({ prenom, nom, email, telephone, numPermis }) ou null. */
    profil: function () { return etat.profil; },
    /** Réservation au nom du client connecté : l'identité vient de la connexion, pas du formulaire. */
    reserver: function (demande) { return appel('/api/client/reservations', demande); },
    /** Ouvre « Mon compte » ; apresConnexion est appelé une fois le client connecté et la fenêtre fermée. */
    ouvrir: ouvrir
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', demarrer);
  } else {
    demarrer();
  }
})(window.jQuery);
