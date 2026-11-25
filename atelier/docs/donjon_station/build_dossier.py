"""Génère « Donjon de la Station - dossier de conception.pdf » (HTML -> PDF via Chrome headless).

Régénérer, depuis la racine du dépôt :
    python atelier/docs/donjon_station/build_dossier.py
Le PDF est écrit à côté de ce script. Aucune dépendance Python (Chrome ou Edge doit être installé ; sinon définir CHROME_PATH).
Les plans sont dessinés dans figs.py (plans de principe, pas extraits d'un gabarit).
"""
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import figs as F  # noqa: E402

HERE = Path(__file__).parent
OUT_PDF = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "Donjon de la Station - dossier de conception.pdf"
OUT_HTML = HERE / "_dossier.html"
def _chrome():
    """Chrome ou Edge (même moteur) : variable CHROME_PATH, sinon le PATH, sinon les emplacements habituels."""
    import os
    import shutil
    candidats = [os.environ.get("CHROME_PATH")]
    candidats += [shutil.which(n) for n in ("chrome", "google-chrome", "chromium", "msedge")]
    for base in (os.environ.get("ProgramFiles"), os.environ.get("ProgramFiles(x86)"), os.environ.get("LOCALAPPDATA")):
        if base:
            candidats += [str(Path(base) / "Google/Chrome/Application/chrome.exe"),
                          str(Path(base) / "Microsoft/Edge/Application/msedge.exe")]
    for c in candidats:
        if c and Path(c).is_file():
            return c
    sys.exit("Chrome ou Edge introuvable : définir CHROME_PATH vers l'exécutable.")


CHROME = _chrome()

D = '<span class="tag d">Décidé</span>'
P = '<span class="tag p">Proposé</span>'
M = '<span class="tag m">À mesurer</span>'
O = '<span class="tag o">Ouvert</span>'


def fig(n, svg, caption):
    return f'<figure><div class="fb">{svg}</div><figcaption><b>Figure {n}.</b> {caption}</figcaption></figure>'


def sym(inner):
    return F.sym_svg(inner)


CSS = """
@page { size: A4; margin: 14mm 13mm 15mm; }
* { box-sizing: border-box; }
body { font-family: 'Segoe UI', 'Noto Sans', Arial, sans-serif; font-size: 9.4pt; line-height: 1.42; color: #1d2330; margin: 0; }
h1 { font-size: 22pt; margin: 0 0 3pt; color: #1f2a52; letter-spacing: -.3px; }
h2 { font-size: 13.5pt; margin: 17pt 0 6pt; color: #1f2a52; border-bottom: 2.2pt solid #3d3a8c; padding-bottom: 2pt; break-after: avoid; }
h3 { font-size: 10.8pt; margin: 11pt 0 3pt; color: #2d3a73; break-after: avoid; }
p { margin: 0 0 5pt; }
.sub { font-size: 8.4pt; color: #5b6273; margin-bottom: 9pt; }
code { font-family: Consolas, 'Cascadia Mono', monospace; font-size: 8.4pt; background: #eef0f6; padding: 0 2pt; border-radius: 2pt; }
.call { background: #eceefb; border-left: 3pt solid #4a47b0; padding: 6pt 9pt; margin: 7pt 0; border-radius: 0 3pt 3pt 0; break-inside: avoid; }
.warn { background: #fff4e0; border-left: 3pt solid #e08a1e; padding: 6pt 9pt; margin: 7pt 0; border-radius: 0 3pt 3pt 0; break-inside: avoid; }
ul { margin: 2pt 0 6pt; padding-left: 14pt; }
li { margin-bottom: 2.4pt; }
table { border-collapse: collapse; width: 100%; margin: 5pt 0 9pt; font-size: 8.6pt; }
th { background: #e5e8f4; text-align: left; padding: 3.2pt 5pt; border: .6pt solid #c4c9dd; }
td { padding: 3pt 5pt; border: .6pt solid #d3d7e6; vertical-align: top; }
tr { break-inside: avoid; }
tr:first-child { break-after: avoid; }
td.c { text-align: center; white-space: nowrap; }
figure { margin: 7pt 0 9pt; break-inside: avoid; }
.fb { border: .6pt solid #1d2330; border-radius: 3pt; overflow: hidden; }
.fb svg { display: block; }
figcaption { font-size: 8.2pt; color: #4b5263; margin-top: 3pt; }
.tag { display: inline-block; font-size: 6.8pt; font-weight: 700; padding: 0 4pt; border-radius: 6pt; vertical-align: 1pt; margin-right: 2pt; letter-spacing: .2px; }
.tag.d { background: #d6f0dc; color: #14602b; }
.tag.p { background: #dbe6fb; color: #1d4a9a; }
.tag.m { background: #ffe6c4; color: #8a4a00; }
.tag.o { background: #f6d6dc; color: #8a1a2e; }
.pb { break-before: page; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 10pt; }
ol.sc { padding-left: 0; list-style: none; counter-reset: s; }
ol.sc li { counter-increment: s; position: relative; padding-left: 20pt; margin-bottom: 5pt; }
ol.sc li::before { content: counter(s); position: absolute; left: 0; top: 0; width: 14pt; height: 14pt; border-radius: 50%; background: #3d3a8c; color: #fff; font-size: 8pt; font-weight: 700; text-align: center; line-height: 14pt; }
.legend td { vertical-align: middle; }
.legend td.s { width: 30pt; text-align: center; padding: 1pt; }
"""

# ----------------------------------------------------------------------------- contenu
parts = []
a = parts.append

