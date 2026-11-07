param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$output = Join-Path $workspace 'outputs\choir-android'
$latest = Join-Path $output 'The-Choir-1.11.apk'
$previous = Join-Path $output 'The-Choir-1.10.apk'
$release = Get-Content -LiteralPath (Join-Path $output 'version-1.11-release.json') -Raw | ConvertFrom-Json
if (!(Test-Path -LiteralPath $previous) -or !(Test-Path -LiteralPath $latest)) { throw 'Keep both latest APKs before cleanup.' }
if ((Get-FileHash -LiteralPath $latest -Algorithm SHA256).Hash.ToLowerInvariant() -ne $release.sha256) { throw 'Published APK checksum mismatch.' }
$files = @(Get-ChildItem -LiteralPath $output -File | Where-Object {
    $_.Name -match '^The-Choir-1\.[0-9]\.apk$' -or $_.Name -match '^version-1\.[0-9]-'
})
foreach ($name in @('firestore-debug.log','firebase-debug.log','ui-debug.log','database-debug.log')) {
    $candidate = Join-Path $workspace "firebase\$name"
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { $files += Get-Item -LiteralPath $candidate }
}
$folders = @('android\app\build','android\build','android\.gradle','android\.kotlin','outputs\choir-android\screenshots') |
    ForEach-Object { Join-Path $workspace $_ } | Where-Object { Test-Path -LiteralPath $_ -PathType Container }
$targets = @($files.FullName) + @($folders)
foreach ($target in $targets) {
    $resolved = [IO.Path]::GetFullPath($target)
    if (!$resolved.StartsWith($workspace + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw "Outside workspace: $resolved" }
    if ((Get-Item -LiteralPath $resolved).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing linked target: $resolved" }
    if (Test-Path -LiteralPath $resolved -PathType Container) {
        if (Get-ChildItem -LiteralPath $resolved -Force -Recurse -Attributes ReparsePoint | Select-Object -First 1) { throw "Linked content in $resolved" }
    }
}
$bytes = ($files | Measure-Object -Property Length -Sum).Sum
foreach ($folder in $folders) { $bytes += (Get-ChildItem -LiteralPath $folder -File -Recurse -Force | Measure-Object -Property Length -Sum).Sum }
$report = [ordered]@{applied=[bool]$Apply;reclaimedBytes=$bytes;targets=$targets;retainedApks=@($latest,$previous)}
if ($Apply) {
    foreach ($file in $files) { Remove-Item -LiteralPath $file.FullName -Force }
    foreach ($folder in $folders) { Remove-Item -LiteralPath $folder -Recurse -Force }
    $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $output 'version-1.11-local-cleanup.json') -Encoding utf8
}
$report | ConvertTo-Json -Depth 6

