# Notes de sécurité — AutoLoc

## Configuration

`config/SecurityConfig` déclare deux beans :

- un `SecurityFilterChain` qui définit les règles d'accès ;
- un `PasswordEncoder` BCrypt.

Les comptes sont stockés **dans MySQL**, table `utilisateur`, avec une empreinte BCrypt et jamais le mot de passe en clair. Ils sont lus par `service/compte/UtilisateurDetailsService`, que Spring Security utilise automatiquement. `config/ComptesInitializer` crée au premier démarrage les deux comptes du TP : `student / student123` (rôle `USER`, e-mail `student@autoloc.test`) et `admin / admin123` (rôle `ADMIN`, e-mail `admin@autoloc.test`). Un compte existant n'est jamais écrasé, si bien qu'un mot de passe changé survit aux redémarrages.

> Première version du TP : deux comptes en mémoire (`InMemoryUserDetailsManager`). Le passage en base était nécessaire pour la fonction « mot de passe oublié » : un mot de passe changé en mémoire serait perdu au redémarrage. Voir les deux dernières questions ci-dessous.

L'authentification se fait en **HTTP Basic** : chaque requête envoie l'en-tête `Authorization: Basic base64(utilisateur:motdepasse)`.

### Règles d'accès

| Ordre | Motif d'URL | Règle | Raison |
|---|---|---|---|
| 1 | `/`, `/index.html`, `/admin.html`, `/reinitialisation.html`, `/inscription.html`, `/favicon.svg`, `/css/**`, `/js/**`, `/images/**`, `/fonts/**` | `permitAll()` | Fichiers statiques de la vitrine. Ces pages sont publiques mais ne contiennent aucune donnée : tout ce qu'elles affichent vient de l'API, protégée. |
| 2 | `/api/public/**` | `permitAll()` | Routes publiques : flotte, devis, réservation, consultation et annulation par le client. |
| 3 | `/actuator/health`, `/actuator/prometheus` | `permitAll()` | Prometheus doit pouvoir collecter les métriques sans compte. |
| 4 | `/error` | `permitAll()` | Page d'erreur de Spring Boot : permet de renvoyer un corps JSON lisible avec les 401/403/404. |
| 5 | `/api/admin/**` | `hasRole("ADMIN")` | Routes d'administration, dont le back-office des réservations. |
| 6 | `/api/client/**` | `hasRole("CLIENT")` | Espace du client connecté. Le personnel n'y a pas accès (403). |
| 7 | `/api/**` | `hasAnyRole("USER", "ADMIN")` | Reste de l'API, pour le personnel. Un client connecté reçoit 403 : il n'a accès qu'à son espace. |
| 8 | toute autre requête | `authenticated()` | Refus par défaut de ce qui n'est pas prévu. |

### Matrice de test (Postman ou curl)

| Requête | Anonyme | `student` (USER) | `admin` (ADMIN) |
|---|---|---|---|
| `GET /api/public/ping` | 200 | 200 | 200 |
| `GET /api/hello` | **401** | 200 | 200 |
| `GET /api/admin/report` | **401** | **403** | 200 |
| `GET /api/admin/reservations` | **401** | **403** | 200 |
| `GET /admin.html` (page sans données) | 200 | 200 | 200 |

Collection Postman prête à importer : `docs/postman/AutoLoc-security.postman_collection.json`. Les mêmes vérifications en curl :

```bash
curl -i http://localhost:8080/api/public/ping                        # 200
curl -i http://localhost:8080/api/hello                              # 401
curl -i -u student:student123 http://localhost:8080/api/hello        # 200
curl -i -u student:student123 http://localhost:8080/api/admin/report # 403
curl -i -u admin:admin123 http://localhost:8080/api/admin/report     # 200
```

Ces 9 cas, plus les deux endpoints Actuator, sont aussi vérifiés par `SecurityRulesTest` : de vraies requêtes HTTP sur l'application démarrée, sans mock.

## Réponses aux questions du TP

**Pourquoi `/api/admin/**` doit-il être déclaré avant la règle plus large `/api/**` ?**
Spring Security parcourt les règles dans l'ordre de déclaration et applique **la première qui correspond**. `/api/admin/report` correspond aussi à `/api/**`. Si `/api/**` était placé en premier, un simple `USER` serait autorisé sur les routes d'administration, et la règle `ADMIN` ne serait jamais atteinte. Les règles vont donc de la plus précise à la plus large. Spring Security 6+ refuse d'ailleurs de démarrer si une règle est placée après `anyRequest()`.

