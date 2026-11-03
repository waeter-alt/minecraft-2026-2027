"""Generate French names and Minecraft icons for every non-hut blueprint base name."""
import re, sys, json, collections

# word: (kind, french, gender)   kind N noun, A adjective (m, f forms "x|y"), M material (complement), P position (complement), X skip
W = {}
def n(words, fr, g='m'):
    for w in words.split(): W[w] = ('N', fr, g)
def a(words, fr):
    for w in words.split(): W[w] = ('A', fr, None)
def m(words, fr):
    for w in words.split(): W[w] = ('M', fr, None)
def p(words, fr):
    for w in words.split(): W[w] = ('P', fr, None)
def x(words):
    for w in words.split(): W[w] = ('X', '', None)

# Nouns
n('road roads', 'route', 'f'); n('street streets', 'rue', 'f'); n('path paths footpath', 'chemin'); n('walk walkway', 'allée', 'f')
n('rail rails railroad railway track', 'voie ferrée', 'f'); n('birail', 'double voie', 'f'); n('minirail', 'mini-voie', 'f')
n('subway', 'métro'); n('tunnel', 'tunnel'); n('bridge', 'pont'); n('stairs stair steps staircase', 'escalier'); n('step stepup', 'marche', 'f')
n('ramp slope incline', 'rampe', 'f'); n('intersection intersect crossing cross junction', 'carrefour'); n('fork split', 'embranchement')
n('tjunction tjumction tee tcorner', 'carrefour en T'); n('corner', 'angle'); n('turn curve', 'virage'); n('end endcap', 'extrémité', 'f')
n('beginning start', 'départ'); n('entrance entry', 'entrée', 'f'); n('exit', 'sortie', 'f'); n('connector', 'raccord'); n('plug', 'bouchon')
n('segment section piece', 'tronçon'); n('straight', 'ligne droite', 'f'); n('station', 'gare', 'f'); n('platform platforms', 'plateforme', 'f')
n('wall walls', 'mur'); n('palisade', 'palissade', 'f'); n('fence fences', 'clôture', 'f'); n('gate gates gatehouse', 'porte', 'f')
n('tower', 'tour', 'f'); n('bumpout', 'avancée', 'f'); n('moat', 'douve', 'f'); n('battlement', 'créneau'); n('pillar pillars column', 'pilier')
n('span', 'travée', 'f'); n('arch', 'arche', 'f'); n('aqueduct', 'aqueduc'); n('canal', 'canal'); n('lock', 'écluse', 'f'); n('sewage sewer', 'égout')
n('field fields', 'champ'); n('farm', 'ferme', 'f'); n('garden gardens', 'jardin'); n('greenhouse', 'serre', 'f'); n('park parks', 'parc')
n('playground', 'aire de jeux', 'f'); n('fountain', 'fontaine', 'f'); n('statue statues', 'statue', 'f'); n('pedestal pedastal', 'piédestal')
n('bench benches', 'banc'); n('seating', 'coin repos'); n('area', 'espace'); n('square', 'place', 'f'); n('market markets marketsquare', 'marché')
n('minimarket', 'échoppe', 'f'); n('stall', 'étal'); n('cart', 'charrette', 'f'); n('wagon coveredwagon', 'chariot'); n('tent', 'tente', 'f')
n('camp encampment', 'campement'); n('lantern lanterns', 'lanterne', 'f'); n('lamp', 'lampe', 'f'); n('light lights', 'lumière', 'f'); n('torch', 'torche', 'f')
n('ship ships', 'navire'); n('boat', 'bateau'); n('trireme', 'trirème', 'f'); n('galleon', 'galion'); n('buoy', 'bouée', 'f'); n('harbor harbour port', 'port')
n('dock pier jetty', 'quai'); n('crane', 'grue', 'f'); n('cargo', 'cargaison', 'f'); n('construction', 'chantier naval'); n('wreck', 'épave', 'f')
n('house home', 'maison', 'f'); n('mansion', 'manoir'); n('estate', 'domaine'); n('cottage', 'chaumière', 'f'); n('hut', 'cabane', 'f'); n('shed', 'remise', 'f')
n('hall megahall halls', 'grande salle', 'f'); n('temple', 'temple'); n('shrine', 'sanctuaire'); n('grave graves', 'tombe', 'f'); n('tomb', 'tombeau')
n('well', 'puits'); n('tree trees', 'arbre'); n('megatree', 'arbre géant'); n('bush bushes', 'buisson'); n('flower flowers flowergarden', 'parterre de fleurs')
n('hedge', 'haie', 'f'); n('pond', 'mare', 'f'); n('lake', 'lac'); n('waterfall', 'cascade', 'f'); n('lavafall', 'cascade de lave', 'f')
n('rock rocks boulder', 'rocher'); n('cliff', 'falaise', 'f'); n('landing', 'palier'); n('climb', 'montée', 'f'); n('mine mineshaft mineshafts shaft', 'puits de mine')
n('quarry', 'carrière', 'f'); n('cap', 'couvercle'); n('fill filler', 'remblai'); n('foundation', 'fondation', 'f'); n('base', 'base', 'f')
n('storage', 'entrepôt'); n('barrel barrels', 'tonneau'); n('crate crates', 'caisse', 'f'); n('chest', 'coffre'); n('supply supplies supplyship', 'ravitaillement')
n('banner', 'bannière', 'f'); n('flag', 'drapeau'); n('sign', 'panneau'); n('medal', 'médaille', 'f'); n('waystone', 'pierre de passage', 'f')
n('orb', 'orbe'); n('unit', 'unité', 'f'); n('cultivation', 'culture', 'f'); n('plants plant', 'plantes', 'f'); n('vine vines', 'lianes', 'f')
n('kelp', 'varech'); n('kelpfarm', 'ferme à varech', 'f'); n('treefarm', 'ferme forestière', 'f'); n('mushroomfarm', 'champignonnière', 'f')
n('bamboo', 'bambou'); n('sugar sugarcane', 'canne à sucre', 'f'); n('cocoa', 'cacao'); n('cactus', 'cactus'); n('wheat', 'blé'); n('pumpkin', 'citrouille', 'f')
n('melon', 'pastèque', 'f'); n('carrot carrots', 'carotte', 'f'); n('potato potatoes', 'pomme de terre', 'f'); n('beetroot', 'betterave', 'f')
n('fungus', 'champignon du Nether'); n('nether', 'Nether');
W['fungus'] = ('N', 'champignon', 'm'); n('sea', 'mer', 'f'); n('beach', 'plage', 'f'); n('roof', 'toit')
n('facade facades', 'façade', 'f'); n('panel', 'panneau'); n('window', 'fenêtre', 'f'); n('door', 'porte', 'f'); n('chair', 'fauteuil'); n('staff', 'bâton')
n('sword wallsword', 'épée', 'f'); n('lion', 'lion'); n('buddha', 'bouddha'); n('dragon', 'dragon'); n('knight', 'chevalier'); n('soldier', 'soldat')
n('yinyang', 'yin-yang'); n('kongming', 'lanterne céleste', 'f'); n('patrol patrolpoint', 'point de patrouille'); n('outpost', 'avant-poste')
n('workshop', 'atelier'); n('mill', 'moulin'); n('windmill', 'moulin à vent'); n('watermill', 'moulin à eau'); n('bakery', 'boulangerie', 'f')
n('restaurant', 'restaurant'); n('tavern inn', 'auberge', 'f'); n('residence', 'résidence', 'f'); n('playermansion', 'manoir du seigneur')
n('highlander', 'maison des Highlands', 'f'); n('townhall', 'hôtel de ville'); n('courier courieroffices ccourier', 'relais des coursiers')
n('carpenter', 'menuiserie', 'f'); n('warehouse', 'entrepôt'); n('composthut', 'cabane à compost', 'f'); n('cowhand', 'étable', 'f'); n('mine', 'mine', 'f')
n('farmerhall', 'salle des fermiers', 'f'); n('forestryhall', 'salle des forestiers', 'f'); n('herderhall', 'salle des éleveurs', 'f')
n('geology', 'salle de géologie', 'f'); n('miner', 'mineur'); n('guard', 'garde'); n('barrackstower', 'tour de caserne', 'f')
n('walltower', 'tour de rempart', 'f'); n('centertower', 'tour centrale', 'f'); n('cornerguard', "poste d'angle")
n('bag', 'cul-de-sac'); n('sunk wreckage', 'épave', 'f'); n('lane', 'ruelle', 'f'); n('alley', 'ruelle', 'f'); n('plaza', 'place', 'f')
n('hatch', 'trappe', 'f'); n('chunk halfchunk', 'demi-chunk'); n('illumination', 'éclairage'); n('fruit', 'fruit'); n('niflheim', 'Niflheim')
n('atlas', 'Atlas'); n('zeus', 'Zeus'); n('trap', 'piège'); n('pit', 'fosse', 'f'); n('arena', 'arène', 'f'); n('ruin ruins', 'ruine', 'f')
n('stable stables', 'écurie', 'f'); n('dome', 'dôme'); n('gazebo', 'kiosque'); n('pavilion', 'pavillon'); n('altar', 'autel'); n('obelisk', 'obélisque')
n('canalintersection', 'carrefour de canaux'); n('corridor', 'couloir'); n('wagonpark', 'parc à chariots')
n('surface', 'surface', 'f'); n('level groundlevel', 'niveau'); n('return', 'retour'); n('loop', 'boucle', 'f'); n('switch', 'aiguillage')
n('stilts', 'pilotis'); n('inner', 'intérieur'); n('outside outer', 'extérieur'); n('mid middle', 'milieu'); n('center centre', 'centre')
n('lower', 'partie basse', 'f'); n('upper', 'partie haute', 'f'); n('top', 'sommet'); n('bottom', 'bas'); n('side', 'côté')
n('diagonal', 'diagonale', 'f'); n('vertical', 'verticale', 'f'); n('panel', 'panneau'); n('bricks', 'briques', 'f')
# Adjectives (m|f)
a('large big huge grand mega', 'grand|grande'); a('small little tiny mini', 'petit|petite'); a('medium', 'moyen|moyenne')
a('long', 'long|longue'); a('short', 'court|courte'); a('wide', 'large|large'); a('narrow', 'étroit|étroite'); a('double dual doublec', 'double|double')
a('single singleb singleblock', 'simple|simple'); a('simple basic plain poor', 'simple|simple'); a('fancy nice fancyeven decorative', 'orné|ornée')
a('advanced premium', 'élaboré|élaborée'); a('covered roofed', 'couvert|couverte'); a('raised', 'surélevé|surélevée'); a('sloped steep', 'en pente|en pente')
a('curved', 'courbe|courbe'); a('powered', 'alimenté|alimentée'); a('rural', 'rural|rurale'); a('urban', 'urbain|urbaine'); a('military', 'militaire|militaire')
a('pirate', 'pirate|pirate'); a('docked', 'à quai|à quai'); a('honored', "d'honneur|d'honneur"); a('ancient old', 'ancien|ancienne'); a('new', 'neuf|neuve')
a('parallel', 'parallèle|parallèle'); a('even', 'pair|paire'); a('odd', 'impair|impaire'); a('integrated', 'intégré|intégrée'); a('scorched', 'brûlé|brûlée')
a('underground', 'souterrain|souterraine'); a('overground aboveground', 'en surface|en surface'); a('weeping', 'pleureur|pleureuses')
a('ivycovered ivy', 'envahi de lierre|envahie de lierre'); a('threelong', 'triple|triple'); a('starter', 'de départ|de départ')
a('threeway', 'à trois voies|à trois voies'); a('flat', 'plat|plate'); a('high', 'haut|haute'); a('low', 'bas|basse'); a('deep', 'profond|profonde')
a('green', 'vert|verte'); a('red', 'rouge|rouge'); a('blue', 'bleu|bleue'); a('cyan', 'cyan|cyan'); a('lightgray', 'gris clair|gris clair'); a('gray grey', 'gris|grise')
a('white', 'blanc|blanche'); a('black', 'noir|noire'); a('yellow', 'jaune|jaune'); a('bw', 'noir et blanc|noire et blanche'); a('ry', 'rouge et jaune|rouge et jaune')
a('crimson', 'pourpre|pourpre'); a('magma', 'de magma|de magma'); a('smoked kelpsmoked', 'fumé|fumée'); a('hill curvedhill', 'en colline|en colline')
# Materials
m('stone', 'en pierre'); m('cobble cobblestone', 'en pierres'); m('brick bricks', 'en briques'); m('wood wooden timber', 'en bois'); m('oak', 'en chêne')
m('spruce', 'en sapin'); m('birch', 'en bouleau'); m('jungle', 'en bois exotique'); m('acacia', "en acacia"); m('darkoak', 'en chêne noir')
m('sandstone sand', 'en grès'); m('andesite', 'en andésite'); m('diorite', 'en diorite'); m('granite', 'en granit'); m('deepslate', 'en ardoise des abîmes')
m('clay terracotta', 'en argile'); m('concrete', 'en béton'); m('redconcrete', 'en béton rouge'); m('iron', 'en fer'); m('gold golden', 'en or'); m('glass', 'en verre')
m('quartz', 'en quartz'); m('prismarine', 'en prismarine'); m('blackstone', 'en roche noire'); m('mud', 'en terre crue'); m('dirt', 'en terre'); m('gravel', 'en gravier')
m('lionstone', 'en pierre'); m('liondiorite', 'au lion en diorite'); m('water', "d'eau"); m('lava', 'de lave'); m('ice', 'de glace')
# Positions / complements
p('left', 'gauche'); p('right', 'droite'); p('half', '(demi)'); p('dual', 'double')
# Skips
x('the of and a an to with in on by alt')
# EXTRA-WORDS

