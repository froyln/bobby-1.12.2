<div align="center">

# Bobby 1.12.2

**Keep explored terrain in view beyond the server’s view distance.**

![Minecraft 1.12.2](https://img.shields.io/badge/Minecraft-1.12.2-62B47A)
![LiteLoader](https://img.shields.io/badge/Loader-LiteLoader-4C93D6)
![Java 8](https://img.shields.io/badge/Java-8-E68A2E)

**[Documentation](docs/README.md) · [Compatibility](docs/COMPATIBILITY.md) · [Report an issue](https://github.com/froyln/bobby-1.12.2/issues)**

</div>

---

## What does it do?

Bobby 1.12.2 saves terrain your client receives and keeps it available for rendering after the server unloads it. Revisit an area or reconnect, and previously cached terrain can load from disk.

Explore an area first, then move away. Set Bobby’s cached distance above Minecraft’s live distance to keep that terrain visible, including in singleplayer. Bobby displays only terrain already recorded or present in an enabled fallback world.

## Features

- **Persistent terrain cache** — keep explored terrain between sessions.
- **Live terrain takes priority** — newly received server data replaces cached terrain.
- **Separate world and dimension caches** — store snapshots outside your world saves.
- **Read-only fallback world** — optionally display terrain from a matching Minecraft 1.12.2 world copy.
- **Independent terrain distance** — display explored chunks beyond the live distance in singleplayer or multiplayer.
- **In-game settings** — configure caching, distance, fallback and profiles through LiteLoader; an optional key toggles caching.
- **Background disk loading and saving** — keep file work off the client thread.

## Installation

Requires **Minecraft 1.12.2**, **LiteLoader**, and **Java 8**.

1. Set up a Minecraft 1.12.2 instance with LiteLoader.
2. Build the mod using the instructions below.
3. Copy `bobby112-liteloader-1.12.2-0.1.0.litemod` from `build/libs/` into your instance’s `minecraft/mods/1.12.2/` folder.
4. Launch Minecraft and explore to populate the cache.

Install Bobby on the client. No server-side installation is needed.

## Controls and configuration

Open LiteLoader’s mod list from the chest tab on the main menu or pause menu (or **Ctrl+Shift+Tab** if the tab is hidden). Select **Bobby 1.12.2 → Settings...** to change terrain caching, cached distance, the fallback world option, the singleplayer live distance, cached-terrain dimming, or the profile for your next connection. Click **Save & Close**, or press **Escape**, to save and apply your changes.

Open **Options → Controls → Bobby 1.12.2** and assign a key to **Toggle Bobby terrain cache**. It starts unassigned to avoid key conflicts.

Press your assigned key in game to enable or disable Bobby. An on-screen message confirms the change, and the setting is saved. Disabling removes cached terrain from view and stops new caching; existing disk files remain available when you re-enable it.

You can also edit `liteconfig/common/bobby112.json` in your instance’s Minecraft folder:

```json
{
  "enabled": true,
  "cacheRenderDistance": 16,
  "integratedServerDistance": 0,
  "tintCachedTerrain": false,
  "fallbackEnabled": false,
  "cacheProfile": ""
}
```

`cacheRenderDistance` sets how far explored cached terrain can be displayed, independently of Minecraft’s render distance. It defaults to 16 chunks and is limited to 2–32. The visible terrain range uses the larger of the two settings; Minecraft’s setting controls live chunk requests and singleplayer loading by default. For example, use Minecraft distance 6 and cache distance 16 to display explored terrain beyond the live area. Explore that terrain first: Bobby does not generate unseen chunks.

Changes are reloaded while Minecraft is running. Disabling Bobby restores Minecraft’s normal terrain distance.

For a separate singleplayer live range, set **Singleplayer live distance** in Bobby’s settings, or `integratedServerDistance` in JSON. `0` (**Auto**) follows Minecraft; positive values are limited to 3–32 chunks. For example, keep Minecraft distance 8 and cached distance 16, then set the singleplayer live distance to 3. This changes the integrated server’s loading and tracking range for the host and LAN guests. Spawn chunks and other vanilla loading rules still apply. It takes effect on the next unpaused server tick; disabling Bobby restores Minecraft’s live distance. Dedicated servers keep their own distance.

Set **Tint cached terrain** or `tintCachedTerrain` to `true` to make cached terrain visibly distinct: rendered sky light drops by 3–10 levels depending on gamma, and rendered block light rises by up to 5. It defaults to off and does not change saved light data or live terrain.

## Cache profiles

Use a different `cacheProfile` name for each world behind a proxy address, or after a server reset. Select it in Bobby’s settings **before joining**. If you edit it while playing, disconnect and reconnect to apply it; changing dimension keeps the current connection’s profile.

Blank uses your existing cache. Named profiles start with separate recorded terrain. Select a previous name, or blank, to reopen its cache. Names are case-sensitive, support letters, numbers, spaces, dots, underscores and hyphens, and are limited to 64 characters. Surrounding spaces are trimmed.

Profile selection is manual; Bobby does not detect proxy world switches automatically.

## Fallback world

To fill areas missing from the recorded cache, copy a matching **Minecraft 1.12.2 Anvil world** into `saves/bobby-fallback` and set `fallbackEnabled` to `true`. Keep its `region/`, `DIM-1/region/` and `DIM1/region/` directories in their original layout.

Live chunks take priority, followed by recorded cache, then fallback terrain. The fallback files are opened read-only: Bobby does not generate, repair, convert or save that world. Entities, block entities and scheduled ticks are excluded. Modern world formats and symlinked sources are not supported.

The fallback is shared by this Minecraft instance, including all cache profiles. Enable it only while playing the matching world/server, and disable it when switching to an unrelated world. Changes to the option reload terrain automatically; after replacing fallback files, toggle the option off and on to refresh them.

## Cache storage

Terrain is stored under `.bobby112/v1/` inside the instance’s Minecraft folder, separately from `saves/`. The current disk budget is **2 GiB**, shared across cached worlds. Resident terrain follows the larger of your live and cached render distances, with two extra columns for meshing neighbors.

Cached chunks are visual snapshots. They may be outdated, and they do not keep farms running, simulate entities, or extend interaction distance.

## Build from source

With **JDK 8** selected:

```bash
git clone https://github.com/froyln/bobby-1.12.2.git
cd bobby-1.12.2
./gradlew build
```

Output: `build/libs/bobby112-liteloader-1.12.2-0.1.0.litemod`.

The project uses the included Gradle wrapper, MCP stable_39 mappings, and LiteLoader. See [compatibility](docs/COMPATIBILITY.md) for inspected versions and runtime checks.

## Current limitations

- Automatic proxy-world detection is not implemented; select a separate profile and reconnect for each world or reset.
- Full upstream Bobby feature parity and broad modpack compatibility are not established. A dedicated-server check with a smaller server view distance remains outstanding.

When reporting a rendering issue, include your mod versions, dimension, client/server view distances, and whether the affected terrain was previously explored.

## Credits

Inspired by [Bobby](https://www.curseforge.com/minecraft/mc-mods/bobby). This is a separate Minecraft 1.12.2 LiteLoader project and is not affiliated with upstream Bobby.
