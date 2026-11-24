/*
 * AutoLoc : outils partagés par la vitrine (autoloc.js) et l'espace agence (admin.js).
 * Aucune donnée venant du serveur n'est insérée avec innerHTML : uniquement textContent.
 */
(function () {
  'use strict';

  var formatPrix = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });
  var formatDateLongue = new Intl.DateTimeFormat('fr-FR', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
  var formatDateCourte = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' });
  var formatDateHeure = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
  var JOUR_MS = 86400000;

  /* ---------- Dates au format ISO « AAAA-MM-JJ », sans piège de fuseau horaire ---------- */

  function versUtc(iso) {
    var p = iso.split('-');
    return Date.UTC(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
  }

  function iso(date) {
    return date.getUTCFullYear() + '-' + String(date.getUTCMonth() + 1).padStart(2, '0') + '-' + String(date.getUTCDate()).padStart(2, '0');
  }

  var dates = {
    aujourdhui: function () {
      var maintenant = new Date();
      return iso(new Date(Date.UTC(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate())));
    },
    ajouterJours: function (jour, n) {
      return iso(new Date(versUtc(jour) + n * JOUR_MS));
    },
    /** Nombre de jours de la période [debut, fin) : identique au calcul du serveur. */
    joursEntre: function (debut, fin) {
      return Math.round((versUtc(fin) - versUtc(debut)) / JOUR_MS);
    },
    longue: function (jour) {
      return formatDateLongue.format(new Date(versUtc(jour)));
    },
    courte: function (jour) {
      return formatDateCourte.format(new Date(versUtc(jour)));
    },
    heure: function (dateHeure) {
      return formatDateHeure.format(new Date(dateHeure));
    }
  };

  /* ---------- DOM ---------- */

  function element(balise, classe, texte) {
    var el = document.createElement(balise);
    if (classe) { el.className = classe; }
    if (texte !== undefined && texte !== null) { el.textContent = String(texte); }
    return el;
  }

  function vider(el) {
    while (el.firstChild) { el.removeChild(el.firstChild); }
    return el;
  }

  /* ---------- Appels à l'API (erreurs au format RFC 9457) ---------- */

  /**
   * Appelle l'API et renvoie le JSON. En cas d'échec, rejette avec
   * { status, titre, detail, erreurs } où erreurs associe un champ à son message.
   */
  function api(url, options) {
    var reglages = options || {};
    var entetes = { Accept: 'application/json' };
    if (reglages.corps !== undefined) { entetes['Content-Type'] = 'application/json'; }
    if (reglages.identifiants) {
      entetes.Authorization = 'Basic ' + btoa(unescape(encodeURIComponent(reglages.identifiants)));
      // Spring Security répond alors 401 sans en-tête WWW-Authenticate : pas de fenêtre de connexion du navigateur
      entetes['X-Requested-With'] = 'XMLHttpRequest';
    }
    return fetch(url, {
      method: reglages.methode || (reglages.corps !== undefined ? 'POST' : 'GET'),
      headers: entetes,
      body: reglages.corps !== undefined ? JSON.stringify(reglages.corps) : undefined
    }).then(function (reponse) {
      var type = reponse.headers.get('Content-Type') || '';
      var lecture = type.indexOf('json') !== -1 ? reponse.json() : Promise.resolve(null);
      return lecture.then(function (donnees) {
        if (reponse.ok) { return donnees; }
        var erreur = new Error((donnees && donnees.detail) || ('Erreur HTTP ' + reponse.status));
        erreur.status = reponse.status;
        erreur.titre = (donnees && donnees.title) || 'Erreur';
        erreur.detail = erreur.message;
        erreur.erreurs = (donnees && donnees.erreurs) || {};
        throw erreur;
      });
    }, function () {
      var erreur = new Error('Le serveur AutoLoc ne répond pas. Vérifiez qu\'il est démarré.');
      erreur.status = 0;
      erreur.titre = 'Serveur injoignable';
      erreur.detail = erreur.message;
      erreur.erreurs = {};
      throw erreur;
    });
  }

  /* ---------- Code de réservation (mêmes règles que CodeAcces côté serveur) ---------- */

  var ALPHABET_CODE = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

  /** Saisie d'un humain (minuscules, espaces, tiret) vers le code ; null si ce ne peut pas en être un. */
  function normaliserCode(saisie) {
    var code = String(saisie || '').replace(/[\s-]/g, '').toUpperCase();
    if (code.length !== 10) { return null; }
    for (var i = 0; i < code.length; i++) {
      if (ALPHABET_CODE.indexOf(code.charAt(i)) === -1) { return null; }
    }
    return code;
  }

  function codeLisible(code) {
    return code ? code.slice(0, 5) + '-' + code.slice(5) : '';
  }

  var STATUTS_RESERVATION = {
    EN_ATTENTE: { libelle: 'En attente', classe: 'attente' },
    CONFIRMEE: { libelle: 'Confirmée', classe: 'confirmee' },
    ANNULEE: { libelle: 'Annulée', classe: 'annulee' },
    TERMINEE: { libelle: 'Terminée', classe: 'terminee' }
  };

  var MODES_PAIEMENT = { CARTE: 'Carte bancaire', ESPECES: 'Espèces', VIREMENT: 'Virement' };

  function badgeStatut(statut) {
    var info = STATUTS_RESERVATION[statut] || { libelle: statut, classe: 'attente' };
    return element('span', 'badge-reservation badge-' + info.classe, info.libelle);
  }

  /* Une erreur affichée sous un champ disparaît dès que l'utilisateur le corrige ;
     la validation complète est rejouée à la soumission suivante. Écouteur en phase
     de capture : il s'exécute AVANT ceux du champ, qui peuvent donc re-signaler une
     erreur (ex. : contrôle immédiat des dates) sans qu'elle soit aussitôt effacée. */
  function oublierErreurChamp(evenement) {
    var champ = evenement.target;
    if (champ.classList && champ.classList.contains('is-invalid')) { champ.classList.remove('is-invalid'); }
  }
  document.addEventListener('input', oublierErreurChamp, true);
  document.addEventListener('change', oublierErreurChamp, true);

  window.AutoLoc = {
    api: api,
    normaliserCode: normaliserCode,
    codeLisible: codeLisible,
    dates: dates,
    element: element,
    vider: vider,
    prix: function (montant) { return formatPrix.format(Number(montant)); },
    badgeStatut: badgeStatut,
    STATUTS_RESERVATION: STATUTS_RESERVATION,
    MODES_PAIEMENT: MODES_PAIEMENT
  };
})();
