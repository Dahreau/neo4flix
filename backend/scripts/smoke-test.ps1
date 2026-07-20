<#
.SYNOPSIS
Fast local "CI" replacement for neo4flix. No Jenkins/SonarQube on this project,
so this script gives quick build + functional feedback in one command instead
of retesting every endpoint by hand in Postman after each change.

What it does:
  1. Build check: `mvnw compile` on all 4 microservices. Stops on the first
     failure with Maven's own error output - same fast-fail idea as a Jenkins
     build stage.
  2. Smoke test: full functional flow against the RUNNING services (register
     a user, promote to admin, log in, create a movie, rate it, check the
     average got recalculated, watchlist, all 3 recommendation endpoints),
     asserting each step. Assumes Neo4j + the 4 services are already started
     separately (see docs/00-getting-started.md).

What it does NOT do: static analysis / code smells. That was SonarQube's job
on buy-02 - genuinely not replaced here, out of scope for this script.

.PARAMETER SkipBuild
Skip the build check, go straight to the smoke test.

.PARAMETER BuildOnly
Only run the build check, skip the smoke test (useful if services aren't
running yet).

.EXAMPLE
.\scripts\smoke-test.ps1
.\scripts\smoke-test.ps1 -SkipBuild
.\scripts\smoke-test.ps1 -BuildOnly
#>

param(
    [switch]$SkipBuild,
    [switch]$BuildOnly
)

$ErrorActionPreference = "Stop"

$services = @("movie-service", "user-service", "rating-service", "recommendation-service")
$movieUrl = "http://localhost:8091"
$userUrl = "http://localhost:8092"
$ratingUrl = "http://localhost:8093"
$recoUrl = "http://localhost:8094"

function Write-Step($msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Write-Ok($msg) {
    Write-Host "  [OK] $msg" -ForegroundColor Green
}

function Write-Fail($msg) {
    Write-Host "  [FAIL] $msg" -ForegroundColor Red
}

function Assert-True($condition, $message) {
    if (-not $condition) {
        Write-Fail $message
        throw "Assertion failed: $message"
    }
    Write-Ok $message
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Split-Path -Parent $scriptDir

if (-not $SkipBuild) {
    Write-Step "Build check (mvnw compile on all 4 services)"
    foreach ($service in $services) {
        $path = Join-Path $backendDir $service
        Write-Host "  Building $service..."
        Push-Location $path
        try {
            & .\mvnw.cmd -DskipTests compile
            if ($LASTEXITCODE -ne 0) {
                Write-Fail "$service failed to compile (see Maven output above)"
                exit 1
            }
            Write-Ok "$service compiles"
        }
        finally {
            Pop-Location
        }
    }
}

if ($BuildOnly) {
    Write-Host ""
    Write-Host "Build check done, skipping smoke test (-BuildOnly)." -ForegroundColor Yellow
    exit 0
}

Write-Step "Checking the 4 services are listening"
$ports = @{ "movie-service" = 8091; "user-service" = 8092; "rating-service" = 8093; "recommendation-service" = 8094 }
foreach ($name in $ports.Keys) {
    $port = $ports[$name]
    try {
        $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction Stop
        Write-Ok "$name is listening on port $port"
    }
    catch {
        Write-Host "  [WARN] Could not confirm $name on port $port - continuing anyway" -ForegroundColor Yellow
    }
}

Write-Step "1. Register a user and promote to admin"
# Creating/deleting a movie is admin-only (movie-service requires ROLE_ADMIN on
# writes, see docs/04-security.md) - this test account needs that role for
# steps 2 and 8 below.
$suffix = Get-Random
$username = "smoketest_$suffix"
$password = "Motdepasse123"
$registerBody = @{ username = $username; email = "$username@test.fr"; password = $password } | ConvertTo-Json
$registration = Invoke-RestMethod -Uri "$userUrl/api/auth/register" -Method Post -Body $registerBody -ContentType "application/json"
Assert-True ($null -ne $registration.token) "User registered ($username), token received"

& (Join-Path $scriptDir "promote-admin.ps1") -Username $username | Out-Null
Write-Ok "Promoted $username to ROLE_ADMIN"

Write-Step "2. Login"
$loginBody = @{ username = $username; password = $password } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$userUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
Assert-True ($login.requiresTwoFactor -eq $false) "Login without 2FA returns a direct token"
Assert-True ($null -ne $login.auth.token) "Login token received"
# Issued after the promotion above, so this token's roles claim already includes ROLE_ADMIN.
$headers = @{ Authorization = "Bearer $($login.auth.token)" }

Write-Step "3. Create a movie"
$movieBody = @{ title = "Smoke Test Movie"; releaseDate = "2020-01-01"; genres = @("Test") } | ConvertTo-Json
$movie = Invoke-RestMethod -Uri "$movieUrl/api/movies" -Method Post -Body $movieBody -ContentType "application/json" -Headers $headers
Assert-True ($null -ne $movie.movieId) "Movie created (movieId: $($movie.movieId))"
$movieId = $movie.movieId

Write-Step "4. Rate the movie"
$ratingBody = @{ score = 4.5; comment = "Smoke test" } | ConvertTo-Json
Invoke-RestMethod -Uri "$ratingUrl/api/movies/$movieId/rating" -Method Put -Body $ratingBody -ContentType "application/json" -Headers $headers | Out-Null
Write-Ok "Rating submitted"

Write-Step "5. Check the average rating was recalculated"
$updatedMovie = Invoke-RestMethod -Uri "$movieUrl/api/movies/$movieId"
Assert-True ($updatedMovie.averageRating -eq 4.5) "Movie averageRating is 4.5 (got $($updatedMovie.averageRating))"

Write-Step "6. Watchlist"
Invoke-RestMethod -Uri "$userUrl/api/users/me/watchlist/$movieId" -Method Post -Headers $headers | Out-Null
# @() forces array semantics even when the API returns exactly one item - without it,
# PowerShell unwraps a single-element JSON array into a bare object, which has no
# .Count property and silently fails the assertion below even though the watchlist
# entry is actually there.
$watchlist = @(Invoke-RestMethod -Uri "$userUrl/api/users/me/watchlist" -Headers $headers)
Assert-True (@($watchlist | Where-Object { $_.movieId -eq $movieId }).Count -gt 0) "Movie appears in watchlist"

Write-Step "7. Recommendation endpoints respond"
Invoke-RestMethod -Uri "$recoUrl/api/recommendations/me/content-based" -Headers $headers | Out-Null
Write-Ok "content-based responded"
Invoke-RestMethod -Uri "$recoUrl/api/recommendations/me/collaborative" -Headers $headers | Out-Null
Write-Ok "collaborative responded"
Invoke-RestMethod -Uri "$recoUrl/api/recommendations/me/similar-users" -Headers $headers | Out-Null
Write-Ok "similar-users responded"

Write-Step "8. Cleanup"
Invoke-RestMethod -Uri "$ratingUrl/api/movies/$movieId/rating" -Method Delete -Headers $headers | Out-Null
Invoke-RestMethod -Uri "$movieUrl/api/movies/$movieId" -Method Delete -Headers $headers | Out-Null
Write-Ok "Test movie and rating removed"

Write-Host ""
Write-Host "ALL CHECKS PASSED" -ForegroundColor Green
