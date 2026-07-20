# Getting started

Guide pour lancer le projet en local et vérifier que tout communique. Pour comprendre
*pourquoi* le projet est architecturé ainsi, voir [`01-architecture.md`](01-architecture.md). Pour les concepts
Neo4j utilisés partout dans le code, voir [`03-neo4j-concepts.md`](03-neo4j-concepts.md).

## Prérequis

- JDK 21
- Maven (fourni via le wrapper `mvnw` dans chaque microservice, pas besoin d'installer Maven globalement)
- Node.js (voir [`10-frontend.md`](10-frontend.md) pour la version exacte requise par Angular 22)
- Docker Desktop
- Postman (l'audit du projet s'en sert explicitement pour tester l'API)

## Comment un microservice a été créé (Spring Initializr)

Les 4 microservices ont été générés via [start.spring.io](https://start.spring.io),
pas écrits à la main depuis zéro. Paramètres utilisés pour chacun :

- **Project** : Maven
- **Language** : Java 21
- **Spring Boot** : dernière version stable 3+ disponible au moment de la
  génération (résolue en pratique à Spring Boot 4.1.0 avec Spring Data Neo4j 8.1.0
  — les instructions du projet imposent toujours la version la plus récente)
- **Packaging** : Jar
- **Dependencies** : Spring Web, Spring Data Neo4j, Validation (+ Spring Boot
  DevTools en dev)

Spring Initializr génère la structure de base (`pom.xml`, `mvnw`/`mvnw.cmd`,
`.mvn/wrapper/`, `src/main/java/.../XxxApplication.java`, `application.properties`
vide) — tout le reste (entités, repositories, services, controllers, sécurité)
est écrit ensuite dans ce squelette. **Pour créer un futur 5ᵉ microservice**,
repartir de start.spring.io avec les mêmes paramètres plutôt que de copier-coller
un service existant, pour éviter de traîner des dépendances ou de la config
propres à l'ancien service.

## 1. Tout lancer avec Docker (le plus simple)

### Une seule fois : créer ton fichier `.env`

`docker-compose.yml` lit le mot de passe Neo4j et le secret JWT depuis
`backend/.env` — ce fichier n'est **pas** suivi par git (il contiendrait de
vraies valeurs), seul un gabarit l'est :

```powershell
cd backend
Copy-Item .env.example .env
```

Les valeurs par défaut dans `.env.example` sont des valeurs de dev déjà
utilisées partout dans les scripts/docs — les copier tel quel suffit en local,
pas besoin de les changer sauf si tu veux un secret différent du reste de
l'équipe.

### Lancer

Depuis `backend/`, un seul `docker-compose.yml` construit et lance Neo4j, les 4
microservices et le frontend :

```powershell
docker compose up -d --build
```

`--build` reconstruit les images (nécessaire la première fois, et après toute
modif de code — Docker ne rebuild pas automatiquement). `docker compose ps` pour
vérifier que tout tourne. Chaque service attend que Neo4j soit `healthy` avant de
démarrer (`depends_on: condition: service_healthy` dans le compose file), donc pas
besoin de gérer l'ordre de démarrage toi-même.

Ports exposés sur l'hôte, identiques que tu sois en Docker ou en lancement manuel
(section 2) :
- `7474` : Neo4j Browser (http://localhost:7474), `7687` : Bolt.
- `8091`-`8094` : les 4 microservices.
- `4201` : le frontend (servi par nginx en conteneur, voir [`10-frontend.md`](10-frontend.md)).

Identifiants Neo4j : `neo4j` / la valeur de `NEO4J_PASSWORD` dans ton `.env`
(`neo4flix_dev` par défaut). Voir [`04-security.md`](04-security.md) pour le pourquoi de ce
`.env` plutôt qu'une valeur en dur dans `docker-compose.yml`.

### Appliquer le schéma (contraintes + index)

Le conteneur Neo4j officiel n'exécute aucun script au démarrage, il faut l'appliquer
une fois manuellement (à refaire seulement si tu recrées le volume `neo4j/data`) :

```powershell
Get-Content neo4j\init\schema.cypher | docker exec -i neo4flix_db cypher-shell -u neo4j -p neo4flix_dev
```

Vérification dans Neo4j Browser :

```cypher
SHOW CONSTRAINTS;
SHOW INDEXES;
```

## 2. Alternative : lancer chaque service à la main (dev avec rechargement à chaud)

Pratique quand tu modifies du code souvent et ne veux pas reconstruire une image
Docker à chaque changement. Démarre Neo4j seul via Docker, le reste en local :

```powershell
cd backend
docker compose up -d neo4j
```

| Service | Port | Commande (depuis `backend/<service>`) |
|---|---|---|
| movie-service | 8091 | `.\mvnw.cmd spring-boot:run` |
| user-service | 8092 | `.\mvnw.cmd spring-boot:run` |
| rating-service | 8093 | `.\mvnw.cmd spring-boot:run` |
| recommendation-service | 8094 | `.\mvnw.cmd spring-boot:run` |

Chaque service se connecte à `bolt://localhost:7687` par défaut hors Docker (voir
`src/main/resources/application.properties` de chacun — `spring.neo4j.uri` lit la
variable d'env `NEO4J_URI` si elle existe, sinon retombe sur `localhost`, ce qui
permet au même jar de tourner en local ou en conteneur sans modification).

Frontend : `cd frontend && npx ng serve` (voir [`10-frontend.md`](10-frontend.md)).

Si un service refuse de démarrer avec `Port XXXX was already in use`, un autre
processus occupe déjà ce port sur ta machine (vérifie avec
`Get-Process -Id (Get-NetTCPConnection -LocalPort <port>).OwningProcess` en PowerShell).

## 3. Peupler une base vide

Une base Neo4j fraîche n'a aucun film — la home page affichera "Aucun film
trouvé.", normal. Peuple-la avec une vingtaine de films réels (genres qui se
recoupent volontairement, pour que les recommandations aient un vrai signal) :

```powershell
cd backend
.\scripts\seed-movies.ps1
```

Nécessite movie-service démarré (8091, Docker ou local). Le script crée et
promeut son propre compte admin jetable pour pouvoir créer les films (voir
[`04-security.md`](04-security.md) — créer un film est une action admin depuis que movie-service
est sécurisé). Détail dans [`09-testing.md`](09-testing.md).

## 4. Vérifier que tout fonctionne

Deux façons, du plus rapide au plus manuel :

**Rapide — le script de smoke test** rejoue tout le scénario ci-dessous en une
commande, avec une assertion à chaque étape :

```powershell
cd backend
.\scripts\smoke-test.ps1 -SkipBuild
```

(`-SkipBuild` si les 4 services tournent déjà et que tu veux juste le test
fonctionnel, sans recompiler. Sans le flag, il compile d'abord les 4 services.)

Pour les cas limites et la sécurité (entrées invalides, auth manquante/insuffisante,
tentative d'injection...), il y a aussi :

```powershell
.\scripts\security-test.ps1
```

Détail complet des deux scripts dans [`09-testing.md`](09-testing.md).

**Manuel — dans Postman**, dans cet ordre (utile pour comprendre chaque étape
ou déboguer un cas précis) :

1. **Créer un compte** — `POST http://localhost:8092/api/auth/register`
   ```json
   { "username": "daro", "email": "daro@test.fr", "password": "Motdepasse123" }
   ```
   Mot de passe : 8 caractères min, une majuscule, une minuscule, un chiffre.
   Récupère le `token` de la réponse (2FA désactivée par défaut, donc
   `/api/auth/login` renverrait `{"requiresTwoFactor": false, "auth": {"token": ...}}` —
   voir [`04-security.md`](04-security.md) pour activer la 2FA et le flux à deux étapes).

2. **Passer ce compte admin** (nécessaire pour l'étape 4 — créer un film est
   une action admin) — depuis un terminal, pas Postman :
   ```powershell
   cd backend
   .\scripts\promote-admin.ps1 -Username daro
   ```

3. **Se reconnecter** — `POST http://localhost:8092/api/auth/login` avec le
   même body qu'à l'étape 1. **Important** : le token de l'étape 1 ne contient
   pas encore `ROLE_ADMIN` (les rôles sont figés au moment où le token est
   émis) — il faut ce nouveau token. Dans Postman, onglet Authorization de
   chaque requête protégée ci-dessous → type `Bearer Token` → colle ce
   nouveau `token`.

4. **Créer un film** — `POST http://localhost:8091/api/movies`
   ```json
   { "title": "Inception", "releaseDate": "2010-07-16", "genres": ["Action", "Sci-Fi"] }
   ```
   Récupère le `movieId` de la réponse.

5. **Noter le film** — `PUT http://localhost:8093/api/movies/{movieId}/rating`
   ```json
   { "score": 4.5, "comment": "Très bon film" }
   ```

6. **Vérifier la moyenne recalculée** — `GET http://localhost:8091/api/movies/{movieId}`
   → `averageRating` doit être à `4.5`.

7. **Watchlist** — `POST http://localhost:8092/api/users/me/watchlist/{movieId}`,
   puis `GET http://localhost:8092/api/users/me/watchlist` pour vérifier.

Si les 7 étapes passent, l'environnement complet (Neo4j + 4 microservices +
sécurité JWT partagée, y compris le rôle admin) fonctionne de bout en bout.

## Sommaire de la doc

- [`01-architecture.md`](01-architecture.md) — vue d'ensemble microservices, qui possède quoi, pourquoi une seule base Neo4j partagée.
- [`02-data-model.md`](02-data-model.md) — nœuds, relations, contraintes.
- [`03-neo4j-concepts.md`](03-neo4j-concepts.md) — bases graphe/Cypher, Spring Data Neo4j (OGM) vs Neo4jClient, et pourquoi ce choix compte.
- [`04-security.md`](04-security.md) — flux JWT, secret partagé, ce qui reste à faire.
- [`05-movie-service.md`](05-movie-service.md), [`06-user-service.md`](06-user-service.md), [`07-rating-service.md`](07-rating-service.md) — détail par service (endpoints, décisions).
- [`08-recommendation-service.md`](08-recommendation-service.md) — les 3 stratégies de recommandation, Cypher et GDS expliqués.
- [`09-testing.md`](09-testing.md) — script de smoke test local, ce qu'il couvre et ce qu'il ne remplace pas.
- [`10-frontend.md`](10-frontend.md) — architecture Angular, structure des dossiers, auth côté client.
