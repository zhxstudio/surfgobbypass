# zhxstudio — keep this key for all future releases.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$keyPath = Join-Path $PSScriptRoot 'signing/surfgobbypass.jks'
$propertiesPath = Join-Path $PSScriptRoot 'signing/keystore.properties'
if ((Test-Path $keyPath) -and (Test-Path $propertiesPath)) { exit 0 }
if ((Test-Path $keyPath) -or (Test-Path $propertiesPath)) {
    throw 'Signing files are incomplete. Restore the matching key and keystore.properties from your backup.'
}
New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot 'signing') | Out-Null
$keytool = 'keytool.exe'
if ($env:JAVA_HOME) { $keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe' }
$password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$env:ZHX_SIGNING_PASSWORD = $password
try {
    & $keytool -genkeypair -noprompt -keystore $keyPath -storetype JKS -alias zhxstudio -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=zhxstudio, OU=surfgobbypass, O=zhxstudio' -storepass:env ZHX_SIGNING_PASSWORD -keypass:env ZHX_SIGNING_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Release key generation failed.' }
    [IO.File]::WriteAllText($propertiesPath, "keyAlias=zhxstudio`nstorePassword=$password`nkeyPassword=$password`n", (New-Object Text.UTF8Encoding($false)))
} finally {
    Remove-Item Env:ZHX_SIGNING_PASSWORD -ErrorAction SilentlyContinue
}
Write-Host 'Release key created. Back up the signing directory; do not publish it.'
