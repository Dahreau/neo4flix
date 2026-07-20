# Sécurité

## JWT — comment ça marche ici

Un JWT (JSON Web Token) est une chaîne signée en 3 parties séparées par des
points : `header.payload.signature`. Le payload contient des informations
("claims") lisibles par n'importe qui (ce n'est **pas** chiffré, juste encodé),
la signature garantit que le contenu n'a pas été modifié depuis son émission
par le détenteur du secret.

Flux complet dans neo4flix :

1. **Register/login** (`user-service`) : le mot de passe est vérifié
   (`BCryptPasswordEncoder`, jamais stocké en clair), puis `JwtService`
   génère un token signé (HMAC-SHA256) contenant `sub` (username),
   `userId`, `roles`, `iat` (émis à), `exp` (expire à).
2. Le client stocke ce token (côté frontend : à faire, pas encore construit)
   et le renvoie dans le header `Authorization: Bearer <token>` sur chaque
   requête protégée.
3. Chaque service (`user-service`, `rating-service`, et bientôt
   `recommendation-service`) a un `JwtAuthenticationFilter`
   (`OncePerRequestFilter`) qui intercepte la requête, extrait le token,
   vérifie la signature avec le secret partagé, et si valide peuple le
   `SecurityContext` de Spring Security avec le `userId` comme principal —
   c'est ce qui permet à un controller de récupérer "qui fait la requête" via
   `Principal.getName()` sans jamais avoir à faire confiance à un `userId`
   passé en paramètre par le client.

## Pourquoi un secret partagé plutôt qu'un appel à user-service à chaque requête

rating-service et recommendation-service n'émettent jamais de token, ils ne
font que le **valider**, avec exactement le même secret HMAC que user-service
(`jwt.secret` dans chaque `application.properties`, actuellement une valeur en
dur — voir section suivante). Ça évite un aller-retour réseau vers
user-service à chaque requête protégée : la validation est purement locale et
cryptographique.

**Contrainte à ne jamais oublier** : si le secret change dans user-service, il
doit changer *partout* en même temps, sinon rating-service/recommendation-service
rejetteront des tokens pourtant valides.

## Pourquoi ces librairies précisément

**`jjwt` (io.jsonwebtoken) plutôt que `spring-boot-starter-oauth2-resource-server`** :
la solution "la plus Spring" pour valider des JWT est normalement le module
OAuth2 Resource Server, avec un `JwtDecoder` qui va chercher les clés
publiques sur un endpoint JWKS exposé par un Authorization Server. Ce module a
du sens quand un fournisseur d'identité tiers (Keycloak, Auth0...) émet les
tokens. Ici, on est *nous-mêmes* l'émetteur (user-service) et le validateur
(les autres services) — monter un vrai Authorization Server juste pour
obtenir des JWT signés serait une complexité disproportionnée pour ce projet.
`jjwt` donne un contrôle direct sur la signature/claims avec une API simple,
ce qui correspond à une architecture "on émet et on valide nous-mêmes".

**Secret HMAC symétrique plutôt que paire de clés RSA (asymétrique)** :
avec HMAC, n'importe quel service qui peut *valider* un token peut aussi en
*forger* un, puisqu'il détient le même secret que celui qui signe. Avec une
paire de clés (RS256), user-service garderait une clé privée pour signer,
et rating-service/recommendation-service n'auraient besoin que de la clé
publique pour vérifier — plus sûr, parce qu'une fuite du secret d'un service
"lecteur" ne compromettrait jamais la capacité à émettre de faux tokens.
On a choisi le symétrique pour aller plus vite (moins de gestion de clés à
mettre en place), en connaissance de cause : c'est le compromis le plus
faible de toute la stack sécurité actuelle, documenté ci-dessous comme axe
d'amélioration.

**`dev.samstevens.totp` pour la 2FA** : petite librairie dédiée
(génération de secret, génération de QR code compatible Google
Authenticator/Authy, vérification de code TOTP conforme à la RFC 6238),
plutôt que réimplémenter l'algorithme HMAC-based OTP à la main — l'implémenter
soi-même est un classique nid à bugs subtils (troncature, fenêtre de temps,
encodage Base32) qu'une lib mature et testée évite. Nécessite `com.google.zxing`
(`core` + `javase`) pour le rendu du QR code en PNG, que la lib délègue plutôt
que d'embarquer directement.

## Comment fonctionne la 2FA (TOTP)

TOTP (Time-based One-Time Password) : un secret partagé entre le serveur et
l'application d'authentification (Google Authenticator, etc.), combiné au
timestamp actuel (par tranches de 30 secondes) via HMAC, produit un code à 6
chiffres que les deux parties peuvent calculer indépendamment sans jamais se
reparler. C'est pour ça que ça marche hors-ligne côté application mobile.

Flux :

1. `POST /api/auth/2fa/setup` (authentifié) : génère un secret, le stocke sur
   `User.totpSecret` (2FA pas encore activée à ce stade), renvoie le secret
   en clair (fallback pour saisie manuelle) + un QR code en data URI à
   afficher côté frontend.
