param([string]$Repository = 'tovypytho/AppLexsa')
$ErrorActionPreference = 'Stop'
$lexsaRoot = Split-Path -Parent $PSScriptRoot
$lexsaDir = Join-Path $lexsaRoot '.signing'
$lexsaKey = Join-Path $lexsaDir 'lexsa-test.jks'
$lexsaRecovery = Join-Path $lexsaDir 'recovery.json'
New-Item -ItemType Directory -Force -Path $lexsaDir | Out-Null
if ((Test-Path -LiteralPath $lexsaKey) -xor (Test-Path -LiteralPath $lexsaRecovery)) {
    throw 'Incomplete signing backup. Restore the missing key or recovery file; do not overwrite the existing key.'
}
if (!(Test-Path -LiteralPath $lexsaRecovery)) {
    $lexsaBytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($lexsaBytes)
    $lexsaCredentials = @{ password = [Convert]::ToBase64String($lexsaBytes); alias = 'lexsa-test' }
    $lexsaCredentials | ConvertTo-Json | Set-Content -LiteralPath $lexsaRecovery -Encoding utf8
    $lexsaOldPassword = $env:LEXSA_TEMP_KEY_PASSWORD
    try {
        $env:LEXSA_TEMP_KEY_PASSWORD = $lexsaCredentials.password
        & keytool -genkeypair -keystore $lexsaKey -storetype JKS -alias $lexsaCredentials.alias `
            -storepass:env LEXSA_TEMP_KEY_PASSWORD -keypass:env LEXSA_TEMP_KEY_PASSWORD `
            -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=AppLexsa TEST ONLY' -noprompt
        if ($LASTEXITCODE -ne 0) { throw 'keytool failed; inspect the local signing backup before retrying.' }
    } finally { $env:LEXSA_TEMP_KEY_PASSWORD = $lexsaOldPassword }
} else { $lexsaCredentials = Get-Content -Raw -LiteralPath $lexsaRecovery | ConvertFrom-Json }
$lexsaSecrets = @{
    LEXSA_TEST_KEYSTORE_BASE64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($lexsaKey))
    LEXSA_TEST_STORE_PASSWORD = $lexsaCredentials.password
    LEXSA_TEST_KEY_PASSWORD = $lexsaCredentials.password
    LEXSA_TEST_KEY_ALIAS = $lexsaCredentials.alias
}
foreach ($lexsaName in $lexsaSecrets.Keys) {
    $lexsaSecrets[$lexsaName] | & gh secret set $lexsaName --repo $Repository
    if ($LASTEXITCODE -ne 0) { throw "Could not set $lexsaName" }
}
Write-Output 'Stable test signing configured. Keep .signing/ private and backed up; never use this key for production.'
