# One-time setup: merge Windows ROOT certificates into a Java truststore for Gradle.
# Required when HTTPS works in the browser/PowerShell but Gradle fails with PKIX / SSL handshake errors.

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$destDir = Join-Path $projectRoot ".gradle"
$dest = Join-Path $destDir "windows-cacerts"

$jbrCandidates = @(
    "$env:JAVA_HOME",
    "C:\Program Files\Android\Android Studio\jbr",
    "C:\Program Files\JetBrains\Android Studio\jbr"
) | Where-Object { $_ -and (Test-Path $_) }

$jbr = $jbrCandidates | Select-Object -First 1
if (-not $jbr) {
    Write-Error "Could not find a JDK/JBR. Set JAVA_HOME or install Android Studio."
}

$keytool = Join-Path $jbr "bin\keytool.exe"
$srcCacerts = Join-Path $jbr "lib\security\cacerts"

if (-not (Test-Path $keytool)) { Write-Error "keytool not found at $keytool" }
if (-not (Test-Path $srcCacerts)) { Write-Error "cacerts not found at $srcCacerts" }

New-Item -ItemType Directory -Force -Path $destDir | Out-Null
Copy-Item -Force $srcCacerts $dest

$stores = @("Cert:\LocalMachine\Root", "Cert:\CurrentUser\Root")
$imported = 0

foreach ($store in $stores) {
    $i = 0
    Get-ChildItem $store -ErrorAction SilentlyContinue | ForEach-Object {
        $cer = Join-Path $env:TEMP ("winroot-" + ($store -replace '[^A-Za-z]', '') + "-$i.cer")
        try {
            Export-Certificate -Cert $_ -FilePath $cer -ErrorAction Stop | Out-Null
            $alias = "win-" + ($store -replace '[^A-Za-z]', '') + "-$i"
            & $keytool -importcert -noprompt -alias $alias -file $cer -keystore $dest -storepass changeit 2>$null
            if ($LASTEXITCODE -eq 0) { $imported++ }
        } finally {
            Remove-Item $cer -ErrorAction SilentlyContinue
        }
        $i++
    }
}

Write-Host "Created $dest (imported $imported Windows root certificates)."
Write-Host "Retry: .\gradlew.bat :app:assembleAmazonDebug"
