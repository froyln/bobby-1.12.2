<div align="center">

# Bobby 1.12.2

**A LiteLoader mod in development that caches Minecraft 1.12.2 terrain for viewing beyond a server’s view distance.**

</div>

---

## What is it?

This project brings Bobby-style terrain caching to Minecraft 1.12.2 with LiteLoader, with LiteOptimizer compatibility as a required target.

**Status:** Early development. A focused regression exposed cached terrain holes in the earlier build; section creation, resident eviction, cached biome reads and worker neighbor checks have been corrected. Current runtime evidence and remaining limits are recorded in [compatibility](docs/COMPATIBILITY.md). Complete Bobby feature coverage and broad modpack compatibility remain unfinished; this is not release-ready.

## Documentation

**[Bobby 1.12.2 Documentation](docs/README.md)**

## Current scope

- Store terrain received from a server and display it after it leaves the server’s view distance.
- Restore cached terrain in later sessions, with separate caches for worlds and dimensions.
- Work with LiteOptimizer’s terrain renderer.

### Enable or disable

Open **Options → Controls → Bobby 1.12.2**, assign a key to **Toggle Bobby terrain cache**, and press it in game. The binding starts unassigned to avoid conflicts. An on-screen message shows the new state, which is saved for the next launch. Disabling clears cached terrain from view; it keeps the disk cache for re-enabling and leaves live server terrain active.

You can also change `enabled` in `liteconfig/common/bobby112.json`; the file is reloaded while the game is running. A native settings panel is not currently included.

The full reference feature scope and unresolved requirements are recorded in [Project requirements](docs/PROJECT.md).

## Target platform

Minecraft 1.12.2 with LiteLoader. LiteOptimizer compatibility is a development requirement.

## Installation and building

Use JDK 8 and run `./gradlew build`. The `.litemod` is written to `build/libs/`. Terrain snapshots are stored separately from saves under `.minecraft/.bobby112/v1/`, with a fixed 2 GiB disk budget. See [documentation](docs/README.md) for current compatibility evidence and limitations.

## References

- [Bobby](https://www.curseforge.com/minecraft/mc-mods/bobby)
- [LiteOptimizer](https://github.com/froyln/LiteOptimizer)
