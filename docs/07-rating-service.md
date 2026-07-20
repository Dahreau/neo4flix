# rating-service

Port **8093**. Seul service autorisé à écrire la relation `RATED`
(`User`-[:RATED]->`Movie`), et seul service autorisé à écrire la propriété
`Movie.averageRating`.

## Modèle

Aucune entité OGM (`@Node`) pour `User` ou `Movie` dans ce service — tout
passe par `Neo4jClient` (`RatingRepository`), voir [`03-neo4j-concepts.md`](03-neo4j-concepts.md)
section 4 pour le pourquoi.

Propriétés de la relation `RATED` : `score` (0.5 à 5.0), `comment`, `ratedAt`.

## Endpoints

| Méthode | Path | Body | Auth | Description |
|---|---|---|---|---|
| PUT | `/api/movies/{movieId}/rating` | `RatingRequest` | oui | Note (ou met à jour sa note pour) un film |
| DELETE | `/api/movies/{movieId}/rating` | — | oui | Retire sa propre note |
| GET | `/api/movies/{movieId}/rating` | — | oui | Sa propre note pour ce film |
| GET | `/api/movies/{movieId}/ratings` | — | non | Toutes les notes d'un film (public) |
| GET | `/api/users/me/ratings` | — | oui | Toutes les notes de l'utilisateur connecté |

## Décisions techniques

- **`PUT` plutôt que `POST` pour noter un film** : noter deux fois le même
  film met à jour la note existante (`MERGE` sur la relation), ce n'est pas
  une création répétée — sémantiquement c'est un "remplace l'état actuel",
  donc `PUT`.
- **Recalcul de `Movie.averageRating` dans la même requête Cypher que
  l'écriture de la note** (`RatingRepository.upsertRating`/`deleteRating`),
  pas dans une étape séparée : évite une fenêtre où la moyenne serait
  incohérente avec les notes réellement présentes, et évite un aller-retour
  réseau supplémentaire.
- **Validation d'existence avant écriture** (`userExists`, `movieExists`
  dans `RatingRepository`) : un `MATCH` Cypher qui ne trouve rien échoue
  silencieusement (0 ligne affectée, pas d'exception), donc sans cette
  vérification explicite un `userId`/`movieId` invalide donnerait un succès
  muet côté API au lieu d'une 404.
- **Lecture des notes d'un film publique, écriture protégée** : cohérent
  avec le fait que la fiche film elle-même (movie-service) est publique en
  lecture.
- **Validation du score (0.5 à 5.0) côté service** (`RatingService.rate`),
  pas côté base : Neo4j ne fait pas de validation de plage nativement, donc
  ce genre de règle métier doit vivre dans le code applicatif.
