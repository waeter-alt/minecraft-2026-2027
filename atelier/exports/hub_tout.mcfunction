# [claude-build] Etape 1 : charge les zones, regle le monde, puis lance l'etape 2 dans 30 secondes.
forceload add -114 -114 113 113
forceload add -126 114 125 365
forceload add 114 -96 305 95
forceload add -366 -126 -115 125
forceload add -126 -366 125 -115
gamerule doFireTick false
gamerule doWeatherCycle false
weather clear
gamerule doMobSpawning false
gamerule spawnRadius 0
kill @e[type=!player]
tellraw @s {"text":"Chargement des zones, patiente 30 secondes...","color":"gold"}
schedule function atelier:hub_pose 600t