a(f"""
<h1>Donjon de la Station — dossier de conception</h1>
<div class="sub">Le donjon 2 du projet Kodistan (titre de travail). Dossier généré le 2026-11-17 à partir du brainstorm sur la
Cape lestée (mémoire du projet, PR #53), du dossier de travail du donjon 1 (Donjon Suspendu, PR #49), des réglages de la
cape dans le mod et du boss Léviathan (PR #22). Régénérer : <code>python atelier/docs/donjon_station/build_dossier.py</code>.</div>

<div class="call"><b>À qui s'adresse ce document.</b> À Duncan et Luca pour valider la direction, puis à un agent (Opus) qui
construira le donjon salle par salle. Contrairement au dossier du donjon 1, <b>rien n'est construit ici</b> : c'est la conception
complète (game design et level design) qui sort du brainstorm. Chaque choix porte donc une étiquette :
{D} décidé par l'utilisateur ; {P} proposé dans ce dossier, à valider ; {M} chiffre à régler en jeu ;
{O} question à trancher. Les plans sont des <b>plans de principe</b> (cotes indicatives, dessinés à la main), pas des extraits
d'un gabarit. Ils reprennent les codes couleurs de Luca (§ 2).</div>

<h2>1. En bref</h2>
<ul>
<li><b>Principe</b> : le donjon est une <b>machine à remettre en marche</b>. Une station de purification de l'eau, à l'abandon,
sèche au départ ; le joueur en est le technicien. Il la remet sous eau, restaure trois pompes, puis la vidange l'emporte vers le
boss. Structure radicalement différente du donjon 1 {D} : ni hall à portes colorées, ni descente de salle en salle ; une
<b>boucle autour d'une cuve dont le niveau d'eau change</b>.</li>
<li><b>Objet du donjon</b> : la <b>Cape lestée</b> {D} (déjà faite, PR #53). Elle donne au joueur trois verbes dans l'eau
— <b>nager vite</b>, <b>se lester</b> (marcher au fond), <b>tourbillon</b> (attaque et bond) — et ces trois verbes sont la grammaire
du donjon {P}. Le grappin du donjon 1 y sert aussi {D}, à l'air libre.</li>
<li><b>Ambiance</b> {D} : station de purification (tuyaux, engrenages, chaudières, steampunk) croisée avec un <b>espace liminal</b>,
le « niveau piscine » des Backrooms : carrelage blanc, halls de bassins vides, lumière froide, bourdonnement. Le mot d'ordre de la
cape reste « le joueur doit se sentir comme un poisson dans l'eau », sans aucune image de poisson dans les noms, textes ou rendus.</li>
<li><b>Fil rouge</b> {P} : pas de clés. Le niveau d'eau de la Grande Cuve (Haut, Médian, Bas) est l'état central : chaque pompe
restaurée l'abaisse d'un cran, et chaque cran change <i>comment</i> on atteint les secteurs (nage, bond, grappin). Trois secteurs
indépendants (un par verbe), une épreuve de synthèse (la Cheminée), puis le boss, le Léviathan, dans le Réservoir.</li>
<li><b>Contournement</b> {D} : un joueur qui revient déjà équipé de la cape (farm de butin) doit pouvoir sauter l'épreuve qui la donne,
grâce à la cape elle-même. {P} Forme proposée : le Drain, un long conduit noyé à contre-courant, visible dès l'entrée (§ 4.5). Aucune vérification d'inventaire.</li>
<li><b>État</b> : brainstorm validé dans l'esprit ; ce dossier est la première mise à plat. <b>Rien n'est généré, rien n'a été
essayé en jeu.</b> Seule la cape est faite et validée en jeu ; le boss Léviathan existe (PR #22) mais pour l'ancien design noyé à
cloches d'air, à adapter (§ 8).</li>
</ul>

<h3>Ce qui change par rapport au donjon 1</h3>
<table>
<tr><th style="width:17%"></th><th style="width:41%">Donjon 1 — Donjon Suspendu</th><th>Donjon 2 — la Station</th></tr>
<tr><td><b>Forme</b></td><td>Un bâtiment en surface, trois sous-sols qu'on descend l'un après l'autre.</td><td>Une boucle de secteurs autour d'une Grande Cuve de 60 de diamètre. On monte, on descend, on revient : la Cuve est le plateau de jeu.</td></tr>
<tr><td><b>Ce qui verrouille</b></td><td>Trois portes colorées, chacune ouverte par une clé trouvée plus bas.</td><td>Le niveau d'eau et les écluses. Aucune clé : on va où l'on sait aller.</td></tr>
<tr><td><b>Ordre</b></td><td>Linéaire : bleu, jaune, rouge.</td><td>Trois secteurs dans l'ordre qu'on veut. L'ordre change la difficulté d'accès, jamais la faisabilité.</td></tr>
<tr><td><b>Outil</b></td><td>Le grappin, gagné au sous-sol 1.</td><td>La cape, gagnée dans la première partie ; le grappin revient en complément.</td></tr>
<tr><td><b>Verbes</b></td><td>Viser un ancrage, enchaîner des tirs au-dessus du vide.</td><td>Nager vite, se lester, tourbillon, bond, plus le grappin.</td></tr>
<tr><td><b>Rythme</b></td><td>Surface, puis de plus en plus profond.</td><td>Sec, noyé, mixte, sec, noyé : la sensation change quatre fois.</td></tr>
<tr><td><b>Boss</b></td><td>Gardien des Racines, golem à points faibles frappé à l'épée.</td><td>Léviathan, serpent de mer de 36 blocs qui teste les trois verbes.</td></tr>
</table>

<h2>2. Légende — les codes couleurs de Luca, et ce qu'on ajoute</h2>
<p>Luca dessine ses plans sur GIMP avec les codes ci-dessous (§ 2 du dossier du donjon 1). Les plans de ce document les reprennent
tels quels ; les lignes « ajout » sont propres à ce dossier.</p>
<table class="legend">
<tr><th style="width:34pt"></th><th style="width:25%">Code</th><th style="width:30%">Sens</th><th>Où on le trouve ici</th></tr>
<tr><td class="s">{sym(F.s_cross(13,13))}</td><td>Croix rouge</td><td>Monstre</td><td>Embuscade du bassin de 50 m ; gardiens (laser) et noyés des secteurs A, B et C ; blazes de la halle haute B</td></tr>
<tr><td class="s">{sym(F.s_mini(13,13))}</td><td>Gros « M »</td><td>Mini-boss</td><td>Secteur C : le « Gardien du rotor » (provisoire, à définir par Luca)</td></tr>
<tr><td class="s">{sym(F.s_anchor(13,13))}</td><td>Rond bleu</td><td>Point d'ancrage permanent du grappin</td><td>Hall (plongeon, toboggan), halle haute B, Cheminée ; paroi de la Cuve devant chaque bouche</td></tr>
<tr><td class="s">{sym(F.s_lever(13,13,1))}</td><td>Carré orange numéroté</td><td>Levier ou volant</td><td>Les 3 volants du Hall (1, 2, 3) ; volants des pompes A, B, C ; levier général du Poste (« ! »)</td></tr>
<tr><td class="s">{sym(F.s_chest(13,13))}</td><td>Rond jaune</td><td>Coffre (vide pour l'instant)</td><td>Un par secteur, un dans le Hall, un dans le Réservoir : butin à définir par Luca</td></tr>
<tr><td class="s">{sym(F.s_key(13,13,1))}</td><td>Rond vert numéroté</td><td>Coffre qui donne un objet clé</td><td>Vert 1 : le casier de la Cape lestée (Vestiaire). N'existe pas au départ : il apparaît en cinématique quand l'épreuve est réussie</td></tr>
<tr><td class="s">{sym('<rect x="3" y="3" width="20" height="20" fill="url(#hatch)" stroke="#f28c28"/>')}</td><td>Sol hachuré</td><td>Vide (chute)</td><td>Les creux des bassins du Hall et les fosses des halles hautes (non mortels : échelle ou eau en bas)</td></tr>
<tr><td class="s">{sym('<rect x="3" y="5" width="20" height="16" rx="3" fill="#3f86b4" stroke="#9fd0ee" stroke-width="1.6"/>')}</td><td>Ajout : bleu</td><td>Eau (zone noyée)</td><td>Cuve, sas, secteurs, Réservoir. Plus c'est foncé, plus c'est profond</td></tr>
<tr><td class="s">{sym('<rect x="3" y="3" width="20" height="20" fill="#cbbd96" stroke="#2a2a2a"/>')}</td><td>Ajout : beige</td><td>Sol sec où l'on marche</td><td>Hall, Vestiaire, Coursive, Poste, halles hautes</td></tr>
<tr><td class="s">{sym(F.s_arrow(3,13,23,13,F.CYAN,3.6))}</td><td>Ajout : flèche cyan</td><td>Courant (sens de poussée) ; la taille donne la force</td><td>Collecteur B, rampe de la Cheminée, courants ascendants du secteur C</td></tr>
<tr><td class="s">{sym(F.s_gate(13,13,16,True))}</td><td>Ajout : barre noire</td><td>Vanne (porte à glissière qui se referme)</td><td>Chaînes chronométrées du secteur A et de la Cheminée</td></tr>
<tr><td class="s">{sym(F.s_plate(13,13,1))}</td><td>Ajout : carré cuivre numéroté</td><td>Plaque lestée (ne réagit qu'à un joueur qui marche au fond)</td><td>Salle des chaudières (secteur B)</td></tr>
<tr><td class="s">{sym(F.s_turbine(13,13,10))}</td><td>Ajout</td><td>Turbine à lancer au tourbillon</td><td>Puits des turbines (secteur C), Cheminée</td></tr>
<tr><td class="s">{sym('<rect x="3" y="7" width="20" height="12" fill="url(#hatchT)" stroke="#d4a84c"/>')}</td><td>Ajout : ocre hachuré</td><td>Tartre : dépôt qui ne se brise qu'au tourbillon</td><td>Grilles des secteurs C et de la Cheminée</td></tr>
<tr><td class="s">{sym(F.s_gear(13,13,10))}</td><td>Ajout</td><td>Pompe à remettre en marche</td><td>Fin de chaque secteur</td></tr>
<tr><td class="s">{sym(F.s_boss(13,13,11))}</td><td>Étoile violette « B »</td><td>Boss</td><td>Le Léviathan, dans le Réservoir</td></tr>
<tr><td class="s">{sym(F.s_arrow(3,13,23,13,'#2e9e4f',4))}</td><td>Flèche verte</td><td>Entrée ou arrivée</td><td>Entrée du donjon</td></tr>
</table>
<p>Non utilisés ici : le rond cyan numéroté (ancrage qui apparaît quand on tire un levier) et le cadre rouge « zone à traiter
à part », que Luca placera à sa guise sur ses propres croquis.</p>

<h2 class="pb">3. Le monde et la technique</h2>
<table>
<tr><td style="width:15%"><b>Monde</b></td><td>« Donjon de la Station » {P} : superflat profond, plus épais que celui du donjon 1 (le gabarit fait 112 de haut, il lui faut une plaine vers y 45). Survie, difficulté normale, <b>monde zip prêt à copier</b> dans <code>mod/run/client/saves/</code> avec pose automatique à la première ouverture, comme le donjon 1 ({D}, règle « jamais d'état local supposé »).</td></tr>
<tr><td><b>Versions</b></td><td>Minecraft 1.21.1, NeoForge 21.1.216 (figé, jamais changé). Mod « grappin » du projet (porte la cape, l'objet du donjon, le grappin et les boss) ; <b>dépendance Accessories</b> pour l'emplacement de cape, à valider par Duncan (PR #53).</td></tr>
<tr><td><b>Gabarit</b></td><td>{P} 240 × 112 × 240 blocs (x, y, z du gabarit ; à poser avec un décalage comme le donjon 1). Structures prévues : <code>station_hall</code>, <code>station_cuve</code>, <code>station_a</code>, <code>station_b</code>, <code>station_c</code>, <code>station_cheminee</code>, <code>station_reservoir</code>.</td></tr>
<tr><td><b>Hauteurs</b></td><td>{P} Plaine 108 · escalier de 24 marches · sol du Hall et Coursive de la Cuve 84 · plafond du Hall 98 · Cuve : sol 30, plafond 96 · niveaux d'eau Haut 76, Médian 58, Bas 42 · Réservoir : sol 0, plafond 24 (noyé). Les secteurs et la Cheminée sont donnés au § 6.</td></tr>
<tr><td><b>Logique</b></td><td>{P} Comme au donjon 1 : <b>fonctions de datapack seulement</b>, aucun bloc de commande. <code>station_tick</code> (combats, plaques, vannes, courants, chutes), <code>station_load</code>, la pose par étapes, <code>station_niveau_haut / _median / _bas</code> (le niveau de la Cuve), <code>station_ecluse_*</code>, <code>station_cine_*</code>, remise à zéro <code>/function atelier:station_reset</code>.</td></tr>
<tr><td><b>Fichiers prévus</b></td><td>{P} <code>donjon_station.py</code> (assemblage, fonctions, monde, <code>--zip</code>), <code>station_hall.py</code>, <code>station_cuve.py</code>, <code>station_secteur_a/b/c.py</code>, <code>station_cheminee.py</code>, <code>station_reservoir.py</code>, <code>station_cine.py</code> ; vérificateurs <code>verif_station_*.py</code> ; tests <code>atelier/tests/test_station_*.py</code>.</td></tr>
</table>

<h3>Ce que le datapack ne sait pas faire seul : besoins côté mod</h3>
<p>Le donjon 1 tenait en datapack. Celui-ci a besoin de quatre ajouts au mod « grappin » {P}, à faire <b>avant</b> de construire les
épreuves qui en dépendent :</p>
<table>
<tr><th style="width:24%">Besoin</th><th>Pourquoi, et forme proposée</th></tr>
<tr><td><b>Zones de courant</b></td><td>Aucun courant horizontal en vanilla. Le mod pousse un nageur dans une boîte donnée (direction, force) ; un joueur <b>lesté</b> (pieds au sol) n'est freiné que de moitié. Réglage par boîte, défini en datapack. Sert au Drain, au collecteur B, à la rampe de la Cheminée, aux courants ascendants de C, au tourbillon de la vidange et à la phase 2 du boss.</td></tr>
<tr><td><b>Signal du tourbillon</b></td><td>Quand un joueur lance son tourbillon, le mod lui pose un tag (quelques ticks). Le datapack en tire : turbines à lancer, tartre à briser, interrupteurs à frapper. Le tourbillon lui-même reste du code du mod.</td></tr>
<tr><td><b>État « lesté » lisible</b></td><td>Un tag ou score tant que le joueur est en mode lesté, pour les plaques lestées. Les vraies plaques de pression ne s'immergent pas : elles sont remplacées par une <b>zone détectée par position</b> (joueur lesté et au sol), marquée visuellement par une dalle de cuivre.</td></tr>
<tr><td><b>Léviathan adapté</b></td><td>Le boss de la PR #22 est conçu pour des cloches d'air : à refaire autour de la cape (§ 8).</td></tr>
</table>
<p>Reste du datapack, sans mod : engrenages et turbines qui tournent (entités d'affichage animées, pas de blocs Create, § 12),
cinématiques, vannes, niveaux d'eau (remplissage par couches, scripté), vapeur et bulles (particules, colonnes de bulles vanilla).</p>

<h2>4. Game design</h2>
<h3>4.1 La progression</h3>
<table>
<tr><th style="width:15%">Étape</th><th style="width:34%">Ce qu'on y fait</th><th style="width:25%">Ce que ça change</th><th>Ce qu'on y apprend</th></tr>
<tr><td><b>I · À sec</b><br>Hall + Vestiaire</td><td>Tourner les 3 volants (grappin, embuscade), ouvrir le casier, prendre la cape.</td><td>Le Sas s'ouvre ; la Cape lestée est acquise.</td><td>Le grappin en tutoriel, l'ambiance liminale, le Drain entrevu.</td></tr>
<tr><td><b>II · Mise en eau</b><br>Grande Cuve</td><td>Franchir le Sas : la station se met en route et la Cuve se remplit (cinématique). Plonger.</td><td>Niveau Haut. Les 3 secteurs s'ouvrent, dans l'ordre qu'on veut.</td><td>La première nage libre, la grammaire de la cape, la Cuve comme plateau.</td></tr>
<tr><td><b>III · Trois secteurs</b><br>A, B, C</td><td>Chaque secteur : écluse, épreuve du verbe, salle de pompe.</td><td>Chaque pompe abaisse la Cuve d'un cran (Haut, Médian, Bas) et allume un témoin.</td><td>Nager vite (A), se lester (B), tourbillon (C).</td></tr>
<tr><td><b>IV · La Cheminée</b><br>et le Poste</td><td>Épreuve de synthèse : les trois verbes plus le bond et le grappin ; levier général du Poste.</td><td>La bonde de la Cuve s'ouvre.</td><td>Combiner. Le plongeoir final.</td></tr>
<tr><td><b>V · Le Réservoir</b></td><td>Plonger dans le tourbillon, se battre contre le Léviathan.</td><td>Sortie par l'Exutoire, retour à la lumière.</td><td>Tout à la fois, dans l'eau.</td></tr>
</table>

<h3>4.2 Les règles qui tiennent tout</h3>
<ul>
<li>{D} <b>Pas de limite d'air, pas de cloches.</b> La cape donne la respiration illimitée : la tension vient des courants, des gardiens, du niveau d'eau et du boss. Les anciennes pistes (compte à rebours d'air, zones de pression) sont retirées.</li>
<li>{P} <b>Les objets clés n'existent pas d'avance</b>, comme au donjon 1 : le casier de la cape apparaît en cinématique quand les trois volants sont tournés. La cape n'est jamais améliorée dans le donjon : elle a tout dès l'obtention.</li>
<li>{P} <b>Tomber n'est jamais mortel</b> hors boss : les creux à sec font 12 blocs au plus avec une échelle ; tomber dans l'eau est sans dégât ; la seule fosse sans eau est la Cuve avant la mise en eau, ceinte de garde-corps.</li>
<li>{P} <b>Écluses</b> : chaque secteur est étanche. On y entre par une écluse (petit sas à deux vannes, un bouton) qui règle l'eau du sas sur celle du secteur ; sans elle, l'eau d'un secteur se viderait dans la Cuve quand celle-ci baisse. L'écluse est aussi un moment de répit entre deux ambiances.</li>
<li>{P} <b>Niveau d'eau global</b> : trois valeurs pour la Cuve, changées seulement par les pompes (scripté, cinématique). Les secteurs ont leur propre eau, fixe.</li>
<li>{P} <b>Lumière 5 partout où l'on marche ou nage</b> ; aucun monstre n'apparaît tout seul ; tous sont invoqués par le donjon, une seule fois (règle du donjon 1, qui vaut aussi pour les noyés, plus sensibles à la pénombre).</li>
<li>{P} <b>Grappin</b> (règles du donjon 1) : portée 30, ancrage frôlé à moins de 1 bloc, on s'arrête à 1,2 bloc et on tombe tout droit. On place l'ancrage 3 à 4 blocs au-dessus de l'arrivée et à 3,5 du bord d'un trou. Les trous font au moins 5 à 6 blocs : un saut en franchit 4. On ne s'accroche qu'aux blocs d'ancrage.</li>
<li>{P} <b>Multijoueur</b> : les événements (mise en eau, pompes, bonde) sont globaux et ne se jouent qu'une fois ; chaque joueur doit avoir sa propre cape. Les chronos des vannes se déclenchent par joueur.</li>
<li>{P} <b>Cinématiques à la Zelda</b> (datapack, comme au donjon 1) : les joueurs proches passent en spectateur et regardent par une caméra qui avance en ligne droite sans jamais tourner. Nouveau : la caméra peut <b>suivre la surface de l'eau qui monte ou descend</b> (mise en eau, pompes).</li>
<li>{O} <b>Mode aventure obligatoire dans le donjon</b> (anti-triche de l'ancien design) : compatible avec ce design, qui n'a aucune épreuve de minage (les tartres sont brisés par le tourbillon, pas à la pioche). À confirmer.</li>
</ul>

<h3>4.3 La cape comme grammaire — chiffres de dimensionnement</h3>
<p>Valeurs lues dans les réglages du mod (PR #53), validées en jeu pour la sensation ; elles fixent toutes les distances du donjon.</p>
<table>
<tr><th style="width:21%">Verbe</th><th style="width:33%">Ce que fait la cape</th><th>Ce que le donjon en tire</th></tr>
<tr><td><b>Nager vite</b></td><td>9,1 blocs/s, 14,3 au sprint ; inertie : environ 1 s pour atteindre sa vitesse. 30 blocs se traversent en 3,3 s (2,1 s au sprint, plus l'élan).</td><td>Chaînes de vannes qui se referment (secteur A, Cheminée), courants favorables, fuite des gardiens et du boss.</td></tr>
<tr><td><b>Se lester</b> (touche V)</td><td>On coule, on marche au fond, saut depuis le fond ≈ un saut terrestre ; on garde le minage normal. Freiné de moitié par les courants {P}.</td><td>Remonter un courant contraire à pied (Drain, collecteur B, rampe de la Cheminée), plaques lestées (B), résister à la vidange (boss).</td></tr>
<tr><td><b>Tourbillon</b> (clic gauche en nage rapide)</td><td>Portée 3,5, 6 de dégâts (3 cœurs), recharge 0,8 s, élan vers l'avant ≈ 16 blocs/s.</td><td>Lancer les turbines, briser le tartre, frapper les interrupteurs, tuer les ennemis nageurs, toucher la perle du boss.</td></tr>
<tr><td><b>Bond hors de l'eau</b></td><td>Tourbillon près de la surface en visant vers le haut : ≈ 8 blocs de haut, ≈ 10 en avant.</td><td>Atteindre une bouche au-dessus de l'eau (Médian, Bas), sortir du puits de la Cheminée.</td></tr>
<tr><td><b>Grappin</b> (donjon 1)</td><td>Portée 30, recharge 0,3 s, tiré en ligne droite jusqu'à l'ancrage.</td><td>Tutoriel du Hall, halle haute B, bouches hautes de la Cuve (Bas), Cheminée.</td></tr>
</table>
<div class="warn"><b>Exemple de calcul d'une chaîne de vannes</b> {M} : une galerie de 50 blocs dont les vannes se referment 5,0 s après le bouton.
Au sprint : 50 / 14,3 ≈ 3,5 s plus ≈ 0,9 s d'élan = 4,4 s, soit 0,6 s de marge. À 9,1 blocs/s : 5,5 s plus 0,9 s = 6,4 s, trop lent d'1,4 s.
La chaîne sépare donc ceux qui sprintent de ceux qui nagent normalement. Tous les chiffres de temps de ce dossier sont à régler après avoir
senti la vitesse en jeu.</div>

<h3>4.4 Le niveau d'eau de la Cuve</h3>
<p>La Cuve a trois niveaux. Elle est pleine à Haut après la mise en eau, et chaque pompe restaurée la fait descendre d'un cran
(Haut 76 → Médian 58 → Bas 42). Les bouches des secteurs sont à des hauteurs différentes, si bien que <b>le même secteur s'atteint
autrement selon le niveau</b> : on commence en poisson, on finit en grimpeur. Aucune combinaison ne bloque : à chaque niveau, chaque
bouche a un moyen d'accès, et le Drain, le sas et la Coursive restent praticables.</p>
<table>
<tr><th style="width:18%">Bouche (hauteur)</th><th style="width:21%">Haut (76)</th><th style="width:21%">Médian (58)</th><th>Bas (42)</th></tr>
<tr><td><b>A · Filtration</b><br>64–70, ouest</td><td>sous l'eau : on nage</td><td>6 blocs au-dessus de l'eau : bond</td><td>22 blocs au-dessus : grappin (ancrages permanents de la paroi)</td></tr>
<tr><td><b>B · Chaufferie</b><br>32–38, est</td><td>nage</td><td>nage</td><td>nage, ou on marche au fond (lesté)</td></tr>
<tr><td><b>C · Turbines</b><br>48–54, nord</td><td>nage</td><td>nage (sommet à 54 &lt; 58)</td><td>6 blocs au-dessus : bond</td></tr>
<tr><td><b>D · Cheminée</b><br>30–36, nord-est</td><td colspan="3">scellée par une vanne tant que les 3 pompes ne sont pas restaurées ; ensuite sous 6 à 12 blocs d'eau (Bas)</td></tr>
</table>
<p>Conséquence de jeu {P} : l'ordre confortable est <b>A, puis C, puis B</b> (le secteur le plus haut d'abord, tant que l'eau est
haute). Qui commence par B se retrouve plus tard à devoir bondir ou grappiner pour entrer en A et C. Libre à lui.</p>

<h3>4.5 Le contournement pour qui a déjà la cape</h3>
<p>{D} Un joueur déjà équipé ne doit pas refaire l'épreuve de la cape. {P} Le <b>Drain</b> : dans l'angle nord-est du Hall, le
bassin d'essai (8 × 8, trois de profondeur) est la bouche d'un grand conduit noyé de 4 × 4, long d'environ 75 blocs, qui débouche
au pied de la Cuve. Le conduit crache de l'eau : c'est le dernier organe qui tourne dans la station morte, et son bruit se
remarque dès l'entrée. Le courant (≈ 16 blocs/s) <b>repousse tout nageur, même au sprint</b> ; seul un joueur lesté le remonte, à
pas lents (environ 2 blocs/s, soit 30 à 35 s). Sans la cape, le bassin est inoffensif : le courant renvoie dehors.</p>
<ul>
<li>Aucune vérification d'inventaire : le passage est physique, comme les piliers du grappin visibles avant qu'on puisse les atteindre.</li>
<li>Le coffre de la cape ne donne rien à qui la porte déjà (message « le casier est vide »), mais l'ouvrir ouvre quand même le Sas.</li>
<li><b>Casier de secours</b> : il redonne une cape à qui n'en a pas (mort et perte de l'objet au fond d'un secteur). Le test d'inventaire doit lire l'emplacement Accessories {O}.</li>
<li>Le Drain débouche en bas de la Cuve avant la mise en eau : <b>la mise en eau se déclenche à la première entrée d'un joueur dans la Cuve</b>, par le Sas ou par le Drain.</li>
</ul>
""")

