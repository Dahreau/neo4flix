<#
.SYNOPSIS
Seeds movie-service with a small but varied catalogue so the frontend (home,
search, recommendations) has something real to show instead of an empty grid.

Posters are placeholder images (picsum.photos, seeded per movie for visual
variety) - no real poster assets/licensing involved, purely decorative for dev.

Genres deliberately overlap across movies (several Sci-Fi, several Drama, etc.)
so content-based/collaborative/GDS recommendations have actual signal to work
with - a catalogue where every movie has a unique genre would make every
recommendation strategy return nothing.

Creating a movie is an admin-only action (movie-service requires ROLE_ADMIN on
writes, see docs/04-security.md) - this script registers a disposable admin
account, promotes it via promote-admin.ps1, logs in, and uses that token for
every POST. The account is left in place afterwards (harmless, it's not deleted
by this script) so you can reuse its credentials if you re-run this.

.PARAMETER MovieServiceUrl
Defaults to http://localhost:8091.

.PARAMETER UserServiceUrl
Defaults to http://localhost:8092.

.EXAMPLE
.\scripts\seed-movies.ps1
#>

param(
    [string]$MovieServiceUrl = "http://localhost:8091",
    [string]$UserServiceUrl = "http://localhost:8092"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "Setting up a disposable admin account to create movies..." -ForegroundColor Cyan
$username = "seed_admin"
$password = "SeedAdmin123"
$registerBody = @{ username = $username; email = "$username@neo4flix.local"; password = $password } | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$UserServiceUrl/api/auth/register" -Method Post -Body $registerBody -ContentType "application/json" | Out-Null
    Write-Host "  Registered $username" -ForegroundColor Green
}
catch {
    Write-Host "  $username already exists, reusing it" -ForegroundColor Yellow
}

& (Join-Path $scriptDir "promote-admin.ps1") -Username $username | Out-Null
Write-Host "  Promoted $username to ROLE_ADMIN" -ForegroundColor Green

$loginBody = @{ username = $username; password = $password } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$UserServiceUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
if (-not $login.auth.token) {
    throw "Could not log in as $username - check that user-service is running and 2FA isn't enabled on this account."
}
$headers = @{ Authorization = "Bearer $($login.auth.token)" }

