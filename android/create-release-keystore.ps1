param(
    [string]$Alias = "accessible-youtube",
    [string]$KeystoreName = "accessible-youtube-release.jks"
)

$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$KeystorePath = Join-Path $ProjectDir $KeystoreName
$PropertiesPath = Join-Path $ProjectDir "keystore.properties"

if (-not $env:JAVA_HOME) {
    $androidStudioJbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $androidStudioJbr) {
        $env:JAVA_HOME = $androidStudioJbr
    }
}

$Keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
if (-not (Test-Path $Keytool)) {
    throw "Could not find keytool.exe. Set JAVA_HOME to a JDK path first."
}

if (Test-Path $KeystorePath) {
    throw "Keystore already exists: $KeystorePath"
}

$StorePassword = Read-Host "Keystore password"
$KeyPassword = Read-Host "Key password. You may reuse the same password"

& $Keytool `
    -genkeypair `
    -v `
    -keystore $KeystorePath `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -storepass $StorePassword `
    -keypass $KeyPassword `
    -dname "CN=Accessible YouTube Player, OU=Personal, O=Meowloid, L=Local, ST=Local, C=MY"

@"
storeFile=$KeystoreName
storePassword=$StorePassword
keyAlias=$Alias
keyPassword=$KeyPassword
"@ | Set-Content -Path $PropertiesPath -Encoding ASCII

Write-Host "Created $KeystorePath"
Write-Host "Created $PropertiesPath"
Write-Host "Both files are ignored by Git. Keep them safe; losing the keystore means future release updates cannot use the same signature."
