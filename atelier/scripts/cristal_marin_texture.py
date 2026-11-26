# [claude-build] La texture du Cristal des mers (le boss de la Station, le Leviathan, est soigne par des cristaux comme l'Ender Dragon) : le cristal de l'End, dont on a juste change
# la couleur (violet et magenta -> bleu-vert marin), sans toucher a la forme ni au decoupage de la texture (128 x 64). La texture de depart est celle du jeu (cherchee dans le jar
# des ressources de Minecraft que Gradle a deja prepare) ; le resultat est assets/donjon/textures/entity/cristal_marin.png.
#   python atelier/scripts/cristal_marin_texture.py [<jar des ressources de Minecraft>]
import colorsys
import sys
import zipfile
from io import BytesIO
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / "atelier/donjon/arene/src/main/resources/assets/donjon/textures/entity/cristal_marin.png"
JAR = RACINE / "mod/build/moddev/artifacts/neoforge-21.1.216-client-extra-aka-minecraft-resources.jar"      # a passer en argument depuis un worktree sans build
CHEMIN = "assets/minecraft/textures/entity/end_crystal/end_crystal.png"
TEINTE_MER = 0.52                        # bleu-vert (turquoise), sur le cercle des teintes 0..1


def main():
    jar = Path(sys.argv[1]) if len(sys.argv) > 1 else JAR
    with zipfile.ZipFile(jar) as z:
        img = Image.open(BytesIO(z.read(CHEMIN))).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            s = min(1.0, s * 1.15 + 0.05) if s > 0.05 else s            # un peu plus vif : le cristal doit se voir de loin
            v = min(1.0, v * 1.05)
            nr, ng, nb = colorsys.hsv_to_rgb(TEINTE_MER, s, v)
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    img.save(SORTIE)
    print(f"{SORTIE.name} ecrit ({img.width} x {img.height})")


if __name__ == "__main__":
    main()
