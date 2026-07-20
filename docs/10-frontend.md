# Frontend (Angular)

## Stack et choix structurants

- **Angular 22**, généré via `ng new frontend --routing --style=scss --ssr=false`.
- **Standalone components partout** : pas de `NgModule`. C'est le seul mode supporté
  par défaut depuis Angular 19+, donc pas vraiment un choix mais une convention à
  connaître si tu viens d'un projet Angular plus ancien (buy-02 par ex. avait
  probablement encore des `NgModule`).
- **Signals** (`signal`, `computed`) plutôt que `BehaviorSubject`/RxJS pour l'état
  local (ex : session utilisateur dans `AuthService`, état de chargement des pages).
  RxJS reste utilisé pour les appels HTTP (`HttpClient` retourne des `Observable`),
  signals pour l'état qui découle de ces appels.
- **SSR désactivé** (`--ssr=false`). Ce projet est une SPA pure qui consomme une API
  REST déjà authentifiée par JWT côté client — le SSR n'apporte rien ici (pas de
  besoin SEO, pas de contenu public à pré-rendre) et complique l'accès à
  `localStorage`/`window`. Voir la remarque dans `auth.service.ts`.
- **Build system** : `@angular/build` (esbuild), plus rapide que l'ancien
  `@angular-devkit/build-angular` basé Webpack. C'est le défaut de la CLI 22.
- **Vitest** comme test runner (défaut de la CLI 22, remplace Karma/Jasmine).

## Arborescence

```
src/app/
├── core/                     # Rien de visuel : services, modèles, auth
│   ├── api-config.ts         # URLs des 4 microservices (voir 01-architecture.md)
│   ├── models/                # Interfaces TS calquées sur les DTO backend
│   ├── auth/
│   │   ├── auth.service.ts    # État de session (signal) + appels login/register/2FA
│   │   ├── auth.interceptor.ts # Injecte le header Authorization sur chaque requête
│   │   └── auth.guard.ts      # Bloque l'accès aux routes protégées si non connecté
│   └── movie.service.ts
└── features/                  # Une page = un dossier = un standalone component
    ├── home/
    └── auth/
        ├── login/
        └── register/
```

