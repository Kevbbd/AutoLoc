# Stratégie de chargement et de cascade — AutoLoc

Ce document justifie, association par association, les choix de **fetch** (quand une association est chargée) et de **cascade** (quelles opérations se propagent du parent vers l'enfant).

## Règles appliquées partout

| Règle | Pourquoi |
|---|---|
| Toutes les associations sont `LAZY`, y compris `@ManyToOne` et `@OneToOne` (EAGER par défaut en JPA). | Charger une entité ne doit pas déclencher en chaîne le chargement de tout le graphe d'objets. |
| Les données liées sont chargées **à la demande** par une requête explicite (`@EntityGraph`, `JOIN FETCH`) et jamais en passant l'association en EAGER. | On paie le coût du chargement uniquement dans le cas d'usage qui en a besoin. |
| La cascade `REMOVE` (incluse dans `ALL`) n'est utilisée que pour une **composition** : l'enfant n'a pas d'existence sans son parent. | Supprimer un parent ne doit jamais effacer une donnée qui a sa propre vie (véhicule, équipement partagé, historique). |
| Les clés étrangères sont nommées explicitement avec `@JoinColumn` (`id_agence`, `id_client`, …). | Schéma lisible et stable, homogène avec les clés primaires. |
| Les deux côtés d'une association bidirectionnelle sont maintenus par des méthodes `ajouterX()` / `retirerX()`. Les collections n'ont pas de setter (`@Setter(AccessLevel.NONE)`). | Le modèle objet reste cohérent avec la base. Remplacer une collection gérée par Hibernate casse `orphanRemoval` (« A collection with cascade=all-delete-orphan was no longer referenced »). |
| Pas de `@Data`, `@EqualsAndHashCode` ni `@ToString` de Lombok sur les entités. | Ils parcourent les associations bidirectionnelles : récursion infinie, et `hashCode` instable dans les `Set`. |
| `spring.jpa.open-in-view=false` | Sinon la session Hibernate reste ouverte jusqu'à la fin de la requête HTTP et masque les `LazyInitializationException` : les problèmes de chargement apparaîtraient seulement en production. |

## Choix par association

| Association | Mapping / côté propriétaire | Fetch | Cascade | Justification |
|---|---|---|---|---|
| **Contrat → Paiement** | `@OneToMany(mappedBy = "contrat")` / `@ManyToOne` ; `paiement.id_contrat` NOT NULL | LAZY | `ALL` + `orphanRemoval = true` | **Composition** (losange plein en UML) : un paiement n'existe pas sans son contrat. Supprimer le contrat supprime ses paiements, et retirer un paiement de la liste le supprime en base. |
| **Agence → Vehicule** | `@OneToMany(mappedBy = "agence")` / `@ManyToOne` ; `vehicule.id_agence` (nullable) | LAZY | aucune | Un véhicule survit à la fermeture d'une agence : il est réaffecté. La FK est nullable pour pouvoir détacher les véhicules avant de supprimer l'agence. |
| **Agence → Employe** | `@OneToMany(mappedBy = "agence")` / `@ManyToOne` ; `employe.id_agence` (nullable) | LAZY | aucune | Un employé peut changer d'agence : son cycle de vie (RH) est indépendant. |
| **Vehicule ↔ Equipement** | `@ManyToMany` ; `Vehicule` propriétaire de la table `vehicule_equipement (id_vehicule, id_equipement)` ; inverse `mappedBy = "equipements"` | LAZY | aucune | Équipements **partagés** : un `REMOVE` supprimerait le GPS de tous les autres véhicules. Collection `Set` : pas de doublon, et Hibernate modifie seulement la ligne concernée (avec une `List`, il supprime puis réinsère toute la table de jointure du véhicule). |
| **Client → Reservation** | `@OneToMany(mappedBy = "client")` / `@ManyToOne` ; `reservation.id_client` NOT NULL | LAZY | `PERSIST` | Un nouveau client et sa première réservation s'enregistrent en un seul `save`. Pas de `REMOVE` : les réservations sont liées à des contrats et paiements (données comptables) qu'on ne doit pas effacer en supprimant un client. |
| **Reservation → Vehicule** | `@ManyToOne` unidirectionnel ; `reservation.id_vehicule` NOT NULL | LAZY | aucune | Le véhicule est une ressource de référence partagée par de nombreuses réservations. Pas de collection inverse : « les réservations d'un véhicule » relève d'une requête de repository, pas d'une liste chargée dans l'entité. |
| **Reservation ↔ Contrat** | `@OneToOne` ; `Contrat` propriétaire : `contrat.id_reservation` NOT NULL **UNIQUE** ; inverse `mappedBy = "reservation"` | LAZY (voir la limite plus bas) | `ALL` côté `Reservation` | Le contrat formalise une réservation et partage son cycle de vie. La contrainte `UNIQUE` garantit la multiplicité 1 — 0..1 en base. |
| **Vehicule → Maintenance** | `@OneToMany(mappedBy = "vehicule")` / `@ManyToOne` ; `maintenance.id_vehicule` NOT NULL | LAZY | `PERSIST` | Une maintenance s'enregistre avec le véhicule. Pas de `REMOVE` : l'historique d'entretien est conservé pour la traçabilité. |

Chaîne de cascade complète, utilisée par `DemoDataInitializer` et par les tests : `clientRepository.save(client)` enregistre la réservation (`PERSIST`), qui enregistre le contrat (`ALL`), qui enregistre les paiements (`ALL`).

## Observations de la partie guidée Contrat ↔ Paiement

**Étape 1 — association unidirectionnelle `Paiement → Contrat`.** Sans `@JoinColumn`, la stratégie de nommage de Spring Boot compose le nom de la colonne à partir du nom de la propriété et de la colonne référencée : `contrat_id_contrat`. Nous l'avons ensuite fixé à `id_contrat`. Avec `ddl-auto=update`, l'ancienne colonne n'est **pas** supprimée : il faut recréer la base de développement (`DROP DATABASE autoloc_db;`).
À ce stade, un paiement peut naviguer vers son contrat (`paiement.getContrat()`), mais un contrat ne connaît pas ses paiements : la classe `Contrat` n'a aucun champ qui les référence. La relation n'existe que dans la table `paiement` et dans la classe `Paiement`.

**Étape 2 — bidirectionnelle avec `mappedBy`.** `mappedBy = "contrat"` désigne le **nom du champ Java** dans `Paiement`, pas le nom de la colonne. Aucune colonne ni table n'est ajoutée. Sans `mappedBy`, Hibernate verrait deux associations indépendantes et créerait une table de jointure `contrat_paiements` inutile.

**Étape 3 — cascade.** Sans cascade, un paiement ajouté à la liste d'un contrat n'est pas enregistré par `save(contrat)` : il faudrait l'enregistrer séparément. Avec `ALL`, il l'est. `ALL` inclut `REMOVE` : supprimer le contrat supprime ses paiements. `orphanRemoval` va plus loin : retirer un paiement de la liste suffit à le supprimer.

**Étape 4 — LAZY vs EAGER.**

| Fetch | SQL observé au `findById(contrat)` | Conséquence |
|---|---|---|
| LAZY (retenu) | Un `select` sur `contrat` seulement. Les paiements sont lus par un second `select` au premier appel de `getPaiements()`, **dans une transaction**. | Lecture légère. Hors transaction : `LazyInitializationException`. |
| EAGER (testé puis abandonné) | Contrat et paiements lus immédiatement (jointure), à **chaque** chargement de contrat. | Coût payé même quand les paiements sont inutiles. Sur une liste de contrats, Hibernate déclenche une requête supplémentaire par contrat (problème N+1). |

Configuration finale : LAZY, et `ContratRepository.findWithPaiementsByIdContrat(id)` (annoté `@EntityGraph(attributePaths = "paiements")`) quand les paiements sont nécessaires : une seule requête avec `LEFT JOIN`.

## Limite connue : le côté inverse d'un `@OneToOne`

`Reservation.contrat` est déclaré LAZY, mais Hibernate **ne peut pas** le rendre paresseux sans *bytecode enhancement*. La clé étrangère est dans `contrat` : pour savoir si le champ vaut `null` ou un proxy, Hibernate doit interroger la table `contrat` à chaque chargement d'une réservation.

Pour une seule ligne au plus, ce coût est acceptable et nous gardons le modèle du TP. Si cela devenait un problème, trois solutions existent :

- activer le bytecode enhancement (plugin `hibernate-enhance-maven-plugin`) ;
- partager la clé primaire avec `@MapsId` ;
- rendre l'association unidirectionnelle `Contrat → Reservation`.

## Schéma généré

| Table | Colonnes (clés étrangères en gras) |
|---|---|
| `agence` | `id_agence` PK, `nom`, `ville`, `adresse`, `telephone` |
| `employe` | `id_employe` PK, `nom`, `prenom`, `role`, **`id_agence`** |
| `vehicule` | `id_vehicule` PK, `immatriculation` UNIQUE, `marque`, `modele`, `categorie`, `tarif_journalier` decimal(10,2), `statut`, **`id_agence`** |
| `equipement` | `id_equipement` PK, `libelle` UNIQUE |
| `vehicule_equipement` | **`id_vehicule`**, **`id_equipement`** (table de jointure) |
| `client` | `id_client` PK, `nom`, `prenom`, `email` UNIQUE, `telephone`, `num_permis` UNIQUE, `date_inscription` |
| `reservation` | `id_reservation` PK, `date_debut`, `date_fin`, `statut`, **`id_client`**, **`id_vehicule`** |
| `contrat` | `id_contrat` PK, `date_signature`, `montant_total` decimal(10,2), `valide`, **`id_reservation`** UNIQUE |
| `paiement` | `id_paiement` PK, `montant` decimal(10,2), `date_paiement`, `mode_paiement`, **`id_contrat`** |
| `maintenance` | `id_maintenance` PK, `date_debut`, `date_fin`, `description`, **`id_vehicule`** |

Sur MySQL, Hibernate génère une colonne de type **`enum('…')` natif** pour chaque `@Enumerated(EnumType.STRING)`. Conséquence : si on ajoute une valeur à une énumération Java, `ddl-auto=update` ne modifie pas la colonne existante et l'insertion de la nouvelle valeur échoue. Il faut alors faire un `ALTER TABLE` ou recréer la base de développement.

## Vérification automatisée

`ContratPaiementPersistenceTest` (base H2) vérifie la chaîne de cascade, `orphanRemoval`, la suppression en cascade et le chargement LAZY / `@EntityGraph`. `AssociationsBidirectionnellesTest` vérifie que les méthodes `ajouterX()` / `retirerX()` maintiennent les deux côtés de chaque association.
