#!/bin/sh
# Launch the installed RuneLite client (1.12.28) in developer mode so the sideloaded
# Bank Organiser plugin (symlinked into ~/.runelite/sideloaded-plugins/) is loaded,
# carrying over your Jagex-account session so login works.
#
# WHY THIS SCRIPT EXISTS
#   * RuneLite only loads sideloaded-plugins in developer mode.
#   * The official launcher (and the Jagex Launcher that wraps it) force-disable
#     developer mode, so we must launch the client jars directly.
#   * A direct launch needs: -ea (the injected client asserts), --developer-mode,
#     the gameval API jar on the classpath (so dev-mode's DevTools doesn't crash),
#     and — for a Jagex account — the JX_* session env vars the Jagex Launcher sets.
#
# USAGE
#   1. Launch RuneLite ONCE via the Jagex Launcher and get to the character/login
#      screen (this authenticates your Jagex account).
#   2. Run this script. It captures the JX_* session from that running client,
#      then relaunches our dev client with the sideloaded plugin.
#   3. You can close the Jagex-launched client once this one is up.
#
# Re-run after editing the plugin — it rebuilds the jar first and the symlink points
# at it, so your latest code is always loaded.
set -e

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@11}"
REPO="$HOME/.runelite/repository2"
HERE="$(cd "$(dirname "$0")" && pwd)"

# The gameval.* classes (VarbitID, InterfaceID, ...) are stripped from RuneLite's
# runtime jars, so developer-mode's DevTools plugin can't load without them. We build a
# small jar containing just that package (extracted from the compile-time API jar) and
# prepend it — adding gameval without shadowing any other runtime class.
GAMEVAL_API="$HERE/devtools-libs/gameval-1.12.28.jar"
if [ ! -f "$GAMEVAL_API" ]; then
	FULL_API=$(find "$HOME/.gradle/caches/modules-2" -name 'runelite-api-1.12.28.jar' 2>/dev/null | head -1)
	if [ -n "$FULL_API" ]; then
		echo "Building gameval helper jar (one-time)..."
		mkdir -p "$HERE/devtools-libs" "$HERE/.gvtmp"
		( cd "$HERE/.gvtmp" && "$JAVA_HOME/bin/jar" xf "$FULL_API" net/runelite/api/gameval \
			&& "$JAVA_HOME/bin/jar" cf "$GAMEVAL_API" net )
		rm -rf "$HERE/.gvtmp"
	fi
fi

[ -d "$REPO" ] || { echo "RuneLite runtime jars not found at $REPO — run RuneLite once first." >&2; exit 1; }

echo "Rebuilding plugin jar..."
JAVA_HOME="$JAVA_HOME" "$HERE/gradlew" --no-daemon -q jar

# --- Carry over a Jagex-account session if one isn't already in our environment -----
if [ -z "$JX_ACCESS_TOKEN" ] && [ -z "$JX_SESSION_ID" ]; then
	JXPID=""
	for p in $(pgrep -f 'RuneLite' 2>/dev/null); do
		if ps eww "$p" 2>/dev/null | tr ' ' '\n' | grep -q '^JX_'; then JXPID="$p"; break; fi
	done
	if [ -n "$JXPID" ]; then
		echo "Capturing Jagex session from running RuneLite (pid $JXPID)..."
		for kv in $(ps eww "$JXPID" | tr ' ' '\n' | grep -E '^JX_[A-Z_]+='); do
			export "$kv"
			echo "  + ${kv%%=*}"
		done
	else
		echo "NOTE: no Jagex-authenticated RuneLite found running."
		echo "      If you use a Jagex account, launch RuneLite via the Jagex Launcher first,"
		echo "      then re-run this script. (A non-Jagex/legacy account can log in directly.)"
	fi
fi

CP="$REPO/*"
[ -f "$GAMEVAL_API" ] && CP="$GAMEVAL_API:$REPO/*"

# --- Isolated dev RuneLite home -----------------------------------------------------
# The plugin is published on the Plugin Hub, so a normal dev launch would load BOTH the
# installed hub copy AND our sideloaded build — two instances fighting over the bank UI.
# Point the dev client at its own home (via -Duser.home) with NO external plugins, so only
# our sideloaded copy runs. Your real ~/.runelite and the hub plugin are left untouched.
DEV_HOME="$HOME/.bankassistant-dev"
DEV_RL="$DEV_HOME/.runelite"
REAL_RL="$HOME/.runelite"

mkdir -p "$DEV_RL/plugins" "$DEV_RL/sideloaded-plugins"
ln -sfn "$HERE/build/libs/bank-organiser-1.0.0.jar" "$DEV_RL/sideloaded-plugins/bank-assistant.jar"

# Share the big read-mostly caches so the game cache isn't re-downloaded.
for shared in jagexcache cache; do
	[ -e "$REAL_RL/$shared" ] && ln -sfn "$REAL_RL/$shared" "$DEV_RL/$shared"
done
[ -e "$HOME/jagexcache" ] && ln -sfn "$HOME/jagexcache" "$DEV_HOME/jagexcache"

# Seed the dev config from your real one ONCE (so loadouts/overrides/order carry over),
# but strip all external plugins so the hub copy isn't pulled in. Delete
# ~/.bankassistant-dev to re-sync from your real config.
if [ ! -d "$DEV_RL/profiles2" ] && [ -d "$REAL_RL/profiles2" ]; then
	cp -R "$REAL_RL/profiles2" "$DEV_RL/profiles2"
	for prof in "$DEV_RL/profiles2/"*.properties; do
		[ -f "$prof" ] || continue
		sed -i '' -E 's/^runelite\.externalPlugins=.*/runelite.externalPlugins=/' "$prof"
		sed -i '' -E '/^runelite\.bankorganiserplugin=/d' "$prof"
		printf 'runelite.bankorganiserplugin=true\n' >> "$prof"
	done
fi

echo "Launching RuneLite (developer mode) with sideloaded Bank Assistant (isolated home)..."
# -ea is REQUIRED for the injected client; without it RuneLite aborts with
# "Developers should enable assertions -ea".
exec "$JAVA_HOME/bin/java" \
	-ea \
	-Duser.home="$DEV_HOME" \
	-XX:+UseG1GC -Xmx768m \
	-cp "$CP" \
	net.runelite.client.RuneLite --developer-mode "$@"
