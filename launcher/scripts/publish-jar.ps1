<#
.SYNOPSIS
  Publie un jar auto-heberge (par defaut notre mod `interregne`) dans le pack.

.DESCRIPTION
  1. Copie le jar dans launcher/pack/files/ (servi tel quel par GitHub Pages,
     mais ignore par l'index grace a .packwizignore).
  2. Supprime les anciennes versions de ce jar dans files/.
  3. Reecrit launcher/pack/mods/<Slug>.pw.toml : filename, URL et sha512.
  4. Rafraichit l'index (packwiz refresh si present, sinon refresh-index.ps1).

  Sert aussi de solution de repli pour un mod CurseForge dont la
  distribution tierce serait bloquee (voir MISSING_MODS.md).

.EXAMPLE
  # Apres ./gradlew build dans mod/ :
  .\launcher\scripts\publish-jar.ps1 -PackUrl https://moncompte.github.io/interregne-pack/pack.toml

.EXAMPLE
  # Repli MineColonies auto-heberge :
  .\launcher\scripts\publish-jar.ps1 -PackUrl https://.../pack.toml -Slug minecolonies `
      -Name MineColonies -Jar "C:\chemin\minecolonies-1.1.1041-1.21.1.jar"
#>
param(
    [Parameter(Mandatory = $true)][string]$PackUrl,
    [string]$Jar,
    [string]$Slug = 'interregne',
    [string]$Name = "L'Interregne (mod maison)",
    [ValidateSet('both', 'client', 'server')][string]$Side = 'both'
)
$ErrorActionPreference = 'Stop'
$launcher = Split-Path $PSScriptRoot
$repo = Split-Path $launcher
$pack = Join-Path $launcher 'pack'
$files = Join-Path $pack 'files'
$utf8 = New-Object System.Text.UTF8Encoding $false

if ($PackUrl -notmatch '^https?://.+/pack\.toml$') {
    throw "PackUrl doit etre l'URL complete de pack.toml (ex: https://compte.github.io/depot/pack.toml)"
}
if (-not $Jar) {
    if ($Slug -ne 'interregne') { throw "-Jar est obligatoire pour un autre mod que interregne" }
    $Jar = Get-ChildItem (Join-Path $repo 'mod\build\libs') -Filter 'interregne-*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|dev|slim)\.jar$' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
    if (-not $Jar) { throw "Aucun jar dans mod\build\libs : lancer d'abord .\gradlew build dans mod\" }
}
$jarItem = Get-Item -LiteralPath $Jar
$fileName = $jarItem.Name

New-Item -ItemType Directory -Force $files | Out-Null
$prefix = ($fileName -replace '[-_]?\d.*$', '')
Get-ChildItem $files -Filter "$prefix*.jar" | Where-Object { $_.Name -ne $fileName } | Remove-Item -Force
Copy-Item -LiteralPath $jarItem.FullName -Destination (Join-Path $files $fileName) -Force

$sha512 = (Get-FileHash -Algorithm SHA512 -LiteralPath (Join-Path $files $fileName)).Hash.ToLowerInvariant()
$base = $PackUrl -replace '/pack\.toml$', ''
$url = "$base/files/" + [Uri]::EscapeDataString($fileName)

# "Interregne" avec accent grave, ecrit via son code pour garder ce script en ASCII
$displayName = $Name.Replace("Interregne", "Interr$([char]0x00E8)gne")
$toml = "name = `"$($displayName.Replace('"', '\"'))`"`nfilename = `"$fileName`"`nside = `"$Side`"`npin = true`n`n" +
        "[download]`nurl = `"$url`"`nhash-format = `"sha512`"`nhash = `"$sha512`"`n"
[IO.File]::WriteAllText((Join-Path $pack "mods\$Slug.pw.toml"), $toml, $utf8)
Write-Host "mods\$Slug.pw.toml -> $url"

if (Get-Command packwiz -ErrorAction SilentlyContinue) {
    Push-Location $pack; try { packwiz refresh } finally { Pop-Location }
} else {
    & (Join-Path $PSScriptRoot 'refresh-index.ps1') -PackDir $pack
}
Write-Host "OK. Pense a commiter et pousser launcher/pack (le jar dans files/ compris)."