# -------------------------------------------------------------------------- niveaux
a(f"""
<h2 class="pb">5. Niveau d'ensemble</h2>
<p>La station est un anneau de secteurs autour de la Grande Cuve, qu'on voit de dessus (fig. 1) et en coupe (fig. 2). L'entrée est
au sud ; les trois secteurs sont à l'ouest (A), au nord (C) et à l'est (B) ; la Cheminée monte au nord-est vers le Poste ; le Réservoir
est sous la Cuve. Le tracé est circulaire : une fois les secteurs faits, on les parcourt dans n'importe quel sens.</p>
{fig(1, F.fig_ensemble(), "Vue d'ensemble de dessus. Les bouches A, B, C, D sont les portes des secteurs sur la paroi de la Cuve ; le Drain contourne le Sas.")}
{fig(2, F.fig_coupe(), "Coupe dépliée : le Hall à gauche, la Cuve et ses trois niveaux d'eau au centre, la Cheminée et le Poste à droite, le Réservoir dessous. Hauteurs en blocs du gabarit.")}

<h2>6. Niveau par niveau</h2>
<h3>6.1 Acte I — À sec : le Hall des Bassins et le Vestiaire</h3>
<p>{P} <b>Fonction</b> : tutoriel sec du grappin, mise en place de l'ambiance, obtention de la cape. Rien d'aquatique : la station est
éteinte et vidée.</p>
<ul>
<li><b>Pavillon d'entrée</b> : une petite maison de bassin carrelée sur la plaine (12 × 10), porte, un escalier de <b>24 marches</b> vers le Hall.</li>
<li><b>Le Hall des Bassins</b> (120 × 55, 14 de haut) : le décor liminal à l'état pur. Carrelage blanc, pilastres répétés, lumière froide au plafond, tuyauterie apparente, panneaux de profondeur (« 0,9 m », « 1,8 m ») sur les murs. Deux bassins <b>vides</b>, une tour de toboggan, et un petit bassin <b>plein</b>.</li>
<li><b>Volant 1 · le plongeon</b> : un bassin carré de 24, creux de 12, avec en son centre une <b>tour de plongeoirs</b> à trois plateformes (+4, +8, +12). Le volant est sur la plus haute. Premier usage du grappin : un ancrage sur la tuyauterie au-dessus de chaque plateforme, plus quatre sur le bord. Tomber est sans gravité : une échelle à chaque coin.</li>
<li><b>Volant 2 · le bassin de 50 m</b> : un long bassin peu profond. En y descendant, <b>embuscade</b> : 3 zombies et un squelette surgissent du fond. Une fois vaincus, on remonte au volant, à l'extrémité est. Pas de grappin ici : c'est la pause de combat.</li>
<li><b>Volant 3 · la tour du toboggan</b> : une tour ronde de 16 de diamètre, toboggan en spirale. Le volant est au sommet ; les 4 ancrages se chaînent le long de la spirale, du premier tir facile au dernier au-dessus du vide.</li>
<li><b>Résultat</b> : chaque volant allume un témoin sur la porte du Sas (ordre libre). Au troisième, cinématique : au loin les tuyaux tremblent, la vanne du Sas se lève, et au Vestiaire un casier s'ouvre (coffre vert 1, <b>la Cape lestée</b>).</li>
<li><b>Le Vestiaire</b> : 42 × 44, deux rangées de casiers en acier. Le casier de la cape, une fois apparu, reste là : c'est le casier de secours (§ 4.5).</li>
<li><b>Le Drain</b> : voir § 4.5. À l'est, le bassin d'essai, plein, d'où sort le bruit de l'eau.</li>
<li><b>Le Sas</b> : un couloir carrelé de 30 de long, vanne à trois témoins. Il débouche sur la Coursive de la Cuve.</li>
</ul>
{fig(3, F.fig_acte1(), "Acte I. Trois volants numérotés dans le Hall ; le casier de la cape dans le Vestiaire ; le Sas fermé au nord ; le Drain à l'est.")}

<h3>6.2 Acte II — La mise en eau et la Grande Cuve</h3>
<p>{P} <b>Moment fort du donjon.</b> En franchissant le Sas, le joueur débouche sur la Coursive, un balcon circulaire de 5 de large avec
garde-corps. Au-dessous : une cuve vide de 54 de profondeur, 60 de diamètre, murs carrelés, six colonnes de tuyaux, au fond un
trou de bonde grillé. Au centre du mur sud, au-dessus de l'entrée, une rangée de trois lampes éteintes : les <b>témoins des pompes</b>.</p>
<p><b>La mise en eau</b> (cinématique) : un grondement, les pompes s'amorcent, l'eau monte par le fond. La caméra monte avec la surface
(de 30 à 76, environ 14 s). La Cuve se vide de son silence : elle est pleine, claire, immense. Le joueur, sur la Coursive 8 blocs
au-dessus de l'eau, a la cape et un seul geste évident : <b>plonger</b>. C'est le moment « poisson dans l'eau ».</p>
<ul>
<li><b>La Cuve</b> : cylindre de 60 de diamètre, sol à 30, plafond à 96. Six colonnes de tuyaux de cuivre, des balcons près des bouches, des engrenages en entités d'affichage qui tournent lentement sur les murs. Au centre du sol, la <b>bonde</b> (rayon 6), grillée, sur le Réservoir.</li>
<li><b>Les quatre bouches</b> : A (ouest), C (nord), B (est), D (nord-est, scellée) ; chacune est une <b>écluse</b>, un sas à deux vannes qu'on actionne d'un bouton. Les ancrages permanents de la paroi sont posés au-dessus des bouches A et C pour le grappin à bas niveau.</li>
<li><b>Les témoins</b> : trois lampes sur la Coursive, une par pompe ; à chaque pompe restaurée, une s'allume et le niveau baisse d'un cran (cinématique de 8 à 9 s).</li>
</ul>

<h3>6.3 Acte III — Les trois secteurs</h3>
<p>{P} Un secteur = <b>écluse</b>, <b>épreuve de son verbe</b>, <b>salle de pompe</b> avec un volant. Le volant déclenche une cinématique (la pompe
tourne, la Cuve baisse d'un cran) et le joueur ressort par l'écluse, dans une Cuve plus basse. Chaque secteur contient aussi un coffre
jaune à gagner par l'adresse (contenu à définir par Luca). Les secteurs sont indépendants : on peut les faire dans n'importe quel ordre.</p>

<h3>Secteur A — La Filtration (nager vite)</h3>
{fig(4, F.fig_secteur_a(), "Secteur A. Les vannes de la galerie se referment 5 s après le bouton ; la salle des lits est un slalom entre quatre gardiens.")}
<ul>
<li><b>Ambiance</b> : lits de filtration, grandes cuves rectangulaires de sable et de gravier, carrelage blanc et sable, lumière verdâtre. Le plus liminal des trois : couloirs interminables et parfaitement droits.</li>
<li><b>A1 · l'écluse</b> : on entre, on appuie sur le bouton, l'eau du sas s'ajuste, la vanne s'ouvre.</li>
<li><b>A2 · la galerie des vannes</b> : 50 blocs de long, 4 vannes. Le bouton d'entrée les ouvre toutes ; elles se referment 5,0 s plus tard. <b>Sprint obligatoire</b> (§ 4.3). Un bouton de relance se trouve entre chaque vanne, pour retenter sans punition (on ne meurt pas coincé : respiration illimitée).</li>
<li><b>A3 · la salle des lits</b> : 54 × 64, trois longs murets de sable alternés (les lits de filtration) : un slalom. 4 gardiens (laser, portée 15) et 2 noyés ; les lits cassent la ligne de vue, il faut sprinter d'abri en abri. On peut aussi les tuer au tourbillon, mais ils frappent fort de loin.</li>
<li><b>A4 · la salle de la Pompe A</b> : deux gardiens de plus, le volant A. Récompense : la pompe tourne, la Cuve passe d'un cran.</li>
<li><b>Coffre</b> : une chambre cachée (au nord de A3), derrière deux vannes en 2,5 s, pour qui a repéré la fente dans le mur.</li>
<li><b>Courants favorables</b> : un courant dans le sens de la marche, plus loin dans la galerie, qui pousse à 14 blocs/s en plus : un cadeau, mais qui fait dépasser la vanne si on ne freine pas.</li>
</ul>

<h3>Secteur B — La Chaufferie (se lester)</h3>
{fig(5, F.fig_secteur_b(), "Secteur B, deux étages : en bas, la salle des chaudières noyée ; en haut, la halle sèche et ses passerelles.")}
<ul>
<li><b>Ambiance</b> : cuivre, rouille, chaudières, rivets. Chaud, vapeur, lumière orangée, bruit de marteau lointain. Le plus steampunk.</li>
<li><b>B1 · le collecteur</b> : galerie en pente de 28 blocs, noyée, avec un <b>torrent de 10 blocs/s vers la Cuve</b>. En nageant on est rejeté ; en se lestant on avance au sol. C'est l'apprentissage du verbe.</li>
<li><b>B2 · la salle des chaudières</b> : 40 × 52, noyée, quatre chaudières. Trois <b>jets</b> gardent chacun un passage. Trois <b>plaques lestées</b> (cuivre) ; chacune coupe le jet qui garde la suivante. La troisième ouvre la vanne d'une <b>colonne de bulles</b> (sable des âmes, vanilla) qui monte au dessus. Détection par position, joueur au sol et lesté (§ 3). 4 noyés.</li>
<li><b>B3 · la halle haute</b> : sèche, chaude. De grandes passerelles coupées au-dessus d'un <b>bassin de trempe</b> (eau). C'est le coin du grappin : 6 ancrages sur les poutres. <b>Jets de vapeur</b> chronométrés qui blessent (1 cœur). 3 blazes {P}. Tomber, c'est nager et remonter par l'échelle.</li>
<li><b>Pompe B</b> au bout, à l'est. Coffre jaune sur un îlot de passerelle, au grappin.</li>
</ul>

<h3>Secteur C — Les Turbines (tourbillon)</h3>
<ul>
<li><b>Ambiance</b> : un gouffre cylindrique de 36 de diamètre et 48 de haut, noyé. Trois turbines géantes sur des arbres verticaux. Lumière bleutée, grondement, bulles.</li>
<li><b>Le geste</b> : frapper une turbine au <b>tourbillon</b> la lance ; il en faut 3 coups en 10 s pour qu'elle garde son élan (recharge 0,8 s : environ 2,5 s si on ne rate rien). Elle crée alors un <b>courant ascendant</b> qui porte à l'étage suivant. Si l'on rate, elle ralentit et il faut recommencer.</li>
<li><b>Tartre</b> : les grilles des étages 2 et 3 sont bouchées par du tartre, qu'<b>seul le tourbillon</b> brise.</li>
<li><b>Ennemis</b> : à chaque turbine, une vague de 3 noyés et 1 à 2 gardiens ; le tourbillon les tue. <b>Mini-boss</b> {P} avant la dernière turbine : le « Gardien du rotor » (provisoire, un gardien ancien vanilla en attendant le choix de Luca).</li>
<li><b>Salle de la Pompe C</b> : en haut, sèche (le niveau d'eau du puits est à 82, la salle à 86). Volant C, coffre jaune dans un renfoncement au-dessus de la grille d'évacuation.</li>
</ul>
{fig(6, F.fig_secteur_c(), "Secteur C : le puits des turbines en coupe. Chaque turbine lancée au tourbillon crée un courant ascendant vers l'étage suivant.")}

<h3>6.4 Acte IV — La Cheminée et le Poste</h3>
<p>{P} <b>Épreuve de synthèse.</b> Quand la troisième pompe est restaurée, la Cuve est à Bas et la vanne D se lève (cinématique : la caméra
montre, au nord-est du fond, une grande vanne qui s'ouvre). La Cheminée est un puits de 14 de diamètre qui monte jusqu'au Poste. Quatre
temps, un par verbe plus un :</p>
<ol class="sc">
<li><b>Les vannes</b> (nager vite) : galerie de 24 blocs, 3 vannes qui se referment en 3,0 s. Sprint : 1,7 s plus 0,9 s d'élan, soit 2,6 s : on passe. Nage normale : 3,5 s : on rate.</li>
<li><b>La rampe</b> (se lester) : une rampe en lacets dans le puits, avec un torrent descendant de 10 blocs/s. Lesté, on monte pas à pas ; nageant, on est ramené en bas.</li>
<li><b>La grille et la turbine</b> (tourbillon) : une grille de tartre barre le chemin ; le tourbillon la brise, un second coup lance la turbine, dont le courant ascendant porte jusqu'à la surface du puits (76).</li>
<li><b>Le bond et le grappin</b> : on sort de l'eau par un bond (≈ 8 blocs), on retombe sur un rebord à 84. Puis trois ancrages au-dessus de trous de 6, sur la chambre sèche. Tomber, c'est retomber dans l'eau du puits : sans danger, mais on recommence le bond.</li>
</ol>
{fig(7, F.fig_cheminee(), "La Cheminée en coupe : entrée au fond de la Cuve (Bas), puits noyé jusqu'à 76, chambre sèche et Poste en haut.")}
<p><b>Le Poste de commande</b> : une cabine vitrée, des cadrans, un grand levier (le « levier général »). Une fenêtre donne sur la Cuve
à 40 blocs du sol : un <b>plongeoir</b>, repris de celui du Hall. Le levier ouvre la bonde ; l'eau de la Cuve (à Bas, 42) forme un
tourbillon qui s'engouffre. Le joueur saute du plongeoir dans le tourbillon et se laisse emporter, courant favorable, vers le
Réservoir : la descente finale, un « toboggan d'eau ».</p>

<h3>6.5 Acte V — Le Réservoir</h3>
{fig(8, F.fig_reservoir(), "Le Réservoir : arène circulaire noyée de 88 de diamètre, onze piliers en ruine comme abris, quatre bondes de fond (phase 2).")}
<p>{P} La salle du boss : un réservoir circulaire noyé de 88 de diamètre et 26 de haut, à 30 sous la Cuve. Onze piliers en ruine comme
abris (le rayon du boss est arrêté par la pierre), quatre bondes de fond. Une sortie en conduit au sud, l'<b>Exutoire</b>, fermé tant que
le boss vit. Le tourbillon de la bonde y dépose le joueur par le plafond, au centre. Détails du combat au § 8.</p>
<p><b>Après le boss</b> {P} : l'Exutoire s'ouvre. Un long conduit monte d'environ 80 blocs vers un lac sur la plaine, avec un courant
ascendant favorable (≈ 8 s) : le joueur jaillit hors de l'eau en plein jour (bond final). C'est la cinématique de victoire du
donjon ; un coffre jaune attend dans le Réservoir (butin du boss à définir).</p>
""")

