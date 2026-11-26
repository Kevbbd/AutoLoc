# AutoLoc API

API REST de location de véhicules, réalisée dans le cadre du module **UP ASI — Architecture des Systèmes d'Information** (ECE, 2026-2027).

**Stack :** Java 17 · Spring Boot 4.2 · Spring Data JPA / Hibernate 7 · MySQL · Lombok · Spring Security · Actuator + Micrometer · Prometheus · Grafana · Docker Compose

## Contenu par étape du TP

| Étape | Ce qui est livré | Fichiers |
|---|---|---|
| Workshop 1 — Socle | Projet Maven, connexion MySQL, packages, entité `Vehicule` | `pom.xml`, `application.properties`, `domain/Vehicule.java`, `StatutVehicule`, `CategorieVehicule` |
| Devoir — 8 entités | `Agence`, `Client`, `Employe`, `Equipement`, `Reservation`, `Contrat`, `Paiement`, `Maintenance` + énumérations `RoleEmploye`, `StatutReservation`, `ModePaiement` | `domain/` |
| Associations JPA | Contrat ↔ Paiement (`mappedBy`, `ALL`, `orphanRemoval`, LAZY) et les 7 autres associations, repositories | `domain/`, `repository/`, [`docs/fetch-strategy.md`](docs/fetch-strategy.md) |
| Bonus « Going further » | Profil `dev` et `CommandLineRunner` de données de démo | `application-dev.properties`, `config/DemoDataInitializer.java` |
| Spring Security | HTTP Basic, 2 comptes en mémoire (BCrypt), routes publiques / USER / ADMIN | `config/SecurityConfig.java`, `web/controller/SecurityDemoController.java`, [`docs/security-notes.md`](docs/security-notes.md), `docs/postman/` |
| Monitoring | Actuator, Prometheus, Grafana (source de données et tableau de bord provisionnés) | `docker-compose.yml`, `monitoring/`, [`docs/monitoring-notes.md`](docs/monitoring-notes.md) |
| Au-delà du TP — Mot de passe oublié | Comptes en base (BCrypt), lien de réinitialisation par e-mail (jeton haché, 30 min, usage unique), boîte de test Mailpit | `domain/Utilisateur`, `service/compte/`, `reinitialisation.html`, [`docs/security-notes.md`](docs/security-notes.md#mot-de-passe-oublié) |
| Au-delà du TP — Vitrine et réservation | Site public (template Carbook) branché sur la base : recherche par dates, réservation, consultation et annulation par le client, espace agence (encaissement, restitution, annulation) | `static/`, `service/`, `web/`, [`docs/reservation.md`](docs/reservation.md) |

```
src/main/java/fr/ece/autoloc
├── AutolocApiApplication.java
├── config          SecurityConfig, ComptesInitializer, DemoDataInitializer (profil dev), HorlogeConfig, AsynchroneConfig
├── domain          9 entités du TP + Utilisateur, JetonReinitialisation ; énumérations, Periode, RegleMetierException
├── repository      11 interfaces Spring Data JPA (chevauchements, verrous, consommation atomique des jetons)
├── securite        blocage après échecs de connexion, limitation des formulaires publics (réponses 429)
├── service         VitrineService, ReservationService, ReglesLocation, exception/
│   └── compte      UtilisateurDetailsService, MotDePasseService, RegleMotDePasse, Jetons, envoi d'e-mails
└── web
    ├── ApiExceptionHandler   erreurs au format RFC 9457 (ProblemDetail)
    ├── controller  SecurityDemoController, VitrineController, ReservationController, AdminReservationController, MotDePasseController
    └── dto         records d'entrée (validés) et de sortie
src/main/resources/static   index.html (vitrine + réservation), admin.html (espace agence), reinitialisation.html, js/, css/
```

## Vitrine web et réservation en ligne

L'application sert aussi une page d'accueil sur **<http://localhost:8080>**, sans authentification. Elle est construite à partir du template Carbook de Colorlib (licence CC BY 3.0 : le lien Colorlib en pied de page doit rester). La page affiche :

- la flotte **lue en direct dans la base**, avec filtres par disponibilité et par catégorie ;
- la **recherche par dates** : chaque véhicule indique s'il est libre sur la période et son prix total ;
- la **réservation** : prix recalculé par le serveur à chaque changement de date, contrôles champ par champ, numéro de réservation à la fin ;
- **« Ma réservation »** : consultation (code secret + e-mail) et annulation tant que la location n'a pas commencé ;
- **« Mon compte »** (facultatif) : inscription confirmée par e-mail, connexion par adresse e-mail, toutes ses réservations (en cours, à venir, passées), formulaire de réservation pré-rempli, mot de passe oublié ;
- les agences et le nombre de véhicules de chacune ;
- les chiffres clés ;
- l'état de l'API, vérifié toutes les 15 secondes via `/actuator/health` ;
- des liens vers Grafana et Prometheus.

| Élément | Fichier |
|---|---|
| Pages et styles | `static/index.html`, `static/admin.html`, `static/inscription.html`, `static/reinitialisation.html`, `css/autoloc.css` |
| Scripts | `js/commun.js` (appels API, dates, prix), `js/autoloc.js` (vitrine), `js/compte.js` (« Mon compte »), `js/inscription.js`, `js/admin.js` (espace agence). Textes insérés avec `textContent`, jamais `innerHTML`. |
| API publique | `/api/public/vehicules`, `…/devis`, `…/periodes-reservees`, `/api/public/agences`, `/api/public/reservations` |
| API agence (`ADMIN`) | `/api/admin/reservations` : liste, paiements, terminaison, annulation |
| API client (`CLIENT`) | `/api/client/profil`, `/api/client/reservations` (liste, réservation, annulation) ; inscription publique sous `/api/public/clients/inscription` |

**Espace agence :** <http://localhost:8080/admin.html>, compte `admin / admin123` (e-mail `admin@autoloc.test`). Il affiche les indicateurs (réservations actives, chiffre d'affaires, encaissé, taux d'occupation) et toutes les réservations. On y encaisse un acompte ou le solde, on enregistre la restitution, on annule.

**Mot de passe oublié :** sur la page de connexion de l'espace agence, saisir `admin@autoloc.test`. L'e-mail arrive dans la boîte de test **Mailpit**, <http://localhost:8025> (démarrée avec le monitoring, il faut donc Docker Desktop). Il contient un lien valable 30 minutes. Sans Docker, le profil `dev` écrit le lien dans `logs\app.log`. Après une réinitialisation, `admin123` ne fonctionne plus : c'est le nouveau mot de passe qui compte, même après un redémarrage.

**Compte client :** sur le site, « Mon compte » → « Créer un compte », saisir une adresse (par exemple `nina@exemple.test`). Ouvrir l'e-mail dans **Mailpit** (<http://localhost:8025>), cliquer le lien, compléter l'identité et choisir le mot de passe. Se connecter ensuite avec l'adresse e-mail : « Mes réservations » s'affiche, et le formulaire de réservation est pré-rempli. Avec l'adresse d'une réservation faite sans compte, cette réservation apparaît dans le compte. Détails et mesures de sécurité : [`docs/security-notes.md`](docs/security-notes.md#comptes-clients).

Règles métier, cycle de vie, gestion de la concurrence (jamais de double réservation, jamais de trop-perçu), API et format des erreurs : [`docs/reservation.md`](docs/reservation.md).

Les entités ne sont jamais exposées telles quelles : `VitrineService` les convertit en DTO (`record`) à l'intérieur d'une transaction en lecture seule. `VehiculeRepository.findAllByOrderByMarqueAscModeleAsc()` charge l'agence et les équipements en une seule requête (`@EntityGraph`), sans problème N+1. Grafana reprend la même charte : thème clair, bleu `#1089ff`, vert `#01d28e`, codes 200 / 401 / 403 en vert, ambre et rouge.

## Sous Windows : tout vérifier en un double-clic

Démarrer MySQL dans le panneau XAMPP (et Docker Desktop pour le monitoring), puis double-cliquer sur **`LANCER-TOUT.cmd`**. Le script :

1. vérifie les outils ;
2. compile et lance les tests ;
3. démarre l'API ;
4. contrôle la base ;
5. rejoue la matrice 200 / 401 / 403 (y compris l'espace agence) ;
6. démarre Prometheus, Grafana et Mailpit (boîte de réception de test).

Les journaux sont écrits dans `logs\`. Chaque étape existe aussi seule dans `scripts\`.

## Démarrer l'application

**Prérequis :** JDK 17 ou plus, un serveur MySQL démarré, IntelliJ IDEA (Lombok est intégré aux versions récentes).

1. Ouvrir le dossier `autoloc-api` dans IntelliJ (*File › Open*) et laisser Maven télécharger les dépendances.
2. Activer *Settings › Build, Execution, Deployment › Compiler › Annotation Processors › Enable annotation processing*.
3. Créer la configuration de lancement de `AutolocApiApplication` (*Run › Edit Configurations*) :
   - **Environment variables :** `DB_PASSWORD=votre_mot_de_passe_mysql`. Ajouter aussi `DB_USERNAME=...` si l'utilisateur n'est pas `root`.
   - **Active profiles :** `dev`, pour charger les données de démonstration. Facultatif.
4. Lancer l'application. La console doit afficher `Started AutolocApiApplication in … seconds`, puis les `create table …` générés par Hibernate.
5. Vérifier dans MySQL Workbench ou DBeaver : `USE autoloc_db; SHOW TABLES;` doit lister **10 tables** (les 9 entités + `vehicule_equipement`).

En ligne de commande : `DB_PASSWORD=... ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (sous Windows : `mvnw.cmd`).

> Le mot de passe MySQL n'est **jamais** écrit dans `application.properties` : il est lu dans la variable d'environnement `DB_PASSWORD`.

## Tests automatisés

```bash
./mvnw test
```

Les tests utilisent une base **H2 en mémoire** (profil `test`) : aucun serveur MySQL n'est nécessaire.

| Test | Ce qu'il vérifie |
|---|---|
| `AssociationsBidirectionnellesTest` | Les méthodes `ajouterX()` / `retirerX()` maintiennent les deux côtés de chaque association. |
| `ContratPaiementPersistenceTest` | La chaîne de cascade Client → Réservation → Contrat → Paiement, `orphanRemoval`, la suppression en cascade, et LAZY face à `@EntityGraph`. |
| `SecurityRulesTest` | La matrice 200 / 401 / 403, l'accès public à Actuator et à la vitrine, le refus d'un mauvais mot de passe ou d'un compte inconnu (comptes lus en base), et les en-têtes de protection (CSP sans `unsafe-inline`, `nosniff`, `DENY`…), avec de vraies requêtes HTTP. |
| `VitrineApiTest` | Les endpoints publics renvoient véhicules, agence et équipements sans `LazyInitializationException`, et la page d'accueil est servie. |
| `PeriodeTest`, `ReservationContratTest` | Règles du domaine sans Spring : chevauchement de périodes semi-ouvertes, transitions de statut, paiements (trop-perçu, contrat annulé). |
| `ReglesLocationTest` | Dates passées, durée maximale, délai de réservation, arrondi du montant, avec une horloge fixée. |
| `ReservationRepositoryTest` | Les requêtes de chevauchement et de disponibilité, cas limites compris, sur une vraie base. |
| `RegleMotDePasseTest`, `JetonsTest` | Règles du nouveau mot de passe (dont la limite de 72 octets de BCrypt), jetons de 256 bits, SHA-256 comparé à la valeur de référence du NIST. |
| `CodeAccesTest`, `CompteurEchecsTest`, `LimiteurDebitTest`, `FiltresSecuriteTest` | Code de réservation imprévisible ; blocage après 5 échecs puis déblocage (horloge simulée) ; limite de 30 envois par minute ; réponses 429 avec `Retry-After`. |
| `ProtectionConnexionApiTest` | En HTTP réel : 5 mauvais mots de passe bloquent le compte, même avec le bon ensuite. Un identifiant inexistant est traité pareil, et une connexion réussie remet le compteur à zéro. |
| `MotDePasseApiTest` | Mot de passe oublié en HTTP réel. Couvre : e-mail reçu, lien, nouveau mot de passe, ancien refusé, lien à usage unique, expiré ou remplacé par une nouvelle demande. Vérifie aussi la même réponse pour une adresse inconnue, la limite de 3 demandes par heure et le seul jeton haché en base. Huit envois simultanés du même lien : un seul réussit. |
| `CompteClientApiTest` | Comptes clients en HTTP réel : inscription par lien, rien de créé avant confirmation, même réponse pour une adresse déjà inscrite (et avertissement de son titulaire), 3 demandes par heure, lien remplacé, lien restauré après une erreur, 8 finalisations simultanées = 1 compte. Réservations sans compte rattachées, identité tirée de la connexion et non du JSON, cloisonnement entre clients (404), séparation client / personnel (401, 403), mot de passe oublié et blocage après 5 échecs pour un client. |
| `ReservationApiTest` | Le parcours complet en HTTP réel : devis, réservation (201), conflits (409), validation (400), identité e-mail/permis, consultation, annulation, back-office (401/403/200), encaissement et restitution. Il couvre aussi **la concurrence** : 8 réservations simultanées donnent exactement 1 succès, et 8 encaissements simultanés ne dépassent jamais le montant du contrat. |

## Sécurité : vérification manuelle

Comptes de TP : `student / student123` (USER) et `admin / admin123` (ADMIN).

**Avec Swagger UI** (sans rien installer) : ouvrir <http://localhost:8080/swagger-ui.html>, groupe « Démonstration du TP ». « Try it out » puis « Execute » sans compte donne 401 ; « Authorize » avec `student` puis `admin` donne 403 puis 200 sur `/api/admin/report`. Swagger UI est fourni par springdoc-openapi 3.2 (`config/OpenApiConfig`).

**Avec Postman** : importer `docs/postman/AutoLoc-security.postman_collection.json`, puis lancer la collection avec le **Runner**. Chaque requête vérifie automatiquement le code attendu. Détails et réponses aux questions du TP : [`docs/security-notes.md`](docs/security-notes.md).

## Monitoring

```bash
# l'application doit déjà tourner sur le port 8080
docker compose up -d
```

- Prometheus : <http://localhost:9090> › *Status › Targets*. La cible `autoloc-api` doit être **UP**.
- Mailpit : <http://localhost:8025> : e-mails envoyés par l'application (mot de passe oublié).
- Grafana : <http://localhost:3000> (`admin / admin`, puis changer le mot de passe) › *Dashboards › AutoLoc › AutoLoc — Application Monitoring*.
- Arrêt : `docker compose down`.

La source de données et le tableau de bord sont créés automatiquement (*provisioning*). Les étapes 6 et 7 du TP (ajout manuel de la source et des panneaux) restent faisables à la main pour la démonstration : les requêtes PromQL sont listées dans [`docs/monitoring-notes.md`](docs/monitoring-notes.md).

## Captures d'écran à fournir

- [ ] Console : `Started AutolocApiApplication` et les `create table`
- [ ] MySQL : les 10 tables, les clés étrangères et la table `vehicule_equipement`
- [ ] Postman : une réponse **200**, une **401** et une **403**
- [ ] Prometheus : *Targets* avec `autoloc-api` **UP**
- [ ] Grafana : le tableau de bord avec au moins 4 panneaux alimentés (après avoir généré du trafic avec le Runner Postman)
- [ ] Vitrine : une recherche par dates, puis la confirmation d'une réservation avec son numéro
- [ ] Espace agence : la liste des réservations après un encaissement
- [ ] Mot de passe oublié : l'e-mail reçu dans Mailpit, puis la page « Nouveau mot de passe »
- [ ] Compte client : l'e-mail de confirmation dans Mailpit, la page « Créer mon compte », puis « Mon espace » avec ses réservations

## Choix techniques assumés par rapport à l'énoncé

Ces écarts sont volontaires et justifiés. L'audit de sécurité complet (faiblesses trouvées, corrections, tests, risques assumés) est dans [`docs/security-notes.md`](docs/security-notes.md#audit-de-sécurité).

| Choix | Raison |
|---|---|
| `spring-boot-starter-webmvc` au lieu de `spring-boot-starter-web` | Nouveau nom du starter depuis Spring Boot 4 (l'ancien est déprécié). C'est ce que génère Spring Initializr aujourd'hui. |
| `spring.jpa.show-sql` absent | Il fait doublon avec `logging.level.org.hibernate.SQL=DEBUG` : chaque requête serait affichée deux fois. |
| `spring.jpa.open-in-view=false` | Rend visibles les problèmes de chargement LAZY dès le développement. |
| `@JoinColumn` explicites (`id_contrat`, `id_agence`…) | Schéma homogène. Le nom implicite (`contrat_id_contrat`) est documenté dans `fetch-strategy.md`. |
| `orphanRemoval = true` sur Contrat → Paiement | Exigé par la règle de conception et les livrables, absent de l'extrait de code de l'énoncé. |
| `GenerationType.IDENTITY` | MySQL ne gère pas les séquences, quelle que soit la version (contrairement à ce qu'indique l'énoncé) : avec `SEQUENCE`, Hibernate les simulerait avec une table. |
| Comptes en base au lieu de comptes en mémoire | Nécessaire pour « mot de passe oublié » : un mot de passe changé en mémoire serait perdu au redémarrage. Mêmes identifiants qu'au TP (`student` / `admin`), créés au premier démarrage. |
| API sans état + CSRF désactivé | API REST appelée en HTTP Basic, sans cookie de session. Évite les 403 sur les futurs POST/PUT/DELETE. Justification dans `security-notes.md`. |
| Détails de santé visibles par ADMIN uniquement | Recommandation de l'énoncé lui-même : ne pas exposer publiquement les détails de santé. |
| Images Docker en version figée, `extra_hosts` sous Linux, provisioning Grafana | Environnement reproductible sur toutes les machines. |

## Dépannage

| Problème | Solution |
|---|---|
| `Access denied for user 'root'@'localhost'` | La variable `DB_PASSWORD` n'est pas définie dans la configuration de lancement. |
| Erreur de clé étrangère ou de colonne `NOT NULL` au démarrage | La base contient d'anciennes tables (par exemple celles du devoir, ou une colonne `contrat_id_contrat`). `ddl-auto=update` ne supprime ni ne renomme rien. En développement : `DROP DATABASE autoloc_db;` puis relancer (la base est recréée automatiquement). |
| Getters/setters « introuvables » dans IntelliJ | Activer l'annotation processing (étape 2) et vérifier le plugin Lombok. |
| `Data truncated for column 'statut'` après l'ajout d'une valeur d'énumération | Hibernate crée des colonnes `enum(...)` natives sous MySQL, que `update` ne modifie pas. Recréer la base de développement. |
| `Port 8080 was already in use` | Une autre instance tourne : l'arrêter dans IntelliJ ou fermer la fenêtre « AutoLoc API ». |
| MySQL XAMPP s'arrête (« MySQL shutdown unexpectedly ») au redémarrage de l'API | En mode `update`, Hibernate relit les clés étrangères existantes. Sans catalogue par défaut, il interroge toutes les bases du serveur, ce que le MariaDB de XAMPP ne supporte pas. `hibernate.default_catalog=autoloc_db` limite cette lecture à la base du projet. Relancez MySQL dans XAMPP. |
| Erreur 500 et, dans `logs\app.log`, `error in your SQL syntax … near 'of v1_0'` | Un verrou JPA `@Lock` génère `FOR UPDATE OF`, syntaxe MySQL 8 refusée par le MariaDB de XAMPP. Les verrous du projet sont écrits en SQL natif portable pour cette raison (voir `docs/reservation.md`). |
| L'e-mail de « mot de passe oublié » n'arrive pas | Mailpit n'est pas démarré : lancer Docker Desktop puis `scripts\monitoring-demarrer.cmd`. En attendant, avec le profil `dev`, le lien est écrit dans `logs\app.log` (chercher « Lien de réinitialisation »). |
| **429** « Trop de tentatives de connexion » | 5 mauvais mots de passe en 15 minutes sur le même identifiant (ou 20 depuis le même PC) : attendre 15 minutes. C'est la protection contre la force brute, voir l'audit dans `docs/security-notes.md`. Pour débloquer tout de suite, redémarrer l'application (le compteur est en mémoire). |
| `admin / admin123` refusé | Le mot de passe a été changé par « mot de passe oublié » : utiliser le nouveau, ou refaire une demande pour `admin@autoloc.test`. |
| `HHH000511: The 5.5.5 version for MySQLDialect is no longer supported` | Avertissement sans conséquence avec XAMPP. XAMPP fournit MariaDB 10.4, qui se présente au pilote MySQL comme une version « 5.5.5 ». Le schéma et les données sont corrects. Il disparaît avec un vrai serveur MySQL 8. |

## Commits suggérés

Sous Windows, `scripts\git-initialiser.cmd` crée le dépôt dans le dossier parent et fait 6 commits :

1. `Implement AutoLoc domain model with JPA associations, cascade and fetch`
2. `Add Spring Security authentication and authorization`
3. `Add Prometheus and Grafana monitoring`
4. `Add Windows scripts to build, run and verify AutoLoc`
5. `Add showcase website with online reservation, client accounts, payments and back-office` (tout le reste)
6. `Add illustrated README for readers` (`README.md` à la racine du dépôt, pour tous les lecteurs, et ses captures d'écran)

Il ne pousse rien. **Pour publier sur GitHub en un double-clic : `PUBLIER-SUR-GITHUB.cmd`.**

1. Sur <https://github.com/new>, créer un dépôt **vide** nommé `AutoLoc` (ne cocher ni README, ni .gitignore, ni licence). Public : le professeur le voit directement ; privé : l'inviter dans *Settings > Collaborators*.
2. Double-cliquer sur `PUBLIER-SUR-GITHUB.cmd`. Le script crée le dépôt local s'il n'existe pas (les 6 commits ci-dessus), demande l'adresse du dépôt GitHub, puis envoie le projet. La première fois, Git ouvre une fenêtre de connexion à GitHub.

Les dossiers `target` et `logs` ne sont jamais envoyés (`.gitignore`). À la main, l'équivalent est :

```bash
git remote add origin https://github.com/VOTRE-COMPTE/AutoLoc.git
git push -u origin main
```
