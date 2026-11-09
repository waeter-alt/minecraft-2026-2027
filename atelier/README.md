# Atelier — structures, arènes, donjons et mod Grappin

Copie (2026-11-09) du dossier de travail de Luca (« Atelier », hors dépôt) : tout ce qui sert à reproduire les structures du serveur Kodistan.

| Dossier | Contenu |
|---|---|
| `scripts/` | Scripts Python qui génèrent les structures (arènes, hub à 7 portes, donjons) et les vérifient. Dans l'Atelier ce dossier s'appelait `sources/` ; renommé ici car `.gitignore` ignore tout dossier `sources/`. Certains scripts et fiches citent encore l'ancien nom. |
| `exports/` | Structures `.nbt`, plans et captures PNG, listes de points `*_cibles.json`, jars Grappin 1.0.0 à 2.3.0. |
| `docs/` | Fiches d'arènes et de donjons, salles cachées, `MANIFEST.md` (registre de tout ce qui a été généré, avec ses chemins d'origine dans l'Atelier). |
| `grappin/` | Code source du mod NeoForge 1.21.1 Grappin (sans `build/` ni `.gradle/`). |

Pas copiés : les zips de mondes (`Arene_*_map.zip`, `Pack_ami.zip`), `launcher/`, `outils/`, `sauvegardes/`, `.venv`.

## Versions

L'Atelier tourne en NeoForge 21.1.265 avec Create 6.0.10. Le mod `mod/` de ce dépôt est figé en NeoForge 21.1.216 (Create 6.0.9 en `localRuntime`). Les structures `.nbt` sont du Minecraft 1.21.1 vanilla, mais celles qui contiennent des blocs Create (arène Piège, Abysses) demandent Create. Grappin n'a pas été vérifié contre 21.1.216.
