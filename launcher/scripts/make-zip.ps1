<#
.SYNOPSIS
  Fabrique le zip a distribuer aux joueurs a partir du dossier Prism portable.

.DESCRIPTION
  Copie le dossier Prism dans un dossier temporaire avec robocopy, en EXCLUANT :
    - accounts.json (TES jetons Microsoft : ne jamais les distribuer !) ;
    - logs, caches, captures, mondes solo, rapports de crash ;
    - par defaut (mode leger) : assets, librairies, Java telecharge et les mods
      deja installes, plus le manifeste packwiz.json, pour que tout soit
      re-telecharge proprement au premier lancement chez chaque joueur.
  Avec -Full, garde assets/librairies/Java/mods (zip plus gros, premier
  lancement plus rapide).

.EXAMPLE
  .\launcher\scripts\make-zip.ps1 -PrismDir C:\Interregne\PrismLauncher
#>
param(
    [Parameter(Mandatory = $true)][string]$PrismDir,
    [string]$Output = (Join-Path (Split-Path $PSScriptRoot) ("dist\Interregne-{0:yyyy-MM-dd}.zip" -f (Get-Date))),
    [string]$InstanceFolder = 'Interregne',
    [switch]$Full
)
$ErrorActionPreference = 'Stop'
$PrismDir = (Resolve-Path $PrismDir).Path
$stage = Join-Path ([IO.Path]::GetTempPath()) ("interregne-zip-" + [guid]::NewGuid().ToString('N'))
$root = Join-Path $stage 'Interregne'

$xd = @('logs', 'cache', 'crash-reports', 'screenshots', 'saves', 'downloads', '.cache', 'debug')
$xf = @('accounts.json', 'accounts.json.bak', 'metacache', '*.log')
if (-not $Full) {
    $xd += @('assets', 'libraries', 'java', 'meta', 'mods', 'shaderpacks', '.fabric', 'Distant_Horizons_server_data')
    $xf += @('packwiz.json', 'packwiz-installer.jar')
}

robocopy $PrismDir $root /E /NFL /NDL /NJH /NJS /NP /XD $xd /XF $xf | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy a echoue (code $LASTEXITCODE)" }
$global:LASTEXITCODE = 0   # robocopy renvoie 1..7 en cas de succes

# garde-fous
if (Get-ChildItem $root -Recurse -Filter 'accounts.json' -ErrorAction SilentlyContinue) { throw "accounts.json present dans le zip !" }
$cfg = Join-Path $root "instances\$InstanceFolder\instance.cfg"
if (-not (Test-Path $cfg)) { throw "Instance $InstanceFolder absente : lancer prepare-instance.ps1 d'abord" }
$cfgText = Get-Content -Raw $cfg
if ($cfgText -match 'PACK_URL|SERVER_ADDRESS') { throw "instance.cfg contient encore un marqueur PACK_URL / SERVER_ADDRESS" }
if (-not (Test-Path (Join-Path $root "instances\$InstanceFolder\minecraft\packwiz-installer-bootstrap.jar"))) {
    throw "packwiz-installer-bootstrap.jar absent de l'instance"
}

New-Item -ItemType Directory -Force (Split-Path $Output) | Out-Null
if (Test-Path $Output) { Remove-Item $Output -Force }
Compress-Archive -Path $root -DestinationPath $Output -CompressionLevel Optimal
Remove-Item $stage -Recurse -Force
$size = [math]::Round((Get-Item $Output).Length / 1MB, 1)
Write-Host "Zip pret : $Output ($size Mo)"
