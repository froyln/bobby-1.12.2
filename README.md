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

Explore an area first, then move away. With your client render distance set higher than the server’s view distance, Bobby can fill the surrounding view with terrain you have already seen. It cannot display unexplored terrain.

## Features

- **Persistent terrain cache** — keep explored terrain between sessions.
- **Live terrain takes priority** — newly received server data replaces cached terrain.
- **Separate world and dimension caches** — store snapshots outside your world saves.
- **In-game toggle** — hide or restore cached terrain with a configurable key.
- **Background disk loading and saving** — keep file work off the client thread.

## Installation

Requires **Minecraft 1.12.2**, **LiteLoader**, and **Java 8**.

1. Set up a Minecraft 1.12.2 instance with LiteLoader.
2. Build the mod using the instructions below.
3. Copy `bobby112-liteloader-1.12.2-0.1.0.litemod` from `build/libs/` into your instance’s `minecraft/mods/1.12.2/` folder.
4. Launch Minecraft and explore to populate the cache.

Install Bobby on the client. No server-side installation is needed.

## Controls and configuration

Open **Options → Controls → Bobby 1.12.2** and assign a key to **Toggle Bobby terrain cache**. It starts unassigned to avoid key conflicts.

Press your assigned key in game to enable or disable Bobby. An on-screen message confirms the change, and the setting is saved. Disabling removes cached terrain from view and stops new caching; existing disk files remain available when you re-enable it.

You can also edit `liteconfig/common/bobby112.json` in your instance’s Minecraft folder:

```json
{
  "enabled": true
}
```

Changes are reloaded while Minecraft is running.

## Cache storage

Terrain is stored under `.bobby112/v1/` inside the instance’s Minecraft folder, separately from `saves/`. The current disk budget is **2 GiB**, shared across cached worlds. Resident terrain follows your client render-distance window.

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

- Fallback worlds and a native settings panel are not implemented.
- Servers hosting multiple worlds behind one address can share a cache identity; user-selectable world profiles are not implemented.
- Full upstream Bobby feature parity and broad modpack compatibility are not established. A dedicated-server check with a smaller server view distance remains outstanding.

When reporting a rendering issue, include your mod versions, dimension, client/server view distances, and whether the affected terrain was previously explored.

## Credits

Inspired by [Bobby](https://www.curseforge.com/minecraft/mc-mods/bobby). This is a separate Minecraft 1.12.2 LiteLoader project and is not affiliated with upstream Bobby.
