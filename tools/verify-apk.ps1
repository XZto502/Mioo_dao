param(
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][ValidateSet('release','preview','beta')][string]$Channel,
    [Parameter(Mandatory)][int]$ExpectedVersionCode,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ExpectedSignerSha256,
    [Parameter(Mandatory)][string]$BuildTools
)
$ErrorActionPreference = 'Stop'
$file = (Resolve-Path -LiteralPath $Apk).Path
$badging = & (Join-Path $BuildTools 'aapt.exe') dump badging $file
if ($LASTEXITCODE -ne 0) { throw 'Cannot read APK manifest' }
$packageLine = $badging | Select-String '^package:' | Select-Object -First 1
if ("$packageLine" -notmatch "^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'") {
    throw 'Missing package metadata'
}
$package, $code, $version = $Matches[1], [int]$Matches[2], $Matches[3]
$expectedPackage = if ($Channel -eq 'beta') { 'com.mioo.dao.beta' } else { 'com.mioo.dao' }
if ($package -ne $expectedPackage -or $code -ne $ExpectedVersionCode) { throw "Unexpected package/versionCode: $package $code" }
if ($Channel -eq 'release') {
    if ($version -notmatch '^\d+\.\d+\.\d+$' -or $code % 100 -ne 99) { throw 'Unexpected release version' }
} elseif ($version -notmatch '-beta\.(\d+)$' -or [int]$Matches[1] -notin (1..98) -or $code % 100 -ne [int]$Matches[1]) {
    throw 'Unexpected test channel version'
}
$certs = & (Join-Path $BuildTools 'apksigner.bat') verify --print-certs $file
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
$digests = @($certs | Select-String '^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$')
if ($digests.Count -ne 1 -or $digests[0].Matches[0].Groups[1].Value -ne $ExpectedSignerSha256) {
    throw 'Unexpected APK signer'
}
[pscustomobject]@{ Channel=$Channel; Package=$package; Version=$version; VersionCode=$code; FileSha256=(Get-FileHash -LiteralPath $file).Hash }
