<#
.SYNOPSIS
Edge-case / robustness checks against the RUNNING services - complements
smoke-test.ps1, which only proves the happy path works. This script instead
throws bad input, missing/garbage auth, and out-of-range values at the API and
checks it fails *gracefully* (a proper 4xx, no 500, no data leak) rather than
crashing or misbehaving.

Covers, roughly, the audit's "test against any possible flaws or edge cases"
and "security testing" checklist items. Does NOT cover:
  - Real load/stress testing (k6, Gatling, JMeter) - the "light concurrency
    check" at the end is a smoke check that concurrent requests don't crash
    the service, not a real load test with throughput/latency numbers.
  - Fuzzing or automated penetration testing tools.
  - Anything requiring a second real Neo4j instance (this hits the same dev
    DB as everything else - it registers a few disposable test users but
    doesn't touch existing data).

Unlike smoke-test.ps1, this script does NOT stop on the first failure - it
runs every check and prints a summary at the end, so one broken endpoint
doesn't hide problems in the others.

.EXAMPLE
.\scripts\security-test.ps1
#>

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$movieUrl = "http://localhost:8091"
$userUrl = "http://localhost:8092"
$ratingUrl = "http://localhost:8093"

$script:passCount = 0
$script:failCount = 0

function Write-Step($msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Get-StatusCode($errorRecord) {
    # Works on both Windows PowerShell 5.1 (System.Net.HttpWebResponse) and
    # PowerShell 7+ (System.Net.Http.HttpResponseMessage) - both expose .StatusCode.
    if ($errorRecord.Exception.Response) {
        return [int]$errorRecord.Exception.Response.StatusCode
    }
    return $null
}

function Assert-Status {
    param(
        [string]$Description,
        [string]$Method,
        [string]$Uri,
        [object]$Body = $null,
        [hashtable]$Headers = $null,
        [int]$ExpectedStatus
    )

    $params = @{ Uri = $Uri; Method = $Method; ErrorAction = "Stop" }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json)
        $params.ContentType = "application/json"
    }
    if ($Headers) { $params.Headers = $Headers }

    $actual = $null
    try {
        Invoke-RestMethod @params | Out-Null
        $actual = 200
    }
    catch {
        $actual = Get-StatusCode $_
    }

    if ($actual -eq $ExpectedStatus) {
        Write-Host "  [OK] $Description (got $actual)" -ForegroundColor Green
        $script:passCount++
    }
    else {
        Write-Host "  [FAIL] $Description (expected $ExpectedStatus, got $actual)" -ForegroundColor Red
        $script:failCount++
    }
}

# --- Setup: one throwaway user for the checks below ---
Write-Step "Setup"
$suffix = Get-Random
$username = "sectest_$suffix"
$password = "Motdepasse123"
$registerBody = @{ username = $username; email = "$username@test.fr"; password = $password } | ConvertTo-Json
Invoke-RestMethod -Uri "$userUrl/api/auth/register" -Method Post -Body $registerBody -ContentType "application/json" | Out-Null
$login = Invoke-RestMethod -Uri "$userUrl/api/auth/login" -Method Post -Body (@{ username = $username; password = $password } | ConvertTo-Json) -ContentType "application/json"
$userToken = @{ Authorization = "Bearer $($login.auth.token)" }
Write-Host "  Test user ready ($username, no admin role)" -ForegroundColor DarkGray

# --- Registration input validation ---
Write-Step "Registration: malformed input"
Assert-Status -Description "Blank username rejected" -Method Post -Uri "$userUrl/api/auth/register" `
    -Body @{ username = ""; email = "x@test.fr"; password = $password } -ExpectedStatus 400

Assert-Status -Description "Malformed email rejected" -Method Post -Uri "$userUrl/api/auth/register" `
    -Body @{ username = "sectest_$(Get-Random)"; email = "not-an-email"; password = $password } -ExpectedStatus 400

Assert-Status -Description "Weak password rejected" -Method Post -Uri "$userUrl/api/auth/register" `
    -Body @{ username = "sectest_$(Get-Random)"; email = "y@test.fr"; password = "weak" } -ExpectedStatus 400

Assert-Status -Description "Duplicate username rejected" -Method Post -Uri "$userUrl/api/auth/register" `
    -Body @{ username = $username; email = "z@test.fr"; password = $password } -ExpectedStatus 409

# --- Authentication ---
Write-Step "Authentication: wrong credentials don't leak account existence"
Assert-Status -Description "Wrong password on existing account -> 401" -Method Post -Uri "$userUrl/api/auth/login" `
    -Body @{ username = $username; password = "WrongPassword123" } -ExpectedStatus 401

Assert-Status -Description "Nonexistent username -> 401 (same status as wrong password)" -Method Post -Uri "$userUrl/api/auth/login" `
    -Body @{ username = "definitely_does_not_exist_$(Get-Random)"; password = "WrongPassword123" } -ExpectedStatus 401

# --- Authorization ---
Write-Step "Authorization: missing/garbage/insufficient auth"
Assert-Status -Description "Watchlist without a token -> 401" -Method Get -Uri "$userUrl/api/users/me/watchlist" -ExpectedStatus 401

Assert-Status -Description "Watchlist with a garbage token -> 401 (not 500)" -Method Get -Uri "$userUrl/api/users/me/watchlist" `
    -Headers @{ Authorization = "Bearer this.is.not.a.jwt" } -ExpectedStatus 401

Assert-Status -Description "Non-admin creating a movie -> 403" -Method Post -Uri "$movieUrl/api/movies" `
    -Body @{ title = "Should Not Exist" } -Headers $userToken -ExpectedStatus 403

