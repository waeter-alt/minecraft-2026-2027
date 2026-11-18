# Grappin : le crochet quitte le lanceur, les griffes se déploient (design court)

Demandé par Luca le 2026-11-18, après l'avoir vu en jeu : « quand on tire le grappin, il faudrait que les 4 crochets sortent du grappin ». Choix de Luca : les deux points ci-dessous.

## Ce qui change

1. **Le crochet quitte vraiment le lanceur.** Tant que le joueur a un crochet lancé (en vol ou accroché), le grappin en main, en 1re comme en 3e personne, n'a plus sa tête (tige, collier, pointe, griffes) : il ne reste que la poignée, le corps, la bobine et un embout vide d'où sort la corde. Dès que le crochet disparaît (lâché, arrivé, raté), la tête réapparaît sur le lanceur.
2. **Les griffes se déploient en vol.** Sur le lanceur, les 4 griffes sont repliées le long de la tige. En vol, elles s'ouvrent en 4 ticks (0,2 s) ; à l'accroche, elles se referment à moitié en 3 ticks, comme si elles agrippaient l'ancrage.

## Ce qui ne change pas

Le fonctionnement du grappin (portée, vitesse, traction, ancrages), l'icône de l'inventaire, la corde.

## Comment (mes choix)

- Un second modèle en main, `grappin_main_vide` (sans la tête, avec un embout), choisi par une propriété d'objet `grappin:tire` (1 quand le joueur a un crochet à lui dans le monde) dans les `overrides` de `grappin.json` ; l'icône reste celle d'aujourd'hui.
- Le crochet en vol est dessiné en deux morceaux : la tige (`crochet_tige`) et une griffe (`crochet_griffe`) dessinée 4 fois, tournée d'un quart de tour chaque fois, avec un angle d'ouverture calculé par une fonction pure testée (`GrappinClient.ouvertureGriffes`).
- Travail sur `feature/grappin-apparence` ; les branches empilées dessus (`feature/golem-animations`, `feature/rodeur-racines`) le reçoivent par une fusion, et le jar d'essai `grappin-3.5.0.jar` part de `feature/rodeur-racines` (il contient tout).

## Validation

Tests : modèles présents et valides (`ModelesGrappinTest`), ouverture des griffes (0 au départ, 1 après 4 ticks, 0,5 après l'accroche). En jeu : tirer, regarder le lanceur sans tête puis la tête qui revient, les griffes qui s'ouvrent en vol et serrent l'ancrage.
