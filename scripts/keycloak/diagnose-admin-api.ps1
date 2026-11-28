# Read-only check of TransitHub's Keycloak service account (staff management, #4).
# Prints what decides whether Keycloak accepts the account's tokens. Changes nothing.
#   powershell -ExecutionPolicy Bypass -File scripts\keycloak\diagnose-admin-api.ps1

param(
    [string]$KeycloakHome = 'C:\Users\dell\tools\keycloak-26.8.2',
    [string]$Server = 'http://localhost:8180',
    [string]$Realm = 'staff',
    [string]$AdminUser = 'admin',
    [string]$ClientId = 'transithub-admin-api'
)

$ErrorActionPreference = 'Stop'
$kcadm = Join-Path $KeycloakHome 'bin\kcadm.bat'

$secure = Read-Host -AsSecureString "Keycloak admin password (user '$AdminUser')"
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
& $kcadm config credentials --server $Server --realm master --user $AdminUser --password $plain
$plain = $null
if ($LASTEXITCODE -ne 0) { throw 'Could not sign in to Keycloak as administrator.' }

function Get-Kc([string]$Arguments) {
    $out = & cmd /c "`"$kcadm`" $Arguments 2>&1"
    return ($out -join "`n")
}

Write-Host "`n== Realm ==" -ForegroundColor Cyan
Get-Kc "get realms/$Realm --fields realm,enabled,notBefore,verifyEmail,registrationEmailAsUsername"

Write-Host "`n== Client $ClientId ==" -ForegroundColor Cyan
$client = Get-Kc "get clients -r $Realm -q clientId=$ClientId --fields id,enabled,publicClient,serviceAccountsEnabled,attributes"
$client
$id = (@($client | ConvertFrom-Json))[0].id

Write-Host "`n== Service account user ==" -ForegroundColor Cyan
Get-Kc "get clients/$id/service-account-user -r $Realm --fields id,username,enabled,emailVerified,requiredActions,notBefore"

Write-Host "`nDone. Copy everything above (it contains no passwords or secrets) and send it." -ForegroundColor Green
