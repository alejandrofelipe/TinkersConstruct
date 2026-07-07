# Hot-reloads recently recompiled classes into the running clientHotswap JVM (jdwp on 127.0.0.1:5005).
# Usage: run AFTER `gradlew compileJava` while the client (launched via `gradlew runClientHotswap`) is open:
#   powershell -File hotswap.ps1 [-SinceMinutes 10]
# Finds .class files changed in the last N minutes under both class roots (tinkers + mantle) and issues
# a jdb `redefine` for each — the same JDI mechanism IDE hotswap uses; JBR's DCEVM (enabled via
# -XX:+AllowEnhancedClassRedefinition on the run) allows structural changes, not just method bodies.
# Limits: newly ADDED classes and re-run of initializers/registrations still need a client restart.
param(
  [int]$SinceMinutes = 10,
  [string]$JdbHost = "127.0.0.1",
  [int]$JdbPort = 5005
)

$roots = @(
  "C:\Users\aleja\DEV\New Tinkers\tinkers\build\classes\java\main",
  "C:\Users\aleja\DEV\New Tinkers\repo\build\classes\java\main"
)
$cutoff = (Get-Date).AddMinutes(-$SinceMinutes)

# jdb's `redefine` splits its file argument on whitespace, so paths under "New Tinkers" break —
# stage the changed classes into a space-free temp dir first
$stage = Join-Path $env:LOCALAPPDATA "tinkers-hotswap-stage"
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Force $stage | Out-Null

$commands = New-Object System.Collections.Generic.List[string]
$i = 0
foreach ($root in $roots) {
  if (-not (Test-Path $root)) { continue }
  Get-ChildItem -Path $root -Recurse -Filter *.class | Where-Object { $_.LastWriteTime -gt $cutoff } | ForEach-Object {
    $rel = $_.FullName.Substring($root.Length + 1)
    $className = $rel -replace '\\', '.' -replace '\.class$', ''
    $staged = Join-Path $stage "$i.class"
    Copy-Item $_.FullName $staged
    $i++
    # jdb resolves the class by name across all classloaders (mod classes are unique to the transforming loader)
    $commands.Add("redefine $className $staged")
  }
}

if ($commands.Count -eq 0) {
  Write-Host "hotswap: no .class files changed in the last $SinceMinutes minute(s) - nothing to reload."
  exit 0
}

Write-Host "hotswap: redefining $($commands.Count) class(es) via jdb at ${JdbHost}:${JdbPort}"
$commands | ForEach-Object { Write-Host "  $_" }
$script = ($commands -join "`n") + "`nexit`n"

# jdb ships with any JDK; use the pinned Temurin (the attach side does not need the JBR).
# NOTE: on Windows `jdb -attach host:port` means SHARED MEMORY - a socket needs the explicit connector.
# ASCII pipe encoding: PowerShell 5's default UTF-8 BOM corrupts the first piped command ('?redefine').
$OutputEncoding = [System.Text.ASCIIEncoding]::new()
$jdb = "C:\Users\aleja\scoop\apps\temurin21-jdk\current\bin\jdb.exe"
$output = $script | & $jdb -connect "com.sun.jdi.SocketAttach:hostname=${JdbHost},port=${JdbPort}" 2>&1
$output | ForEach-Object { Write-Host $_ }

# "No class named X" = the class was never loaded in the client (e.g. datagen-only) — nothing to
# swap, an expected skip. Anything else error-ish is a real failure.
$skips = @($output | Select-String -Pattern "No class named")
$errors = @($output | Select-String -Pattern "Unable to|Error|exception|failed" -CaseSensitive:$false |
  Where-Object { $_ -notmatch "Initializing" -and $_ -notmatch "No class named" })
if ($skips.Count -gt 0) { Write-Host "hotswap: skipped $($skips.Count) never-loaded class(es)." }
if ($errors.Count -gt 0) {
  Write-Host "hotswap: FINISHED WITH ERRORS (see above) - a restart may be needed for these changes."
  exit 1
}
Write-Host "hotswap: done - $($commands.Count - $skips.Count) class(es) redefined, $($skips.Count) skipped."
exit 0
