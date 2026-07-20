# Neo4flix

Moteur de recommandation de films : Neo4j + 4 microservices Spring Boot + Angular.

## Démarrage rapide

Tout se lance avec Docker, depuis `backend/`. Une seule fois, créer ton fichier
`.env` (contient le mot de passe Neo4j et le secret JWT — gitignored, jamais
commité, voir `docs/04-security.md`) :

```powershell
cd backend
Copy-Item .env.example .env
docker compose up -d --build
```

Ça construit et lance : Neo4j, les 4 microservices (movie/user/rating/recommendation),
et le frontend Angular. Premier lancement plus long (build des images), les suivants
sont rapides (cache Docker).

Une fois que c'est up (`docker compose ps` pour vérifier que tout est `healthy`/`running`) :

1. **Appliquer le schéma Neo4j** (une seule fois, pas fait automatiquement) :
   ```powershell
   Get-Content neo4j\init\schema.cypher | docker exec -i neo4flix_db cypher-shell -u neo4j -p neo4flix_dev
   ```
   (mot de passe = la valeur de `NEO4J_PASSWORD` dans ton `.env`)
2. **Peupler quelques films** (sinon la home page est vide) :
   ```powershell
   .\scripts\seed-movies.ps1
   ```
3. Ouvrir **http://localhost:4201** — c'est l'appli.

Pour vérifier que tout fonctionne sans cliquer partout à la main :
```powershell
.\scripts\smoke-test.ps1 -SkipBuild
```

| Composant | URL |
|---|---|
| Frontend | http://localhost:4201 |
| Neo4j Browser | http://localhost:7474 |
| movie-service | http://localhost:8091/api |
| user-service | http://localhost:8092/api |
| rating-service | http://localhost:8093/api |
| recommendation-service | http://localhost:8094/api |

## Pour développer (sans tout reconstruire à chaque changement)

Lancer seulement Neo4j via Docker, puis chaque service à la main pour avoir le
rechargement à chaud :

```powershell
cd backend
docker compose up -d neo4j
```

Puis, un terminal par service :
```powershell
cd backend\movie-service        # ou user-service / rating-service / recommendation-service
.\mvnw.cmd spring-boot:run
```

Et pour le frontend :
```powershell
cd frontend
npx ng serve
```

## Documentation complète

Le détail de l'architecture, des choix techniques et de chaque service est dans
[`docs/`](docs/00-getting-started.md) :

- [`docs/00-getting-started.md`](docs/00-getting-started.md) — guide de lancement détaillé, scénario de test complet.
- [`docs/01-architecture.md`](docs/01-architecture.md) — vue d'ensemble, pourquoi une base Neo4j partagée.
- [`docs/02-data-model.md`](docs/02-data-model.md) — nœuds, relations, contraintes.
- [`docs/03-neo4j-concepts.md`](docs/03-neo4j-concepts.md) — bases Neo4j/Cypher, OGM vs Neo4jClient.
- [`docs/04-security.md`](docs/04-security.md) — JWT, 2FA, ce qui reste à faire.
- [`docs/05-movie-service.md`](docs/05-movie-service.md), [`docs/06-user-service.md`](docs/06-user-service.md), [`docs/07-rating-service.md`](docs/07-rating-service.md), [`docs/08-recommendation-service.md`](docs/08-recommendation-service.md) — détail par service.
- [`docs/09-testing.md`](docs/09-testing.md) — script de smoke test local.
- [`docs/10-frontend.md`](docs/10-frontend.md) — architecture Angular.
