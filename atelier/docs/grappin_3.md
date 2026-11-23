<!-- [claude-build] fiche du grappin 3, 2026-11-16 : Luca a demandé de refaire la première version, sans balancier -->
# Grappin 3 : on est tiré vers le point visé

**Demande de Luca (2026-11-16) :** « refaire la première version, sans balancer, juste ça t'attire à un point visé. » Le grappin 3 remplace le balancier du grappin 2 (corde, treuil, molette, gravité du pendule) par une **traction en ligne droite**.

## Ce qu'on fait avec

- **Clic droit** : lance le crochet (portée 30 blocs, il vole tout droit). Il ne s'accroche qu'à un **point d'ancrage** (bloc `donjon:ancrage`) qu'il touche ou frôle à moins de **1 bloc** (le grappin 2 en tolérait 1,5 : il faut maintenant viser le point).
- **Accroché**, on est **tiré en ligne droite** vers l'ancrage (départ progressif, 0,75 bloc par tick au maximum, soit 15 blocs/s ; on ralentit en arrivant). On s'arrête quand le centre du joueur est à **1,2 bloc** de l'ancrage : le crochet se détache tout seul, la vitesse est annulée et **la gravité reprend** (on tombe tout droit, donc sur la plateforme ou la rive qui est sous l'ancrage).
- **Pas de balancier** : plus de corde, plus de clic maintenu, plus de molette (elle change de nouveau d'objet), plus de poussée des touches.
- **Pendant la traction** : **Espace** lâche en route (on garde sa vitesse et on saute un peu), **s'accroupir** lâche aussi, et **un nouveau clic droit** lâche et relance aussitôt (enchaînement de points). Crochet encore en vol : un clic le rappelle.
- **Sécurités** : si un bloc nous retient plus de 10 ticks (on ne se rapproche plus), le crochet se détache ; une traction ne dure jamais plus de 20 s ; pas de dégâts de chute pendant la traction (la chute compte à partir du lâcher).

## Réglages (`config/donjon-common.toml`)

`portee` (30), `vitesseCrochet` (2,5), `aimant` (1,0, **nouvelle clé** : l'ancienne `aimantAncrage` est ignorée, donc un ancien fichier de config ne garde pas 1,5), `recharge` (6), `vitesseTraction` (0,75), `accelerationTraction` (0,15), `distanceArrivee` (1,2), `vitesseAuLacher` (1), `sautAuLacher` (0,42). Supprimés : `graviteBalancier`, `influenceTouches`, `raideurCorde`, `cordeMin`, `vitesseTreuil`, `pasMolette`.

## Dans le code

`GrappinClient.Traction` (ex-`Balancier`, calcul chez le joueur à chaque tick, comme tout mouvement de joueur), `GrappinItem.use` (clic droit), `Crochet` (le crochet côté serveur, inchangé sauf une sécurité de 20 s), `Reglages`. La Paravoile consulte `GrappinClient.Traction.crochetAccroche` pour ne pas s'ouvrir pendant une traction. Version du mod : **3.0.0** (`grappin-3.0.0.jar`).

## Ce qui change pour les donjons

- Le grappin ne sert plus à traverser un vide en se balançant : il **tire vers un point**, donc les ancrages doivent être **au-dessus de l'endroit où l'on veut arriver** (plateforme sous l'ancrage, rive d'en face, corniche). Les anciens donjons et les arènes des boss (Séraphin, Gardien des Racines, Léviathan) ont été conçus avec le balancier : à revoir un par un.
- Le Donjon Suspendu (`scripts/verif_ss1.py`) est vérifié avec ce modèle : tir droit, magnétisme à 1 bloc, arrivée à 1,2 bloc de l'ancrage puis chute verticale.

## À faire

Essayer en jeu (pas encore fait) : le départ progressif, l'arrêt à 1,2 bloc, la chute sans dégâts, Espace et le clic pour lâcher, le cas d'un bloc qui retient, et la compatibilité avec la Paravoile et les boss.
