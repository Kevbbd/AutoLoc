/*
 * AutoLoc : fin de l'inscription d'un client, depuis le lien reçu par e-mail.
 *   POST /api/public/clients/inscription/verification  { jeton }
 *   POST /api/public/clients/inscription/finalisation  { jeton, prenom, nom, telephone, numPermis, motDePasse }
 *
 * Le jeton est dans le fragment de l'adresse (#jeton=…) : le navigateur ne l'envoie jamais au
 * serveur dans l'URL. La page le lit, puis l'efface de la barre d'adresse et de l'historique.
 */
(function () {
  'use strict';

  var A = window.AutoLoc;
  var CHAMPS_IDENTITE = ['prenom', 'nom', 'telephone', 'numPermis'];

  var etat = { jeton: null, email: '', clientExistant: false };

  function debutAdresse() {
    var arobase = etat.email.indexOf('@');
    return (arobase > 0 ? etat.email.slice(0, arobase) : etat.email).toLowerCase();
  }

  /* Mêmes règles que RegleMotDePasse côté serveur, qui reste seul juge. */
  var REGLES = {
    longueur: function (m) { return m.length >= 8; },
    lettre: function (m) { return /\p{L}/u.test(m); },
    chiffre: function (m) { return /\p{Nd}/u.test(m); },
    espaces: function (m) { return m.length > 0 && m.trim() === m; },
    adresse: function (m) {
      var debut = debutAdresse();
      return m.length > 0 && (debut.length < 3 || m.toLowerCase().indexOf(debut) === -1);
    }
  };

  var VALIDATIONS = {
    prenom: function (x) { return x.trim() ? null : 'Le prénom est obligatoire.'; },
    nom: function (x) { return x.trim() ? null : 'Le nom est obligatoire.'; },
    telephone: function (x) { return /^[0-9+ .()-]*$/.test(x) ? null : 'Chiffres, espaces, points, tirets ou + uniquement.'; },
    numPermis: function (x) {
      if (!x.trim()) { return 'Le numéro de permis est obligatoire.'; }
      return /^[A-Za-z0-9 -]+$/.test(x) ? null : 'Lettres, chiffres, espaces ou tirets uniquement.';
    }
  };

  function $id(id) {
    return document.getElementById(id);
  }

  function lireJeton() {
    var correspondance = /(?:^#|&)jeton=([A-Za-z0-9_-]+)/.exec(window.location.hash);
    history.replaceState(null, '', window.location.pathname);
    return correspondance ? correspondance[1] : null;
  }

  function lienInvalide(message) {
    $id('inscription-verification').hidden = true;
    $id('form-inscription').hidden = true;
    $id('inscription-invalide-message').textContent = message;
    $id('inscription-invalide').hidden = false;
  }

  function reglesRespectees(motDePasse) {
    var toutes = true;
    Array.prototype.forEach.call(document.querySelectorAll('#inscription-regles li'), function (li) {
      var ok = REGLES[li.getAttribute('data-regle')](motDePasse);
      li.classList.toggle('ok', ok);
      toutes = toutes && ok;
    });
    return toutes;
  }

  function afficherErreurMotDePasse(message) {
    var zone = $id('inscription-motdepasse-erreur');
    zone.textContent = message || '';
    zone.style.display = message ? 'block' : '';
    $id('inscription-motdepasse').classList.toggle('is-invalid', Boolean(message));
  }

  function marquerChamp(nom, message) {
    if (nom === 'motDePasse') {
      afficherErreurMotDePasse(message);
      return true;
    }
    var champ = $id('inscription-' + nom);
    if (!champ) { return false; }
    champ.classList.add('is-invalid');
    champ.parentNode.querySelector('.invalid-feedback').textContent = message;
    return true;
  }

  function preparerFormulaire(inscription) {
    etat.email = inscription.email;
    etat.clientExistant = inscription.clientExistant;
    $id('inscription-email').textContent = inscription.email;
    $id('inscription-identifiant').value = inscription.email;
    if (inscription.clientExistant) {
      // Fiche client déjà connue (réservations sans compte) : seul le mot de passe est demandé.
      $id('inscription-identite').hidden = true;
      var info = $id('inscription-client-existant');
      info.textContent = 'Bonjour ' + inscription.prenom + ' ' + inscription.nom + ', vous avez déjà réservé avec cette adresse. '
        + 'Choisissez simplement votre mot de passe : vos réservations seront rattachées à votre compte.';
      info.hidden = false;
    }
    $id('inscription-verification').hidden = true;
    $id('form-inscription').hidden = false;
    $id(inscription.clientExistant ? 'inscription-motdepasse' : 'inscription-prenom').focus();
  }

  function valider(champ, confirmation) {
    var valide = true;
    if (!etat.clientExistant) {
      CHAMPS_IDENTITE.forEach(function (nom) {
        var message = VALIDATIONS[nom]($id('inscription-' + nom).value);
        if (message) {
          marquerChamp(nom, message);
          valide = false;
        }
      });
    }
    if (!reglesRespectees(champ.value)) {
      afficherErreurMotDePasse('Le mot de passe ne respecte pas toutes les règles ci-dessous.');
      valide = false;
    }
    if (confirmation.value !== champ.value) {
      confirmation.classList.add('is-invalid');
      valide = false;
    }
    return valide;
  }

  function demande(motDePasse) {
    var corps = { jeton: etat.jeton, motDePasse: motDePasse };
    if (!etat.clientExistant) {
      CHAMPS_IDENTITE.forEach(function (nom) { corps[nom] = $id('inscription-' + nom).value.trim(); });
    }
    return corps;
  }

  function demarrer() {
    etat.jeton = lireJeton();
    if (!etat.jeton) {
      lienInvalide('Ce lien est incomplet. Ouvrez le lien complet reçu par e-mail, ou recommencez l\'inscription.');
      return;
    }

    A.api('/api/public/clients/inscription/verification', { corps: { jeton: etat.jeton } })
      .then(preparerFormulaire)
      .catch(function (e) { lienInvalide(e.erreurs.jeton || e.detail); });

    var champ = $id('inscription-motdepasse');
    var confirmation = $id('inscription-confirmation');

    champ.addEventListener('input', function () {
      reglesRespectees(champ.value);
      afficherErreurMotDePasse(null);
    });

    $id('inscription-afficher').addEventListener('click', function () {
      var visible = champ.type === 'password';
      champ.type = confirmation.type = visible ? 'text' : 'password';
      this.textContent = visible ? 'Masquer' : 'Afficher';
      this.setAttribute('aria-pressed', String(visible));
    });

    $id('form-inscription').addEventListener('submit', function (evenement) {
      evenement.preventDefault();
      var erreur = $id('inscription-erreur');
      erreur.hidden = true;
      if (!valider(champ, confirmation)) {
        var premier = document.querySelector('#form-inscription .is-invalid');
        if (premier) { premier.focus(); }
        return;
      }

      var bouton = $id('inscription-valider');
      bouton.disabled = true;
      A.api('/api/public/clients/inscription/finalisation', { corps: demande(champ.value) })
        .then(function (reponse) {
          champ.value = confirmation.value = '';
          $id('form-inscription').hidden = true;
          $id('inscription-succes-message').textContent = reponse.message;
          $id('inscription-succes').hidden = false;
        })
        .catch(function (e) {
          if (e.erreurs.jeton) {
            lienInvalide(e.erreurs.jeton);
            return;
          }
          var places = Object.keys(e.erreurs).filter(function (nom) { return marquerChamp(nom, e.erreurs[nom]); });
          if (!places.length) {
            erreur.textContent = e.detail;
            erreur.hidden = false;
          }
          bouton.disabled = false;
        });
    });
  }

  // Lien ouvert dans un onglet déjà sur cette page : seul le fragment change, la page ne se recharge
  // pas d'elle-même. On recharge pour repartir proprement avec le nouveau jeton.
  window.addEventListener('hashchange', function () {
    if (/jeton=/.test(window.location.hash)) { window.location.reload(); }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', demarrer);
  } else {
    demarrer();
  }
})();
