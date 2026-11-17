#!/usr/bin/env bash
# Kodistan status line — one glance tells whether Claude is testing on the Minecraft client.
# Orange banner while .claude/testing.flag exists (Claude creates/removes it), green dot otherwise.
input=$(cat) # session JSON, unused for now
if [ -f "$(dirname "$0")/testing.flag" ]; then
  printf '\033[48;5;202m\033[30m\033[1m TESTS EN COURS — CLIENT OCCUPÉ \033[0m \033[33m%s\033[0m' "$(date +%H:%M:%S)"
else
  printf '\033[32m● prêt\033[0m \033[2mKodistan\033[0m'
fi
