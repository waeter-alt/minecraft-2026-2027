"""Builds the Kodistan 2026 project dossier (HTML -> PDF via headless Chrome)."""
import html
import json
import subprocess
import sys
from pathlib import Path

from art import icon, gear, rune_ring, iso_block, svg, defs_glow
from data import F, DOMAINS, PRIO, FAISA, RISKS, DECISIONS

HERE = Path(__file__).parent
OUT_HTML = HERE / "kodistan.html"
OUT_PDF = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "kodistan.pdf"
PAGES_JSON = HERE / "pages.json"
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

e = html.escape
pages = json.loads(PAGES_JSON.read_text()) if PAGES_JSON.exists() else {}


def sessions(prio):
    return sum(x["charge"] for x in F if x["prio"] == prio)


S = {p: sessions(p) for p in PRIO}
TOTAL = sum(S.values())


def fmt(n):
    return (f"{n:.1f}".rstrip("0").rstrip(".")).replace(".", ",")


# ------------------------------------------------------------------ decorative compositions
def cover_art():
    body = defs_glow()
    body += '<circle cx="420" cy="360" r="330" fill="url(#halo)"/>'
    body += gear(420, 360, 300, teeth=28, fill="#d9a93f", opacity=0.13, spokes=6)
    body += gear(690, 170, 120, teeth=14, fill="#b8733a", opacity=0.22, spokes=5)
    body += gear(150, 760, 95, teeth=12, fill="#b8733a", opacity=0.2, spokes=4)
    body += rune_ring(420, 360, 205, color="#54d6c8", count=28, glyph=26, width=1.6, opacity=0.75, seed=7)
    body += rune_ring(420, 360, 150, color="#d9a93f", count=20, glyph=18, width=1.2, opacity=0.55, seed=11)
    body += f'<g transform="translate(328,268) scale(0.36)" filter="url(#glow)">{icon_paths("crown", "#f3d27a")}</g>'
    # floating blocks
    body += f'<g opacity="0.95">{iso_block(120, 930, 74, "grass", seed=2)}</g>'
    body += f'<g opacity="0.95">{iso_block(250, 975, 60, "gold", seed=5)}</g>'
    body += f'<g opacity="0.95">{iso_block(610, 940, 70, "rune", seed=9)}</g>'
    body += f'<g opacity="0.95">{iso_block(720, 990, 52, "copper", seed=4)}</g>'
    body += f'<g opacity="0.9">{iso_block(470, 1010, 44, "stone", seed=3)}</g>'
    return svg(840, 1188, body, cls="cover-art")


def icon_paths(name, color):
    """Return the inner paths of an icon (for use inside another SVG)."""
    raw = icon(name, color=color)
    inner = raw.split(">", 1)[1].rsplit("</svg>", 1)[0]
    return inner


def divider_art(name, accent="#d9a93f", seed=1):
    body = defs_glow()
    body += f'<circle cx="420" cy="520" r="360" fill="url(#halo)"/>'
    body += gear(420, 520, 330, teeth=30, fill=accent, opacity=0.08, spokes=6)
    body += rune_ring(420, 520, 230, color="#54d6c8", count=30, glyph=24, width=1.4, opacity=0.5, seed=seed)
    body += f'<g transform="translate(300,400) scale(0.47)" filter="url(#glow)">{icon_paths(name, accent)}</g>'
    return svg(840, 1040, body, cls="divider-art")


def pips(n, total=4):
    return '<span class="pips">' + "".join(
        f'<i class="{"on" if i < n else ""} f{n}"></i>' for i in range(total)) + "</span>"


def prio_badge(p):
    return f'<span class="prio {p.lower()}">{p}<em>{PRIO[p][0]}</em></span>'


def risk_tag(r):
    cls = {"Faible": "lo", "Moyen": "mid", "Élevé": "hi"}.get(r, "mid")
    return f'<span class="tag risk {cls}">{r}</span>'


def impact_tag(i):
    cls = {"Cœur": "core", "Fort": "strong", "Moyen": "mid", "Confort": "low"}[i]
    return f'<span class="tag imp {cls}">{i}</span>'


def charge_bar(c, maxc=5):
    w = min(100, c / maxc * 100)
    return f'<span class="cbar"><b style="width:{w:.0f}%"></b></span><span class="cnum">{fmt(c)}</span>'


def page_ref(key):
    return str(pages.get(key, "")).strip()


# ------------------------------------------------------------------ pages
def page_cover():
    return f"""
<section class="full dark cover">
  {cover_art()}
  <div class="cover-top"><span>DOSSIER DE PROJET</span><span>ÉDITION 2026 – 2027</span></div>
  <div class="cover-title">
    <div class="kicker">Le retour de la two weeks Minecraft phase</div>
    <h1 class="logo">KODISTAN</h1>
    <div class="subtitle">La Couronne Vacante</div>
    <div class="wip">titre de travail</div>
  </div>
  <div class="cover-tag">L'Empereur se meurt. Quatorze nuits pour prendre sa place<br>— ou pour faire tomber l'Empire.</div>
  <div class="cover-bottom">
    <span>Présentation · Cahier des charges · Faisabilité · Planning</span>
    <span>Duncan &amp; Claude · novembre 2026</span>
  </div>
</section>"""


TOC = [
    ("I", "L'Appel", "Ce qui vous attend", "p1"),
    ("II", "Cahier des charges", "Contexte, objectifs, principes, architecture", "p2"),
    ("III", "Les fonctionnalités", "64 fonctionnalités évaluées une par une", "p3"),
    ("IV", "Planning & priorités", "Charge, capacité, feuille de route, les 14 soirées", "p4"),
    ("V", "Risques & décisions", "Ce qui peut coincer, ce qu'il reste à trancher", "p5"),
]


