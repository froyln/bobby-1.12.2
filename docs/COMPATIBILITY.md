# Compatibility reference

## Status

Recorded 2026-10-24. Earlier marker-only checks missed reproducible holes in cached terrain. A stronger packaged regression records live terrain/light hashes and mesh sizes for 288 columns, unloads those columns on the client, then checks cached rendering, renderer reload and disk recovery with LiteOptimizer 0.6.0-rc2 and Sodium enabled at client distance 12.

The previous artifact (`6978426c014cdddf59df909b7db0b7571b752f3905214d68d11ec2ff3bab9f40`) lost 32 of those columns to its 256-entry LRU and had 288 missing meshes after unload. Disk recovery still left 64 columns and 216 meshes missing. The underlying block/light hashes matched whenever a snapshot was available.

The corrected artifact (`f38215c4bff1a71c4b333236d2d9c9a24532a008fdc078a90b62fb63623579ef`) passed the regression in both dimensions with Sodium enabled:

| Fixture | Cached columns | Compared meshes | Memory | Renderer reload | Disk reload |
| --- | ---: | ---: | --- | --- | --- |
| Overworld | 288 | 288 | Pass | Pass | Pass |
| Nether | 288 | 2194 | Pass | Pass | Pass |

Each pass has zero missing columns, changed terrain/light hashes, missing meshes, changed mesh sizes or changed vertex data. Uploaded quad data includes position, color, texture and light coordinates. The comparison also distinguishes legitimate queued builds outside the frustum from permanently empty sections. Live replacement passed in both dimensions; a late snapshot from the previous dimension was rejected.

The initial Nether vertex comparison caught a moving baseline: only byte 24 (block light) differed in eight opaque sections while geometry, colors and UVs matched. Minecraft continues client relighting over a limited number of visible chunks each tick. The final controlled fixture preserves the received light arrays, stops that recalculation in the test harness, waits for stable inputs and rebuilds the live baseline and its neighbors. All 2194 meshes then match through memory and both reload paths. Production lighting is unchanged. Screenshots and raw logs are retained locally.

These tests deliberately unload client columns in an integrated fixture. They do not establish behavior on every server or modpack. Full Bobby parity, larger-distance memory suitability, broad modpack compatibility and a true independently limited remote server remain unverified. Existing cache files remain readable; no format migration or cache deletion is required.

## Movement handoff

The lifecycle-correct build `f38215c4bff1a71c4b333236d2d9c9a24532a008fdc078a90b62fb63623579ef` still destroyed meshes at live/cache transitions. LiteOptimizer's unload listener removed the render column; Bobby added it back on the next renderer update, requiring asynchronous meshing. Its load listener also replaced an existing cached column before a full chunk packet populated live terrain. A packaged check found 288 missing render sections immediately after unloading 288 Overworld columns, and cached-to-live replacement did not preserve the old container.

Bobby now retains a render column across these handoffs when cached terrain remains in the render window. Normal provider load/unload and LiteOptimizer tracking still run, and full chunk packets invalidate and rebuild the retained meshes after applying live data. Departing columns and disabling caching still remove render resources normally.

The movement fixture checks actual uploaded GPU buffer sizes each frame while crossing chunk boundaries. LiteOptimizer's CPU `getMeshSize()` metadata is unsuitable for this check: `copyAndReplaceMesh` recomputes it from already-consumed vertex payloads after transparency sorting, so it can report zero while GPU geometry remains present. The fixture retains GPU quad comparisons and tests packet invalidation, live priority, cache reloads and the Controls toggle.

The handoff patch artifact is `1796a43cd2ab159ebdbdffa4f5ed68ab74fdfab67d22daee733bbe6153d970fd`. With Sodium enabled, its per-frame movement check produced:

| Fixture | GPU meshes | Rendered frames | Chunk-boundary crossings | Missing section frames |
| --- | ---: | ---: | ---: | ---: |
| Overworld | 288 | 887 | 10 | 0 |
| Nether | 2194 | 897 | 11 | 0 |

