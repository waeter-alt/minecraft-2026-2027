# [claude-build] Etape 2 : pose le hub et les 4 arenes, ouvre les portes tournees vers le hub, ferme les autres portes du Feu.
place template atelier:hub -114 78 -114
place template atelier:forge_v2 -126 74 114
place template atelier:arene_air 114 74 -96
place template atelier:arene_nature -366 68 -126
place template atelier:arene_glace -126 68 -366
# Air (porte ouest) : le verre disparait
fill 114 87 -5 117 92 4 air
# Nature (porte est) : les racines s'ecartent
fill -117 87 -5 -115 95 4 air replace minecraft:mangrove_roots
# Glace (porte sud) : herse levee, bouchon de glace retire
fill -5 87 -117 4 95 -115 air
# Feu : portes est, sud et ouest fermees par des barreaux
fill 122 87 236 123 95 243 minecraft:iron_bars replace minecraft:air
fill -124 87 236 -123 95 243 minecraft:iron_bars replace minecraft:air
fill -4 87 362 3 95 363 minecraft:iron_bars replace minecraft:air
setworldspawn 0 87 20
tp @a 0 87 20
forceload remove all
tellraw @a {"text":"Le Carrefour des Elements est pret !","color":"green"}
