# Modèle de données Neo4flix

## Pourquoi ce modèle

Un moteur de recommandation gagne à être pensé graphe-first : les recommandations
("les utilisateurs qui ont aimé X ont aussi aimé Y", "ce film partage des genres
avec un film que vous avez noté 5/5") sont des traversées de relations, pas des
jointures. Le schéma ci-dessous sépare clairement les nœuds "catalogue" (Movie,
Genre) des nœuds "activité utilisateur" (User, RATED, WANTS_TO_WATCH), ce qui
correspond aussi au découpage en microservices.

## Nœuds

| Nœud | Propriétés | Rôle |
|---|---|---|
| `Movie` | `movieId` (UUID, unique), `title`, `originalTitle`, `releaseDate` (date), `durationMinutes`, `synopsis`, `posterUrl`, `averageRating` (dénormalisé, recalculé), `createdAt` | Catalogue |
| `Genre` | `name` (unique) | Classification, filtre de recherche |
| `User` | `userId` (UUID, unique), `username` (unique), `email` (unique), `passwordHash`, `roles`, `totpSecret` (2FA), `createdAt` | Compte utilisateur |

`averageRating` est dénormalisé sur `Movie` (recalculé par le rating-service à
chaque nouvelle note) pour éviter un agrégat coûteux à chaque affichage de la
page film ou de la home.

### `Person` (acteurs/réalisateurs) — non implémenté, et c'est volontaire

Une première version de ce schéma prévoyait un nœud `Person` avec des relations
`ACTED_IN`/`DIRECTED`, pour du contenu-based basé sur le casting en plus des
genres. **Jamais construit** : le schema.cypher garde encore la contrainte
`person_id_unique` (inoffensive sur un label qui n'existe pas en pratique, mais
à nettoyer si on veut être rigoureux), et aucun code Java ne crée de nœud
`Person`. L'audit ne demande que movies/users/ratings (voir
`docs/neo4flix-audit.md`, section "Data and Design") — Person aurait été un
bonus, pas un requis, donc scope-cut délibérément pour tenir les délais plutôt
que laissé en plan par oubli.

## Relations

| Relation | Sens | Propriétés | Écrite par |
|---|---|---|---|
| `(:Movie)-[:IN_GENRE]->(:Genre)` | Movie → Genre | — | movie-service |
| `(:User)-[:RATED]->(:Movie)` | User → Movie | `score` (0.5–5.0), `comment`, `ratedAt` | rating-service (seul écrivain) |
| `(:User)-[:WANTS_TO_WATCH]->(:Movie)` | User → Movie | `addedAt` | user-service |

## Ownership par microservice (bounded context)

Les 4 microservices pointent vers la **même base Neo4j**, mais chacun est le
seul à écrire sur son périmètre :

- **movie-service** : nœuds `Movie`, `Genre` + relation `IN_GENRE`. Expose
  aussi une recherche multi-critères (titre, genre, date de sortie) via
  l'index plein texte.
- **user-service** : nœud `User` + relation `WANTS_TO_WATCH` (watchlist).
  Gère l'authentification (JWT) et le hash des mots de passe.
- **rating-service** : seul service autorisé à écrire la relation `RATED`.
  Recalcule `Movie.averageRating` après chaque écriture.
- **recommendation-service** : **lecture seule** sur l'ensemble du graphe.
  Combine filtrage collaboratif (traversée `RATED`) et un algorithme de la
  librairie Neo4j Graph Data Science (GDS), déjà activée dans
  `docker-compose.yml` (`NEO4J_PLUGINS=["apoc","graph-data-science"]`).

Le partage de recommandations entre amis n'est pas modélisé comme relation
persistante dans le graphe (pas de `FRIENDS_WITH`, aucun service ne connaît la
notion d'amis) : c'est un simple lien profond vers `/movies/{movieId}` généré
et copié dans le presse-papiers **côté frontend uniquement**
(`core/share.util.ts`), aucun backend impliqué. Voir `10-frontend.md` pour le
détail et le raisonnement complet.

## Comment appliquer ce schéma

Le conteneur `neo4j:5` officiel n'exécute pas de script Cypher au démarrage.
Une fois `docker compose up -d` lancé (déjà fait), on applique le schéma via
`cypher-shell` dans le conteneur :

```powershell
Get-Content backend\neo4j\init\schema.cypher | docker exec -i neo4flix_db cypher-shell -u neo4j -p neo4flix_dev
```

Vérification rapide dans Neo4j Browser (http://localhost:7474) :

```cypher
SHOW CONSTRAINTS;
SHOW INDEXES;
```

## État actuel

Ce schéma est traduit en entités Neo4j OGM (`@Node`, `@Relationship`) côté
movie-service : `Movie`, `Genre`. Les 4 microservices, le frontend et le
schéma sont tous implémentés — voir `00-getting-started.md` pour lancer
l'ensemble.