Both dimensions also preserved all tested meshes at the immediate live-to-cache handoff and passed memory, renderer reload and disk reload comparisons with unchanged terrain/light and GPU quad data. Full live packets preserved the existing container, invalidated its mesh, rebuilt it and won all rendering lookups. The Controls toggle cleared resident cache and cached render columns, retained live terrain, saved enabled=false, ignored a menu key press, and recovered disk terrain after re-enabling. The Controls screen opened successfully. The packaged run completed with zero failures. The fixture moves the spectator across adjacent chunk boundaries after controlled client unloads; it does not simulate a long remote-server flight. The lighting baseline remains controlled as described above.

## Confirmed rendering defects

- LiteOptimizer's section creation read the live provider and marked cached-only sections empty. The actual bytecode invokes `WorldClient.getChunk`, which requires a different redirect owner from the worker's `World.getChunk`. Required injection matches now fail explicitly instead of silently skipping a hook.
- A 256-entry LRU could not hold a 27x27 view area. Scans also loaded snapshots for live columns. Residents now occupy a bounded view window with a meshing border; visible columns do not evict one another, and scans exclude live columns.
- Cloned section biome reads bypassed snapshots. They now resolve through the same live-first terrain source.
- Vanilla's distant worker rejected neighbors absent from the live provider. Its rendering-only neighbor check now accepts cached chunks.
- A late unload could switch the cache back to an old world. Snapshot capture now accepts only the active cache world.

## Reference revisions

