<#
.SYNOPSIS
  Installe l'instance "L'Interregne" dans un dossier Prism Launcher portable.

.DESCRIPTION
  - copie launcher/prism-instance/ vers <PrismDir>/instances/<InstanceFolder>/ ;
  - remplace les marqueurs PACK_URL et SERVER_ADDRESS dans instance.cfg ;
  - copie packwiz-installer-bootstrap.jar dans <instance>/minecraft/ ;
  - genere minecraft/servers.dat (le serveur apparait dans la liste Multijoueur,
    packs de ressources serveur acceptes d'office) ;
  - cree <PrismDir>/Jouer.bat (lance directement l'instance).

  N'ecrase PAS options.txt s'il existe deja dans l'instance (sauf -Force).

.EXAMPLE
  .\launcher\scripts\prepare-instance.ps1 -PrismDir C:\Interregne\PrismLauncher `
      -PackUrl https://moncompte.github.io/interregne-pack/pack.toml `
      -ServerAddress play.exemple.fr:25565 `
      -BootstrapJar $HOME\Downloads\packwiz-installer-bootstrap.jar
#>
param(
    [Parameter(Mandatory = $true)][string]$PrismDir,
    [Parameter(Mandatory = $true)][string]$PackUrl,
    [Parameter(Mandatory = $true)][string]$ServerAddress,
    [Parameter(Mandatory = $true)][string]$BootstrapJar,
    [string]$InstanceFolder = 'Interregne',
    [switch]$Force
)
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding $false
$template = Join-Path (Split-Path $PSScriptRoot) 'prism-instance'

if (-not (Test-Path (Join-Path $PrismDir 'prismlauncher.exe'))) {
    throw "prismlauncher.exe introuvable dans $PrismDir (dezipper d'abord Prism Launcher portable)"
}
if (-not (Test-Path (Join-Path $PrismDir 'portable.txt'))) {
    Write-Warning "portable.txt absent : ce Prism n'est peut-etre pas la version portable."
}
if ($PackUrl -notmatch '^https://.+/pack\.toml$') { throw "PackUrl doit finir par /pack.toml" }
if (-not (Test-Path -LiteralPath $BootstrapJar)) { throw "Bootstrap introuvable : $BootstrapJar" }

$dest = Join-Path $PrismDir "instances\$InstanceFolder"
$mc = Join-Path $dest 'minecraft'
New-Item -ItemType Directory -Force $mc | Out-Null

# instance.cfg et mmc-pack.json : toujours re-appliques depuis le modele
$cfg = [IO.File]::ReadAllText((Join-Path $template 'instance.cfg'), $utf8)
$cfg = $cfg.Replace('PACK_URL', $PackUrl).Replace('SERVER_ADDRESS', $ServerAddress)
[IO.File]::WriteAllText((Join-Path $dest 'instance.cfg'), $cfg, $utf8)
Copy-Item (Join-Path $template 'mmc-pack.json') $dest -Force

$opt = Join-Path $mc 'options.txt'
if ($Force -or -not (Test-Path $opt)) { Copy-Item (Join-Path $template 'minecraft\options.txt') $opt -Force }
Copy-Item -LiteralPath $BootstrapJar (Join-Path $mc 'packwiz-installer-bootstrap.jar') -Force

# --- servers.dat : NBT non compresse, big-endian -------------------------------
function Write-NbtString([IO.BinaryWriter]$w, [string]$s) {
    $b = [Text.Encoding]::UTF8.GetBytes($s)   # = "modified UTF-8" pour ces caracteres
    $w.Write([byte](($b.Length -shr 8) -band 0xFF)); $w.Write([byte]($b.Length -band 0xFF)); $w.Write($b)
}
function Write-NbtInt([IO.BinaryWriter]$w, [int]$i) {
    foreach ($sh in 24, 16, 8, 0) { $w.Write([byte](($i -shr $sh) -band 0xFF)) }
}
$serverName = "L'Interr$([char]0x00E8)gne"
$ms = New-Object IO.MemoryStream
$w = New-Object IO.BinaryWriter $ms
$w.Write([byte]10); Write-NbtString $w ''                                   # racine (compound)
$w.Write([byte]9);  Write-NbtString $w 'servers'; $w.Write([byte]10); Write-NbtInt $w 1  # liste de 1 compound
$w.Write([byte]8);  Write-NbtString $w 'ip';   Write-NbtString $w $ServerAddress
$w.Write([byte]8);  Write-NbtString $w 'name'; Write-NbtString $w $serverName
$w.Write([byte]1);  Write-NbtString $w 'acceptTextures'; $w.Write([byte]1)
$w.Write([byte]0)                                                          # fin du serveur
$w.Write([byte]0)                                                          # fin de la racine
$w.Flush()
[IO.File]::WriteAllBytes((Join-Path $mc 'servers.dat'), $ms.ToArray())

# --- Jouer.bat -----------------------------------------------------------------
$bat = "@echo off`r`nstart `"`" `"%~dp0prismlauncher.exe`" --launch $InstanceFolder`r`n"
[IO.File]::WriteAllText((Join-Path $PrismDir 'Jouer.bat'), $bat, (New-Object System.Text.ASCIIEncoding))

Write-Host "Instance prete : $dest"
Write-Host "Serveur : $ServerAddress | Pack : $PackUrl"
