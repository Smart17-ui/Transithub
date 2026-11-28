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
# Sign in to the admin API. The password is asked here (paste with right-click works;
# stars show as you type) and handed to kcadm only for this sign-in; it is not stored.
$secure = Read-Host -AsSecureString "Keycloak admin password (user '$AdminUser')"
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
& $kcadm config credentials --server $Server --realm master --user $AdminUser --password $plain
$plain = $null
if ($LASTEXITCODE -ne 0) { throw 'Could not sign in to Keycloak as administrator. Check the password in transithub-dev-logins.txt (line "Keycloak admin console").' }

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

# 3b. Keycloak 26.8+: admin tokens must carry the "kc-admin-api" scope, which adds the
#     Admin API audience; without it every admin call is refused with 401.
$scopes = Invoke-Kc "get client-scopes -r $Realm --fields id,name" | ConvertFrom-Json
$adminScope = @($scopes) | Where-Object { $_.name -eq 'kc-admin-api' } | Select-Object -First 1
if (-not $adminScope) {
    Write-Host "Creating client scope 'kc-admin-api' (Admin API audience)"
    $json = Join-Path $env:TEMP 'transithub-kc-admin-scope.json'
    @'
{
  "name": "kc-admin-api",
  "protocol": "openid-connect",
  "description": "Admin REST API audience (needed by Keycloak 26.8+ for admin calls)",
  "attributes": { "include.in.token.scope": "true", "display.on.consent.screen": "false" },
  "protocolMappers": [ {
    "name": "admin api audience",
    "protocol": "openid-connect",
    "protocolMapper": "oidc-admin-api-audience-mapper",
    "config": { "access.token.claim": "true", "introspection.token.claim": "true" }
  } ]
}
'@ | Set-Content -Encoding ascii $json
    Invoke-Kc "create client-scopes -r $Realm -f `"$json`"" | Out-Null
    Remove-Item $json -ErrorAction SilentlyContinue
    $adminScope = @(Invoke-Kc "get client-scopes -r $Realm --fields id,name" | ConvertFrom-Json) | Where-Object { $_.name -eq 'kc-admin-api' } | Select-Object -First 1
}
Write-Host "Attaching 'kc-admin-api' to '$ClientId'"
& cmd /c "`"$kcadm`" update clients/$id/default-client-scopes/$($adminScope.id) -r $Realm 2>nul" | Out-Null

# Not needed (an earlier experiment): keep the client on plain client-credentials tokens.
$plainJson = Join-Path $env:TEMP 'transithub-kc-client.json'
'{"attributes":{"client_credentials.use_refresh_token":"false"}}' | Set-Content -Encoding ascii $plainJson
& cmd /c "`"$kcadm`" update clients/$id -r $Realm -f `"$plainJson`" 2>nul" | Out-Null
Remove-Item $plainJson -ErrorAction SilentlyContinue

# 4. Its secret, into your user environment --------------------------------------------
$secret = (Invoke-Kc "get clients/$id/client-secret -r $Realm" | ConvertFrom-Json).value
if (-not $secret) { throw 'Could not read the client secret.' }
[Environment]::SetEnvironmentVariable('TRANSITHUB_IDP_ADMIN_CLIENT_SECRET', $secret, 'User')

Write-Host ''
Write-Host 'Done. TRANSITHUB_IDP_ADMIN_CLIENT_SECRET is set for your Windows user.' -ForegroundColor Green
Write-Host 'Restart the backend in a NEW Command Prompt window so it picks the secret up.'
Write-Host 'Set-up emails for new staff arrive in Mailpit: http://localhost:8025'