**Quelle différence entre 401 Unauthorized et 403 Forbidden ?**

- **401** : l'application ne sait pas qui fait la requête (identifiants absents ou faux). La réponse contient l'en-tête `WWW-Authenticate: Basic`, qui invite à s'authentifier.
- **403** : l'application sait qui fait la requête, mais cet utilisateur n'a pas le droit. C'est le cas de `student` sur `/api/admin/report`. Se ré-authentifier avec le même compte ne changera rien.

En résumé, 401 est un échec d'**authentification** et 403 un échec d'**autorisation**.

**Pourquoi encoder les mots de passe avec BCrypt plutôt que les stocker en clair ?**
Si la base ou la mémoire fuit, des mots de passe en clair sont immédiatement utilisables, sur ce site et sur tous ceux où l'utilisateur a réutilisé le même mot de passe. BCrypt est une fonction de hachage à sens unique, conçue pour cela :

- elle ajoute un **sel aléatoire** à chaque mot de passe : deux mots de passe identiques donnent deux hachés différents, ce qui rend inutiles les tables pré-calculées ;
- elle est **volontairement lente** (facteur de coût réglable), ce qui rend les attaques par force brute très coûteuses.

À la connexion, on ne déchiffre rien : on hache le mot de passe saisi et on compare (`passwordEncoder.matches`).

**Pourquoi les utilisateurs en mémoire conviennent-ils au TP mais pas à la production ?**
Ils suffisent pour démontrer les règles d'accès sans base de données. En production :

- les comptes sont codés en dur et versionnés dans Git ;
- on ne peut ni créer un compte, ni changer un mot de passe, ni révoquer un accès sans redéployer ;
- ils disparaissent au redémarrage et ne sont pas partagés entre plusieurs instances de l'application.

