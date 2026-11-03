<#
.SYNOPSIS
  Recalcule index.toml et le hash de pack.toml SANS le binaire packwiz.

.DESCRIPTION
  Equivalent minimal de `packwiz refresh` (a utiliser en depannage, ou si
  packwiz n'est pas installe). Indexe tous les fichiers de launcher/pack
  sauf pack.toml, index.toml et les chemins listes dans .packwizignore
  (motifs simples : "dossier/" ou nom de fichier exact).
  Les *.pw.toml sont marques metafile = true. Hash : sha256 des octets.
  Si packwiz est installe, preferer `packwiz refresh` (meme resultat).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File launcher\scripts\refresh-index.ps1
#>
param(
    [string]$PackDir = (Join-Path (Split-Path $PSScriptRoot) 'pack')
)
$ErrorActionPreference = 'Stop'
$PackDir = (Resolve-Path $PackDir).Path
$utf8 = New-Object System.Text.UTF8Encoding $false

$ignore = @('pack.toml', 'index.toml')
$ignoreFile = Join-Path $PackDir '.packwizignore'
if (Test-Path $ignoreFile) {
    foreach ($l in [IO.File]::ReadAllLines($ignoreFile, $utf8)) {
        $t = $l.Trim()
        if ($t -and -not $t.StartsWith('#')) { $ignore += $t }
    }
}

function Test-Ignored([string]$rel) {
    foreach ($p in $ignore) {
        if ($p.EndsWith('/')) { if ($rel.StartsWith($p)) { return $true } }
        elseif ($rel -eq $p) { return $true }
    }
    return $false
}

$entries = @()
Get-ChildItem -Path $PackDir -Recurse -File -Force | ForEach-Object {
    $rel = $_.FullName.Substring($PackDir.Length + 1).Replace('\', '/')
    if (-not (Test-Ignored $rel)) { $entries += $rel }
}
$entries = [string[]]$entries
[Array]::Sort($entries, [StringComparer]::Ordinal)

$sb = New-Object System.Text.StringBuilder
[void]$sb.Append("hash-format = `"sha256`"`n")
foreach ($rel in $entries) {
    $h = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $PackDir $rel)).Hash.ToLowerInvariant()
    [void]$sb.Append("`n[[files]]`nfile = `"$rel`"`nhash = `"$h`"`n")
    if ($rel.EndsWith('.pw.toml')) { [void]$sb.Append("metafile = true`n") }
}
$indexPath = Join-Path $PackDir 'index.toml'
[IO.File]::WriteAllText($indexPath, $sb.ToString(), $utf8)
$ih = (Get-FileHash -Algorithm SHA256 -LiteralPath $indexPath).Hash.ToLowerInvariant()

$packPath = Join-Path $PackDir 'pack.toml'
$pack = [IO.File]::ReadAllText($packPath, $utf8).Replace("`r`n", "`n")
$pack = [regex]::Replace($pack, '(?m)^(\[index\][^\[]*?hash = ")[0-9a-f]*(")', "`${1}$ih`${2}")
[IO.File]::WriteAllText($packPath, $pack, $utf8)

Write-Host ("index.toml : {0} fichiers indexes, hash {1}" -f $entries.Count, $ih)