def page_toc():
    rows = "".join(
        f'<div class="toc-row"><span class="toc-num">{n}</span><div><div class="toc-t">{e(t)}</div>'
        f'<div class="toc-s">{e(s)}</div></div><span class="toc-dots"></span><span class="toc-p">{page_ref(k)}</span></div>'
        for n, t, s, k in TOC)
    return f"""
<section class="flow toc">
  <div class="eyebrow">Sommaire</div>
  <h2 class="big">Le grimoire du projet</h2>
  <p class="lead">Ce dossier rassemble tout ce qu'on a imaginé pour Kodistan 2026, trié, évalué et planifié.
  La première partie se lit comme une bande-annonce. Les suivantes, comme un plan de bataille.</p>
  <div class="toc-list">{rows}</div>
  <div class="toc-note">{icon("scroll-unfurled", "#7a6a52", 26)}<span>Tout ce qui est marqué <b>P0</b> doit exister le soir du lancement.
  Le reste arrive pendant la partie : chaque livraison devient un événement de la soirée.</span></div>
</section>"""


def page_divider(num, title, sub, ico, key, accent="#d9a93f", seed=1):
    return f"""
<section class="full dark divider" data-key="{key}">
  {divider_art(ico, accent, seed)}
  <div class="div-text">
    <div class="div-num">Partie {num}</div><span class="marker">KDX{key}KDX</span>
    <h2 class="div-title">{e(title)}</h2>
    <div class="div-sub">{e(sub)}</div>
  </div>
</section>"""


def page_manifesto():
    return f"""
<section class="full dark manifesto">
  <div class="mani-bg">{svg(840, 1188, defs_glow() + rune_ring(420, 600, 380, color="#54d6c8", count=40, glyph=30, width=1.2, opacity=0.18, seed=21) + gear(760, 1100, 260, teeth=26, fill="#d9a93f", opacity=0.07))}</div>
  <div class="mani-inner">
    <div class="eyebrow light">Prologue</div>
    <h2 class="mani-title">Le trône<br>est vide.</h2>
    <p class="mani-p">L'Empereur se meurt. Dans la citadelle, l'or s'entasse et les édits tombent chaque soir.
    Autour d'elle, des colonies naissent, s'allient, commercent… et se trahissent.</p>
    <p class="mani-p">Quatorze nuits. Une seule couronne. Et un Empire qui ne lâchera rien sans combattre.</p>
    <div class="mani-sep">{icon("rune-stone", "#54d6c8", 34)}</div>
    <p class="mani-small">Cette année, pas de suite. Un nouveau jeu, pensé pour nous,
    construit sur tout ce qu'on a appris de <i>L'Artefact du Chaos</i>.</p>
  </div>
</section>"""


CARDS = [
    ("village", "Bâtis sans construire",
     "Pose un plan, paie, regarde ta ville sortir de terre bloc par bloc. Des murailles qui épousent le relief, des quartiers entiers en un geste, des mines qui rapportent vraiment."),
    ("gold-stack", "L'or a un poids",
     "La Couronne, frappée par l'Empire. Des bourses aux lingots, jusqu'au trésor du dragon. Plus tu es riche, plus ça se voit… et plus tu brilles quand tu le transportes."),
    ("caravan", "Commerce. Ou trahis.",
     "Scelle une alliance : une route se trace d'elle-même entre vos colonies et les caravanes s'élancent, même quand tu dors. Romps-la, et la route s'efface sous vos yeux."),
    ("arena", "L'arène ne dort jamais",
     "Chaque nuit, un nouveau biome, de nouvelles règles. Forge de lave, faille sans gravité, toundra gelée. Défie qui tu veux, parie sur qui tu veux. L'Empereur regarde."),
    ("dragon-head", "Les profondeurs s'éveillent",
     "De nouveaux donjons s'ouvrent au fil des soirs. Monte ton groupe, partage le butin… ou pas. Et quand le dragon revient, même tes pires ennemis auront besoin de toi."),
    ("wizard-staff", "Forge ta légende",
     "Choisis ta voie, trouve les maîtres de la capitale, paie leurs épreuves et triomphes-en. Les compétences ultimes se méritent, la classe secondaire aussi."),
    ("scales", "La loi de l'Empire",
     "La guerre totale existe. Elle est interdite. Déclare-la quand même, si tu es prêt à en payer le prix. Et jette un œil aux primes affichées à l'Hôtel : ton nom y est peut-être."),
    ("crowned-skull", "Renverse l'Empire",
     "Tous les trois jours, la dîme. De plus en plus lourde. Le trésor impérial grossit sous vos yeux. Un soir, quelqu'un lèvera l'Étendard. De quel côté seras-tu ?"),
]


def page_cards(chunk, title, eyebrow):
    cards = "".join(
        f'<div class="card"><div class="card-ico">{icon(i, "#f3d27a", 46)}</div>'
        f'<h3>{e(t)}</h3><p>{e(d)}</p></div>' for i, t, d in chunk)
    return f"""
<section class="full dark cards-page">
  <div class="cards-bg">{svg(840, 1188, gear(820, 80, 230, teeth=24, fill="#b8733a", opacity=0.08) + gear(40, 1150, 190, teeth=20, fill="#d9a93f", opacity=0.07))}</div>
  <div class="cards-inner">
    <div class="eyebrow light">{e(eyebrow)}</div>
    <h2 class="hype-title">{e(title)}</h2>
    <div class="cards">{cards}</div>
  </div>
</section>"""


NIGHTS = [
    ("J1", "Le royaume s'ouvre", "Colonies, capitale, arène, premiers donjons."),
    ("J3", "Première dîme", "Et les premières routes commerciales."),
    ("J5", "Les primes tombent", "Duels publics, paris, champion de l'arène."),
    ("J7", "Le dragon s'éveille", "Les maîtres ouvrent leurs épreuves ultimes."),
    ("J9", "L'Étendard", "La révolte devient possible."),
    ("J12", "La Grande Dîme", "Les sièges commencent."),
    ("J14", "Couronnement… ou chute", "Une seule fin, écrite par vous."),
]