Assert-Status -Description "Creating a movie with no token at all -> 401" -Method Post -Uri "$movieUrl/api/movies" `
    -Body @{ title = "Should Not Exist Either" } -ExpectedStatus 401

# --- Business input validation ---
Write-Step "Business input validation"

# Promote our test user to admin now (movie writes need it) - deliberately after the
# 403 check above, which needed this same account to still be a plain user.
& (Join-Path $scriptDir "promote-admin.ps1") -Username $username | Out-Null
$adminLogin = Invoke-RestMethod -Uri "$userUrl/api/auth/login" -Method Post -Body (@{ username = $username; password = $password } | ConvertTo-Json) -ContentType "application/json"
$adminToken = @{ Authorization = "Bearer $($adminLogin.auth.token)" }

Assert-Status -Description "Blank movie title rejected (as admin)" -Method Post -Uri "$movieUrl/api/movies" `
    -Body @{ title = "" } -Headers $adminToken -ExpectedStatus 400

Assert-Status -Description "Get a nonexistent movie -> 404" -Method Get -Uri "$movieUrl/api/movies/00000000-0000-0000-0000-000000000000" -ExpectedStatus 404

Assert-Status -Description "Rate a nonexistent movie -> 404" -Method Put -Uri "$ratingUrl/api/movies/00000000-0000-0000-0000-000000000000/rating" `
    -Body @{ score = 4.0 } -Headers $userToken -ExpectedStatus 404

# Create one real movie to test out-of-range scoring against.
$movie = Invoke-RestMethod -Uri "$movieUrl/api/movies" -Method Post -Body (@{ title = "Security Test Movie"; genres = @("Test") } | ConvertTo-Json) -ContentType "application/json" -Headers $adminToken
$movieId = $movie.movieId

Assert-Status -Description "Rating score above 5.0 rejected" -Method Put -Uri "$ratingUrl/api/movies/$movieId/rating" `
    -Body @{ score = 9.9 } -Headers $userToken -ExpectedStatus 400

Assert-Status -Description "Rating score below 0.5 rejected" -Method Put -Uri "$ratingUrl/api/movies/$movieId/rating" `
    -Body @{ score = 0.1 } -Headers $userToken -ExpectedStatus 400

# --- Injection-style input handled as literal data, not executable Cypher ---
Write-Step "Injection-style search input"
$injectionQuery = "' MATCH (n) DETACH DELETE n //"
Assert-Status -Description "Cypher-injection-looking search string doesn't error out" -Method Get `
    -Uri "$movieUrl/api/movies/search?q=$([uri]::EscapeDataString($injectionQuery))" -ExpectedStatus 200

# If parameter binding ever regressed into string concatenation, this would have
# deleted every node in the database - confirm the movie we just created is still there.
$stillThere = Invoke-RestMethod -Uri "$movieUrl/api/movies/$movieId"
if ($stillThere.movieId -eq $movieId) {
    Write-Host "  [OK] Database untouched by the injection attempt (movie still exists)" -ForegroundColor Green
    $script:passCount++
}
else {
    Write-Host "  [FAIL] Movie disappeared after an injection-style search - parameter binding may be broken" -ForegroundColor Red
    $script:failCount++
}

# --- XSS-style input stored as literal text (frontend's job to escape on render) ---
Write-Step "XSS-style input in a comment"
$xssComment = "<script>alert('xss')</script>"
Invoke-RestMethod -Uri "$ratingUrl/api/movies/$movieId/rating" -Method Put -Body (@{ score = 3.0; comment = $xssComment } | ConvertTo-Json) -ContentType "application/json" -Headers $userToken | Out-Null
$ownRating = Invoke-RestMethod -Uri "$ratingUrl/api/movies/$movieId/rating" -Headers $userToken
if ($ownRating.comment -eq $xssComment) {
    Write-Host "  [OK] Comment stored as literal text, not executed/stripped server-side" -ForegroundColor Green
    Write-Host "       (Angular's {{ }} interpolation escapes this on render - see docs/10-frontend.md)" -ForegroundColor DarkGray
    $script:passCount++
}
else {
    Write-Host "  [FAIL] Comment was altered unexpectedly: '$($ownRating.comment)'" -ForegroundColor Red
    $script:failCount++
}

# --- Light concurrency check (NOT a real load test - see header) ---
Write-Step "Light concurrency check (20 parallel reads)"
try {
    $results = 1..20 | ForEach-Object -Parallel {
        try {
            Invoke-RestMethod -Uri "$using:movieUrl/api/movies" -ErrorAction Stop | Out-Null
            "ok"
        }
        catch { "fail" }
    } -ThrottleLimit 20
    $okCount = ($results | Where-Object { $_ -eq "ok" }).Count
    if ($okCount -eq 20) {
        Write-Host "  [OK] 20/20 concurrent GETs to /api/movies succeeded" -ForegroundColor Green
        $script:passCount++
    }
    else {
        Write-Host "  [FAIL] Only $okCount/20 concurrent requests succeeded" -ForegroundColor Red
        $script:failCount++
    }
}
catch {
    Write-Host "  [SKIP] ForEach-Object -Parallel not available on this PowerShell version (needs 7+) - skipping" -ForegroundColor Yellow
}

# --- Cleanup ---
Write-Step "Cleanup"
Invoke-RestMethod -Uri "$ratingUrl/api/movies/$movieId/rating" -Method Delete -Headers $userToken | Out-Null
Invoke-RestMethod -Uri "$movieUrl/api/movies/$movieId" -Method Delete -Headers $adminToken | Out-Null
Write-Host "  Test movie and rating removed" -ForegroundColor DarkGray

Write-Host ""
Write-Host "$script:passCount passed, $script:failCount failed" -ForegroundColor $(if ($script:failCount -eq 0) { "Green" } else { "Red" })
if ($script:failCount -gt 0) {
    exit 1
}