- [LiteOptimizer f901f8d](https://github.com/froyln/LiteOptimizer/tree/f901f8de2b12af5c7f6d4f01c1f9bc1da1632661), matching the clean local checkout inspected at `/home/froyln/Dev/LiteOptimizer`.
- [Bobby 241f973](https://github.com/Johni0702/bobby/tree/241f973106ee4e5552a5950d0a12433c07921c93).

These revisions identify inspected sources; they do not establish a supported version range.

## LiteOptimizer rendering ownership

Paths below are relative to LiteOptimizer's `src/main/java/`.

| Source | Observed behavior | Integration constraint |
| --- | --- | --- |
| `dev/froyln/liteoptimizer/mixin/render/sodium/features/chunk_rendering/MixinClientChunkManager.java` | Hooks client chunk load/unload and stores one renderer listener | Preserve that listener and its bookkeeping |
| `me/jellysquid/mods/sodium/client/render/SodiumWorldRenderer.java` | Registers the listener, owns loaded positions and resets render context on world changes | Cached render columns must follow renderer lifecycle |
| `me/jellysquid/mods/sodium/client/render/chunk/ChunkRenderManager.java` | Reads `WorldClient.getChunk` when testing section emptiness; owns columns and neighbors | Publish/remove cached terrain coherently and rebuild borders |
| `me/jellysquid/mods/sodium/client/world/WorldSlice.java` | Reads `World.getChunk` before preparing meshing context | Notifications alone do not provide cached section data |
| `me/jellysquid/mods/sodium/client/world/cloned/ClonedChunkSection.java` | Reads chunks again when copying sections | Rendering input must cover cloned data, light and biome access |
| `me/jellysquid/mods/sodium/client/world/cloned/ClonedChunkSectionCache.java` | Retains cloned sections and exposes invalidation | Invalidate cached copies when terrain source changes |
| `dev/froyln/liteoptimizer/mixin/render/sodium/features/chunk_rendering/MixinWorldRenderer.java` | Replaces terrain methods and cancels vanilla terrain setup | Vanilla RenderGlobal hooks alone cannot establish compatibility |

The preferred architecture keeps terrain snapshots outside gameplay chunk storage and exposes them only to rendering. The exact hooks and thread behavior require a runtime proof. Cached data must not become a source for collision, gameplay interaction or entity ticking.

## Toolchain reference

LiteOptimizer's inspected build specifies Java 8, Gradle 4.10.3, ForgeGradle 2.3 and MCP stable_39. Its README specifies Mixin 0.7.5. This repository builds with the Java 8 toolchain. The packaged invocation owners and descriptors were inspected directly with `javap`; Mixin requires each present hook to match. Broader renderer behavior remains under validation.

## Bobby feature coverage reference

The [Bobby description](https://www.curseforge.com/minecraft/mc-mods/bobby) describes persistent terrain, fallback worlds, config reload and optional config UI. At the pinned source revision, `BobbyConfig.java` also exposes dynamic multiworld handling, block-entity control, cached-terrain tint, unload delay, retention cleanup, maximum render distance and an integrated-server view-distance override. The `commands/` directory includes world-management, merge and upgrade operations.

These are reference behaviors requiring explicit coverage decisions. Modern cache/world format import is not established. Fabric-specific dependencies and UI cannot be assumed compatible with LiteLoader.

## Compatibility evidence required

Test the packaged mod alone, with LiteOptimizer's default renderer enabled, and with LiteOptimizer present but its renderer disabled. Verify cached-to-live transitions, reconnect/restart, dimension/world isolation, lighting and biome borders, renderer reloads, bounded asynchronous work, corrupt-cache recovery and unchanged gameplay behavior. Source inspection does not replace those checks.

## Current terrain lookup contract

Snapshots remain outside the gameplay provider. Vanilla render-cache construction and worker neighbor checks can resolve them. LiteOptimizer section creation, worker slices, cloned sections and biome queries use live terrain first and cached terrain second. Renderer column changes rebuild neighboring meshes and invalidate cloned sections. The resident window extends two columns beyond the client distance, up to radius 34 for the supported maximum distance of 32. Disk publication rejects stale world tokens, live replacements and columns outside that window.

Snapshots store copied block states, lighting, biomes and height data in a separate `.bobby112/v1/` Anvil tree. See [architecture](ARCHITECTURE.md) for storage bounds and remaining identity limitations.

## Persistence regression checks (2026-10-25)

Startup retention now reclaims enough room below the write threshold to resume saving. Read queue rejection permits another request, and failed reads use a bounded two-second retry backoff. Queued and actively written terrain is available to reads before older disk copies.

Orderly shutdown drains accepted saves. A packaged isolated MultiMC check with Sodium enabled queued 1,000 delayed fixture writes and immediately requested shutdown; all 1,000 completed before exit. Unit checks cover a blocked active writer plus 1,000 queued writes, continuation after a failed task and interruption preservation. Forced termination remains outside this guarantee.

Before disconnect/world replacement, remaining live terrain is captured in drained batches of 512. In an isolated integrated-world fixture at client distance 18 with Sodium enabled, disconnect captured 1,224 still-live columns: all were readable from cache with zero missing columns and zero changes to block, biome, heightmap or light hashes. A subsequent normal game exit captured a fresh live diamond-block change; a new Minecraft process read that change from disk successfully.

The same packaged fixture held the writer while a newer snapshot was queued: cache reads returned the new gold block while disk still contained the old stone block. It then saturated the 128-entry read queue and verified that the cached chunk loaded after capacity returned. The final persistence run completed with zero failures. That persistence gate had 22 passing tests. These are controlled integrated-world checks; broad server/modpack and forced-termination guarantees remain unproven.


## Second audit regression checks (2026-10-25)

The suite now has 31 passing tests. Added checks cover failed retention inspection, cached vanilla boundaries with absent neighbors, independent render-camera selection, malformed height maps, and bounded write retries with superseded snapshots and permanent failures. The boundary and camera checks are focused unit checks; a new full movement/render comparison has not been run for these changes.

An isolated packaged MultiMC run with LiteOptimizer/Sodium enabled saved a stone marker, deliberately closed its cache region handle, then saved gold at the same coordinate. Minecraft logged its swallowed `Stream Closed` error; Bobby detected the failed write, reopened the cache handle on retry, and a separate Anvil reader found gold. The fixture completed with zero failures. This also exercised the packaged Anvil serializer hook and region-registry lookup. Persistent failures are capped at three attempts and reported; durable storage cannot be guaranteed when the disk remains unavailable.