PROMISES = [
    ("hourglass", "Personne ne t'attend", "Ton absence ne bloque personne. Celle des autres ne te bloque pas."),
    ("chest", "Ton stuff t'appartient", "Jamais perdu, même en mourant. Seul l'or que tu portes est en jeu."),
    ("magic-portal", "Zéro installation", "Un dossier, un clic, tu joues. Les mises à jour se font toutes seules."),
    ("sands-of-time", "Chaque soir compte", "L'Influence est plafonnée par soirée : la présence pèse plus que les heures."),
    ("crossed-swords", "Le PvP a un sens", "Des enjeux, des titres, des paris. Pas de punition qui dégoûte."),
    ("crown", "Une fin garantie", "Le jour 14, la couronne trouve une tête. Ou l'Empire tombe."),
]


def page_nights():
    steps = "".join(
        f'<div class="night"><div class="n-day">{d}</div><div class="n-dot"></div>'
        f'<div class="n-t">{e(t)}</div><div class="n-s">{e(s)}</div></div>' for d, t, s in NIGHTS)
    proms = "".join(
        f'<div class="prom">{icon(i, "#54d6c8", 30)}<div><b>{e(t)}</b><span>{e(s)}</span></div></div>'
        for i, t, s in PROMISES)
    return f"""
<section class="full dark nights-page">
  <div class="cards-bg">{svg(840, 1188, rune_ring(420, 330, 330, color="#d9a93f", count=36, glyph=22, width=1, opacity=0.12, seed=31))}</div>
  <div class="cards-inner">
    <div class="eyebrow light">La campagne</div>
    <h2 class="hype-title">Quatorze nuits</h2>
    <p class="hype-lead">Chaque soir, quelque chose s'ouvre, tombe ou s'embrase.
    Ce qui n'existe pas encore au lancement arrive pendant la partie, comme un événement.</p>
    <div class="nights">{steps}</div>
    <h3 class="hype-sub">Ce qui change par rapport à 2025</h3>
    <div class="proms">{proms}</div>
  </div>
  <div class="blocks-row">{svg(840, 260, iso_block(90, 40, 70, "grass", seed=12) + iso_block(240, 80, 56, "wood", seed=13) + iso_block(370, 30, 80, "rune", seed=14) + iso_block(540, 85, 54, "gold", seed=15) + iso_block(680, 45, 66, "copper", seed=16))}</div>
</section>"""


def page_context():
    return f"""
<section class="flow" data-key="cdc">
  <div class="eyebrow">Cahier des charges · 1</div>
  <h2 class="big">D'où l'on part</h2>
  <p class="lead">Du 4 au 24 décembre 2025, <b>Kodistan – L'Artefact du Chaos</b> a tourné sous NeoForge 1.21.1 avec 55 mods :
  MineColonies, Create, la série RPG, Better Combat, Simple Voice Chat, Distant Horizons. Deux équipes, une arène centrale
  et ses deux autels à capturer, un braquage, une économie en émeraudes, le tout piloté par un datapack et des blocs de commande.</p>
  <div class="two">
    <div class="box good"><h4>{icon("target-prize", "#3f6b3a", 20)} Ce qui a plu</h4><ul>
      <li>Les donjons à épreuves, avec du bon butin à la clé.</li>
      <li>Le système de classes RPG, les sorts, les armures.</li>
      <li>Le fait de jouer en équipe.</li>
      <li>Quelques combats mémorables à l'arène.</li></ul></div>
    <div class="box bad"><h4>{icon("skull-crossed-bones", "#8e2a24", 20)} Ce qui a coulé l'événement</h4><ul>
      <li><b>L'écart d'investissement</b> entre joueurs : ceux qui jouaient peu ont démotivé les autres, en réaction en chaîne.</li>
      <li>La chute d'intérêt au bout d'une semaine sur deux.</li>
      <li>Le farm écrasant de MineColonies, impossible à corriger à l'époque.</li>
      <li>Un PvP boudé : l'équipe en tête écrasait l'autre, peur de perdre son stuff.</li>
      <li>Un événement final prévu… qui n'a jamais eu lieu.</li></ul></div>
  </div>
  <div class="callout">{icon("all-seeing-eye", "#9c6b1e", 30)}<p><b>La leçon centrale :</b> le problème n'était pas l'équipe,
  c'était <i>la dépendance</i>. Kodistan 2026 garde le plaisir de jouer ensemble et supprime tout ce qui fait qu'un absent pénalise les présents.</p></div>
  <h4 class="mt4">Kodistan 2025 en chiffres</h4>
  <div class="stats">
    <div class="stat"><b>20</b><span>jours de serveur</span></div>
    <div class="stat"><b>55</b><span>mods installés</span></div>
    <div class="stat"><b>2 / 4</b><span>équipes vraiment actives</span></div>
    <div class="stat"><b>299</b><span>captures d'autels annoncées</span></div>
    <div class="stat"><b>1</b><span>semaine avant la chute d'intérêt</span></div>
    <div class="stat red"><b>0</b><span>événement final joué</span></div>
  </div>

</section>"""


RULES = [
    ("hourglass", "L'absence ne bloque jamais personne",
     "Caravanes, colonies, Empire : tout continue de tourner sans le joueur absent."),
    ("shaking-hands", "Grouper rapporte, jouer seul ne coûte rien",
     "Des bonus de groupe, jamais de malus en solo. Des groupes pour une activité, pas pour toute la partie."),
    ("sands-of-time", "La présence pèse plus que les heures",
     "Influence plafonnée par soirée, revenus passifs, rattrapage pour ceux qui sont en retard."),
    ("chest", "On ne perd jamais son stuff",
     "Seuls l'or porté, un peu d'Influence et la durabilité sont en jeu."),
    ("crossed-swords", "Le PvP attire, il ne punit pas",
     "Des zones sûres, des enjeux consentis, des récompenses qu'on ne trouve nulle part ailleurs."),
    ("wax-seal", "Les trahisons ont un poids",
     "Routes qui s'effacent, Marque du traître, investissements perdus."),
    ("crown", "Une fin garantie",
     "Couronnement ou chute de l'Empire : le jour 14 a lieu, quoi qu'il arrive."),
    ("gears", "Tout se règle à chaud",
     "Chaque valeur d'équilibrage se modifie en cours de partie, sans redémarrer."),
]


