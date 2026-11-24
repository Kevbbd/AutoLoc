/*
 * AutoLoc : choix d'un nouveau mot de passe depuis le lien reçu par e-mail.
 *   POST /api/public/mot-de-passe/verification      { jeton }
 *   POST /api/public/mot-de-passe/reinitialisation  { jeton, motDePasse } → { message, espace }
 * Sert aux clients comme au personnel : « espace » (CLIENT ou AGENCE) choisit la page de connexion.
 *
 * Le jeton est dans le fragment de l'adresse (#jeton=…) : le navigateur ne l'envoie jamais au
 * serveur dans l'URL. La page le lit, puis l'efface de la barre d'adresse et de l'historique.
 */
(function () {
  'use strict';

  var A = window.AutoLoc;

  var REGLES = {
    longueur: function (m) { return m.length >= 8; },
    lettre: function (m) { return /\p{L}/u.test(m); },
    chiffre: function (m) { return /\p{Nd}/u.test(m); },
    espaces: function (m) { return m.length > 0 && m.trim() === m; }
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
    $id('reinit-verification').hidden = true;
    $id('form-reinit').hidden = true;
    $id('reinit-invalide-message').textContent = message;
    $id('reinit-invalide').hidden = false;
  }

  function reglesRespectees(motDePasse) {
    var toutes = true;
    Array.prototype.forEach.call(document.querySelectorAll('#reinit-regles li'), function (li) {
      var ok = REGLES[li.getAttribute('data-regle')](motDePasse);
      li.classList.toggle('ok', ok);
      toutes = toutes && ok;
    });
    return toutes;
  }

  function afficherErreurMotDePasse(message) {
    var zone = $id('reinit-motdepasse-erreur');
    zone.textContent = message || '';
    zone.style.display = message ? 'block' : '';
    $id('reinit-motdepasse').classList.toggle('is-invalid', Boolean(message));
  }

  function demarrer() {
    var jeton = lireJeton();
    if (!jeton) {
      lienInvalide('Ce lien est incomplet. Ouvrez le lien complet reçu par e-mail, ou faites une nouvelle demande.');
      return;
    }

    A.api('/api/public/mot-de-passe/verification', { corps: { jeton: jeton } })
      .then(function () {
        $id('reinit-verification').hidden = true;
        $id('form-reinit').hidden = false;
        $id('reinit-motdepasse').focus();
      })
      .catch(function (e) { lienInvalide(e.erreurs.jeton || e.detail); });

    var champ = $id('reinit-motdepasse');
    var confirmation = $id('reinit-confirmation');

    champ.addEventListener('input', function () {
      reglesRespectees(champ.value);
      afficherErreurMotDePasse(null);
    });
    confirmation.addEventListener('input', function () { confirmation.classList.remove('is-invalid'); });

    $id('reinit-afficher').addEventListener('click', function () {
      var visible = champ.type === 'password';
      champ.type = confirmation.type = visible ? 'text' : 'password';
      this.textContent = visible ? 'Masquer' : 'Afficher';
      this.setAttribute('aria-pressed', String(visible));
    });

    $id('form-reinit').addEventListener('submit', function (evenement) {
      evenement.preventDefault();
      var erreur = $id('reinit-erreur');
      erreur.hidden = true;
      var valide = true;
      if (!reglesRespectees(champ.value)) {
        afficherErreurMotDePasse('Le mot de passe ne respecte pas toutes les règles ci-dessous.');
        valide = false;
      }
      if (confirmation.value !== champ.value) {
        confirmation.classList.add('is-invalid');
        valide = false;
      }
      if (!valide) { return; }

      var bouton = $id('reinit-valider');
      bouton.disabled = true;
      A.api('/api/public/mot-de-passe/reinitialisation', { corps: { jeton: jeton, motDePasse: champ.value } })
        .then(function (reponse) {
          champ.value = confirmation.value = '';
          $id('form-reinit').hidden = true;
          $id('reinit-succes-message').textContent = reponse.message;
          $id('reinit-succes-lien').href = reponse.espace === 'AGENCE' ? 'admin.html#reinitialise' : 'index.html#connexion';
          $id('reinit-succes').hidden = false;
        })
        .catch(function (e) {
          if (e.erreurs.jeton) {
            lienInvalide(e.erreurs.jeton);
          } else if (e.erreurs.motDePasse) {
            afficherErreurMotDePasse(e.erreurs.motDePasse);
          } else {
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
