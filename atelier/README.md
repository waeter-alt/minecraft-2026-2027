# Atelier — structures, arènes, donjons et mod Grappin

Copie (2026-11-09) du dossier de travail de Luca (« Atelier », hors dépôt) : tout ce qui sert à reproduire les structures du serveur Kodistan.

| Dossier | Contenu |
|---|---|
| `scripts/` | Scripts Python qui génèrent les structures (arènes, hub à 7 portes, donjons) et les vérifient. Dans l'Atelier ce dossier s'appelait `sources/` ; renommé ici car `.gitignore` ignore tout dossier `sources/`. Certains scripts et fiches citent encore l'ancien nom. |
| `exports/` | Structures `.nbt`, plans et captures PNG, listes de points `*_cibles.json`, jar Grappin à jour (compilé pour NeoForge 21.1.216). |
| `docs/` | Fiches d'arènes et de donjons, salles cachées, `MANIFEST.md` (registre de tout ce qui a été généré, avec ses chemins d'origine dans l'Atelier). |
| `grappin/` | Code source du mod NeoForge 1.21.1 Grappin (sans `build/` ni `.gradle/`). |

Pas copiés : les zips de mondes (`Arene_*_map.zip`, `Pack_ami.zip`), `launcher/`, `outils/`, `sauvegardes/`, `.venv`.

## Versions

Tout ce dossier cible la version de NeoForge figée dans `mod/gradle.properties` (**21.1.216**), comme l'exige le `CLAUDE.md`. Grappin se compile pour cette version (`atelier/grappin/gradle.properties`, `neo_version=21.1.216`) ; les anciens jars compilés pour une autre version ont été retirés. Create 6.0.9 (celui de `mod/libs/`) suffit pour les structures qui contiennent des blocs Create (arène Piège, Abysses) ; Create 6.0.10 exige NeoForge 21.1.219 ou plus et n'est donc pas compatible.

Grappin a un onglet créatif dédié, « Donjons » (grappin, point d'ancrage et **Voile des Profondeurs**, le casque de respiration sous l'eau du donjon aquatique ; la place des futurs objets de donjon). Textures dessinées par `scripts/icone_voile.py`.