n('support supports', 'support'); n('footing', 'semelle', 'f'); n('shipw', 'épave', 'f'); n('cacao', 'cacao'); n('brandybuck', 'Brandebouc')
n('bas', 'base', 'f'); n('fancyt', 'carrefour orné en T')
W['one'] = ('X', '', None); W['two'] = ('A', 'double|double', None)
n('post', 'poste'); n('grass', 'herbe', 'f'); n('pickles pickle', 'cornichons de mer'); n('facility', 'installation', 'f'); n('fisher fisherman fisherm', 'pêcherie', 'f')
n('meat', 'viande', 'f'); n('norsemen', 'Nordiques'); n('ground', 'sol'); n('jog', 'décrochement'); n('crease', 'pli'); n('growth', 'pousse', 'f')
n('apiary apiaryopenair', 'rucher'); n('pot potted', 'pot'); n('drop', 'chute', 'f'); n('bireme', 'birème', 'f'); n('player', 'joueur'); n('log', 'tronc')
n('city', 'cité', 'f'); n('cage', 'cage', 'f'); n('poseidon', 'Poséidon'); n('barracks', 'caserne', 'f'); n('leaves', 'feuillage'); n('forester', 'forestier')
n('cavern', 'caverne', 'f'); n('plot', 'parcelle', 'f'); n('desert', 'désert'); n('safety', 'sécurité', 'f'); n('ladder ladders', 'échelle', 'f')
n('uturn', 'demi-tour'); n('barbican', 'barbacane', 'f'); n('glowberries glowberry', 'baies lumineuses', 'f'); n('crops crop', 'cultures', 'f')
n('fungi', 'champignons'); n('cane', 'canne', 'f'); n('vegetation', 'végétation', 'f'); n('hop hops', 'houblon'); n('shepherd', 'bergerie', 'f')
n('swineherd', 'porcherie', 'f'); n('alchemist', 'alchimiste'); n('cookery', 'cuisine', 'f'); n('dyer', 'teinturerie', 'f'); n('flour flourmill', 'minoterie', 'f')
n('offices office', 'bureau'); n('deliveryman', 'coursier'); n('cloth', 'tissu'); n('dye', 'teinture', 'f'); n('fish', 'poisson'); n('food', 'nourriture', 'f')
n('greens', 'verdure', 'f'); n('melons', 'pastèques', 'f'); n('pagodas pagoda', 'pagode', 'f'); n('swords', 'épées', 'f'); n('falls', 'chutes', 'f'); n('empty', 'emplacement vide')
n('bath bathhouse', 'bains'); n('group', 'groupe'); n('purification', 'purification', 'f'); n('wisteria', 'glycine', 'f'); n('claw', 'griffe', 'f')
n('shipwreck', 'épave', 'f'); n('aquarium fullaquarium', 'aquarium'); n('courtyard', 'cour', 'f'); n('maingate', 'porte principale', 'f')
n('cafe', 'café'); n('concert', 'scène de concert', 'f'); n('kennel', 'chenil'); n('gallows', 'potence', 'f'); n('headsman', 'billot du bourreau')
n('hole', 'trou'); n('combo', 'ensemble'); n('mausoleum', 'mausolée'); n('way', 'voie', 'f'); n('pharao pharaoh', 'pharaon'); n('pillory', 'pilori')
n('carriage', 'carrosse'); n('town towncenter', 'centre-ville'); n('war', 'guerre', 'f'); n('maze', 'labyrinthe'); n('rink', 'patinoire', 'f'); n('oasis', 'oasis', 'f')
n('playset', 'jeux'); n('rest', 'repos'); n('volleyballcourt volleyball', 'terrain de volley'); n('hay', 'foin'); n('dwarfking', 'roi nain')
n('horse', 'cheval'); n('arms', 'armes', 'f'); n('present', 'cadeau'); n('shield', 'bouclier'); n('weight', 'poids'); n('smelter', 'fonderie', 'f')
n('machine', 'machine', 'f'); n('grinder', 'hachoir'); n('seat', 'siège'); n('library', 'bibliothèque', 'f'); n('builder', 'bâtisseur'); n('citizen', 'citoyen')
n('grachtenpand', 'maison de canal', 'f'); n('villa', 'villa', 'f'); n('perpendicular', 'perpendiculaire', 'f'); n('nature', 'nature', 'f'); n('head', 'tête', 'f')
n('foot', 'pied'); n('insert', 'insert'); n('quadfieldinsert', 'insert de quatre champs'); n('safetystairinsert', 'insert d’escalier de sécurité'); n('hive', 'ruche', 'f')
n('scholars', 'savants'); n('dig', 'excavation', 'f'); n('waypoint', 'balise', 'f'); n('spiral', 'colimaçon'); n('stop', 'arrêt'); n('trail', 'piste', 'f')
n('batpole', 'perche'); n('hospital', 'hôpital'); n('enchanters', 'enchanteurs'); n('mountdoom', 'montagne du Destin', 'f'); n('drawbridge', 'pont-levis')
n('defense', 'défense', 'f'); n('redoubt', 'redoute', 'f'); n('mangrove', 'palétuvier'); n('sitting', 'assise', 'f'); n('noble', 'noble'); n('imperial', 'impérial')
n('kitchen', 'cuisine', 'f'); n('skadi', 'Skadi'); n('blk block', 'bloc'); n('place', 'place', 'f'); n('mystic', 'site mystique'); n('zen', 'zen')
n('auto', 'automatique'); n('fortress', 'forteresse', 'f'); n('tunnelt', 'tunnel en T'); n('rcorner', 'angle droit'); n('groundenter', 'entrée au sol', 'f')
a('two twob twowide twosquared', 'double|double'); a('three threeb', 'triple|triple'); a('quad fourway', 'quadruple|quadruple'); a('twoway', 'à deux voies|à deux voies')
a('warped', 'biscornu|biscornue'); a('glow glowing', 'lumineux|lumineuse'); a('purple', 'violet|violette'); a('beige', 'beige|beige'); a('orange', 'orange|orange')
a('brown', 'brun|brune'); a('lime', 'vert clair|vert clair'); a('magenta', 'magenta|magenta'); a('pink', 'rose|rose'); a('mossy', 'moussu|moussue')
a('round', 'rond|ronde'); a('tall', 'haut|haute'); a('full', 'complet|complète'); a('solid', 'plein|pleine'); a('open', 'ouvert|ouverte')
a('visible', 'visible|visible'); a('floating float', 'flottant|flottante'); a('aquatic', 'aquatique|aquatique'); a('warm', 'chaud|chaude')
a('twisted twist', 'tordu|tordue'); a('fungal', 'fongique|fongique'); a('general', 'général|générale'); a('luxury', 'de luxe|de luxe'); a('tasty', 'gourmand|gourmande')
a('inverted', 'inversé|inversée'); a('uni', 'uni|unie'); a('both', 'double|double'); a('alternative', 'alternatif|alternative'); a('ushaped', 'en U|en U')
a('med', 'moyen|moyenne'); a('lrg', 'grand|grande'); a('sml', 'petit|petite'); a('hot', 'brûlant|brûlante'); a('up', 'montant|montante'); a('down', 'descendant|descendante')
a('out', 'extérieur|extérieure'); a('off', 'arrêté|arrêtée'); a('trans', 'de transition|de transition')
x('un al ma for est yr ian rx sup gra sert apia potte libra sitt dwarfk headsm luxu hosp vegetati cact glowber cooke fices deliverym purificati pillo grachtenp ernative oubt draw verted wn s')

