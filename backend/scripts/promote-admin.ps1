<#
.SYNOPSIS
Grants ROLE_ADMIN to an existing user, directly in Neo4j via cypher-shell.

Registration (POST /api/auth/register) never accepts a role from the client -
letting anyone self-register as admin would defeat the point of having an admin
role at all. Since there's no admin-management API (out of scope for this
project), promoting an account is a manual dev/ops action instead.

.PARAMETER Username
The username of an already-registered account (register via the app first).

.EXAMPLE
.\scripts\promote-admin.ps1 -Username daro
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$Username
)

$ErrorActionPreference = "Stop"

$query = "MATCH (u:User {username: '$Username'}) SET u.roles = ['ROLE_ADMIN', 'ROLE_USER'] RETURN u.username AS username, u.roles AS roles;"

docker exec -i neo4flix_db cypher-shell -u neo4j -p neo4flix_dev $query

Write-Host ""
Write-Host "$Username now has ROLE_ADMIN. Log in again (or re-register a new token) to get a JWT that includes it - an already-issued token still has the old roles baked in." -ForegroundColor Yellow