# -------------------------------------------------------------------------- énigmes
a(f"""
<h2 class="pb">7. Les épreuves et leurs solutions</h2>
<table>
<tr><th style="width:11%">Où</th><th style="width:15%">Épreuve</th><th style="width:50%">Solution</th><th>Récompense / effet</th></tr>
<tr><td>Hall</td><td>Volant 1 (plongeon)</td><td>Grappin : bord du bassin → ancrage au-dessus de chaque plateforme de la tour → volant, au sommet. Une chute se rattrape par l'échelle.</td><td>1er témoin du Sas</td></tr>
<tr><td>Hall</td><td>Volant 2 (50 m)</td><td>Descendre, tuer 3 zombies et le squelette, remonter à l'extrémité est.</td><td>2e témoin</td></tr>
<tr><td>Hall</td><td>Volant 3 (toboggan)</td><td>Chaîner les 4 ancrages le long de la spirale jusqu'au sommet de la tour.</td><td>3e témoin ; cinématique ; casier de la cape ; Sas ouvert</td></tr>
<tr><td>Hall</td><td>Casier de la cape</td><td>Ouvrir le coffre vert 1. Qui a déjà la cape : « le casier est vide », le Sas s'ouvre quand même.</td><td>Cape lestée</td></tr>
<tr><td>Hall</td><td>Le Drain</td><td>Entrer dans le bassin d'essai, se lester (V), remonter le conduit au fond pendant 30 à 35 s.</td><td>Débouche au pied de la Cuve : saute les volants</td></tr>
<tr><td>Cuve</td><td>Mise en eau</td><td>Franchir le Sas (ou entrer par le Drain) ; plonger.</td><td>Niveau Haut ; trois secteurs ouverts</td></tr>
<tr><td>A</td><td>Galerie des vannes</td><td>Appuyer sur le bouton, sprinter 50 blocs en moins de 5 s ; rebouton de relance entre les vannes.</td><td>Accès à la salle des lits</td></tr>
<tr><td>A</td><td>Salle des lits</td><td>Slalom entre les lits, sprint d'abri en abri, ou tourbillon sur les gardiens.</td><td>Accès à la pompe</td></tr>
<tr><td>A</td><td>Pompe A</td><td>Tourner le volant A.</td><td>Cuve d'un cran ; 1er témoin ; coffre (chambre cachée)</td></tr>
<tr><td>B</td><td>Collecteur</td><td>Se lester (V), marcher au sol contre le torrent de 10 blocs/s.</td><td>Accès aux chaudières</td></tr>
<tr><td>B</td><td>Chaudières</td><td>Plaque 1 (jet 1 faible) → coupe le jet qui garde la 2 → plaque 2 → coupe le jet de la 3 → plaque 3 : la colonne de bulles s'ouvre.</td><td>Accès à la halle haute</td></tr>
<tr><td>B</td><td>Halle haute</td><td>Grappin d'une passerelle à l'autre au-dessus du bassin de trempe ; esquiver les jets de vapeur ; 3 blazes.</td><td>Accès à la pompe B ; coffre sur l'îlot</td></tr>
<tr><td>B</td><td>Pompe B</td><td>Tourner le volant B.</td><td>Cuve d'un cran ; 2e témoin</td></tr>
<tr><td>C</td><td>Turbines T1 à T3</td><td>3 coups de tourbillon en 10 s sur chaque turbine ; monter par le courant ascendant ; tuer la vague.</td><td>Passage aux étages suivants</td></tr>
<tr><td>C</td><td>Tartre</td><td>Un coup de tourbillon sur la grille.</td><td>Accès à l'étage</td></tr>
<tr><td>C</td><td>Gardien du rotor</td><td>Mini-boss : tourbillon, nager pour esquiver le laser.</td><td>Accès à la dernière turbine</td></tr>
<tr><td>C</td><td>Pompe C</td><td>Tourner le volant C.</td><td>Cuve d'un cran ; 3e témoin</td></tr>
<tr><td>Cuve</td><td>Troisième pompe</td><td>Restaurer les trois pompes.</td><td>Vanne D levée</td></tr>
<tr><td>Cheminée</td><td>Les quatre temps</td><td>Vannes en 3,0 s au sprint ; rampe lestée ; tartre + turbine au tourbillon ; bond + trois ancrages (§ 6.4).</td><td>Accès au Poste</td></tr>
<tr><td>Poste</td><td>Levier général</td><td>Tirer le levier ; sauter du plongeoir dans le tourbillon.</td><td>Bonde ouverte ; arrivée dans le Réservoir</td></tr>
<tr><td>Réservoir</td><td>Boss</td><td>Voir § 8.</td><td>L'Exutoire s'ouvre</td></tr>
</table>

<h2>8. Le boss : le Léviathan</h2>
<p>{P} Le boss est le Léviathan (PR #22, par Luca), qui teste les trois verbes de la cape. Ce qui existe : un serpent de mer d'environ
36 blocs, à pièces de collision (tête à mâchoire, huit anneaux, queue), perle lumineuse sur le front, 150 PV par défaut, équilibrage dans
<code>config/grappin-common.toml</code>, combat à toutes les difficultés. La salle de la PR est noyée, avec des cloches d'air : <b>à adapter</b>.</p>
<h3>Phase 1 — dès le départ (existant, à garder)</h3>
<ul>
<li>Il tourne autour du joueur à 14 blocs et ne fonce que pour la morsure ou la plongée.</li>
<li><b>Rayon de l'œil</b> précédé d'une ligne d'étincelles, arrêté par les piliers (pas par l'eau). Puis la <b>perle</b> est exposée 6 s et prend ×3. Le corps ne prend que 15 % des dégâts.</li>
<li>Coup de queue. Immunisé à la noyade et au feu.</li>
<li><b>Verbes</b> : nager vite (esquiver la morsure et la plongée), tourbillon (toucher la perle pendant la fenêtre de 6 s), les piliers comme abris contre le rayon.</li>
</ul>
<h3>Phase 2 — sous 50 % de vie : « la vidange » {P}, à valider avec Luca</h3>
<ul>
<li><b>Remplace l'attaque sur les cloches d'air</b>, devenue sans objet avec la respiration illimitée.</li>
<li>Toutes les 40 s environ, le Léviathan plonge au fond et <b>arrache une bonde</b> (une des quatre). Un <b>courant d'aspiration</b> de 12 s tire tout vers cette bonde, dans un rayon de 20.</li>
<li><b>Se lester</b> permet de tenir au sol ; <b>nager au sprint</b> (14,3 b/s) permet de fuir le rayon. Les deux marchent, le premier est plus sûr, le second plus rapide.</li>
<li>Pendant qu'il arrache la bonde, sa perle est exposée <b>8 s</b> : le joueur lesté peut se rapprocher à pied et frapper au tourbillon. Risque contre récompense.</li>
</ul>
<h3>À modifier dans le code de la PR #22</h3>
<ul>
<li>Remplacer l'attaque sur les cloches d'air par la vidange ; supprimer les cloches de la salle et de la logique.</li>
<li>Tout nom ou texte qui parle du « Voile » (obsolète) ; le vérificateur « absence d'air hors des cloches » devient « bondes au sol, piliers à distance de tir, Exutoire fermé ».</li>
<li>Étendre le courant d'aspiration aux zones de courant du mod (§ 3).</li>
<li>La mort du boss pose le tag <code>grappin:leviathan_mort</code> : l'Exutoire s'ouvre, message « Le Léviathan est vaincu ».</li>
<li>Chute hors du boss : impossible (tout est noyé, pas de vide). La remise à zéro supprime le boss, rétablit les bondes, referme l'Exutoire.</li>
</ul>

<h2>9. Scénario attendu du joueur</h2>
<ol class="sc">
<li>Il descend l'escalier de 24 marches depuis le pavillon et arrive dans le Hall des Bassins : vide, carrelé, silencieux. Au fond, à l'est, un bruit d'eau : le Drain.</li>
<li>Il tourne le volant 1 : grappin sur la tour du plongeon, premier tir, première plateforme, deuxième, le volant. Un témoin s'allume.</li>
<li>Il descend dans le bassin de 50 m : embuscade, combat, volant 2.</li>
<li>Il grimpe la spirale du toboggan au grappin : volant 3. Cinématique : tuyaux au loin, vanne du Sas, et au Vestiaire un casier s'ouvre.</li>
<li>Il y trouve la Cape lestée, l'équipe, retourne au Sas et le franchit.</li>
<li>Il débouche sur la Coursive : une cuve vide, profonde. Il passe le seuil ; l'eau monte, la caméra la suit. Il plonge : première nage libre, 9 blocs/s, sprint à 14.</li>
<li>Il explore la Cuve et ses quatre bouches. Il choisit A (la plus haute) : écluse, sprint dans la galerie des vannes, slalom des gardiens, pompe A. La Cuve descend d'un cran.</li>
<li>Il enchaîne C (bouche à 54, encore sous l'eau) : turbines, tourbillon, tartre, mini-boss, pompe C. Deuxième cran.</li>
<li>Il va à B : torrent du collecteur (il se lestera), plaques, colonne de bulles, halle haute au grappin, vapeur, pompe B. Troisième cran, la Cuve est à Bas.</li>
<li>La vanne D se lève. La Cheminée : vannes au sprint, rampe lestée, tourbillon sur la grille, bond, grappin. Il arrive au Poste.</li>
<li>Il tire le levier général. La bonde s'ouvre ; au plongeoir, il saute dans le tourbillon et descend avec le courant.</li>
<li>Le Réservoir : le Léviathan surgit. Phase 1 (esquives, perle) puis phase 2 (la vidange : se lester ou fuir). Le Léviathan meurt.</li>
<li>L'Exutoire s'ouvre. Il remonte par le conduit, courant favorable, et jaillit du lac en plein jour.</li>
</ol>
<p><b>Durée visée</b> {M} pour un premier passage : 55 à 75 minutes (Hall 8, mise en eau 2, secteurs 10 à 14 chacun, Cheminée 5, boss 8 à 12). Estimation, à mesurer.</p>

<h2>10. Ambiance, matériaux, sons</h2>
<ul>
<li><b>Liminal</b> {D} : grandes salles vides, répétition (pilastres, bassins identiques, couloirs sans fin), lumière froide uniforme, aucun décor vivant, aucun texte sauf des repères de profondeur. L'inquiétant vient du vide et du bourdonnement, pas de monstres.</li>
<li><b>Palette</b> {P} : quartz lisse et blanc pour le carrelage, terre cuite cyan clair, béton blanc, cuivre poli et oxydé (patine verte), fer, chaînes, barreaux de fer, lanternes marines et blocs de lumière pour les plafonds. Chaudières en cuivre et fer. Rien de vivant, hors les gardiens et les noyés.</li>
<li><b>Steampunk</b> {D} : tuyaux apparents, volants, cadrans, engrenages. Les mécanismes qui tournent sont des <b>entités d'affichage animées</b> (datapack) : aucun mod tiers.</li>
<li><b>Sons</b> {P} : bourdonnement bas de la station éteinte ; au fur et à mesure des pompes, le grondement monte. Le seul bruit du Hall est celui du Drain.</li>
<li><b>Repères de profondeur</b> {P} : des panneaux « 1,2 m », « 2,4 m » sur les murs du Hall ; dans la Cuve, trois repères « HAUT / MÉDIAN / BAS » gravés dans la paroi, pour qu'on lise le niveau d'eau sans compter.</li>
</ul>

<h2>11. Ce qui sera contrôlé automatiquement</h2>
<p>{P} Sur le modèle des vérificateurs du donjon 1, par simulation de la marche, de la nage et du grappin :</p>
<ul>
<li><b>Accès par niveau</b> : pour chaque niveau d'eau (Haut, Médian, Bas) et chaque bouche, un trajet existe avec la cape et le grappin ; aucun soft-lock, jamais.</li>
<li><b>Chronos</b> : chaque chaîne de vannes se franchit au sprint avec ≥ 0,4 s de marge ; elle échoue à 9,1 blocs/s.</li>
<li><b>Bond</b> : chaque accès par bond reste à ≤ 8 blocs ; chaque trou au grappin à 5 ≤ trou ≤ 28, ancrage à 3 à 4 blocs au-dessus de l'arrivée, et à 3,5 du bord.</li>
<li><b>Étanchéité</b> : l'eau d'un secteur ne s'écoule pas dans la Cuve quand elle baisse ; chaque écluse règle correctement l'eau du sas.</li>
<li><b>Zones de courant</b> : le Drain se remonte lesté en moins de 40 s et pas à la nage ; la rampe de la Cheminée de même.</li>
<li><b>Plaques lestées</b> : joueur lesté et au sol, pas en nage au-dessus.</li>
<li><b>Boss</b> : un joueur lesté atteint la perle à chaque orientation du boss ; chaque pilier met à l'abri du rayon ; l'Exutoire est fermé tant que le boss vit.</li>
<li><b>Lumière 5 partout</b> ; aucun fond d'air non prévu.</li>
<li><b>Fonctions de datapack</b> : noms valides, fonctions appelées existantes, cinématiques (caméra dans l'eau ou l'air, ≤ 1,2 bloc par tick, jamais de rotation), chaque particule existe dans le jeu.</li>
</ul>

<h2>12. Points ouverts et décisions à prendre</h2>
<table>
<tr><th style="width:5%"></th><th style="width:38%">Question</th><th>Recommandation</th></tr>
<tr><td class="c">{O}</td><td><b>Vrais blocs du mod Create, ou esthétique reconstruite avec nos blocs ?</b></td><td>{P} Reconstruite. Create ajoute une dépendance, charge des chunks et lie le donjon à une version précise de Create ; des entités d'affichage animées donnent des engrenages et des turbines qui tournent sans rien de tout cela.</td></tr>
<tr><td class="c">{O}</td><td><b>Méthode : Luca dessine salle par salle (comme au donjon 1), ou squelette proposé ?</b></td><td>{P} Ce dossier est le squelette ; Luca le redessine salle par salle avec ses codes couleurs, et chaque écart est noté, comme au donjon 1.</td></tr>
<tr><td class="c">{O}</td><td><b>Par quoi commencer.</b></td><td>{P} 1) les ajouts au mod (courants, signal tourbillon, état lesté) ; 2) le Hall, le Vestiaire, la Cuve et la mise en eau ; 3) le Drain (pour valider la sensation de courant). Le reste (secteurs, Cheminée, boss) ensuite.</td></tr>
<tr><td class="c">{O}</td><td><b>Dépendance Accessories</b> (emplacement de cape) et casier de secours (lire l'emplacement).</td><td>À valider par Duncan avec la PR #53.</td></tr>
<tr><td class="c">{O}</td><td><b>Ordre des donjons.</b> L'Acte I suppose le grappin déjà acquis (donjon 1).</td><td>{P} Prérequis écrit : le grappin. Si l'ordre libre est voulu, l'Acte I perd son tutoriel du grappin.</td></tr>
<tr><td class="c">{O}</td><td><b>Grappin sous l'eau</b> : fonctionne-t-il ? Le design n'en dépend pas, tout est à l'air libre.</td><td>À tester en jeu.</td></tr>
<tr><td class="c">{O}</td><td><b>Mode aventure obligatoire</b> dans le donjon.</td><td>{P} Oui : il n'y a aucun minage dans ce design.</td></tr>
<tr><td class="c">{O}</td><td><b>Respawn</b> : où réapparaît un joueur mort ? Le casier de secours est au Hall.</td><td>{P} Au pavillon. À confirmer.</td></tr>
<tr><td class="c">{O}</td><td><b>Retour après la vidange</b> : la Cuve reste vide après le boss.</td><td>{P} Pas de retour prévu ; les coffres manqués restent atteignables au grappin sur le Hall et la Coursive, mais pas dans les secteurs (secs, donc sans verbes de la cape). À discuter.</td></tr>
<tr><td class="c">{O}</td><td><b>Contenu à définir par Luca</b> : vrai mini-boss (C), butin des coffres jaunes, autres ennemis que zombies, squelettes, gardiens, noyés, blazes, butin du Léviathan.</td><td>—</td></tr>
<tr><td class="c">{M}</td><td><b>Toutes les distances et durées</b> (vannes, courants, fenêtres des turbines, vitesse du Drain).</td><td>À régler après avoir senti la vitesse en jeu (30 blocs en 3 s).</td></tr>
<tr><td class="c">{O}</td><td><b>Titre définitif</b> du donjon.</td><td>« La Station de Purification » est un titre de travail.</td></tr>
</table>

<h2>13. Règles du projet à respecter (CLAUDE.md)</h2>
<ul>
<li>Jamais de push sur <code>main</code> ; une branche et une PR par sujet ; le merge est décidé par Duncan.</li>
<li>Ne jamais changer <code>neo_version</code> (NeoForge 21.1.216) ; tout cible cette version.</li>
<li>« Le build passe » n'est pas « ça marche » : tester en jeu avec le client du dépôt (<code>mod/</code>, <code>./gradlew runClient</code>).</li>
<li>Un script ne suppose aucun état local ; livrer le monde prêt à copier (<code>--zip</code>).</li>
<li>Blockbench pour tout nouveau modèle (la cape a un modèle procédural provisoire à remplacer) ; MCreator exclu.</li>
<li>Construire salle par salle d'après les croquis de Luca, avec ses codes couleurs ; signaler chaque écart au croquis.</li>
<li>Commits en français, qui expliquent le pourquoi.</li>
</ul>
""")

html_doc = f"""<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>Donjon de la Station — dossier de conception</title>
<style>{CSS}</style></head><body>{''.join(parts)}</body></html>"""

OUT_HTML.write_text(html_doc, encoding="utf-8")
subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                f"--print-to-pdf={OUT_PDF}", OUT_HTML.as_uri()], check=True)
OUT_HTML.unlink()
print("écrit :", OUT_PDF)
