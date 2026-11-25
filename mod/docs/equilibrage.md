# Équilibrage : points ouverts

Ce fichier liste ce qui est volontairement laissé brut et que Duncan et l'équipe doivent trancher ensemble avant d'ouvrir le serveur.

## À rediscuter à l'équilibrage

### Commerce

- **Prix libres des contrats.** Rien n'empêche un contrat à 1 pièce la caisse, ni à un prix délirant : le vendeur fixe le prix comme il veut (`unitPrice >= 1` seulement). Un joueur peut donc donner des ressources à un allié, ou s'en servir pour déplacer de l'argent. À revoir ensemble : plancher et plafond liés au marché, ou prix libres assumés.
- **Coût et force des escortes.** Défauts de `Config` : coûts 150 / 400 / 900 / 2 000 / 4 500 Couronnes par contrat, payés à chaque fois (rendus seulement si l'offre est retirée avant le départ) ; compositions 2+1, 3+1, 4+2, 5+3, 6+4 (épéistes + archers). Le palier 1 (deux épéistes en fer et un archer) tue un joueur seul en fer en quelques secondes dans le duel de `CommerceTests` : c'est peut-être plus dur que « buter dessus ». Le coût d'un palier 5 est à comparer à la valeur d'une caravane pleine. À régler : PV, armures, dégâts, et si l'escorte doit coûter à chaque trajet ou par contrat.
- **Mule et conducteur protégés.** Tant qu'un soldat de l'escorte vit, la mule et son conducteur ne peuvent pas être blessés : il faut abattre l'escorte d'abord. Les soldats morts ne reviennent pas. À confirmer, ou à remplacer par une protection partielle.
- **Vitesse des caravanes.** `caravanBlocksPerSecond` = 3 blocs par seconde : un trajet de 600 blocs dure 200 secondes. À mettre en regard de la marche d'un joueur (environ 4 à 5 blocs par seconde) pour savoir si on peut rattraper ou devancer une caravane à pied. La caravane s'arrête tant que son escorte se bat.
- **Capacité par niveau du Relais.** Deux contrats en même temps par niveau (2 à 10), et le niveau du Relais plafonne aussi le palier d'escorte (un palier par niveau). Quantité maximale par contrat : 256 caisses. À revoir selon le volume d'échanges attendu.

### Guerre : assaut

Règles complètes : `assaut.md`. Toutes les valeurs ci-dessous sont des valeurs de départ, choisies sans test en jeu (constantes en tête de `WarAssaults`, `AssaultGoal`, `AssaultRangedGoal`).

- **Chrono.** Départ 8 min, +90 s par bâtiment ou muraille tombé, +30 s par garde tué, plafond 12 min restantes. À regarder : la marche depuis le point de ralliement mange une partie des 8 minutes de départ.
- **Blocage par la garnison.** Un garde fige le bâtiment s'il est à 24 blocs ou moins et a échangé un coup avec une troupe dans les 10 dernières secondes. À regarder : un garde archer posté dans une tour fermée qui tire par une meurtrière se bat, donc il bloque, alors que les légionnaires ne peuvent pas l'atteindre (seules les archères le peuvent).
- **Portées des troupes.** Légionnaire : cherche à 16 blocs, lâche à 32. Archère : cherche à 24, tire à 16. Aggro partagée : 16 blocs.
- **Garnison figée pendant l'assaut.** Aucun achat de garde tant qu'un assaut vise la colonie.

### PvP et items de donjon

- **Toucher un joueur en plein grappin décroche le grappin (codé dans `GrappinEvenements`).** Pour récompenser l'adresse de celui qui atteint un adversaire en vol, alors que le grappin le rend difficile à toucher : dès qu'un joueur accroché subit des dégâts, **quel qu'en soit le type** (mêlée, flèche, magie, feu, etc.), son grappin se décroche. Pas de distinction par source, c'est plus simple à écrire, à tester et à expliquer aux joueurs. La chute ne compte pas : elle est déjà annulée tant qu'on est tenu par la corde. Un coup totalement absorbé (dégâts à 0 après armure ou bouclier) ne décroche pas. À trancher à l'équilibrage : la règle vaut aujourd'hui aussi contre les monstres et l'environnement, pas seulement entre joueurs ; et il n'y a aucun délai avant de pouvoir relancer un crochet juste après.