ICONS = [  # (keyword in name, item) first match wins
 ('apiary','beehive'),('hive','beehive'),('fisher','fishing_rod'),('shepherd','white_wool'),('swineherd','porkchop'),('sewer','iron_bars'),('cookery','cooked_beef'),
 ('dyer','red_dye'),('flour','wheat'),('corridorgreens','fern'),('corridormelons','melon_slice'),('corridorpagodas','spruce_planks'),('empty','structure_void'),
 ('shrine','candle'),('well','water_bucket'),('windmill','white_wool'),('wisteria','purple_dye'),('aquarium','tropical_fish_bucket'),('cafe','cake'),('concert','note_block'),
 ('kennel','bone'),('gallows','chain'),('headsman','iron_axe'),('cage','iron_bars'),('mausoleum','skeleton_skull'),('pharao','gold_block'),('pillory','oak_fence'),
 ('carriage','minecart'),('stable','saddle'),('towncenter','bell'),('citysquare','bell'),('plaza','polished_diorite'),('retaining','stone_bricks'),('knight','iron_helmet'),
 ('dwarfking','golden_helmet'),('smelter','blast_furnace'),('concrete_machine','white_concrete_powder'),('grinder','grindstone'),('libraryalt','bookshelf'),
 ('builderalt','iron_shovel'),('citizen','red_bed'),('forester','oak_sapling'),('grachtenpand','oak_door'),('hillside','oak_door'),('tavern','honey_bottle'),('villa','oak_door'),
 ('station','powered_rail'),('plub','rail'),('floating','oak_boat'),('ladder','ladder'),('dig','iron_shovel'),('waypoint','lodestone'),
 ('mountdoom','magma_block'),
 
 ('mangrove','mangrove_log'),
 ('bamboo','bamboo'),('sugar','sugar_cane'),('cocoa','cocoa_beans'),('cactus','cactus'),('weeping','weeping_vines'),('crimson','crimson_fungus'),('fungus','warped_fungus'),
 ('vine','vine'),('kelp','kelp'),('sea_plant','seagrass'),('seaplant','seagrass'),('orb','glow_berries'),('fruit','glow_berries'),('mushroom','red_mushroom'),
 ('wheat','wheat'),('treefarm','oak_sapling'),('megatree','oak_sapling'),('tree','oak_sapling'),('bush','sweet_berries'),('flower','poppy'),('garden','poppy'),('greenhouse','glass'),
 ('field','wheat_seeds'),('farm','wooden_hoe'),('nether','netherrack'),('subway','powered_rail'),('powered','powered_rail'),('rail','rail'),('track','rail'),
 ('canal','water_bucket'),('lock','water_bucket'),('aqueduct','water_bucket'),('fountain','water_bucket'),('waterfall','water_bucket'),('lavafall','lava_bucket'),
 ('sewage','cauldron'),('bridge','oak_planks'),('stair','stone_brick_stairs'),('step','stone_brick_stairs'),('tunnel','stone_bricks'),('mine','iron_pickaxe'),
 ('quarry','iron_pickaxe'),('road','dirt_path'),('street','dirt_path'),('path','dirt_path'),('walk','dirt_path'),('intersection','dirt_path'),('junction','dirt_path'),
 ('moat','water_bucket'),('palisade','spruce_log'),('fence','oak_fence'),('gate','oak_fence_gate'),('tower','stone_brick_wall'),('wall','cobblestone_wall'),
 ('ship','oak_boat'),('trireme','oak_boat'),('boat','oak_boat'),('buoy','lantern'),('crane','chain'),('cargo','barrel'),('dock','oak_planks'),('harbor','oak_boat'),
 ('lantern','lantern'),('kongming','lantern'),('lamp','lantern'),('light','torch'),('illumination','torch'),('statue','armor_stand'),('buddha','armor_stand'),
 ('lion','armor_stand'),('pedestal','chiseled_stone_bricks'),('pedastal','chiseled_stone_bricks'),('medal','gold_ingot'),('sword','iron_sword'),('grave','skeleton_skull'),
 ('temple','chiseled_quartz_block'),('market','emerald'),('stall','emerald'),('cart','minecart'),('wagon','minecart'),('camp','white_wool'),('tent','white_wool'),
 ('bench','oak_stairs'),('seating','oak_stairs'),('chair','oak_stairs'),('park','grass_block'),('playground','slime_ball'),('platform','smooth_stone_slab'),
 ('pillar','quartz_pillar'),('storage','chest'),('supply','barrel'),('waystone','lodestone'),('yinyang','ender_eye'),('house','oak_door'),('home','oak_door'),
 ('mansion','oak_door'),('estate','oak_door'),('residence','red_bed'),('bakery','bread'),('restaurant','cooked_beef'),('courier','leather_boots'),('warehouse','chest'),
 ('carpenter','crafting_table'),('compost','composter'),('cowhand','wheat'),('hall','bell'),('geology','iron_ore'),('guard','iron_sword'),('patrol','iron_sword'),
 ('bag_end','oak_door'),('facade','stone_bricks'),('panel','stone_bricks'),('plug','stone_bricks'),('fill','dirt'),('cliff','stone'),('beach','sand'),
 ('playermansion','oak_door'),('highlander','oak_door'),('workshop','crafting_table'),('hatch','oak_trapdoor'),('chunk','grass_block'),('down','ladder'),
 ('turn','dirt_path'), ('trans','dirt_path'), ('straight','dirt_path'), ('connector','stone_bricks'), ('upper','stone_bricks'), ('lower','stone_bricks'), ('head','oak_planks'), ('threelong','oak_door'), ('twosquared','oak_door'), ('twowide','oak_door'), ('cornerinside','oak_door'), ('jungle','jungle_log'), ('tiny','poppy'), ('pagoda','spruce_planks'),
]

