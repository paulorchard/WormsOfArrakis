#!/usr/bin/env bash
# Boots the game's server with no client, with this mod and Dunes of Arrakis loaded, and types console commands into it.
# Set NO_DUNES=1 and HEADLESS_DIR=<folder> to run without Dunes of Arrakis in a world of its own (vanilla sand test).
# usage: tools/headless/run.sh <step>...      a step is a console command, or "wait <seconds>"
#   tools/headless/run.sh "wait 28" "wormtest selftest" "wait 15"
# Commands run on different threads: put a "wait 1" between two that must run in order.
# The server always gets "stop" at the end. The universe is kept in build/headless, so a second run is a restart
# of the same world; delete that folder for a fresh one. Build the jar first (gradlew jar).
set -e
root="$(cd "$(dirname "$0")/../.." && pwd)"
game="$APPDATA/Hytale/install/release/package/game/latest"
java="${JAVA_HOME:-$HOME/.jdks/loom-ea-25-loom+1-11}/bin/java"
dir="${HEADLESS_DIR:-$root/build/headless}"
mkdir -p "$dir/mods"
cp "$root"/build/libs/IslandCraft-WormsOfArrakis-*.jar "$dir/mods/"
if [ -z "$NO_DUNES" ]; then cp "$APPDATA"/Hytale/UserData/Mods/IslandCraft-DunesOfArrakis-*.jar "$dir/mods/"; fi
cd "$dir"

pause() { node -e "setTimeout(() => {}, $1 * 1000)"; }
{
  for step in "$@"; do
    if [[ "$step" == wait\ * ]]; then pause "${step#wait }"; else echo "$step"; fi
  done
  echo stop
  pause 12
} | "$java" -Xmx4g -jar "$(cygpath -w "$game/Server/HytaleServer.jar")" --assets "$(cygpath -w "$game/Assets.zip")" \
      --disable-sentry --bind 0.0.0.0:55231 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > server.log

# Console commands and their replies, everything this mod logged, and any stack trace.
grep -n -A2 -iE 'executed command|Worm|paulorchard|Exception' server.log | grep -v -E '^--$|PluginManager|executed command: stop|Shut' | cut -c1-220
echo "full log: $dir/server.log"