def page_principles():
    rules = "".join(
        f'<div class="rule"><div class="rule-ico">{icon(i, "#9c6b1e", 30)}</div><div><b>{e(t)}</b><span>{e(s)}</span></div></div>'
        for i, t, s in RULES)
    return f"""
<section class="flow">
  <div class="eyebrow">Cahier des charges · 2</div>
  <h2 class="big">Objectifs & contraintes</h2>
  <div class="two">
    <div><h4>Objectifs</h4><ul class="check">
      <li>Un <b>tout nouveau mode de jeu</b>, très ambitieux, pas une suite.</li>
      <li>Garder l'intérêt <b>pendant les quatorze soirées</b>, avec une montée en tension jusqu'au final.</li>
      <li>Un PvP qui attire au lieu de faire peur, sans l'imposer.</li>
      <li>Des villes belles et vivantes, même pour ceux qui n'aiment pas construire.</li>
      <li>Une politique qui compte : alliances, trahisons, et un Empire à renverser.</li>
      <li><b>Zéro configuration</b> pour les joueurs.</li></ul></div>
    <table class="spec"><tbody>
      <tr><th>Joueurs</th><td>5 à 10, la même bande de potes</td></tr>
      <tr><th>Durée</th><td>14 soirées</td></tr>
      <tr><th>Période</th><td>Décembre 2026 – janvier 2027</td></tr>
      <tr><th>Horaires</th><td>Le soir (19 h 30 – minuit en 2025)</td></tr>
      <tr><th>Plateforme</th><td>Minecraft Java, NeoForge, version à figer</td></tr>
      <tr><th>Accès</th><td>Prism Launcher portable préconfiguré</td></tr>
      <tr><th>Serveur</th><td>Dédié, hébergé</td></tr>
      <tr><th>Langue</th><td>Français</td></tr>
      <tr><th>L'Empire</th><td>Duncan + 2 joueurs (selon les inscrits)</td></tr>
    </tbody></table>
  </div>
  <div class="eyebrow mt">Cahier des charges · 3</div>
  <h2 class="big">Les huit règles d'or</h2>
  <p class="lead">Chaque fonctionnalité de ce dossier est jugée à l'aune de ces règles. En cas de doute, elles tranchent.</p>
  <div class="rules">{rules}</div>

</section>"""


def arch_diagram():
    def box(x, y, w, h, title, lines, color="#9c6b1e", fill="#fbf4e1"):
        t = (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="{fill}" stroke="{color}" stroke-width="2"/>'
             f'<text x="{x+14}" y="{y+26}" class="d-title" fill="{color}">{e(title)}</text>')
        for i, l in enumerate(lines):
            t += f'<text x="{x+14}" y="{y+50+i*19}" class="d-line">{e(l)}</text>'
        return t

    def arrow(x1, y1, x2, y2, label=""):
        t = f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="#7a6a52" stroke-width="2" marker-end="url(#arr)"/>'
        if label:
            t += f'<text x="{(x1+x2)/2+6}" y="{(y1+y2)/2-6}" class="d-lab">{e(label)}</text>'
        return t

    b = ('<defs><marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">'
         '<path d="M0,0 L10,5 L0,10 z" fill="#7a6a52"/></marker></defs>')
    b += box(20, 20, 220, 120, "Joueur", ["Dossier Kodistan (Prism", "portable préconfiguré)", "Compte Microsoft"], "#2f5f73")
    b += box(300, 20, 230, 120, "Pack (packwiz)", ["Liste des mods + configs", "hébergée en ligne", "Synchro à chaque lancement"], "#2f5f73")
    b += box(590, 20, 200, 120, "Sources des mods", ["Modrinth, CurseForge", "Téléchargés à la source"], "#2f5f73")
    b += arrow(240, 80, 298, 80, "lance")
    b += arrow(530, 80, 588, 80, "")
    b += box(20, 200, 360, 250, "Client NeoForge", ["Mods tiers : MineColonies, Create,", "série RPG, Better Combat, Xaero,",
                                                    "Simple Voice Chat, Distant Horizons…", "Mod Kodistan (partie client) :",
                                                    "interfaces, sabliers, notifications", "FancyMenu, écran de chargement",
                                                    "Pack de ressources Kodistan"], "#9c6b1e")
    b += box(430, 200, 360, 250, "Serveur NeoForge", ["Mod Kodistan (partie serveur)", "MineColonies (patché par mixins)",
                                                      "Create, série RPG / Spell Engine", "Simple Voice Chat",
                                                      "Datapacks : recherches, recettes,", "butins, sorts", "Configs rechargeables à chaud"], "#9c6b1e")
    b += arrow(130, 140, 130, 198, "installe")
    b += f'<line x1="380" y1="325" x2="428" y2="325" stroke="#7a6a52" stroke-width="2" marker-end="url(#arr)" marker-start="url(#arr)"/>'
    mods = ["Économie", "Empire", "Colonies", "Routes", "Arène", "Aventure", "Politique", "Admin"]
    b += f'<text x="20" y="500" class="d-title" fill="#8e2a24">Modules du mod Kodistan</text>'
    for i, m in enumerate(mods):
        x = 20 + i * 97
        b += (f'<rect x="{x}" y="515" width="88" height="40" rx="8" fill="#8e2a24" opacity="0.9"/>'
              f'<text x="{x+44}" y="540" text-anchor="middle" class="d-mod">{m}</text>')
    b += box(20, 590, 360, 110, "Outils de création", ["Générateur de structures (Python →", ".nbt / .schem / plans Structurize)", "Blockbench : modèles 3D"], "#5b6f3a")
    b += box(430, 590, 360, 110, "Qualité", ["GameTests NeoForge, serveur de test", "Colonie de test accélérée", "spark : profilage des performances"], "#5b6f3a")
    return svg(810, 720, b, cls="arch")