def segment(word):
    best = [(0, [])] + [None] * len(word)
    for i in range(1, len(word) + 1):
        cands = []
        for j in range(max(0, i - 20), i):
            if best[j] is None: continue
            piece = word[j:i]
            if piece in W:
                cost = best[j][0] + 1
            elif re.fullmatch(r'\d+', piece):
                cost = best[j][0] + 1
            elif len(piece) == 1 and i == len(word) and piece in 'abcdefghlmp':
                cost = best[j][0] + 1
            elif len(piece) >= 2:
                cost = best[j][0] + 50 + len(piece)
            else:
                continue
            cands.append((cost, best[j][1] + [piece]))
        best[i] = min(cands, key=lambda c: c[0]) if cands else None
    return best[len(word)][1] if best[len(word)] else None

UNKNOWN = collections.Counter()
PRENOMINAL = {'grand', 'petit', 'moyen', 'long', 'double', 'triple', 'quadruple', 'haut', 'ancien', 'neuf'}

def french(base, folder=''):
    tokens = []
    for part in re.split(r'[_\-\s]+', base):
        if not part: continue
        seg = segment(part)
        if seg is None: return None, part
        tokens += seg
    nouns, adjs, mats, poss, extras = [], [], [], [], []
    for t in tokens:
        if t in W:
            k, fr, g = W[t]
            {'N': nouns, 'A': adjs, 'M': mats, 'P': poss, 'X': extras}[k].append((fr, g))
        elif re.fullmatch(r'\d+', t):
            extras.append((t, None))
        elif len(t) == 1:
            extras.append((t.upper(), None))
        else:
            UNKNOWN[t] += 1
    if not nouns:
        for f in folder.split(','):
            last = f.split('/')[-1]
            seg = segment(last.rstrip('s')) or segment(last) or []
            found = [W[t] for t in seg if t in W and W[t][0] == 'N']
            if found:
                nouns = [(found[-1][1], found[-1][2])]
                break
    if not nouns:
        nouns = [('élément', 'm')]
    head, g = nouns[-1]
    pre, post = [], []
    for fr, _ in adjs:
        mf = fr.split('|'); word = mf[1] if g == 'f' and len(mf) > 1 else mf[0]
        (pre if mf[0] in PRENOMINAL else post).append(word)
    out = ' '.join(dict.fromkeys(pre + [head] + post))
    for fr, _ in reversed(nouns[:-1]):
        if fr != head:
            out += (" d'" if fr[0] in 'aeiouéèêh' else ' de ') + fr
    for fr, _ in mats: out += ' ' + fr
    for fr, _ in poss: out += ' ' + fr
    letters = [e for e, _ in extras if e and not e.isdigit() and len(e) == 1]
    digits = [e for e, _ in extras if e.isdigit()]
    if digits: out += ' ' + '×'.join(digits)
    if letters: out += ' ' + ''.join(letters)
    out = re.sub(r'\s+', ' ', out).strip()
    return out[0].upper() + out[1:], None

