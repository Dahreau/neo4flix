# user-service

Port **8092**. Possède le nœud `User` et la relation `WANTS_TO_WATCH`
(watchlist). Seul service qui émet des JWT.

## Modèle

- `User` (`domain/User.java`) : `userId` (UUID généré), `username` (unique),
  `email` (unique), `passwordHash` (BCrypt), `roles`, `createdAt`.
  **Pas de relation `@Relationship` vers `Movie`** — volontaire, voir
  [`03-neo4j-concepts.md`](03-neo4j-concepts.md) section 4 (risque de `save()` en cascade qui
  écraserait des propriétés de `Movie`, propriété de movie-service).

## Endpoints

| Méthode | Path | Body | Auth | Description |
|---|---|---|---|---|
| POST | `/api/auth/register` | `RegisterRequest` | non | Crée un compte (mot de passe validé), renvoie un token |
| POST | `/api/auth/login` | `LoginRequest` | non | Authentifie. Renvoie un token direct, ou `{requiresTwoFactor: true}` si la 2FA est active |
| POST | `/api/auth/2fa/setup` | — | oui | Génère un secret TOTP + QR code (2FA pas encore activée) |
| POST | `/api/auth/2fa/enable` | `TwoFactorCodeRequest` | oui | Active la 2FA après vérification d'un premier code |
| POST | `/api/auth/2fa/disable` | `TwoFactorCodeRequest` | oui | Désactive la 2FA |
| POST | `/api/auth/2fa/verify-login` | `TwoFactorLoginRequest` | non | Deuxième étape du login si 2FA active, renvoie le token |
| GET | `/api/users/me` | — | oui | Profil de l'utilisateur connecté |
| GET | `/api/users/me/watchlist` | — | oui | Liste la watchlist |
| POST | `/api/users/me/watchlist/{movieId}` | — | oui | Ajoute un film à la watchlist |
| DELETE | `/api/users/me/watchlist/{movieId}` | — | oui | Retire un film de la watchlist |

Détail du flux 2FA et des choix de librairies : [`04-security.md`](04-security.md).

## Décisions techniques

- **Watchlist via `Neo4jClient` (`WatchlistRepository`), pas via une relation
  OGM** : `add`/`remove`/`findAll` sont des requêtes Cypher ciblées qui ne
  touchent que la relation `WANTS_TO_WATCH`, jamais les propriétés du nœud
  `Movie` visé. Avant d'ajouter, on vérifie que le film existe
  (`movieExists`) pour renvoyer une 404 propre plutôt que d'échouer
  silencieusement (un `MATCH` qui ne trouve rien ne lève pas d'erreur en
  Cypher, il retourne juste 0 ligne).
- **JWT (`security/`)** : `JwtService` génère le token (`jjwt` 0.13.0, API
  fluent `Jwts.builder()...signWith(key)`), `JwtAuthenticationFilter`
  l'intercepte sur chaque requête, `SecurityConfig` définit ce qui est public
  (`/api/auth/**`) vs protégé (tout le reste). Détail du flux complet dans
  [`04-security.md`](04-security.md).
- **Message d'erreur de login volontairement générique**
  (`InvalidCredentialsException` : "Identifiants invalides", jamais "mauvais
  mot de passe" ou "utilisateur inconnu") pour ne pas révéler à un attaquant
  si un compte existe. Les deux cas renvoient le même statut 401, vérifié par
  `scripts/security-test.ps1`.
- **Validation (`@NotBlank`/`@Email` sur `RegisterRequest`)** : username/email
  vides ou email mal formé sont rejetés avant même d'atteindre `AuthService` —
  distinct de la vérification de robustesse du mot de passe
  (`AuthService.validatePasswordStrength`), qui reste une règle métier avec
  son propre message, pas une simple validation de présence.
