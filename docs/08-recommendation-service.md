# recommendation-service

Port **8094**. Service en lecture seule : ne possède aucun nœud, ne modifie
jamais `User`, `Movie` ou `RATED`. Il ne fait que traverser le graphe pour
produire des recommandations. C'est le service que l'audit va creuser le plus
en profondeur ("does the auditee understand the Cypher used to express complex
recommendation logic"), donc ce doc explique chaque requête en détail —
prépare-toi à pouvoir la redessiner au tableau.

## Trois stratégies, trois endpoints

L'audit demande explicitement qu'un algorithme "content-based, collaborative
filtering..." de la librairie Graph Algorithms de Neo4j soit employé. On
couvre les trois approches classiques plutôt qu'une seule, avec un endpoint
par stratégie (plus simple à expliquer et à tester séparément qu'un seul
endpoint qui mélangerait tout en interne) :

| Endpoint | Stratégie | Utilise GDS ? |
|---|---|---|
| `GET /api/recommendations/me/content-based` | Genres en commun avec ce que tu as aimé | Non, Cypher pur |
| `GET /api/recommendations/me/collaborative` | Utilisateurs qui ont noté les mêmes films que toi | Non, Cypher pur |
| `GET /api/recommendations/me/similar-users` | Similarité calculée par un algorithme GDS | Oui, `gds.nodeSimilarity` |

Les trois sont protégés (JWT), acceptent `?genre=`, `?from=`, `?to=` en
filtre optionnel, et renvoient une liste de `MovieRecommendation` (movieId,
title, posterUrl, averageRating, relevanceScore — ce dernier champ change de
sens selon la stratégie, voir plus bas).

## 1. Content-based (genres en commun)

```cypher
MATCH (me:User {userId: $userId})-[r:RATED]->(liked:Movie)-[:IN_GENRE]->(g:Genre)<-[:IN_GENRE]-(rec:Movie)
WHERE r.score >= 4.0
  AND rec <> liked
  AND NOT (me)-[:RATED]->(rec)
RETURN rec, count(DISTINCT g) AS relevanceScore
ORDER BY relevanceScore DESC
```

Lecture pas à pas :
- `(me)-[r:RATED]->(liked:Movie)` : les films que j'ai notés.
- `-[:IN_GENRE]->(g:Genre)<-[:IN_GENRE]-(rec:Movie)` : les autres films qui
  partagent au moins un genre avec `liked`. Ce double saut (`Movie → Genre →
  Movie`) est exactement le genre de requête qui serait une double jointure
  auto-jointe pénible en SQL, et qui se lit ici comme une phrase.
- `WHERE r.score >= 4.0` : je ne pars que des films que j'ai *aimés*, pas de
  tous ceux que j'ai notés (un film noté 1/5 ne doit pas influencer les
  recommandations dans le même sens qu'un 5/5).
- `NOT (me)-[:RATED]->(rec)` : n'a de sens que si je n'ai pas déjà vu/noté ce
  film.
- `count(DISTINCT g)` : le score de pertinence est le nombre de genres
  partagés — un film qui partage 3 genres avec un film que j'ai adoré est
  plus pertinent qu'un film qui n'en partage qu'un.

**Limite assumée** : deux films peuvent aussi bien matcher parce qu'ils
partagent "Action" (un genre très large) que parce qu'ils partagent
"Cyberpunk" (un genre pointu) — on ne pondère pas par rareté du genre. Amélioration
possible mais hors scope pour l'instant.

## 2. Collaborative filtering (Cypher pur, sans GDS)

```cypher
MATCH (me:User {userId: $userId})-[:RATED]->(:Movie)<-[:RATED]-(peer:User)
WHERE peer <> me
MATCH (peer)-[r:RATED]->(rec:Movie)
WHERE NOT (me)-[:RATED]->(rec)
RETURN rec, avg(r.score) AS relevanceScore
ORDER BY relevanceScore DESC
```

- Première ligne : je trouve tous les `peer` (autres utilisateurs) qui ont
  noté *au moins un* film que j'ai aussi noté — peu importe la note, juste le
  fait d'avoir un goût en commun avec moi sur ce point.
- Deuxième `MATCH` : parmi tout ce que ces `peer` ont noté, je regarde les
  films que je n'ai pas encore vus.
- `avg(r.score)` : le score de pertinence est la moyenne des notes que mes
  "voisins" ont donné à ce film. Plus mes voisins l'ont aimé, plus il remonte.

C'est la version "collaborative filtering" la plus simple qui existe : pas de
pondération par similarité réelle entre utilisateurs (quelqu'un qui n'a qu'un
seul film en commun avec moi compte autant qu'un vrai "jumeau de goût"). C'est
justement la limite que la stratégie 3 corrige.

## 3. Similarité via Neo4j GDS (Node Similarity)

C'est ici qu'on utilise vraiment la librairie **Graph Data Science** (activée
dans `docker-compose.yml`), pas juste du Cypher classique. Trois étapes,
chacune une méthode séparée dans `RecommendationRepository` pour rester
lisible :

### Étape 1 — projeter un graphe temporaire en mémoire

```cypher
CALL gds.graph.project('userMovieGraph', ['User', 'Movie'], {
    RATED: { orientation: 'UNDIRECTED' }
})
```

GDS ne travaille pas directement sur le graphe stocké sur disque : il faut
d'abord "projeter" (copier en mémoire, format optimisé pour le calcul) la
partie du graphe qui nous intéresse — ici uniquement les nœuds `User`/`Movie`
et la relation `RATED` entre eux, sans direction (`UNDIRECTED`) puisqu'on ne
s'intéresse qu'au fait qu'un lien existe, pas à son sens.

### Étape 2 — calculer la similarité entre utilisateurs

```cypher
CALL gds.nodeSimilarity.filtered.stream('userMovieGraph', {
    sourceNodeFilter: 'User', targetNodeFilter: 'User'
})
YIELD node1, node2, similarity
```

`nodeSimilarity` compare deux nœuds par le **coefficient de Jaccard** de leurs
voisins : `|films notés en commun| / |films notés par l'un OU l'autre|`. Deux
utilisateurs qui ont noté exactement les 5 mêmes films ont une similarité de
1.0 ; deux utilisateurs qui n'ont qu'un film en commun sur 50 films notés au
total ont une similarité proche de 0. C'est une amélioration directe sur la
stratégie 2, qui elle traitait "1 film en commun" et "20 films en commun"
de la même façon.

`sourceNodeFilter`/`targetNodeFilter: 'User'` : sans ça, GDS calculerait
aussi la similarité entre paires de `Movie` (deux films sont "similaires" s'ils
ont été notés par les mêmes utilisateurs) — utile pour une V2 mais pas ce
qu'on veut ici.

`node1`/`node2` sont des identifiants internes GDS (pas nos `userId`), d'où
`gds.util.asNode(node1)` pour récupérer le vrai nœud `User` et lire sa
propriété `userId`.

### Étape 3 — recommander les films des utilisateurs les plus proches

Une fois qu'on a la liste des `userId` les plus similaires (top 10 par
`similarity`), on repasse en Cypher classique — même requête que la
stratégie 2, mais restreinte à ce petit groupe d'utilisateurs vraiment
proches plutôt qu'à "n'importe qui qui a un film en commun".

### Nettoyage

Le graphe projeté vit en mémoire côté serveur Neo4j tant qu'on ne le
supprime pas explicitement (`gds.graph.drop`). On le supprime avant *et*
après chaque appel (`dropGdsGraphIfPresent`) : avant, pour repartir d'un état
propre si un appel précédent a échoué en cours de route ; après, pour libérer
la mémoire immédiatement plutôt que d'accumuler des projections.

**Limite assumée** : on reprojette le graphe à chaque appel plutôt que de
garder une projection persistante rafraîchie périodiquement. Correct
(toujours à jour avec les dernières notes) mais pas optimisé pour un vrai
trafic de production — un vrai déploiement rafraîchirait la projection sur un
planning (ex: toutes les heures) plutôt qu'à chaque requête utilisateur.

## Pourquoi Node Similarity et pas un autre algorithme GDS

GDS propose plusieurs familles d'algorithmes qui auraient pu s'appliquer ici.
Ce qui a fait pencher vers Node Similarity plutôt que les alternatives :

- **FastRP / Node2Vec (embeddings) + kNN** : transforme chaque nœud en vecteur,
  puis cherche les plus proches voisins dans cet espace vectoriel. Plus
  puissant et plus scalable sur des graphes énormes (des millions
  d'utilisateurs), mais c'est une boîte noire — impossible d'expliquer
  simplement *pourquoi* deux utilisateurs sont jugés proches, juste "leurs
  vecteurs sont proches". Pour un projet noté sur la compréhension de ce qui a
  été construit, c'est un mauvais compromis : on n'a pas l'échelle qui
  justifierait la complexité.
- **Personalized PageRank** : propage de l'importance depuis un ou plusieurs
  nœuds de départ. Utile pour "en partant des films que j'aime, qu'est-ce qui
  est le plus 'accessible' dans le graphe", mais ne modélise pas directement
  "quels utilisateurs me ressemblent" — pas le bon outil pour du collaborative
  filtering utilisateur-utilisateur.
- **Louvain / Label Propagation (détection de communautés)** : regroupe les
  utilisateurs en clusters. Plus grossier qu'une similarité par paire (on perd
  la notion de "à quel point" deux utilisateurs se ressemblent), et ajoute une
  couche d'interprétation (que représente un cluster ?) qu'on n'a pas besoin
  de gérer.
- **Node Similarity (Jaccard)** : calcule directement une similarité par paire
  d'utilisateurs à partir de leurs films notés en commun. Le résultat est
  interprétable ("ces deux utilisateurs ont noté 60% des mêmes films"), il
  prolonge exactement l'intuition de la stratégie 2 (collaborative filtering
  en Cypher pur) en corrigeant sa faiblesse principale (pondérer par le
  *taux* de recoupement plutôt que par sa simple présence), et sa complexité
  reste raisonnable pour la taille de graphe d'un projet étudiant. C'est aussi
  l'algorithme mis en avant dans le guide officiel Neo4j "Build a Cypher
  recommendation engine" cité dans les ressources du sujet — cohérent avec ce
  que l'énoncé attendait implicitement.

## Pourquoi ce service est en lecture seule (aucune entité OGM)

Comme rating-service et la watchlist de user-service : partager une seule
base Neo4j entre microservices n'est tenable que si chaque service reste
strict sur ce qu'il a le droit d'écrire. recommendation-service ne possède
rien du tout — il n'écrit jamais, donc le risque de `save()` en cascade qui
motivait déjà l'usage de `Neo4jClient` ailleurs ne se pose même pas ici, mais
on garde la même approche (`Neo4jClient`, pas d'entité `@Node`) par cohérence
et parce que ce service n'a de toute façon aucun agrégat à modéliser — que des
requêtes de lecture complexes.