Règle appliquée : `core/` ne contient jamais de composant (pas d'UI), `features/`
ne contient jamais de logique métier partagée. Un service utilisé par plusieurs
pages vit dans `core/`, un composant vit dans `features/<page>/`.

## Authentification côté client

- Le token JWT est stocké dans `localStorage` (clé `neo4flix_auth`), lu une seule
  fois à l'initialisation du signal `authState` dans `AuthService`. Comme il n'y a
  pas de SSR, ce code tourne toujours dans le navigateur — pas de garde
  `isPlatformBrowser` nécessaire.
- `authInterceptor` (fonction, pas classe — voir plus bas) lit `auth.token()` et
  ajoute `Authorization: Bearer <token>` sur **toutes** les requêtes sortantes s'il
  y a un token. Il ne filtre pas par URL : les endpoints publics (login, register,
  lecture des films) ignorent simplement le header s'ils n'en ont pas besoin, donc
  pas de liste d'exclusions à maintenir côté frontend.
- `authGuard` (`CanActivateFn`) redirige vers `/login` si `isAuthenticated()` est
  faux. **Pas encore branché sur aucune route** — `app.routes.ts` n'a que
  `/`, `/login`, `/register` pour l'instant, toutes publiques. À appliquer dès que
  les pages watchlist/notation/recommandations existeront (ce sont elles qui
  nécessitent d'être connecté).

### Pourquoi des interceptors/guards fonctionnels plutôt que des classes

Angular supportait auparavant `HttpInterceptor` (classe avec `intercept()`) et
`CanActivate` (classe avec `canActivate()`). Depuis Angular 15+, l'API
fonctionnelle (`HttpInterceptorFn`, `CanActivateFn`) est recommandée et c'est ce
que génère la CLI 22 par défaut : moins de boilerplate (pas de classe injectable
à déclarer), et l'injection de dépendances se fait avec `inject()` directement
dans la fonction plutôt que par constructeur. Les deux styles sont fonctionnellement
équivalents, mais mélanger les deux dans un même projet compliquerait la revue de
code sans bénéfice — on reste 100% fonctionnel.

## Configuration HTTP

`app.config.ts` branche `provideHttpClient(withInterceptors([authInterceptor]))`.
Pas de `HttpClientModule` (obsolète en standalone) : les providers `provideXxx()`
sont la façon standard de configurer l'app depuis qu'il n'y a plus de `NgModule`
racine.

## Pages (routes)

| Route | Composant | Protégée ? |
|---|---|---|
| `/` | `Home` | non |
| `/login`, `/register` | `Login`, `Register` | non |
| `/movies/:id` | `MovieDetail` | non (voir plus bas) |
| `/watchlist` | `Watchlist` | oui (`authGuard`) |
| `/recommendations` | `Recommendations` | oui (`authGuard`) |

Port de dev par défaut fixé à **4201** dans `angular.json`
(`architect.serve.options.port`) — 4200 est déjà pris par un autre projet sur les
machines de l'équipe, voir `01-architecture.md`.

### Pourquoi la notation est sur la page détail film, pas sur une page séparée

L'énoncé liste "a page for each movie" et "a page for rating movies" comme deux
puces distinctes, mais rien n'empêche que ce soit la même page : noter un film
n'a de sens que dans le contexte de ses détails (titre, notes existantes). Séparer
en deux URLs aurait juste ajouté un aller-retour de navigation sans bénéfice.
`MovieDetail` affiche donc les infos du film, la liste des avis (`GET
/api/movies/{id}/ratings`, public), et le formulaire de notation de l'utilisateur
connecté (`PUT`/`DELETE /api/movies/{id}/rating`) dans une seule page.

`/movies/:id` n'est pas derrière `authGuard` : voir les détails et les avis est
public (comme la home), seul le formulaire de notation et le bouton watchlist
sont conditionnés à `auth.isAuthenticated()` dans le template.

### Pourquoi le partage est un lien copié dans le presse-papiers

L'audit demande "users can share movie recommendations with friends", mais le
modèle de données n'a aucune notion d'amis ou de messagerie (`02-data-model.md`
n'a que `User`/`Movie`/`RATED`/`WANTS_TO_WATCH`). Construire un vrai système
social serait hors scope. La solution retenue (`core/share.util.ts`) génère un
lien `http://localhost:4201/movies/{movieId}` et le copie dans le presse-papiers
(`navigator.clipboard`) — l'utilisateur le partage ensuite par le canal de son
choix (mail, SMS, etc.), ce qui répond littéralement à "share with friends" sans
inventer une fonctionnalité qui n'est demandée nulle part ailleurs dans l'énoncé.
Le bouton est présent sur `MovieDetail` et sur chaque carte de
`Recommendations`.

## Ce qui n'est pas encore fait

- Pas de fichiers d'environnement (`environment.ts`/`environment.prod.ts`) : les
  URLs sont en dur dans `api-config.ts` pointées sur `localhost`. À revoir si un
  jour il y a un déploiement autre que local.
- Pas de tests unitaires écrits au-delà du test par défaut généré par la CLI
  (`app.spec.ts`, nettoyé pour ne plus référencer le placeholder supprimé).
- `WatchlistService.list()` est réutilisé sur `MovieDetail` juste pour savoir si
  *ce* film y est déjà (pas d'endpoint dédié côté backend genre
  `GET /watchlist/{movieId}`) — acceptable tant que la watchlist reste petite,
  mais si elle grossit ça vaudrait le coup d'ajouter cet endpoint plutôt que de
  refetch toute la liste à chaque ouverture de page film.
