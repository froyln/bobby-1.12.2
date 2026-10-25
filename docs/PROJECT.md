# Project requirements

## Goal and status

Create a client mod for Minecraft 1.12.2 and LiteLoader implementing Bobby-style functionality, compatible with LiteOptimizer. The prototype has bounded in-memory snapshots, asynchronous Anvil-region persistence, and renderer hooks. The current package starts in MultiMC with LiteOptimizer's Sodium renderer enabled and visually renders cached terrain with both Sodium enabled and disabled. Its local harness confirmed disk recovery, residency bounded by the current view window, live-chunk priority, and mesh continuity during movement. Independent cached terrain distance is implemented for singleplayer and multiplayer rendering; controlled integrated-world checks use a smaller live distance. A genuine independently limited remote-server test, broad compatibility, and release behavior remain outstanding. The working name is Bobby 1.12.2 and does not imply upstream affiliation.

## Reference behavior

[Bobby’s description](https://www.curseforge.com/minecraft/mc-mods/bobby) describes storing chunks received from a server and displaying them beyond the server’s view distance, optional terrain from a local `bobby-fallback` world, automatic configuration reload, and an optional configuration screen. Packaged persistence and local travel have runtime evidence. The independent cached distance keeps the live setting unchanged; a dedicated smaller-view-distance remote-server test remains outstanding. The LiteLoader JSON `enabled` setting has an in-game Controls binding, saved state and runtime disable/re-enable checks. A native settings-panel attempt failed during packaged Mixin class loading and is not included in the current source. Its Fabric integration and ModMenu/ClothConfig UI cannot be assumed to transfer to LiteLoader.

The feature inventory must cover persistent chunk caching, distant rendering, session reload, fallback worlds, configuration reload, and configuration UI. Establish a feasible LiteLoader equivalent for each before claiming feature parity. The prototype stores terrain in versioned Anvil region files under `.bobby112/v1/` with a fixed 2 GiB retention budget. Selectable remote-world profiles and existing Bobby cache compatibility are not implemented.

## Compatibility constraints

[LiteOptimizer’s README](https://github.com/froyln/LiteOptimizer/blob/main/README.md), consulted on 2026-10-24, describes a replacement terrain pipeline with worker meshing and chunk visibility traversal. Its documented build uses Java 8, Gradle 4.10.3, ForgeGradle 2.3, MCP stable_39, and LiteLoader’s Mixin 0.7.5 runtime. These describe the reference project, not an established build for this repository.

Inspect and record the exact LiteOptimizer revision before designing integration. Trace chunk load/unload, renderer ownership, rebuild notifications, visibility, lighting, and worker-thread reads. Test with its default terrain renderer enabled as well as without LiteOptimizer. Avoid a second competing terrain renderer or a mandatory disable switch.

Server data always supersedes cached terrain. Cached chunks are visual snapshots and may be stale: they must not simulate entities, tick blocks, or authorize interactions. Separate server/world and dimension identity, invalidate stale asynchronous work on disconnect or dimension changes, and prevent old cache results from replacing newly received server data.

Cache writes must not modify existing saves. Define bounded memory use, loading work, disk retention, and recoverable handling of interrupted writes or corrupt data. Any fallback save reader must be read-only.

## Decisions still required

- Mod ID, package name, project license, and attribution requirements before reusing upstream code.
- Reproducible LiteLoader build and dependency availability, exact versions, and artifact name.
- Source-backed chunk and rendering hooks compatible with a pinned LiteOptimizer revision.
- Cache identity, format, location, retention, and asynchronous ownership rules.
- Feasibility and implementation order for fallback worlds and the remaining Bobby settings; a native settings panel and the remaining configuration controls.

## Evidence required before release

A reproducible build and an in-game compatibility run are required. Cover server view distance smaller than client distance, unload/reload and reconnect, live data replacing cached data, world/server/dimension isolation, corrupt cache recovery, and disconnects with pending work. Repeat relevant gameplay cases with LiteOptimizer’s default renderer enabled and without LiteOptimizer. Report the actual revisions and environment used; documentation checks alone do not prove gameplay compatibility.
