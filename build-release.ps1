$ErrorActionPreference = "Stop"

$signingDirectory = Join-Path ([Environment]::GetFolderPath("MyDocuments")) "NondaSigning"
$jdkDirectory = Join-Path ([Environment]::GetFolderPath("MyDocuments")) "NondaBuildTools\\jdk17"
$keystorePath = Join-Path $signingDirectory "nonda-release.jks"
$credentialPath = Join-Path $signingDirectory "nonda-signing-credential.xml"

if (!(Test-Path -LiteralPath $keystorePath) -or !(Test-Path -LiteralPath $credentialPath)) {
    throw "Signing files were not found. Restore the Documents\\NondaSigning backup."
}

$signingCredential = Import-Clixml -LiteralPath $credentialPath
$signingPassword = $signingCredential.GetNetworkCredential().Password
$javaHome = Get-ChildItem -LiteralPath $jdkDirectory -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "bin\\java.exe") } |
    Select-Object -First 1 -ExpandProperty FullName
if (!$javaHome) { throw "Java 17 was not found in Documents\\NondaBuildTools\\jdk17." }

$env:NONDA_KEYSTORE_PATH = $keystorePath
$env:NONDA_KEYSTORE_PASSWORD = $signingPassword
$env:NONDA_KEY_ALIAS = $signingCredential.UserName
$env:NONDA_KEY_PASSWORD = $signingPassword
$env:JAVA_HOME = $javaHome

try {
    & .\gradlew.bat :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "The release APK build failed." }
} finally {
    Remove-Item Env:NONDA_KEYSTORE_PATH -ErrorAction SilentlyContinue
    Remove-Item Env:NONDA_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:NONDA_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:NONDA_KEY_PASSWORD -ErrorAction SilentlyContinue
}

Write-Output "Release APK: app\\build\\outputs\\apk\\release\\app-release.apk"
