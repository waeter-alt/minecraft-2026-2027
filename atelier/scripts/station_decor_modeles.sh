#!/usr/bin/env bash
# [claude-build] Exporte les .bbmodel du decor de la Station en modeles de bloc JSON (a relancer apres une retouche dans Blockbench).
# Usage, depuis la racine du depot : bash atelier/scripts/station_decor_modeles.sh [python]
set -e
PY="${1:-python}"
S=atelier/modeles/station
E=atelier/scripts/bbmodel_vers_bloc.py
"$PY" "$E" "$S/pompe_station.bbmodel" --a-part volant_ --variantes voyant_eteint:arret=voyant_eteint,marche=voyant_allume --objet
"$PY" "$E" "$S/chaudiere.bbmodel" --variantes foyer_eteint:eteinte=foyer_eteint,allumee=feu_allume --objet
"$PY" "$E" "$S/cadran.bbmodel"