def page_exig():
    return f"""
<section class="flow">
  <div class="eyebrow">Cahier des charges · 4</div>
  <h2 class="big">Exigences non fonctionnelles</h2>
  <table class="spec wide"><tbody>
    <tr><th>Accès</th><td>Un dossier à dézipper, une connexion Microsoft, un clic. Mises à jour automatiques à chaque lancement.</td></tr>
    <tr><th>Performances</th><td>Serveur fluide (≈ 20 TPS visés, 18 minimum) avec 10 joueurs, Create, MineColonies et les caravanes. Carte prégénérée.</td></tr>
    <tr><th>Fiabilité</th><td>Versions de tous les mods figées avant le lancement. Sauvegardes automatiques quotidiennes. Correctifs déployables sans réinstallation.</td></tr>
    <tr><th>Réglabilité</th><td>Toutes les valeurs d'économie, de PvP et de rythme dans des configs rechargeables à chaud.</td></tr>
    <tr><th>Équité</th><td>Règles publiques. Pouvoirs d'admin séparés des pouvoirs de l'Empire. Pilote automatique en l'absence de l'Empire.</td></tr>
    <tr><th>Charge de l'Empire</th><td>10 à 15 minutes par soir au total pour l'équipe Empire.</td></tr>
    <tr><th>Licences</th><td>Mods téléchargés depuis leurs sources officielles. Visuels originaux ou sous licence libre. Aucun visuel Mojang redistribué.</td></tr>
  </tbody></table>
  <div class="two">
    <div class="box"><h4>{icon("gears", "#9c6b1e", 20)} Le mod Kodistan</h4>
      <p>Écrit en Java pour NeoForge. Il porte toutes les règles du jeu : monnaies, Empire, chantier, routes, arène,
      primes… Il modifie MineColonies de l'intérieur par des <i>mixins</i>, sans le forker.</p></div>
    <div class="box"><h4>{icon("magic-portal", "#9c6b1e", 20)} Le launcher (plan B)</h4>
      <p>Prism Launcher, non modifié, préconfiguré en version portable : instance, thème, fond, raccourci de connexion directe.
      Aucune demande d'autorisation à Microsoft nécessaire.</p></div>
  </div>
</section>"""


def page_arch():
    return f"""
<section class="flow">
  <div class="eyebrow">Cahier des charges · 5</div>
  <h2 class="big">Architecture technique</h2>
  <p class="lead">Un client et un serveur NeoForge, des mods tiers éprouvés, et notre mod Kodistan en chef d'orchestre.
  Les joueurs ne voient qu'un dossier et un bouton « Jouer ».</p>
  <div class="arch-wrap">{arch_diagram()}</div>
</section>"""


def page_method():
    leg_f = "".join(f'<div class="leg">{pips(n)}<span>{FAISA[n]}</span></div>' for n in (1, 2, 3, 4))
    leg_p = "".join(f'<div class="leg">{prio_badge(p)}<span>{e(PRIO[p][1])}</span></div>' for p in PRIO)
    leg_i = "".join(f'<div class="leg">{impact_tag(i)}<span>{d}</span></div>' for i, d in (
        ("Cœur", "Sans elle, le jeu ne tient pas"), ("Fort", "Change nettement l'expérience"),
        ("Moyen", "Un vrai plus"), ("Confort", "Agréable, pas décisif")))
    return f"""
<section class="flow" data-key="feat">
  <div class="eyebrow">Les fonctionnalités · méthode</div>
  <h2 class="big">Comment lire les fiches</h2>
  <p class="lead">Chaque fonctionnalité est notée sur cinq axes. Les estimations sont en <b>sessions</b> :
  environ trois heures de travail en binôme, Duncan teste et décide, Claude code, y compris en autonomie entre deux sessions.</p>
  <div class="legends">
    <div><h4>Faisabilité</h4>{leg_f}</div>
    <div><h4>Impact sur le projet</h4>{leg_i}</div>
    <div><h4>Priorité</h4>{leg_p}</div>
  </div>
  <div class="callout warn">{icon("sands-of-time", "#9c6b1e", 30)}<p><b>Fiabilité des estimations : ±50 %.</b>
  Elles seront recalibrées après la première tranche verticale (Couronnes + Banque impériale, de bout en bout),
  qui mesurera notre vitesse réelle. Le risque indique la probabilité qu'une fonctionnalité réserve de mauvaises surprises.</p></div>
  <div class="totals">
    {''.join(f'<div class="tot"><div class="tot-n">{fmt(S[p])}</div><div class="tot-l">sessions {prio_badge(p)}</div></div>' for p in PRIO)}
    <div class="tot all"><div class="tot-n">{fmt(TOTAL)}</div><div class="tot-l">sessions au total</div></div>
  </div>
</section>"""


def page_matrix():
    rows = []
    for code, name, ico, _ in DOMAINS:
        items = [x for x in F if x["dom"] == code]
        rows.append(f'<tr class="domrow"><td colspan="7">{icon(ico, "#9c6b1e", 16)} {code} · {e(name)}'
                    f'<span class="domsum">{fmt(sum(x["charge"] for x in items))} sessions</span></td></tr>')
        for x in items:
            rows.append(
                f'<tr><td class="id">{x["id"]}</td><td class="nm">{e(x["nom"])}</td>'
                f'<td>{pips(x["faisa"])}</td><td>{risk_tag(x["risque"])}</td><td>{impact_tag(x["impact"])}</td>'
                f'<td class="ch">{charge_bar(x["charge"])}</td><td>{prio_badge(x["prio"])}</td></tr>')
    return f"""
<section class="flow">
  <div class="eyebrow">Les fonctionnalités · vue d'ensemble</div>
  <h2 class="big">La matrice complète</h2>
  <table class="matrix">
    <thead><tr><th>ID</th><th>Fonctionnalité</th><th>Faisabilité</th><th>Risque</th><th>Impact</th><th>Charge</th><th>Priorité</th></tr></thead>
    <tbody>{''.join(rows)}</tbody>
  </table>
</section>"""


