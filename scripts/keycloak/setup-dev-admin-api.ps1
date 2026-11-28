# Development set-up: connect TransitHub to Keycloak for staff management (#4) and the
# authenticator requirement for Company Administrators (SS-6).
#
# Creates (once) a confidential client "transithub-admin-api" in the staff realm, with a
# service account that may only manage this realm's users, and stores its secret in your
# Windows user environment as TRANSITHUB_IDP_ADMIN_CLIENT_SECRET.
#
# Run from the repository root, with Keycloak running:
#   powershell -ExecutionPolicy Bypass -File scripts\keycloak\setup-dev-admin-api.ps1
# Then restart the backend in a NEW window so it sees the secret.

param(
    [string]$KeycloakHome = 'C:\Users\dell\tools\keycloak-26.8.2',
    [string]$Server = 'http://localhost:8180',
    [string]$Realm = 'staff',
    [string]$AdminUser = 'admin',
    [string]$ClientId = 'transithub-admin-api'
)

$ErrorActionPreference = 'Stop'
$kcadm = Join-Path $KeycloakHome 'bin\kcadm.bat'
if (-not (Test-Path $kcadm)) { throw "kcadm.bat not found under $KeycloakHome. Pass -KeycloakHome <folder>." }

function Invoke-Kc([string]$Arguments) {
    $out = & cmd /c "`"$kcadm`" $Arguments 2>&1"
    if ($LASTEXITCODE -ne 0) { throw "kcadm $Arguments failed: $($out -join ' ')" }
    return ($out -join "`n")
}

# 1. Sign in to the admin API (asks for the password; it is not stored) -----------
& $kcadm config credentials --server $Server --realm master --user $AdminUser
if ($LASTEXITCODE -ne 0) { throw 'Could not sign in to Keycloak as administrator.' }

# 2. The client --------------------------------------------------------------------
$found = Invoke-Kc "get clients -r $Realm -q clientId=$ClientId --fields id" | ConvertFrom-Json
if (@($found).Count -eq 0) {
    Write-Host "Creating client '$ClientId'"
    Invoke-Kc "create clients -r $Realm -s clientId=$ClientId -s enabled=true -s publicClient=false -s serviceAccountsEnabled=true -s standardFlowEnabled=false -s directAccessGrantsEnabled=false -s implicitFlowEnabled=false -s clientAuthenticatorType=client-secret" | Out-Null
    $found = Invoke-Kc "get clients -r $Realm -q clientId=$ClientId --fields id" | ConvertFrom-Json
} else {
    Write-Host "Client '$ClientId' already exists"
}
$id = @($found)[0].id

# 3. Its service account may manage this realm's users, nothing else ------------------
Write-Host 'Giving the service account: manage-users, view-users, query-users'
Invoke-Kc "add-roles -r $Realm --uusername service-account-$ClientId --cclientid realm-management --rolename manage-users --rolename view-users --rolename query-users" | Out-Null

# 4. Its secret, into your user environment --------------------------------------------
$secret = (Invoke-Kc "get clients/$id/client-secret -r $Realm" | ConvertFrom-Json).value
if (-not $secret) { throw 'Could not read the client secret.' }
[Environment]::SetEnvironmentVariable('TRANSITHUB_IDP_ADMIN_CLIENT_SECRET', $secret, 'User')

Write-Host ''
Write-Host 'Done. TRANSITHUB_IDP_ADMIN_CLIENT_SECRET is set for your Windows user.' -ForegroundColor Green
Write-Host 'Restart the backend in a NEW Command Prompt window so it picks the secret up.'
Write-Host 'Set-up emails for new staff arrive in Mailpit: http://localhost:8025'
