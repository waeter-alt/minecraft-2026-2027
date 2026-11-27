#!/usr/bin/env bash
# Rend toutes les images avec Chrome sans fenêtre. Chrome cale parfois au bout de quelques dizaines
# d'images : on le relance alors à partir de la dernière image reçue.
# Prérequis : python recevoir.py et python -m http.server 8094 --bind 127.0.0.1 lancés depuis ce dossier.
cd "$(dirname "$0")"
CHROME="${CHROME:-/c/Program Files/Google/Chrome/Application/chrome.exe}"
PROFILE="${TMPDIR:-/tmp}/kodistan-rendu-chrome"
kill_chrome(){ powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='chrome.exe'\" | Where-Object { \$_.CommandLine -like '*kodistan-rendu-chrome*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue }" ; }
count(){ ls "frames/$1" 2>/dev/null | wc -l; }
run(){ # $1 = paramètres d'URL, $2 = dossier suivi, $3 = cible
  "$CHROME" --headless=new --user-data-dir="$PROFILE" --window-size=1700,1100 --autoplay-policy=no-user-gesture-required \
    --disable-background-timer-throttling --disable-renderer-backgrounding --mute-audio "http://127.0.0.1:8094/rendu.html?$1" >/dev/null 2>&1 &
  local last=-1 idle=0
  while [ "$(count "$2")" -lt "$3" ]; do
    sleep 3; local c; c=$(count "$2")
    if [ "$c" -eq "$last" ]; then idle=$((idle+1)); [ $idle -ge 6 ] && break; else idle=0; fi; last=$c
  done
  kill_chrome; sleep 1
}
kill_chrome
for _ in $(seq 1 10); do m=$(count main); [ "$m" -ge 160 ] && break; echo "boucle : reprise à $m"; run "auto=1&from=$m" main 160; done
for _ in $(seq 1 10); do n=$(count intro); [ "$n" -ge 150 ] && break; echo "intro : reprise à $n"; run "auto=1&intro=1&ifrom=$n" intro 150; done
echo "boucle=$(count main) intro=$(count intro)"