def page_details():
    out = []
    for i, (code, name, ico, desc) in enumerate(DOMAINS):
        items = [x for x in F if x["dom"] == code]
        rows = "".join(
            f'<tr><td class="dn"><div class="dn-h"><span class="id">{x["id"]}</span> {e(x["nom"])} {prio_badge(x["prio"])}</div>'
            f'<div class="dn-d">{e(x["desc"])}</div></td>'
            f'<td>{e(x["outils"])}</td><td>{e(x["durs"])}</td><td>{e(x["contournement"])}</td></tr>'
            for x in items)
        brk = ""
        out.append(f"""
  <div class="dom{brk}">
    <div class="dom-h">{icon(ico, "#9c6b1e", 34)}<div><div class="dom-t">{code} · {e(name)}</div><div class="dom-d">{e(desc)}</div></div></div>
    <table class="detail"><thead><tr><th>Fonctionnalité</th><th>Outils</th><th>Points durs</th><th>Contournement</th></tr></thead>
    <tbody>{rows}</tbody></table>
  </div>""")
    return f"""
<section class="flow">
  <div class="eyebrow">Les fonctionnalités · fiches détaillées</div>
  <h2 class="big">Outils, écueils, parades</h2>
  {''.join(out)}
</section>"""


SCEN = [("sam. 19 déc.", 6.7), ("sam. 26 déc.", 7.7), ("sam. 2 janv.", 8.7)]
RATES = [3, 5, 7]


def capacity_chart():
    W, H = 800, 330
    left, top, bar_h, gap = 150, 40, 22, 12
    maxv = 130
    sx = lambda v: left + v / maxv * (W - left - 30)
    b = ""
    # grid
    for v in range(0, maxv + 1, 20):
        b += f'<line x1="{sx(v):.1f}" y1="{top-10}" x2="{sx(v):.1f}" y2="{H-20}" stroke="#d8c79c" stroke-width="1"/>'
        b += f'<text x="{sx(v):.1f}" y="{H-4}" text-anchor="middle" class="c-ax">{v}</text>'
    y = top
    for label, weeks in SCEN:
        b += f'<text x="{left-12}" y="{y+bar_h*1.6}" text-anchor="end" class="c-lab">{label}</text>'
        for r in RATES:
            cap = weeks * r
            b += (f'<rect x="{left}" y="{y}" width="{sx(cap)-left:.1f}" height="{bar_h-6}" rx="3" '
                  f'fill="{ {3: "#c9b27a", 5: "#b8733a", 7: "#7a4a22"}[r] }"/>'
                  f'<text x="{sx(cap)-6:.1f}" y="{y+bar_h-10}" text-anchor="end" class="c-val in">{cap:.0f} · {r}/sem.</text>')
            y += bar_h
        y += gap
    # reference lines
    for v, lab, col in ((S["P0"], f"Socle P0 · {fmt(S['P0'])}", "#8e2a24"),
                        (S["P0"] + S["P1"], f"P0 + P1 · {fmt(S['P0']+S['P1'])}", "#2f5f73"),
                        (TOTAL, f"Tout · {fmt(TOTAL)}", "#5b6f3a")):
        b += (f'<line x1="{sx(v):.1f}" y1="{top-22}" x2="{sx(v):.1f}" y2="{H-20}" stroke="{col}" stroke-width="2.5" stroke-dasharray="6 4"/>'
              f'<text x="{sx(v)-4:.1f}" y="{top-26}" text-anchor="end" class="c-ref" fill="{col}">{lab}</text>')
    return svg(W, H, b, cls="chart")


def page_capacity():
    return f"""
<section class="flow" data-key="plan">
  <div class="eyebrow">Planning · 1</div>
  <h2 class="big">L'ambition face au calendrier</h2>
  <p class="lead">Capacité disponible en sessions, du lundi 3 novembre à la date de lancement, selon le rythme de travail
  (3, 5 ou 7 sessions par semaine). Les lignes pointillées montrent la charge estimée.</p>
  <div class="chart-wrap">{capacity_chart()}</div>
  <div class="verdict">
    <h4>{icon("scales", "#8e2a24", 22)} Le verdict honnête</h4>
    <ul>
      <li><b>L'ambition complète représente environ {fmt(TOTAL)} sessions</b>, soit deux à trois fois la capacité réaliste avant le lancement. C'est normal : c'est un brainstorm, et c'est pour ça qu'on trie.</li>
      <li><b>Le socle P0 ({fmt(S['P0'])} sessions)</b> tient pour un lancement le <b>2 janvier</b> à 5 sessions par semaine. Pour le 19 décembre, il faudrait 7 sessions par semaine ou alléger le socle.</li>
      <li><b>Les P1 ({fmt(S['P1'])} sessions)</b> sont conçues pour arriver <b>pendant</b> la partie, à raison d'une livraison par soir ou presque. Claude code en journée, les joueurs découvrent le soir. Toutes ne tiendront pas : on les livre dans l'ordre du récit (dîme, routes, primes, dragon, révolte, couronnement).</li>
      <li><b>Les P2 et P3</b> n'arrivent que si la vitesse mesurée dépasse les estimations.</li>
    </ul>
    <p class="reco"><b>Recommandation :</b> décider de la date de lancement le <b>16 novembre</b>, après la tranche verticale qui aura mesuré notre vitesse réelle.</p>
  </div>
</section>"""


