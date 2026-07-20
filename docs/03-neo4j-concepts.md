# Comprendre Neo4j (pour ceux qui découvrent)

Ce doc explique les concepts qu'on utilise partout dans le code, pour que
n'importe qui sur le projet puisse relire `MovieRepository`, `RatingRepository`
etc. sans avoir à redécouvrir Neo4j en lisant le code source.

## 1. Base de données graphe : la différence avec le relationnel

En SQL, on modélise avec des tables et des clés étrangères, et on reconstruit
les relations à coup de `JOIN` au moment de la requête. Dans une base graphe
comme Neo4j, la relation est un citoyen de première classe, stockée
physiquement, pas recalculée : elle existe dans la base au même titre qu'une
donnée.

Trois briques de base :

- **Node (nœud)** : une entité, avec un ou plusieurs **labels** (ex : `Movie`,
  `User`) et des **propriétés** (paires clé/valeur, ex : `title: "Inception"`).
- **Relationship (relation)** : un lien orienté et *typé* entre deux nœuds
  (ex : `(User)-[:RATED]->(Movie)`), qui peut lui-même porter des propriétés
  (ex : `score`, `ratedAt` sur `RATED`).
- **Label** : une étiquette sur un nœud (`Movie`, `Genre`, `User`...),
  l'équivalent d'une "table" en SQL mais un nœud peut avoir plusieurs labels.

Concrètement dans neo4flix :

```
(:Movie {movieId, title, releaseDate, averageRating})
(:Genre {name})
(:User {userId, username, email, passwordHash})

(:Movie)-[:IN_GENRE]->(:Genre)
(:User)-[:RATED {score, comment, ratedAt}]->(:Movie)
(:User)-[:WANTS_TO_WATCH {addedAt}]->(:Movie)
```

L'intérêt concret : "quels films ont été notés 5/5 par des gens qui ont aussi
noté 5/5 le film que je regarde" est une jointure à 3-4 niveaux en SQL (lente,
verbeuse), mais une traversée de 2-3 relations en Cypher (rapide, lisible).
C'est tout l'enjeu du recommendation-service (voir `08-recommendation-service.md`).

## 2. Cypher, le langage de requête

Cypher ressemble à du "ASCII art" : on dessine le pattern qu'on cherche.

```cypher
MATCH (u:User {userId: $userId})-[r:RATED]->(m:Movie)
RETURN m.title, r.score
ORDER BY r.ratedAt DESC
```

Se lit : "trouve un `User` dont `userId` correspond au paramètre, suis la
relation `RATED` vers des `Movie`, renvoie le titre et le score."

Commandes principales utilisées dans le projet :

- `MATCH` : cherche un pattern existant (l'équivalent d'un `SELECT ... FROM ... WHERE`).
- `CREATE` : crée toujours un nouveau nœud/relation, même si un identique existe déjà.
- `MERGE` : crée seulement si le pattern n'existe pas encore, sinon réutilise
  l'existant (l'équivalent d'un "upsert"). On l'utilise pour `RATED` : si
  l'utilisateur a déjà noté ce film, on met à jour la note existante plutôt que
  d'en créer une deuxième.
- `WHERE` : filtre, comme en SQL.
- `RETURN` : ce que la requête renvoie.
- `WITH` : passe des valeurs intermédiaires d'une partie de la requête à la
  suivante (utile pour enchaîner "j'écris la note" puis "je recalcule la
  moyenne" dans la même requête, voir `RatingRepository`).

## 3. Contraintes et index (`schema.cypher`)

```cypher
CREATE CONSTRAINT movie_id_unique IF NOT EXISTS
FOR (m:Movie) REQUIRE m.movieId IS UNIQUE;
```

Une **contrainte d'unicité** garantit qu'aucun autre nœud `Movie` n'aura le
même `movieId`, et crée automatiquement un index dessus (recherche par id en
O(1) au lieu de scanner tous les nœuds).

Un **index classique** (`CREATE INDEX ... ON (m.releaseDate)`) accélère les
recherches/tris sur une propriété qui n'est pas unique.

Un **index plein texte** (`CREATE FULLTEXT INDEX movieSearchIndex ...`) permet
une recherche floue/multi-champs (titre + synopsis), utilisée via
`CALL db.index.fulltext.queryNodes(...)` dans `MovieRepository.searchByTitleOrSynopsis`.

## 4. Deux façons d'accéder au graphe depuis Spring Boot

### Spring Data Neo4j (OGM) — pour ce qu'on possède

`@Node`, `@Id`, `@Relationship` mappent une classe Java à un pattern du graphe,
comme JPA/Hibernate le fait pour SQL. `Neo4jRepository<Movie, String>` donne
gratuitement les méthodes CRUD (`save`, `findById`, `findAll`...) plus des
requêtes dérivées du nom de méthode (`findByTitle`).

Piège important, découvert en construisant user-service : **Spring Data Neo4j
sauvegarde l'agrégat entier au `save()`**. Si `User` avait une relation
`@Relationship` vers `Movie`, le moindre `userRepository.save(user)` (même pour
juste changer un email) réécrirait aussi le nœud `Movie` lié — un nœud qu'on ne
possède pas, avec le risque d'écraser des propriétés gérées par movie-service.

### Neo4jClient (Cypher direct) — pour tout le reste

Quand une opération touche un nœud qu'on ne possède pas (`WatchlistRepository`,
`RatingRepository`), on écrit le Cypher à la main via `Neo4jClient` :

```java
neo4jClient.query("""
        MATCH (u:User {userId: $userId})
        MATCH (m:Movie {movieId: $movieId})
        MERGE (u)-[r:RATED]->(m)
        SET r.score = $score
        """)
    .bind(userId).to("userId")
    .bind(movieId).to("movieId")
    .bind(score).to("score")
    .run();
```

Ça ne touche *que* la relation qu'on cible, jamais les propriétés des nœuds
`User`/`Movie` eux-mêmes. C'est plus verbeux que l'OGM, mais c'est le seul moyen
d'avoir un contrôle exact sur ce qui est écrit — indispensable dès qu'on
partage une base entre plusieurs services.

**Règle de décision utilisée dans tout le projet** : nœud possédé → OGM et
`Neo4jRepository`. Nœud/relation qui touche un domaine d'un autre service →
`Neo4jClient`.

## 5. Neo4j Graph Data Science (GDS)

Activé dans `docker-compose.yml` (`NEO4J_PLUGINS=["apoc", "graph-data-science"]`).
C'est une librairie d'algorithmes de graphe (similarité, PageRank, détection de
communautés...) exécutés côté serveur Neo4j plutôt qu'en Cypher pur — utile
pour du filtrage collaboratif à l'échelle. Détail d'usage dans
`08-recommendation-service.md`, c'est le morceau du projet où cette notion
compte vraiment.