2. L'utilisateur scanne le QR code avec son app, qui se met à générer des
   codes à partir du même secret.
3. `POST /api/auth/2fa/enable` avec `{"code": "123456"}` : vérifie que le
   code correspond bien au secret stocké (preuve que le scan a fonctionné),
   passe `User.twoFactorEnabled` à `true`.
4. À partir de là, `POST /api/auth/login` (username + password) ne renvoie
   plus directement un token si `twoFactorEnabled` est vrai : il renvoie
   `{"requiresTwoFactor": true, "auth": null}`. Le client doit enchaîner sur
   `POST /api/auth/2fa/verify-login` avec `{"username", "code"}` pour obtenir
   enfin le vrai token.

`POST /api/auth/2fa/disable` (authentifié, code requis) repasse le compte en
mode mot de passe seul.

## Ce qui est fait vs ce qui reste

Fait :
- Hash des mots de passe (BCrypt).
- JWT stateless, secret partagé entre services.
- Message d'erreur d'authentification volontairement générique (pas d'indice
  sur l'existence d'un compte).
- **2FA (TOTP)**, décrite ci-dessus.
- **Politique de mot de passe** : au moins 8 caractères, une majuscule, une
  minuscule, un chiffre (`AuthService.validatePasswordStrength`), vérifiée à
  l'inscription.
- **Secret JWT et mot de passe Neo4j lus depuis l'environnement, jamais en dur
  dans un fichier suivi par git** (`${JWT_SECRET:...}` / `${NEO4J_PASSWORD:...}`
  dans chaque `application.properties`). `docker-compose.yml` les lit lui-même
  depuis `backend/.env` (`${JWT_SECRET}`, `${NEO4J_PASSWORD}`) — `.env` est
  gitignored, seul `.env.example` (valeurs placeholder, pas les vraies) est
  suivi par git. Avant ce changement, la valeur de dev était écrite en clair
  directement dans `docker-compose.yml`, donc committée dans l'historique git
  dès le premier commit — pas idéal même pour une valeur "de dev", d'où le
  passage à `.env`. Reste, même avec ce mécanisme : la valeur elle-même est
  encore une valeur de convenance partagée par toute l'équipe, pas un secret
  unique par environnement généré par un vrai secret manager (Vault, AWS
  Secrets Manager...). Suffisant pour ce projet, pas pour une vraie prod.
- **movie-service protégé par JWT + rôle admin** : `GET` reste public (parcourir
  le catalogue n'a pas besoin de compte), mais `POST`/`PUT`/`DELETE /api/movies`
  exigent maintenant un token dont les claims `roles` contiennent `ROLE_ADMIN`
  (même `JwtAuthenticationFilter`/`SecurityConfig` que rating-service et
  recommendation-service, avec `hasRole("ADMIN")` sur les routes d'écriture).
  Les 4 microservices sont désormais tous authentifiés — plus de trou.

### Comment un compte devient admin

L'inscription (`POST /api/auth/register`) n'assigne jamais que `ROLE_USER` —
volontairement : si le client pouvait choisir son propre rôle, n'importe qui
se déclarerait admin. Il n'y a pas d'API de gestion des rôles (hors scope pour
ce projet), donc promouvoir un compte est une action manuelle :

```powershell
cd backend
.\scripts\promote-admin.ps1 -Username <username>
```

Ce script modifie directement le nœud `User` dans Neo4j (`u.roles = ['ROLE_ADMIN', 'ROLE_USER']`)
via `cypher-shell`. **Piège à connaître** : le JWT porte les rôles au moment où
il est émis (`login`) — promouvoir un compte ne change rien à un token déjà en
circulation, il faut se reconnecter (`/api/auth/login` à nouveau) pour obtenir
un token qui inclut `ROLE_ADMIN`. `seed-movies.ps1` fait exactement cette
séquence (inscription → promotion → login → création des films) pour son
propre compte de service.

Pas fait encore, par ordre de priorité :
- **Vrai secret manager pour le JWT** (Vault, cloud secret store...) — voir
  nuance ci-dessus, le mécanisme d'externalisation existe déjà, juste pas
  branché sur un stockage sécurisé.
- **HTTPS** (Let's Encrypt ou certificat auto-signé en dev) — actuellement tout
  tourne en HTTP local ; demanderait un reverse proxy (nginx en fait déjà
  tourner un devant le frontend, on pourrait étendre son rôle) devant les 4
  microservices aussi.
- **Passage à des clés asymétriques (RS256)** pour la signature JWT — voir
  discussion ci-dessus, compromis assumé pour la vitesse de développement.
- **Codes de récupération 2FA** (perte du téléphone) — `dev.samstevens.totp`
  sait les générer (`RecoveryCodeGenerator`), mais le stockage/la
  vérification côté serveur n'est pas branché : coupé du scope pour tenir
  les délais, un utilisateur qui perd son appareil 2FA n'a pas de solution
  de secours pour l'instant.
- **Refresh tokens** — le token actuel a une durée de vie fixe (24h,
  `jwt.expiration-ms`), pas de mécanisme de renouvellement sans se
  reconnecter.