GANTT = [
    ("Phase 0 · Fondations & tranche verticale", "3 nov.", 0, 2, "#2f5f73", "Version, socle du mod, launcher, tests, Couronnes + Banque"),
    ("Jalon · calibrage de la vitesse", "16 nov.", 1.9, 0.2, "#8e2a24", "Choix de la date de lancement"),
    ("Phase 1 · Systèmes cœur", "17 nov.", 2, 3, "#9c6b1e", "Influence, matériaux, trône, dîme, chantier, refonte MineColonies, règle de mort"),
    ("Phase 2 · Monde & contenu", "8 déc.", 5, 2, "#b8733a", "Citadelle, style pack, arènes, donjons, boutiques, serveur"),
    ("Phase 3 · Tests & finitions", "22 déc.", 7, 1.7, "#5b6f3a", "Sessions de test avec des potes, performances, habillage"),
    ("Lancement · J1", "2 janv.", 8.6, 0.2, "#8e2a24", "Hypothèse de travail"),
    ("Phase 4 · Livraisons pendant la partie", "2 janv.", 8.7, 2, "#54a59a", "Les P1, une par soir, dans l'ordre du récit"),
]
WEEKS = ["3 nov", "10", "17", "24", "1 déc", "8", "15", "22", "29", "5 janv", "12"]


def gantt():
    W, top, rowh, lab = 800, 34, 34, 330
    colw = (W - lab) / len(WEEKS)
    b = ""
    for i, w in enumerate(WEEKS):
        x = lab + i * colw
        b += f'<rect x="{x:.1f}" y="{top-26}" width="{colw:.1f}" height="{len(GANTT)*rowh+30}" fill="{"#efe2bf" if i % 2 else "#f6ecd3"}"/>'
        b += f'<text x="{x+4:.1f}" y="{top-10}" class="g-wk">{w}</text>'
    for r, (name, start, s_, d, col, note) in enumerate(GANTT):
        y = top + r * rowh + 6
        b += f'<text x="0" y="{y+13}" class="g-name" fill="{col}">{e(name)}</text>'
        x, w = lab + s_ * colw, max(d * colw, 8)
        b += f'<rect x="{x:.1f}" y="{y}" width="{w:.1f}" height="18" rx="4" fill="{col}"/>'
    return svg(W, top + len(GANTT) * rowh + 8, b, cls="gantt")


def page_roadmap():
    phases = "".join(
        f'<tr><td><span class="sw" style="background:{c}"></span>{e(n)}</td><td>{e(st)}</td><td>{e(note)}</td></tr>'
        for n, st, s, d, c, note in GANTT)
    return f"""
<section class="flow">
  <div class="eyebrow">Planning · 2</div>
  <h2 class="big">Feuille de route</h2>
  <p class="lead">Hypothèse de travail : lancement le samedi 2 janvier 2027, à environ 5 sessions par semaine.
  Si on vise le 19 ou le 26 décembre, les phases 2 et 3 se compriment et une partie du socle bascule pendant la partie.</p>
  <div class="gantt-wrap">{gantt()}</div>
  <table class="spec wide phases"><thead><tr><th>Phase</th><th>Début</th><th>Contenu</th></tr></thead><tbody>{phases}</tbody></table>
</section>"""


EVENING = [
    ("J1", "Ouverture : colonies et chantier, capitale (étape 1), Couronnes et banque, Influence, arène (3 biomes), 2 donjons."),
    ("J2", "Sabliers de l'Influence, marché des joueurs, LFG."),
    ("J3", "Première dîme. Routes organiques et caravanes ; pillage de caravanes."),
    ("J4", "Bourse aux prix dynamiques, monstres d'élite, donjon 3, arène 4."),
    ("J5", "Primes, duels publics et paris, champion de l'arène et défi de l'Empire."),
    ("J6", "Deuxième dîme. Trésor de colonie et pillage, guerre totale déclarée, arène 5."),
    ("J7", "Dragon de l'Ender, maîtres des compétences et épreuves, donjon 4."),
    ("J8", "Murailles par poteaux, quartiers résidentiels, capitale (étape 2), arène 6."),
    ("J9", "Troisième dîme, lourde. Révolte possible : pacte de rébellion et Étendard."),
    ("J10", "Règles du couronnement dévoilées, donjon 5, arène 7."),
    ("J11", "Capitale (étape 3), arène 8, donjon 6."),
    ("J12", "La Grande Dîme. Les sièges commencent."),
    ("J13", "Dernière nuit avant la fin : tout se joue."),
    ("J14", "Couronnement… ou chute de l'Empire."),
]


def page_evenings():
    rows = "".join(
        f'<div class="ev{" hot" if d in ("J1", "J9", "J12", "J14") else ""}"><div class="ev-d">{d}</div><div class="ev-t">{e(t)}</div></div>'
        for d, t in EVENING)
    return f"""
<section class="flow">
  <div class="eyebrow">Planning · 3</div>
  <h2 class="big">Les quatorze soirées</h2>
  <p class="lead">Le calendrier des livraisons pendant la partie. Les dîmes tombent tous les trois jours (J3, J6, J9, J12) ;
  les fonctionnalités P1 arrivent dans l'ordre du récit. Ce plan s'adaptera à la vitesse réelle.</p>
  <div class="evs">{rows}</div>
</section>"""


def page_risks():
    rows = "".join(
        f'<tr><td class="rk-n">{i+1}</td><td><b>{e(r)}</b></td><td>{risk_tag({"Élevée": "Élevé", "Moyenne": "Moyen", "Faible": "Faible"}[p])}</td>'
        f'<td><span class="tag imp {"core" if g == "Critique" else ("strong" if g == "Fort" else "mid")}">{g}</span></td><td>{e(m)}</td></tr>'
        for i, (r, p, g, m) in enumerate(RISKS))
    return f"""
<section class="flow" data-key="risk">
  <div class="eyebrow">Risques & décisions · 1</div>
  <h2 class="big">Les dix risques majeurs</h2>
  <table class="risks"><thead><tr><th>#</th><th>Risque</th><th>Probabilité</th><th>Gravité</th><th>Parade</th></tr></thead><tbody>{rows}</tbody></table>

  <div class="eyebrow mt">Risques & décisions · 2</div>
  <h2 class="big">Ce qu'il reste à trancher</h2>
  <table class="risks dec"><thead><tr><th>Décision</th><th>Options / état</th><th>À trancher</th></tr></thead><tbody>
  {''.join(f'<tr><td><b>{e(a)}</b></td><td>{e(b)}</td><td>{e(c)}</td></tr>' for a, b, c in DECISIONS)}
  </tbody></table>
</section>"""


