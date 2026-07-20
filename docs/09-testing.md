# Tests rapides sans Jenkins/SonarQube

Buy-02 a un vrai pipeline CI/CD (Jenkins + SonarQube). Neo4flix n'en
a pas — ce n'était pas l'objectif de ce projet (l'objectif ici c'est Neo4j).
Plutôt que de retester chaque endpoint à la main dans Postman après chaque
changement, `backend/scripts/smoke-test.ps1` fait un test de bout en bout en
une commande.

## Ce que le script fait

1. **Build check** : `mvnw compile` sur les 4 microservices, s'arrête
   immédiatement sur le premier qui casse, avec la sortie Maven affichée —
   c'est l'équivalent du premier stage d'un pipeline Jenkins (est-ce que ça
   compile), en local et instantané.
2. **Smoke test fonctionnel** : rejoue le scénario complet de
   [`00-getting-started.md`](00-getting-started.md) (créer un compte, le passer admin via
   `promote-admin.ps1`, se reconnecter pour obtenir un token à jour, créer un
   film, le noter, vérifier que la moyenne s'est recalculée, watchlist, les 3
   endpoints de recommandation), avec une assertion à chaque étape. Si une
   étape échoue, le script s'arrête net avec un message clair sur ce qui a
   cassé.

## Ce que le script ne fait pas

**Pas d'analyse statique de code** (complexité, duplication, code smells) —
c'était le rôle de SonarQube sur buy-02, et ce n'est pas remplacé ici.
Accepté comme limite du projet plutôt que masqué.

## Utilisation

```powershell
cd backend

# Build check + smoke test complet
.\scripts\smoke-test.ps1

# Juste le smoke test (les services tournent déjà, pas besoin de rebuild)
.\scripts\smoke-test.ps1 -SkipBuild

# Juste le build check (services pas encore lancés)
.\scripts\smoke-test.ps1 -BuildOnly
```

Prérequis pour le smoke test complet : Neo4j lancé (`docker compose up -d`) et
les 4 microservices démarrés chacun dans leur terminal (`mvn spring-boot:run`),
voir [`00-getting-started.md`](00-getting-started.md).

Le script crée un utilisateur temporaire (`smoketest_<random>`), le promeut
admin, et un film de test, note ce film, vérifie tout, puis nettoie (supprime
la note et le film) à la fin — il ne laisse pas de données de test dans la
base à chaque exécution (le compte utilisateur lui-même reste, mais un compte
de test sans autre effet n'est pas considéré comme une pollution gênante).

### Piège PowerShell à connaître

L'assertion watchlist compare le résultat d'`Invoke-RestMethod` avec
`Where-Object`. Sans `@(...)` autour, PowerShell "déballe" un tableau JSON d'un
seul élément en objet nu — qui n'a pas de propriété `.Count` — et l'assertion
échoue silencieusement même si l'élément est bien présent. D'où le
`@($watchlist | Where-Object {...}).Count` dans le script : forcer le
tableau, systématiquement, résout la classe de bug entière plutôt qu'un seul cas.

## Tests de robustesse / cas limites (`security-test.ps1`)

`smoke-test.ps1` ne prouve que le chemin heureux. `backend/scripts/security-test.ps1`
couvre la partie de l'audit sur les cas limites et la sécurité — il envoie
volontairement de mauvaises entrées à l'API et vérifie que ça échoue
*proprement* (bon code 4xx, pas de 500, pas de fuite de données), pas juste
que ça marche quand tout va bien.

Ce qu'il vérifie :
- **Entrées invalides à l'inscription** : username vide, email mal formé, mot
  de passe faible (tous → 400), username en doublon (→ 409).
- **Pas de fuite d'existence de compte** : mauvais mot de passe et compte
  inexistant renvoient exactement le même statut (401) et le même message
  générique.
- **Autorisation** : accès à une route protégée sans token (401), avec un
  token invalide/garbage (401, pas un 500 qui trahirait un bug de parsing),
  action admin tentée par un compte normal (403).
- **Validation métier** : titre de film vide (400), note hors de la plage
  0.5–5.0 (400), ressource inexistante (404 sur un film/une note qui n'existe
  pas).
- **Chaîne ressemblant à une injection Cypher dans la recherche** : vérifie
  que la requête reste une simple recherche texte (200, pas d'erreur) et que
  la base n'a pas été altérée — la liaison de paramètres (`$searchTerm`)
  empêche déjà l'injection Cypher structurellement, ce test le confirme en
  conditions réelles plutôt que par lecture de code seule.
- **Texte façon XSS dans un commentaire de note** : confirmé stocké tel quel
  côté backend (normal, ce n'est pas son rôle de filtrer du HTML) — c'est
  Angular qui neutralise ça à l'affichage via l'interpolation `{{ }}`, voir
  [`10-frontend.md`](10-frontend.md).
- **Vérification de concurrence légère** : 20 requêtes GET en parallèle sur
  `/api/movies`, juste pour confirmer qu'aucune ne plante. **Ce n'est pas un
  vrai test de charge** (pas de mesure de débit/latence, pas d'outil dédié
  comme k6/Gatling) — limite assumée, pas masquée.

**Trouvé en écrivant ce script** : une recherche contenant des caractères
spéciaux Lucene (parenthèses, tiret, guillemets...) faisait planter
`movie-service` avec une 500, parce que le texte de recherche est passé
directement au parseur de requêtes de l'index plein texte. Corrigé
(`MovieService.escapeLuceneQuery`) — détail dans [`05-movie-service.md`](05-movie-service.md).

Contrairement à `smoke-test.ps1`, ce script ne s'arrête pas à la première
erreur : il fait tourner tous les tests et affiche un résumé à la fin (pass
count / fail count), pour ne pas cacher un problème derrière un autre.

```powershell
cd backend
.\scripts\security-test.ps1
```

## Promouvoir un compte admin

`backend/scripts/promote-admin.ps1 -Username <username>` passe un compte déjà
inscrit en `ROLE_ADMIN` (+ garde `ROLE_USER`), en modifiant directement le
nœud `User` via `cypher-shell` — il n'y a pas d'API pour ça (voir
[`04-security.md`](04-security.md) pour le pourquoi). `seed-movies.ps1` et `smoke-test.ps1`
l'utilisent tous les deux en interne pour pouvoir créer des films. Rappel
important : un token déjà émis ne se met pas à jour tout seul, il faut se
reconnecter après la promotion pour obtenir un JWT qui porte `ROLE_ADMIN`.

## Peupler une base vide

Une base Neo4j fraîche n'a aucun film — la home page affiche "Aucun film
trouvé.", normal. `backend/scripts/seed-movies.ps1` crée une vingtaine de films
réels avec des genres qui se recoupent volontairement (plusieurs Sci-Fi,
plusieurs Drama, etc.) — indispensable pour que les recommandations
content-based/collaborative/GDS aient un vrai signal à exploiter plutôt que de
retourner des listes vides. Les posters sont des images placeholder
(picsum.photos), purement décoratives, pas de vrais assets.

```powershell
cd backend
.\scripts\seed-movies.ps1
```

Nécessite movie-service démarré (port 8091). À lancer une seule fois par base
(relancer recrée des doublons, il n'y a pas de déduplication par titre).
