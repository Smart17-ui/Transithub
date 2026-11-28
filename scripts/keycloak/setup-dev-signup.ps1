# Development set-up for self-service company sign-up (Release 1 §9, SS-1 and SS-5).
#
# What it does to the local Keycloak "staff" realm:
#   1. installs the TransitHub theme (sign-in pages and emails);
#   2. turns on "Create an account", email confirmation and "Forgot password";
#   3. sends Keycloak's emails to Mailpit on this computer (nothing leaves the machine).
#
# Run it from the repository root, with Keycloak running:
#   powershell -ExecutionPolicy Bypass -File scripts\keycloak\setup-dev-signup.ps1
# It asks for the Keycloak admin password; the password is not stored by this script.
# Safe to run again.

param(
    [string]$KeycloakHome = 'C:\Users\dell\tools\keycloak-26.8.2',
    [string]$Server = 'http://localhost:8180',
    [string]$Realm = 'staff',
    [string]$AdminUser = 'admin'
)

$ErrorActionPreference = 'Stop'
$repo = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$kcadm = Join-Path $KeycloakHome 'bin\kcadm.bat'
if (-not (Test-Path $kcadm)) { throw "kcadm.bat not found under $KeycloakHome. Pass -KeycloakHome <folder>." }

# 1. Theme --------------------------------------------------------------------
$source = Join-Path $repo 'keycloak-theme\transithub'
$target = Join-Path $KeycloakHome 'themes\transithub'
$isNew = -not (Test-Path $target)
Write-Host "Installing the TransitHub theme into $target"
if (Test-Path $target) { Remove-Item -Recurse -Force $target }
Copy-Item -Recurse $source $target

if ($isNew) {
    Write-Host ''
    Write-Host 'Keycloak only discovers a NEW theme when it starts.' -ForegroundColor Yellow
    Write-Host 'Stop Keycloak (Ctrl + C in its window), start it again, wait for "Running the server", then press Enter here.' -ForegroundColor Yellow
    [void](Read-Host)
}

# 2. Sign in to the admin API (asks for the password) --------------------------
& $kcadm config credentials --server $Server --realm master --user $AdminUser
if ($LASTEXITCODE -ne 0) { throw 'Could not sign in to Keycloak as administrator.' }

# 3. Realm settings ----------------------------------------------------------
Write-Host "Updating realm '$Realm'"
& $kcadm update "realms/$Realm" `
    -s displayName=TransitHub `
    -s registrationAllowed=true `
    -s registrationEmailAsUsername=true `
    -s loginWithEmailAllowed=true `
    -s duplicateEmailsAllowed=false `
    -s verifyEmail=true `
    -s resetPasswordAllowed=true `
    -s rememberMe=false `
    -s loginTheme=transithub `
    -s emailTheme=transithub `
    -s 'smtpServer.host=localhost' `
    -s 'smtpServer.port=1025' `
    -s 'smtpServer.from=no-reply@transithub.local' `
    -s 'smtpServer.fromDisplayName=TransitHub'
if ($LASTEXITCODE -ne 0) { throw 'Updating the realm failed.' }

# 3b. Sign-up must ask for a password ------------------------------------------
# The "Create an account" form only shows password fields when the realm's registration
# flow contains a REQUIRED "Password Validation" step; without it a new applicant
# could never sign in again.
function Invoke-KcadmJson([string[]]$KcArgs) {
    $out = & cmd /c "`"$kcadm`" $($KcArgs -join ' ') 2>nul"
    if ($LASTEXITCODE -ne 0 -or -not $out) { return $null }
    return ($out -join "`n") | ConvertFrom-Json
}
$realmInfo = Invoke-KcadmJson @('get', "realms/$Realm", '--fields', 'registrationFlow')
$flowAlias = if ($realmInfo -and $realmInfo.registrationFlow) { $realmInfo.registrationFlow } else { 'registration' }
$flowPath = [uri]::EscapeDataString($flowAlias)
$executions = Invoke-KcadmJson @('get', "authentication/flows/$flowPath/executions", '-r', $Realm)
if (-not $executions) {
    Write-Host "Could not read the sign-up flow '$flowAlias'. Check it by hand: Authentication > $flowAlias > 'Password Validation' must be Required." -ForegroundColor Yellow
} else {
    $passwordStep = @($executions) | Where-Object { $_.providerId -eq 'registration-password-action' } | Select-Object -First 1
    if (-not $passwordStep) {
        $form = @($executions) | Where-Object { $_.authenticationFlow -and $_.providerId -eq 'registration-page-form' } | Select-Object -First 1
        $formAlias = if ($form) { $form.displayName } else { 'registration form' }
        Write-Host "Adding the 'Password Validation' step to '$formAlias'"
        & cmd /c "`"$kcadm`" create authentication/flows/$([uri]::EscapeDataString($formAlias))/executions/execution -r $Realm -s provider=registration-password-action 2>nul" | Out-Null
        $executions = Invoke-KcadmJson @('get', "authentication/flows/$flowPath/executions", '-r', $Realm)
        $passwordStep = @($executions) | Where-Object { $_.providerId -eq 'registration-password-action' } | Select-Object -First 1
    }
    if ($passwordStep -and $passwordStep.requirement -ne 'REQUIRED') {
        Write-Host "Making 'Password Validation' required"
        $body = Join-Path $env:TEMP 'transithub-kc-execution.json'
        @{ id = $passwordStep.id; requirement = 'REQUIRED' } | ConvertTo-Json | Set-Content -Encoding ascii $body
        & cmd /c "`"$kcadm`" update authentication/flows/$flowPath/executions -r $Realm -f `"$body`" 2>nul" | Out-Null
        Remove-Item $body -ErrorAction SilentlyContinue
    }
    if ($passwordStep) { Write-Host 'Sign-up asks for a password: OK' -ForegroundColor Green }
    else { Write-Host "Could not add 'Password Validation'. Add it by hand: Authentication > $flowAlias > registration form > Add step > Password Validation (Required)." -ForegroundColor Yellow }
}

# 4. Second-factor check (SS-6: not while applying) ----------------------------
# Applicants must not be forced to set up an authenticator app at sign-up. This only reports.
# Only a report: it must never stop the script, whatever kcadm prints.
$ErrorActionPreference = 'Continue'
$totp = ''
try { $totp = (& cmd /c "`"$kcadm`" get authentication/required-actions/CONFIGURE_TOTP -r $Realm 2>nul") -join "`n" } catch { }
if (-not $totp) {
    Write-Host 'Could not read the "Configure OTP" setting; check it by hand (Authentication > Required actions).' -ForegroundColor Yellow
} elseif ($totp -match '"defaultAction"\s*:\s*true') {
    Write-Host ''
    Write-Host 'Note: "Configure OTP" is a DEFAULT action in this realm, so every new account (applicants too)' -ForegroundColor Yellow
    Write-Host 'is asked to set up an authenticator app. Decision SS-6 says applicants should not be.' -ForegroundColor Yellow
    Write-Host 'Admin console > Authentication > Required actions > Configure OTP > "Set as default action": off.' -ForegroundColor Yellow
    Write-Host 'Staff keep it: it is set on each staff user individually.' -ForegroundColor Yellow
}

Write-Host ''
Write-Host 'Done.' -ForegroundColor Green
Write-Host '  - Start Mailpit (mailpit.exe) to receive the emails; inbox: http://localhost:8025'
Write-Host '  - Try it: http://localhost:5173 > Sign in > "Create an account"'
