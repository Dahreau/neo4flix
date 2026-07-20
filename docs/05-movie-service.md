# movie-service

Port **8091**. Possède les nœuds `Movie`, `Genre` et la relation `IN_GENRE`.
Un nœud `Person` (acteurs/réalisateurs) avait été envisagé mais jamais
implémenté — scope-cut volontaire, pas requis par l'audit, voir
`02-data-model.md`.

## Modèle

- `Movie` (`domain/Movie.java`) : `movieId` (UUID généré), `title`,
  `originalTitle`, `releaseDate`, `durationMinutes`, `synopsis`, `posterUrl`,
  `averageRating` (écrit uniquement par rating-service, jamais ici),
  `createdAt`, relation `IN_GENRE` vers un ensemble de `Genre`.
- `Genre` (`domain/Genre.java`) : `name` fait office d'id (clé métier).

## Endpoints

| Méthode | Path | Body | Auth | Description |
|---|---|---|---|---|
| GET | `/api/movies` | — | non | Liste tous les films |
| GET | `/api/movies/{movieId}` | — | non | Détail d'un film |
| GET | `/api/movies/search?q=&genre=&from=&to=` | — | non | Recherche par titre/synopsis (plein texte), genre, ou plage de date |
| POST | `/api/movies` | `MovieRequest` | **admin** | Crée un film |
| PUT | `/api/movies/{movieId}` | `MovieRequest` | **admin** | Met à jour un film |
| DELETE | `/api/movies/{movieId}` | — | **admin** | Supprime un film |
| GET | `/api/genres` | — | non | Liste tous les genres (alimente les filtres) |

Les écritures exigent un JWT dont les claims `roles` contiennent `ROLE_ADMIN`
(`SecurityConfig`, `hasRole("ADMIN")` — Spring Security cherche l'autorité
`ROLE_ADMIN`, le préfixe est ajouté automatiquement par `hasRole`). Aucun
compte n'est admin par défaut ; voir `04-security.md` pour
`scripts/promote-admin.ps1`.

## Décisions techniques

- **Validation (`@NotBlank` sur `MovieRequest.title`)** : seul le titre est
  obligatoire, le reste (synopsis, poster, durée...) est une métadonnée
  vraiment optionnelle. Sans ça, un `POST` avec un titre vide créait un film
  fantôme sans erreur.
- **Échappement des caractères spéciaux Lucene dans la recherche plein texte**
  (`MovieService.escapeLuceneQuery`) : `db.index.fulltext.queryNodes` passe la
  chaîne de recherche directement au parseur de requêtes Lucene. Le paramètre
  `$searchTerm` protège déjà contre l'injection Cypher (liaison de paramètre
  standard), mais pas contre un texte utilisateur ordinaire contenant des
  caractères spéciaux Lucene (`(`, `)`, `-`, `/`, `"`, etc.) qui ferait planter
  le parseur avec une erreur 500 sur une recherche a priori anodine (ex :
  chercher "Spider-Man" ou "(2020)"). Repéré en écrivant
  `scripts/security-test.ps1`, corrigé en échappant chaque caractère spécial
  avant l'appel au repository.
- **DTOs (`MovieRequest`/`MovieResponse`) plutôt que l'entité directement sur
  le controller** : évite de coupler le format d'échange REST au mapping OGM
  (relations, id interne).
- **`search` avec 4 stratégies distinctes** (`MovieService.search`) : plein
  texte si `q` fourni, sinon par genre, sinon par plage de date, sinon tout —
  volontairement simple plutôt qu'un query builder générique, le besoin
  actuel ne justifie pas plus de complexité.
- **Gestion d'erreur (`GlobalExceptionHandler`) dupliquée par service**, pas
  de librairie partagée : chaque microservice reste buildable/déployable
  indépendamment, pas de couplage pour un détail de ce poids.