**Que faudrait-il pour charger les utilisateurs et rôles depuis MySQL ?** *(c'est désormais ce que fait le projet)*

1. Une entité `Utilisateur` (identifiant unique, mot de passe **haché BCrypt**, actif/inactif) et des rôles (énumération ou entité `Role` en `@ManyToMany`), avec un `UtilisateurRepository.findByUsername(...)`.
2. Une classe `@Service` qui implémente `UserDetailsService` : sa méthode `loadUserByUsername` lit l'utilisateur en base et le convertit en `UserDetails`, en ajoutant le préfixe `ROLE_` aux rôles.
3. Supprimer le bean `InMemoryUserDetailsManager` et garder le `PasswordEncoder` BCrypt.
4. Une procédure pour créer le premier administrateur sans mot de passe en clair dans le code (script de migration ou variable d'environnement au premier démarrage).

Pour un schéma très simple, `JdbcUserDetailsManager` (tables `users` / `authorities` standard) est une alternative sans entité JPA.

Dans le projet : entité `domain/Utilisateur` (identifiant et e-mail uniques, empreinte BCrypt, rôle `USER`/`ADMIN`, actif), `repository/UtilisateurRepository`, `service/compte/UtilisateurDetailsService` et `config/ComptesInitializer`. Le bean `InMemoryUserDetailsManager` a été supprimé.

## Mot de passe oublié

Le parcours :

1. Sur `admin.html` (personnel) ou dans « Mon compte » sur le site (clients), le lien « Mot de passe oublié ? » demande l'adresse e-mail du compte.
2. `POST /api/public/mot-de-passe/oubli` répond toujours **202** avec le même message. Si l'adresse correspond à un compte actif, un lien est envoyé par e-mail.
3. Le lien ouvre `reinitialisation.html`. La page vérifie le lien (`POST …/verification`), puis enregistre le nouveau mot de passe (`POST …/reinitialisation`). La réponse indique l'espace du compte (`CLIENT` ou `AGENCE`) : le bouton « Se connecter » mène au site ou à l'espace agence.

En local, les e-mails arrivent dans **Mailpit**, une boîte de réception de test lancée par `docker compose` : <http://localhost:8025>. Rien n'est envoyé sur Internet.

| Mesure | Pourquoi |
|---|---|
| Jeton de **256 bits** tiré par `SecureRandom` | Impossible à deviner, même par essais massifs. |
| En base, seulement l'**empreinte SHA-256** du jeton | Une fuite de la table (sauvegarde, injection SQL) ne donne aucun lien utilisable. SHA-256 suffit ici, alors que les mots de passe demandent BCrypt : on peut deviner un mot de passe humain, pas 2²⁵⁶ jetons. |
| Lien valable **30 minutes**, **une seule fois** | Limite la fenêtre d'exploitation d'un e-mail intercepté. La consommation est une mise à jour conditionnelle atomique (`… where utilise_le is null and expire_le > now`) : huit envois simultanés du même lien, un seul réussit (testé). |
| Une nouvelle demande, ou le changement du mot de passe, **invalide les autres liens** | Seul le dernier lien envoyé fonctionne. |
| **Même réponse**, que l'adresse existe ou non | La réponse ne permet pas de savoir quelles adresses ont un compte. |
| E-mail envoyé **après validation et en tâche de fond** (`@TransactionalEventListener` + `@Async`) | Aucun lien n'est envoyé pour un jeton qui n'aurait pas été enregistré. Le temps de réponse ne dépend pas de l'existence du compte, ce qui empêche de le mesurer pour deviner les adresses. |
| **3 demandes par heure** au maximum par compte | Empêche d'inonder la boîte d'un employé. Au-delà, la réponse reste la même, mais aucun e-mail n'est envoyé. |
| Jeton après **`#`** dans le lien (`reinitialisation.html#jeton=…`) | Le navigateur n'envoie jamais le fragment au serveur : le jeton n'apparaît ni dans les journaux HTTP ni dans l'en-tête `Referer`. La page l'efface aussitôt de la barre d'adresse et de l'historique, et déclare `referrer: no-referrer`. |
| Adresse du lien lue dans la configuration (`autoloc.url-publique`) | Si elle venait de l'en-tête `Host` de la requête, un attaquant pourrait faire envoyer à sa victime un vrai e-mail contenant un lien vers son propre site (*host header injection*). |
| Règles du mot de passe : 8 à 64 caractères, au moins une lettre et un chiffre, pas d'espace au début ni à la fin, ne contient pas l'identifiant (pour un client : le début de son adresse e-mail, s'il fait au moins 3 caractères) | Vérifiées côté serveur (`RegleMotDePasse`) et affichées en direct dans la page. La limite de 72 **octets** existe parce que BCrypt ignore tout ce qui dépasse ; Spring Security refuse d'ailleurs ces mots de passe. |
| Un mot de passe refusé ne consomme pas le lien | On peut corriger et réessayer sans refaire de demande. |

| **E-mail d'alerte** après chaque changement de mot de passe | Si quelqu'un d'autre a changé le mot de passe, le titulaire le sait aussitôt. |

Limite assumée : il n'y a pas de CAPTCHA, mais les formulaires publics sont limités par adresse IP (voir l'audit ci-dessous). Un écart de quelques millisecondes subsiste entre un compte existant (une écriture en base) et un compte inexistant.

## Comptes clients

Le compte est **facultatif** : on peut toujours réserver sans compte, avec le code secret. Avec un compte, le client se connecte par son adresse e-mail, retrouve toutes ses réservations (en cours, à venir, passées) et réserve sans ressaisir son identité.

Le parcours d'inscription :

1. Dans « Mon compte », « Créer un compte » ne demande **que l'adresse e-mail**. `POST /api/public/clients/inscription` répond toujours **202** avec le même message.
2. L'e-mail contient un lien vers `inscription.html#jeton=…`, valable 24 heures.
3. Sur cette page, le client complète son identité (prénom, nom, permis) et **choisit alors son mot de passe**. Le compte n'est créé qu'à ce moment.

| Mesure | Pourquoi |
|---|---|
| Mot de passe choisi **après** la confirmation de l'adresse, pas avant | Sinon, attaque par *pré-détournement de compte* : quelqu'un s'inscrit avec l'adresse d'un client et fixe lui-même le mot de passe. Il suffit ensuite que la victime clique sur le lien reçu pour activer un compte dont l'attaquant connaît le mot de passe, et qui affiche ses réservations. |
| **Rien n'est créé** avant la confirmation : ni compte, ni fiche client | Une demande d'inscription pour l'adresse d'autrui ne laisse aucune trace exploitable. |
| **Même réponse** que l'adresse ait un compte ou non | Pas d'énumération des clients. Si l'adresse a déjà un compte, son titulaire reçoit un e-mail « vous avez déjà un compte » (sans lien) : il est prévenu, l'attaquant n'apprend rien. Pour une adresse du personnel, rien n'est envoyé : une adresse correspond à un seul compte. |
| Jeton de 256 bits, **empreinte SHA-256** en base, usage unique, après `#` dans le lien | Mêmes garanties que le mot de passe oublié. Une nouvelle demande invalide le lien précédent. |
| **3 demandes par heure** et par adresse | Empêche d'inonder une boîte de réception. Au-delà, même réponse, aucun e-mail. |
| Lien **consommé en premier**, dans la transaction | Deux finalisations simultanées du même lien : la seconde attend le verrou de la ligne puis trouve le lien utilisé. Un seul compte est créé (testé avec 8 envois simultanés). Si une étape suivante échoue (permis manquant, déjà pris), la transaction est annulée et le lien reste utilisable. Le calcul BCrypt est fait **avant** de prendre le verrou, pour le tenir le moins longtemps possible. |
| Réservations faites sans compte **rattachées** au compte | La fiche client de cette adresse existe déjà : elle est reliée au compte. C'est sûr parce que la possession de l'adresse vient d'être prouvée par le lien. |
| Rôle **CLIENT** séparé du personnel | Un client n'accède qu'à `/api/client/**`, le personnel n'y accède pas. Un client se connecte **uniquement par son e-mail**, le personnel **uniquement par son identifiant** : chaque compte a un seul nom de connexion, donc le blocage après 5 échecs ne peut pas être contourné en alternant les deux. |
| L'identité vient **de la connexion**, jamais de la requête | `POST /api/client/reservations` ne contient que le véhicule et les dates. Le client est celui de l'adresse authentifiée : impossible de réserver au nom d'un autre, même en ajoutant des champs au JSON (testé). |
| Cloisonnement entre clients | Les réservations sont toujours cherchées **avec** l'adresse du client connecté. Celle d'un autre client répond 404, comme si elle n'existait pas. |
| Blocage après 5 échecs et mot de passe oublié **étendus aux clients** | Mêmes composants que pour le personnel, sans code supplémentaire. |
| Identifiants gardés en mémoire dans l'onglet | Ni cookie, ni `localStorage` : un script ou une extension ne peut pas les retrouver plus tard. Recharger la page déconnecte (c'est indiqué au client). |

**Migration de la base.** La colonne `utilisateur.role` avait été créée par Hibernate en type `enum('ADMIN','USER')` sous MariaDB. `ddl-auto=update` ne modifie jamais une colonne existante, et MariaDB aurait refusé la valeur `CLIENT`. Au démarrage, `config/MigrationSchema` détecte ce cas et convertit la colonne en `varchar(20)` (une seule fois, sans perte). Le rôle est désormais stocké en texte (`@JdbcTypeCode(SqlTypes.VARCHAR)`) : ajouter un rôle ne demandera plus de migration.

## Choix complémentaires

- **Session sans état (`STATELESS`) et CSRF désactivé.** Cette API est appelée par Postman ou par un client JavaScript qui envoie ses identifiants à chaque requête. Sans session ni cookie d'authentification, une attaque CSRF n'a rien à exploiter. Garder la protection CSRF active ferait échouer avec un **403** tous les futurs `POST` / `PUT` / `DELETE` (Workshop 5), même authentifiés. Si l'API est un jour consommée via une session navigateur (cookie), il faudra réactiver CSRF.
- **Actuator.** Seuls `health` et `prometheus` sont exposés. Les détails de santé (base de données, disque) ne sont visibles que par un `ADMIN` authentifié (`show-details=when-authorized`). Le statut `UP`/`DOWN` reste public. C'est vrai aussi en profil `dev` : l'application écoute sur le réseau, et ces détails n'ont pas à être lisibles depuis le Wi-Fi.
- **Espace agence sans fenêtre d'authentification du navigateur.** `admin.html` envoie l'en-tête `X-Requested-With: XMLHttpRequest` avec ses appels. Spring Security reconnaît alors un appel JavaScript et répond 401 **sans** `WWW-Authenticate` : le navigateur n'ouvre pas sa fenêtre native et la page affiche son propre message. Les identifiants restent en mémoire dans l'onglet (jamais dans `localStorage` ni dans un cookie) : recharger la page déconnecte.
- **Données personnelles hors des URL.** La consultation et l'annulation d'une réservation par le client passent l'e-mail dans le **corps** d'une requête `POST`, jamais dans l'URL : les URL finissent dans les journaux du serveur, l'historique du navigateur et l'en-tête `Referer`. Le numéro de réservation seul ne suffit pas (il est séquentiel, donc devinable) : l'e-mail doit correspondre, et un mauvais couple renvoie la même 404 qu'un numéro inexistant, pour ne pas révéler quels numéros existent.
- **Identifiants.** `student123` et `admin123` sont des identifiants de TP, documentés dans l'énoncé. Le mot de passe MySQL, lui, n'est jamais dans le code : il est lu dans la variable d'environnement `DB_PASSWORD`.

## Audit de sécurité

Revue de tout ce qui est exposé : connexion, formulaires publics, réinitialisation, en-têtes HTTP, journaux, conteneurs Docker. Pour chaque faiblesse : sa correction et le test qui la vérifie.

### Faiblesses trouvées et corrigées

| Gravité | Faiblesse | Correction | Vérifié par |
|---|---|---|---|
| **Critique** | Mailpit (`8025`), Grafana (`3000`, `admin/admin`) et Prometheus (`9090`) étaient ouverts à **tout le réseau local** (`"8025:8025"` écoute sur toutes les interfaces). Sur le Wi-Fi de l'école, un voisin pouvait lire les liens de réinitialisation dans Mailpit et prendre le compte `admin`. | Ports liés à `127.0.0.1` dans `docker-compose.yml` : accessibles depuis ce PC seulement. | Lecture de `docker-compose.yml` |
| **Élevée** | **Force brute** sur la connexion : essais de mots de passe illimités sur `admin`. | `securite/ProtectionConnexion` : 5 échecs en 15 min bloquent l'identifiant 15 min, 20 échecs bloquent l'adresse IP. Réponse **429** avec `Retry-After`, sans même vérifier le mot de passe. Les identifiants inexistants sont traités pareil. | `ProtectionConnexionApiTest` (HTTP réel), `CompteurEchecsTest`, `FiltresSecuriteTest` |
| **Élevée** | **Réservation devinable** : le client la retrouvait avec son numéro (séquentiel : 1, 2, 3…) et son e-mail (qui n'est pas un secret). Connaissant l'e-mail de quelqu'un, on pouvait essayer les numéros pour lire ses coordonnées et annuler sa location. | **Code secret** aléatoire de 10 caractères (`domain/CodeAcces`, ≈ 49 bits) remis à la réservation et exigé avec l'e-mail. L'ancienne route par numéro est supprimée. | `CodeAccesTest`, `ReservationApiTest` (`consulterExigeLeCodeEtLeBonEmail`, `leNumeroSequentielNePermetPlusDAnnuler`) |
| Moyenne | Pas de **Content-Security-Policy**. Or l'espace agence garde les identifiants en mémoire : une injection de script les aurait volés. | CSP stricte (voir ci-dessous), plus `Referrer-Policy` et `Permissions-Policy`. Les 4 styles en ligne du template sont passés dans la feuille CSS. Deux bibliothèques du template sont corrigées : `jquery.stellar` créait un `style=""` ; `scrollax`, inutilisé, injectait un `<style>`. | `SecurityRulesTest.ajouteLesEnTetesDeProtection`. Les 3 pages ont été parcourues dans Chrome sous cette CSP : **0 violation**. |
| Moyenne | **Formulaires publics illimités** : essais de codes en série, fausses réservations en masse, envoi massif d'e-mails. | `securite/FiltreLimiteDebit` : 30 envois par minute et par IP sur `POST /api/public/**`, puis **429**. Les lectures ne sont pas limitées. | `LimiteurDebitTest`, `FiltresSecuriteTest` |
| Moyenne | En profil `dev`, les **paramètres SQL étaient journalisés** (`org.hibernate.orm.jdbc.bind=TRACE`) : empreintes de mots de passe, e-mails et téléphones finissaient dans `logs\app.log`. | Réglage retiré. Les requêtes restent journalisées, sans leurs valeurs. | `application-dev.properties` |
| Moyenne | En profil `dev`, `/actuator/health` affichait à tous le détail de la base et du disque, alors que l'application écoute sur le réseau. | Détails réservés à `ADMIN`, comme en production. | `application-dev.properties` |
| Faible | Identifiant sensible à la casse sous H2, mais pas sous MariaDB : le comportement changeait selon la base. | Identifiant normalisé en minuscules partout (base, connexion, blocage). | `SecurityRulesTest.lIdentifiantNeTientPasCompteDeLaCasse` |
| Faible | Mot de passe changé sans que le titulaire le sache. | E-mail d'alerte après chaque changement. | `MotDePasseApiTest.parcoursComplet` |
| Faible | DevTools ouvrait un serveur LiveReload (port 35729) inutile. | `spring.devtools.livereload.enabled=false`. | `application.properties` |

### Content-Security-Policy

```
default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com;
font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self';
object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'
```

Ni `'unsafe-inline'` ni `'unsafe-eval'` : le navigateur n'exécute que les fichiers `.js` servis par l'application, et le JavaScript ne peut contacter que l'application. C'est une **défense en profondeur** : le code n'insère déjà jamais de données avec `innerHTML`, seulement avec `textContent`. Spring Security ajoute de lui-même `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` et `Cache-Control: no-store`, ainsi que `Strict-Transport-Security` dès que le site est servi en HTTPS.

### Ce qui était déjà correct

- Mots de passe hachés en **BCrypt** ; comparaison en temps constant ; temps de réponse identique pour un identifiant inconnu.
- Aucune requête SQL construite par concaténation : requêtes dérivées, JPQL ou SQL natif **paramétrés**.
- Entrées validées (Bean Validation) ; erreurs au format RFC 9457 sans trace technique (`server.error.include-stacktrace` vaut `never` par défaut).
- Aucune entité exposée directement : des DTO en `record`. Un client ne peut donc pas modifier un champ qu'on ne lui propose pas (*mass assignment*).
- Données personnelles dans le corps des requêtes `POST`, jamais dans l'URL.
- Concurrence : verrous pessimistes, aucune double réservation ni trop-perçu (voir `reservation.md`).

### Risques assumés (TP en local) et ce qu'il faudrait en production

| Point | Pourquoi c'est acceptable ici | En production |
|---|---|---|
| HTTP sans chiffrement | Tout tourne sur `localhost`. | **HTTPS obligatoire** : HTTP Basic envoie le mot de passe à chaque requête. HSTS s'active alors automatiquement. |
| `/actuator/prometheus` public | Demandé par le TP : Prometheus doit lire les métriques sans compte. Elles ne contiennent ni donnée personnelle ni secret. | Port de gestion séparé (`management.server.port`), joignable seulement par le réseau interne. |
| MySQL `root` sans mot de passe | Réglage par défaut de XAMPP, base locale. | Utilisateur dédié avec les seuls droits `SELECT, INSERT, UPDATE, DELETE` sur `autoloc_db`, et mot de passe dans `DB_PASSWORD`. |
| Comptes du TP (`admin123`…) créés au démarrage | Identifiants imposés par l'énoncé. | Premier administrateur créé par une procédure, sans mot de passe dans le code ; MFA pour l'espace agence. |
| Blocage par identifiant | Un attaquant peut bloquer `admin` 15 min en se trompant 5 fois (déni de service ciblé). C'est le compromis habituel : sans blocage, on pourrait essayer les mots de passe sans fin. | Délais progressifs plutôt que blocage net, CAPTCHA après quelques échecs, MFA. |
| Blocage et limites en mémoire | Une seule instance de l'application. | Stockage partagé (Redis) si plusieurs instances. |
| Réservation sans compte ni CAPTCHA | La limite par IP freine les abus. | CAPTCHA ou acompte en ligne contre les réservations fantômes. |
| Client connecté tant que l'onglet reste ouvert | Identifiants en mémoire seulement, effacés à la fermeture. | Session à durée limitée (jeton ou cookie `HttpOnly; Secure; SameSite`) avec expiration après inactivité, et CSRF réactivé si cookie. |