OVERRIDES = {
 'stall_meat_one': 'Étal de boucher', 'stall_meat_two': 'Grand étal de boucher', 'stall_fish': 'Étal de poissonnier',
 'bag_end': 'Cul-de-Sac (trou de hobbit)', 'temple_of_zeus': 'Temple de Zeus', 'playermansion': 'Manoir du seigneur',
 'brandybuck_cafe': 'Café Brandebouc', 'mountdoom': 'Montagne du Destin', 'yinyang_bw': 'Yin-yang noir et blanc',
 'illumination_orb_fruit_cultivation_unit': 'Serre à baies lumineuses', 'niflheim_crimson_vine': 'Lianes pourpres de Niflheim',
 '1by1flowergarden': 'Parterre de fleurs 1×1', 'kongming_blue': 'Lanterne céleste bleue', 'kongming_red': 'Lanterne céleste rouge',
 'war_encampment': 'Campement de guerre', 'small_wheat_cart': 'Petite charrette de blé', 'citizenalt': 'Maison de citoyen',
 'meat_grinder': 'Hachoir à viande', 'auto_smelter': 'Fonderie automatique', 'concrete_machine': 'Bétonnière',
 'headsman_post': 'Billot du bourreau', 'hot_cage': 'Cage ardente', 'wooden_cage': 'Cage en bois', 'pharao': 'Statue de pharaon',
 'dwarfking': 'Statue du roi nain', 'cornerknight': "Chevalier d'angle", 'purification_shrine': 'Sanctuaire de purification',
 'grachtenpand': 'Maison de canal', 'sitting_plaza': 'Place assise', 'citysquare': 'Grand-place', 'towncenter': 'Centre-ville',
 'apiaryopenair': 'Rucher en plein air', 'cornerkennel': "Chenil d'angle", 'roofed_carriage': 'Carrosse couvert', 'concert': 'Scène de concert',
}

