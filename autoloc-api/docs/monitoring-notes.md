# Notes de monitoring — AutoLoc

## Architecture

```
┌──────────────────────────┐   GET /actuator/prometheus    ┌──────────────┐   PromQL    ┌───────────┐
│ autoloc-api (IntelliJ)   │ ◄──────── toutes les 5 s ──── │  Prometheus  │ ◄────────── │  Grafana  │
│ Actuator + Micrometer    │                               │  :9090       │             │  :3000    │
│ :8080 (machine hôte)     │                               │  (Docker)    │             │  (Docker) │
└──────────────────────────┘                               └──────────────┘             └───────────┘
```

| Composant | Rôle dans AutoLoc |
|---|---|
| **Spring Boot Actuator** | Expose `/actuator/health` (état de l'application et de MySQL) et `/actuator/prometheus`. |
| **Micrometer + registre Prometheus** | Mesure la JVM, le CPU et chaque requête HTTP, et publie ces valeurs au format texte Prometheus. |
| **Prometheus** | **Va chercher** les métriques (modèle *pull*) toutes les 5 s, les stocke en séries temporelles et répond aux requêtes PromQL. |
| **Grafana** | Interroge Prometheus et affiche les tableaux de bord. Il ne stocke aucune métrique. |

Prometheus tourne dans Docker mais l'application tourne sur la machine hôte. Depuis un conteneur, `localhost` désigne le conteneur lui-même : la cible est donc `host.docker.internal:8080`. À l'inverse, Grafana et Prometheus sont dans le même réseau Docker Compose : Grafana joint Prometheus par le nom du service, `http://prometheus:9090`.

## Fichiers

| Fichier | Contenu |
|---|---|
| `pom.xml` | `spring-boot-starter-actuator` + `micrometer-registry-prometheus` (versions gérées par Spring Boot). |
| `application.properties` | Exposition de `health` et `prometheus`, histogramme des temps de réponse HTTP. |
| `SecurityConfig` | `/actuator/health` et `/actuator/prometheus` en `permitAll()`, déclarés avant les règles plus larges. |
| `monitoring/prometheus.yml` | Cible `autoloc-api`, chemin `/actuator/prometheus`, collecte toutes les 5 s. |
| `docker-compose.yml` | Prometheus et Grafana, versions figées. `extra_hosts` rend `host.docker.internal` disponible aussi sous Linux. |
| `monitoring/grafana/provisioning/` | Déclare automatiquement la source de données Prometheus et le tableau de bord. |
| `monitoring/grafana/dashboards/autoloc-monitoring.json` | Tableau de bord « AutoLoc — Application Monitoring » : bandeau et 7 graphiques. |

## Habillage

Le tableau de bord reprend la charte de la vitrine (<http://localhost:8080>) :

- **thème clair** par défaut (`GF_USERS_DEFAULT_THEME=light`) ;
- **couleurs :** bleu `#1089ff` et vert `#01d28e`. Les statuts HTTP ont un code couleur fixe : 200 en vert, 401 en ambre, 403 en rouge ;
- **bandeau et liens :** un bandeau en tête de page et des liens vers la vitrine, la santé de l'API et Prometheus ;
- **page d'accueil :** le tableau de bord s'ouvre directement après la connexion (`GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH`).

Le logo et le nom « Grafana » ne peuvent pas être remplacés dans l'édition open source : c'est réservé à Grafana Enterprise.

## Métriques retenues

| Panneau | Requête PromQL | Ce qu'on y lit |
|---|---|---|
| Débit HTTP | `sum(rate(http_server_requests_seconds_count{job="autoloc-api", uri!~"/actuator/.*"}[1m]))` | Charge reçue, en requêtes par seconde. Les appels Actuator sont exclus : sinon la collecte de Prometheus elle-même ferait un bruit de fond de 0,2 requête/s. |
| Latence HTTP p95 | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{…}[5m])))` | Temps sous lequel répondent 95 % des requêtes. Plus représentatif que la moyenne, qui masque les requêtes lentes. |
| Réponses par statut | `sum by (status) (rate(http_server_requests_seconds_count{…}[1m]))` | Répartition 200 / 401 / 403. Rend visibles les refus d'accès générés pendant le TP Spring Security. |
| Mémoire JVM | `sum by (area) (jvm_memory_used_bytes{job="autoloc-api"})` | Heap et non-heap. La métrique brute contient une série par zone mémoire (Eden, Old Gen, Metaspace…) : on les regroupe par `area` pour un graphique lisible. |
| CPU | `process_cpu_usage{job="autoloc-api"}` | Part de CPU consommée par la JVM (0 à 1). |
| Threads actifs | `jvm_threads_live_threads{job="autoloc-api"}` | Nombre de threads. Une hausse continue signale une fuite. |
| Uptime | `process_uptime_seconds{job="autoloc-api"}` | Permet de repérer un redémarrage (la valeur retombe à zéro). |

## Problème rencontré et résolu : panneau p95 vide

**Symptôme.** Les panneaux de débit, mémoire et CPU affichaient des données, mais « HTTP response time p95 » restait sur *No data*.

**Diagnostic.** Dans `http://localhost:8080/actuator/prometheus`, on trouvait bien `http_server_requests_seconds_count`, `_sum` et `_max`, mais **aucune** série `http_server_requests_seconds_bucket`. Or `histogram_quantile` calcule un percentile à partir de ces buckets. Par défaut, Micrometer publie seulement le nombre de requêtes, la somme et le maximum des durées, pas l'histogramme.

**Correction.** Activer l'histogramme pour la métrique HTTP dans `application.properties` :

```properties
management.metrics.distribution.percentiles-histogram.http.server.requests=true
```

Après redémarrage, les séries `_bucket` apparaissent et le panneau p95 se remplit dès qu'on envoie des requêtes. Ce réglage est limité à `http.server.requests`, car chaque bucket est une série de plus à stocker dans Prometheus.

## Autres points de vigilance

| Symptôme | Cause et solution |
|---|---|
| Cible `DOWN` dans Prometheus › Status › Targets | L'application n'est pas démarrée, ou le conteneur ne joint pas l'hôte. Sous Linux, c'est l'entrée `extra_hosts` (déjà présente) qui rend `host.docker.internal` joignable. |
| Cible en erreur 401 | `/actuator/prometheus` n'est pas en `permitAll()`, ou la règle est déclarée après une règle plus large. Ne pas désactiver toute la sécurité pour autant. |
| Grafana : « connection refused » vers Prometheus | La source de données utilise `localhost:9090` au lieu de `http://prometheus:9090`. |

## Lancer et arrêter

```bash
# 1. Démarrer autoloc-api dans IntelliJ, puis à la racine du projet :
docker compose up -d
docker compose ps
# 2. Prometheus : http://localhost:9090  (Status > Targets : autoloc-api doit être UP)
# 3. Grafana    : http://localhost:3000  (admin / admin au premier lancement, puis changer le mot de passe)
#    Dashboards > AutoLoc > « AutoLoc — Application Monitoring »
# 4. Générer du trafic : lancer la collection Postman docs/postman/ avec le Runner (plusieurs itérations)
docker compose down
```