NEXT = [
    ("Auditer les versions", "Vérifier ce que supportent MineColonies, Create et la série RPG, puis figer la version."),
    ("Monter le launcher (plan B)", "Prism portable, pack packwiz, test NeoForge et des mods CurseForge."),
    ("Créer le socle du mod Kodistan", "Projet NeoForge, stockage, réseau, configs à chaud, serveur de test."),
    ("Tranche verticale", "Couronnes avec paliers jusqu'au tas d'or et Banque impériale, de bout en bout, testées en jeu."),
    ("Calibrer et décider", "Mesurer la vitesse réelle, recalibrer ce dossier, fixer la date de lancement."),
]


def page_next():
    steps = "".join(
        f'<div class="step"><div class="step-n">{i+1}</div><div><b>{e(t)}</b><span>{e(d)}</span></div></div>'
        for i, (t, d) in enumerate(NEXT))
    return f"""
<section class="flow">
  <div class="eyebrow">Risques & décisions · 3</div>
  <h2 class="big">Les deux prochaines semaines</h2>
  <p class="lead">Le brainstorm est terminé : on passe à la forge. Objectif pour le dimanche 16 novembre :
  un premier morceau du jeu qui marche vraiment, et une date de lancement.</p>
  <div class="steps">{steps}</div>
  <div class="final">{svg(800, 230, defs_glow() + rune_ring(400, 115, 85, color="#9c6b1e", count=18, glyph=14, width=1.2, opacity=0.6, seed=41) + f'<g transform="translate(345,60) scale(0.215)">{icon_paths("crown", "#9c6b1e")}</g>')}
  <p>« Le trône est vide. Plus pour longtemps. »</p></div>
</section>"""


def page_credits():
    authors = sorted({l.split()[1].split("/")[-1] for l in (HERE / "icons" / "credits.txt").read_text().splitlines()})
    names = {"lorc": "Lorc", "delapouite": "Delapouite", "faithtoken": "Faithtoken", "carl-olsen": "Carl Olsen",
             "darkzaitzev": "DarkZaitzev", "sbed": "Sbed", "skoll": "Skoll", "willdabeast": "Willdabeast"}
    return f"""
<section class="flow credits">
  <div class="eyebrow">Annexe</div>
  <h2 class="big">Crédits & licences</h2>
  <table class="spec wide"><tbody>
    <tr><th>Icônes</th><td>game-icons.net, licence CC BY 3.0. Auteurs : {", ".join(names.get(a, a) for a in authors)}. Recolorées pour ce document.</td></tr>
    <tr><th>Polices</th><td>Cinzel, Cinzel Decorative, EB Garamond, Inter, Silkscreen : SIL Open Font License, via Google Fonts.</td></tr>
    <tr><th>Illustrations</th><td>Engrenages, anneaux runiques et blocs en pixel art isométrique : créations originales générées pour ce dossier.</td></tr>
    <tr><th>Mentions</th><td>Minecraft est une marque de Mojang AB. Kodistan est un projet privé entre amis, non officiel, sans lien avec Mojang ou Microsoft.</td></tr>
  </tbody></table>
</section>"""


# ------------------------------------------------------------------ assemble
CSS = (HERE / "style.css").read_text(encoding="utf-8")


def build_html():
    parts = [
        page_cover(), page_toc(),
        page_divider("I", "L'Appel", "Ce qui vous attend cet hiver", "crown", "p1", seed=3),
        page_manifesto(),
        page_cards(CARDS[:4], "Un royaume à prendre", "Bande-annonce · 1"),
        page_cards(CARDS[4:], "Et une couronne à arracher", "Bande-annonce · 2"),
        page_nights(),
        page_divider("II", "Cahier des charges", "Contexte, objectifs, principes, architecture", "scroll-quill", "p2", seed=5),
        page_context(), page_principles(), page_exig(), page_arch(),
        page_divider("III", "Les fonctionnalités", f"{len(F)} fonctionnalités évaluées une par une", "gears", "p3", "#b8733a", seed=7),
        page_method(), page_matrix(), page_details(),
        page_divider("IV", "Planning & priorités", "Charge, capacité, feuille de route", "sands-of-time", "p4", seed=9),
        page_capacity(), page_roadmap(), page_evenings(),
        page_divider("V", "Risques & décisions", "Ce qui peut coincer, ce qu'il reste à trancher", "skull-crossed-bones", "p5", "#b8733a", seed=11),
        page_risks(), page_next(), page_credits(),
    ]
    return f"""<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>Kodistan 2026 · Dossier de projet</title>
<style>{CSS}</style></head><body>{''.join(parts)}</body></html>"""


def render():
    OUT_HTML.write_text(build_html(), encoding="utf-8")
    OUT_PDF.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    "--run-all-compositor-stages-before-draw", "--virtual-time-budget=10000",
                    f"--print-to-pdf={OUT_PDF}", OUT_HTML.as_uri()], check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def find_pages():
    from pypdf import PdfReader
    reader = PdfReader(str(OUT_PDF))
    found = {}
    for i, page in enumerate(reader.pages):
        txt = (page.extract_text() or "").replace("\n", "").replace(" ", "")
        for k in ("p1", "p2", "p3", "p4", "p5"):
            if f"KDX{k}KDX" in txt:
                found[k] = i + 1
    return found, len(reader.pages)


if __name__ == "__main__":
    render()
    found, n = find_pages()
    if found != pages:
        PAGES_JSON.write_text(json.dumps(found))
        pages = found
        render()
    print("pages:", n, found, "P:", S, "total:", TOTAL)