$movies = @(
    @{ title = "Inception"; releaseDate = "2010-07-16"; durationMinutes = 148; genres = @("Sci-Fi", "Thriller"); synopsis = "Un voleur s'infiltre dans les reves pour y implanter une idee."; posterUrl = "https://picsum.photos/seed/inception/300/450" }
    @{ title = "Interstellar"; releaseDate = "2014-11-05"; durationMinutes = 169; genres = @("Sci-Fi", "Drama"); synopsis = "Un groupe d'explorateurs traverse un trou de ver pour sauver l'humanite."; posterUrl = "https://picsum.photos/seed/interstellar/300/450" }
    @{ title = "The Matrix"; releaseDate = "1999-03-31"; durationMinutes = 136; genres = @("Sci-Fi", "Action"); synopsis = "Un programmeur decouvre que la realite est une simulation."; posterUrl = "https://picsum.photos/seed/matrix/300/450" }
    @{ title = "Blade Runner 2049"; releaseDate = "2017-10-06"; durationMinutes = 164; genres = @("Sci-Fi", "Thriller"); synopsis = "Un blade runner traque des replicants disparus."; posterUrl = "https://picsum.photos/seed/bladerunner/300/450" }
    @{ title = "Mad Max: Fury Road"; releaseDate = "2015-05-15"; durationMinutes = 120; genres = @("Action", "Sci-Fi"); synopsis = "Une fuite a travers un desert post-apocalyptique."; posterUrl = "https://picsum.photos/seed/madmax/300/450" }
    @{ title = "John Wick"; releaseDate = "2014-10-24"; durationMinutes = 101; genres = @("Action", "Thriller"); synopsis = "Un tueur a gages reprend les armes apres la mort de son chien."; posterUrl = "https://picsum.photos/seed/johnwick/300/450" }
    @{ title = "The Dark Knight"; releaseDate = "2008-07-18"; durationMinutes = 152; genres = @("Action", "Crime", "Drama"); synopsis = "Batman affronte le Joker a Gotham City."; posterUrl = "https://picsum.photos/seed/darkknight/300/450" }
    @{ title = "Heat"; releaseDate = "1995-12-15"; durationMinutes = 170; genres = @("Crime", "Thriller"); synopsis = "Un braqueur et un flic s'affrontent a Los Angeles."; posterUrl = "https://picsum.photos/seed/heat/300/450" }
    @{ title = "The Godfather"; releaseDate = "1972-03-24"; durationMinutes = 175; genres = @("Crime", "Drama"); synopsis = "L'ascension d'une famille mafieuse italo-americaine."; posterUrl = "https://picsum.photos/seed/godfather/300/450" }
    @{ title = "Pulp Fiction"; releaseDate = "1994-10-14"; durationMinutes = 154; genres = @("Crime", "Drama"); synopsis = "Des histoires croisees dans la pegre de Los Angeles."; posterUrl = "https://picsum.photos/seed/pulpfiction/300/450" }
    @{ title = "Forrest Gump"; releaseDate = "1994-07-06"; durationMinutes = 142; genres = @("Drama", "Romance"); synopsis = "La vie extraordinaire d'un homme simple a travers l'histoire americaine."; posterUrl = "https://picsum.photos/seed/forrestgump/300/450" }
    @{ title = "La La Land"; releaseDate = "2016-12-09"; durationMinutes = 128; genres = @("Romance", "Drama"); synopsis = "Un musicien et une actrice tentent de percer a Los Angeles."; posterUrl = "https://picsum.photos/seed/lalaland/300/450" }
    @{ title = "Titanic"; releaseDate = "1997-12-19"; durationMinutes = 195; genres = @("Romance", "Drama"); synopsis = "Une histoire d'amour a bord du paquebot qui a fait naufrage."; posterUrl = "https://picsum.photos/seed/titanic/300/450" }
    @{ title = "The Grand Budapest Hotel"; releaseDate = "2014-03-07"; durationMinutes = 99; genres = @("Comedy", "Drama"); synopsis = "Les aventures d'un concierge legendaire et de son groom."; posterUrl = "https://picsum.photos/seed/budapesthotel/300/450" }
    @{ title = "Superbad"; releaseDate = "2007-08-17"; durationMinutes = 113; genres = @("Comedy"); synopsis = "Deux lyceens tentent de reussir leur derniere soiree avant la fac."; posterUrl = "https://picsum.photos/seed/superbad/300/450" }
    @{ title = "Get Out"; releaseDate = "2017-02-24"; durationMinutes = 104; genres = @("Horror", "Thriller"); synopsis = "Un jeune homme decouvre les secrets glacants de la famille de sa copine."; posterUrl = "https://picsum.photos/seed/getout/300/450" }
    @{ title = "Hereditary"; releaseDate = "2018-06-08"; durationMinutes = 127; genres = @("Horror", "Drama"); synopsis = "Une famille est hantee par un lourd secret apres un deces."; posterUrl = "https://picsum.photos/seed/hereditary/300/450" }
    @{ title = "Spirited Away"; releaseDate = "2001-07-20"; durationMinutes = 125; genres = @("Animation", "Adventure"); synopsis = "Une fillette se retrouve prisonniere d'un monde peuple d'esprits."; posterUrl = "https://picsum.photos/seed/spiritedaway/300/450" }
    @{ title = "Coco"; releaseDate = "2017-10-27"; durationMinutes = 105; genres = @("Animation", "Adventure"); synopsis = "Un garcon plonge dans le monde des morts pour percer un secret familial."; posterUrl = "https://picsum.photos/seed/coco/300/450" }
    @{ title = "Indiana Jones and the Last Crusade"; releaseDate = "1989-05-24"; durationMinutes = 127; genres = @("Adventure", "Action"); synopsis = "Indiana Jones part a la recherche de son pere et du Graal."; posterUrl = "https://picsum.photos/seed/indianajones/300/450" }
)

Write-Host ""
Write-Host "Seeding $($movies.Count) movies into $MovieServiceUrl ..." -ForegroundColor Cyan

$created = 0
foreach ($movie in $movies) {
    $body = $movie | ConvertTo-Json
    try {
        $result = Invoke-RestMethod -Uri "$MovieServiceUrl/api/movies" -Method Post -Body $body -ContentType "application/json" -Headers $headers
        Write-Host "  [OK] $($movie.title) -> $($result.movieId)" -ForegroundColor Green
        $created++
    }
    catch {
        Write-Host "  [FAIL] $($movie.title): $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "$created / $($movies.Count) movies created." -ForegroundColor Cyan