def icon(base, folder):
    b = base.replace('-', '_')
    for key, item in ICONS:
        if key in b: return item
    f = folder
    for key, item in (('lanterns','lantern'),('plaza','polished_diorite'),('retaining','stone_bricks'),('fields','wheat_seeds'),('rails','rail'),('roads','dirt_path'),('walls','cobblestone_wall'),('harbor','oak_boat'),('ships','oak_boat'),
                      ('statues','armor_stand'),('parks','grass_block'),('markets','emerald'),('bridges','oak_planks'),('canal','water_bucket'),('mineshafts','iron_pickaxe'),
                      ('platforms','smooth_stone_slab'),('gates','oak_fence_gate'),('facades','stone_bricks'),('storage','chest'),('megahalls','bell'),('sewage','cauldron')):
        if key in f: return item
    return None

if __name__ == '__main__':
    rows = [l.rstrip('\n').split('\t') for l in open(sys.argv[1], encoding='utf-8')]
    unknown = collections.Counter(); missing_icon = []
    result = {}
    for base, folders in rows:
        fr, bad = (OVERRIDES[base], None) if base in OVERRIDES else french(base, folders)
        if fr and 'fields' in folders and not any(w in fr.lower() for w in ('champ', 'ferme', 'serre', 'installation', 'parcelle', 'jardin')):
            fr = 'Champ de ' + fr[0].lower() + fr[1:]
        if fr is None:
            unknown[bad] += 1; continue
        ic = icon(base, folders)
        if ic is None: missing_icon.append((base, folders))
        result[base] = {'name': fr, 'icon': ic or ''}
    if len(sys.argv) > 2:
        json.dump(result, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1, sort_keys=True)
    print('ok', len(result), 'unknown', len(unknown), 'noicon', len(missing_icon))
    print(' '.join(f'{k}' for k, _ in UNKNOWN.most_common(400)))
    print('NOICON', ' '.join(b + '@' + f for b, f in missing_icon[:150]))
