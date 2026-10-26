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

Distance and fallback integration also inspected LiteOptimizer revision `641ffd1459800175cf34430c966816e10eacf0ac`. The runtime jar for those checks is LiteOptimizer 0.6.0-rc2, litemod revision 127, SHA256 `dc5bf8c325a7a2915ff29681657a79b49676df0c0a610df411699cab12d29504`. Its packaged `canRebuild` returns true; the sparse-terrain issue was missing connected visibility nodes, not a neighbor-build gate.

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


## Independent cached render distance (2026-10-25)

`cacheRenderDistance` defaults to 16 and accepts 2–32 chunks, independently of live chunk distance. The effective terrain radius is the larger of the two distances. Renderer-only hooks extend vanilla/Sodium allocation, reload checks, camera projection and fog; they do not change GameSettings or the integrated server's loading distance. Disabling restores the normal live distance. Existing configs without the new field retain the default.

The packaged artifact `d2f095dec7364a7574234d843450af10e4e2239bf8d76976c23d593406c5f49c` was tested in isolated MultiMC singleplayer with live distance 3 and cache distance 10. After exploring chunk (8,0) and returning to chunk (0,0), the target was absent from the live provider but present in cache. Vanilla compiled its terrain normally; LiteOptimizer/Sodium uploaded a nonempty GPU mesh. Both kept GameSettings at 3 and used a 160-block camera distance. Config reload increased the renderer radius to 12; disabling returned it and the camera to 3/48 blocks; re-enabling restored 10. Both final runs completed with zero failures. No user launcher profile was modified.

The vanilla test initially exposed opaque, uncompiled empty columns across an unvisited gap. A direct compile proved the cached target data was valid, and exploring a continuous route allowed normal meshing. Workers now allow unknown empty render sections inside the active bounded cache window to compile, leaving them traversable to the visibility graph. The final fresh-world teleport test passed without forced compilation. Disabled, live-center and outside-window neighbor checks remain covered by regression tests.

JDK8 `./gradlew test build --offline` passes 34 tests; setup and whitespace checks pass. These are controlled integrated-world tests, not an independently limited dedicated-server run or a broad modpack guarantee.


## Read-only fallback regression (2026-10-25)

Artifact `7035d79b875f5a30aa7acf941adbde4c910427801a23f208e12001ccfa71efbc` completed the same isolated MultiMC fixture both without LiteOptimizer and with its Sodium renderer enabled, with zero failures in both final runs. At live distance 3/cache 10, a source-only chunk eight columns from the camera rendered from fallback in the Overworld and Nether while absent from the gameplay provider. The fixture checked source dimension selection, no decoded entity/block-entity objects, enable/disable/re-enable, recorded diamond terrain taking priority over fallback gold, and a client live-provider replacement taking priority over both. These are controlled integrated-world/provider checks, not an independently limited dedicated-server protocol test.

Both source region files retained identical bytes and modification times. The fallback-only Nether chunk remained absent from the recorded cache after orderly process shutdown. A separate reader uses read-only handles, strips simulation tags before terrain decoding, rejects modern data and validates bounded Anvil/NBT input. Unit fixtures include read-only file permissions, negative coordinates, missing dimensions, invalid headers/compression, malformed sections, wrong coordinates and symlinked sources. The final JDK8 `./gradlew test build --offline` gate passes 41 tests; setup and whitespace checks pass.

The initial sparse-source run exposed missing Sodium graph connections, corrected with bounded empty render-only columns. A vanilla Overworld run also exposed uncompiled empty sections at the live frontier when neighbors were unknown; the active-window worker exception now covers that frontier. The final runs required no forced target compilation. Larger-distance frame time and broad modpack suitability remain unmeasured. At that gate, a native settings panel, additional upstream feature parity, licensing and the dedicated-server release gate were outstanding.


## Native settings panel (2026-10-25)

Artifact `6d5662259fec8e2eb8b9c5f08f13804c206eba8490461cc89e080f1f9cef17e3` opens a native LiteLoader configuration panel through the registered ConfigManager factory and real mod-screen host with LiteOptimizer/Sodium enabled. A packaged isolated MultiMC check exercised all three controls, repeated clicks at the 2/32 distance bounds, draft preservation across resize callbacks, Save/Close, Escape, JSON persistence and reopen. It then changed JSON externally and confirmed both the panel and renderer picked up the new value. Minecraft's live setting stayed at 3 while the renderer changed 16→8→16. The final run completed with zero failures, and actual initial/edited panel screenshots were visually inspected for layout and text.

The panel uses LiteLoader's standard AbstractConfigPanel registration and saves the same exposed fields already used by JSON and the toggle key. A separate registration probe also booted successfully. Historical panel-failure logs were unavailable; these checks demonstrate the current implementation works, rather than establishing the cause of the old attempt's class-loading failure. The UI fixture uses the actual native host and button callbacks; it does not exercise every GUI scale or operating-system window size. JDK8 test/build passes 41 tests, and setup/whitespace checks pass. Dedicated-server validation, licensing and remaining feature gaps are still open.


## Named cache profiles (2026-10-25)

Artifact `111e5faa91c14c0284ac1742610ca2cf3a925a51770d2e6d491713834c553edc` completed a packaged isolated MultiMC/Sodium check with zero failed assertions and no fatal log entries. The real native settings field enforced the native 64-unit limit, rejected path characters and oversized supplementary-Unicode names, preserved a valid supplementary name through resize, and retained text/focus/editability. JSON persisted the selection.

The fixture joined an integrated world with profile A, stored gold terrain, then selected B while connected. The active directory remained A, including through Nether travel and further writes. Native pause-menu Save and Quit followed by reconnect activated B, which could not read A's diamond marker; B stored its own gold marker independently. Another reconnect with A recovered its original diamond marker while B retained gold. Cached marker terrain remained outside the gameplay provider. Returning the selected name to blank while connected still kept A active until disconnect. Post-shutdown independent header checks confirmed A/B Overworld records, A-only Nether data, and no target record in the legacy/default namespace.

The first direct-disconnect harness run logged stale time-update callbacks after cleanup; the final fixture used Minecraft's native pause-menu quit flow and completed cleanly. This is not a dedicated remote-server or arbitrary forced-disconnect guarantee. Unit checks cover exact legacy default keys, profile/base-identity separation, validation, connection freezing and invalid-JSON preservation. JDK8 `./gradlew test build --offline` passes 46 tests with no failures/errors/skips; setup and whitespace checks pass. Automatic proxy-world matching is still unavailable: profile changes require reconnecting.


## Recorded terrain validation — issue #25 (2026-10-26)

Before the fix, focused tests reproduced an admitted 17MiB decoded payload and section Y16 reaching native world decoding. The inherited Anvil reader used infinite NBT accounting and could decode simulation tags. Recorded input, fallback input and verification readback now share decoded-byte and NBT-accounting limits of 16 MiB. A bounded preflight checks tag/list/string/array structure and lengths before the native parser, because Minecraft’s primitive-array accounting multiplies integers before widening and can overflow. A focused test reproduced the native allocation attempt; the final reader rejects the tiny forged record without allocating its declared array. A shared validator checks coordinates/version, section indexes and arrays, height/biome arrays and sky lighting; simulation tags are removed before fixing and native decoding. The cache format and profile/default keys are unchanged.

Exact artifact `e5ac00a95cd718caf935b550f8155fa5ea6f393f72753f6aad500bb2fa4cd36c` passed packaged isolated MultiMC with Sodium enabled. A valid gold/chest block-state record containing injected entity/tile-entity/tick tags loaded without simulation objects. Invalid sections, oversized records and an overflowing array-length record were rejected; nearby valid cached meshes still rendered and a normal diamond replacement saved, verified and reloaded. An oversized recorded chunk also used enabled fallback terrain successfully, with identical fallback source bytes and modification time and no gameplay-provider publication. The targeted fixture completed with zero failures.

The same artifact passed the broader GPU regression: 288 columns/288 meshes in the Overworld and 288 columns/2194 meshes in the Nether. Across 859 and 876 movement frames respectively (10 Overworld and 11 Nether boundary crossings), missing cached section frames were zero. Memory, renderer reload and disk reload comparisons had zero missing/changed terrain, light, mesh-byte or GPU vertex results. Both dimensions passed live handoff, packet invalidation/rebuild, and the final disable/re-enable controls recovered disk data. The run completed with zero failures.

JDK8 `./gradlew test build --offline` passes 54 tests with zero failures/errors/skips; setup and whitespace checks pass. This does not establish the complete release matrix, dedicated-server behavior or broad modpack compatibility. EULA acceptance and project licensing remain pending.


## Current-artifact renderer matrix (2026-10-26)

The preserved issue #25 artifact `e5ac00a95cd718caf935b550f8155fa5ea6f393f72753f6aad500bb2fa4cd36c` also passed the independent-distance fixture without LiteOptimizer and with LiteOptimizer 0.6.0-rc2 installed but `sodium: false`. The latter startup log explicitly confirmed `render/sodium: disabled`. Each run completed with zero failed assertions: live distance stayed 3, renderer distance was 10 with a 160-block camera distance, an explored target eight chunks away was cached and absent from the live provider, and vanilla compiled its terrain mesh. JSON reload reached 12; disabling restored 3/48 blocks; re-enabling restored 10. Combined with the Sodium-enabled checks above, this covers the three renderer configurations for this controlled distance scenario, rather than every scenario in the release matrix.

A later JDK8 offline test/build passed again. Rebuilding changes LiteLoader's generated revision and archive timestamps; the rebuilt archive has a different checksum. Runtime evidence here applies to the preserved artifact identified above. Compiled entries in that artifact and the rebuild were identical; only `litemod.json` content changed from revision 60 to 61.


The same artifact also passed an isolated integrated-world End-dimension run with Sodium enabled. After entering dimension 1 and exploring the target, it verified live distance 3, cache renderer distance 10, a nonempty cached GPU mesh eight chunks away with no live-provider target, config reload to 12 and disable/re-enable restoration. The run completed with zero failures. This covers entering and rendering in the End; End exit/credits and forced disconnect during pending I/O remain unchecked.


## Forced disconnect with queued writer work (2026-10-26)

The preserved `e5ac00a95cd718caf935b550f8155fa5ea6f393f72753f6aad500bb2fa4cd36c` artifact passed a packaged MultiMC/Sodium integrated-server disconnect fixture with zero failed assertions and no fatal log entries. It queued 100 tasks through the actual cache writer queue, verified that the writer was active with unfinished tasks, and invoked the server player's native disconnect method. The first task deliberately waited 1.5 seconds. All 100 tasks completed; the client world, cache world and storage cleared; old-world cache positions stayed empty after a further five-second wait.

This checks lifecycle cleanup and draining of admitted writer tasks, rather than simulating remote transport failure, physical disk latency or forced process termination. The existing persistence fixtures cover terrain serialization separately. End exit/credits, dedicated-server protocol coverage and measured travel performance remain release checks.


## Opt-in integrated-server live distance (2026-10-26)

The native settings panel and JSON now expose `integratedServerDistance`. Its default 0 preserves Minecraft's live distance. Positive values clamp to vanilla's server range of 3–32. While Bobby is enabled, only `IntegratedServer.tick` reads the override; disabling returns to the current client setting. Changes apply on an unpaused server tick. This changes singleplayer/LAN loading and tracking, including guests, and does not replace vanilla spawn-chunk rules. Dedicated-server classes and GameSettings are unchanged.

Artifact `a9d7985d940406724b4bd1f199c6238f0b8d959f77f4cd77dd8ef805441dd1e6` passed the packaged Sodium fixture: client distance remained 8, server PlayerList and all three world player-chunk maps used radius 3, and an explored target eight chunks away had a cached GPU mesh while absent from the live provider. JSON reload changed server distance to 5 and cached distance to 12. Disabling restored live distance 8, re-enabling restored 3, and Auto restored 8. The fixture completed with zero failures. Two initial fixture-only inherited-member remapping errors were corrected without production changes.

The same artifact passed native panel open/change/save/reopen, Auto/32 bounds, draft preservation on resize, Escape save and JSON reload with zero failures. Its actual panel screenshot was inspected. JDK8 test/build passes 57 tests with zero failures/errors/skips. These checks do not establish LAN guest behavior or dedicated-server protocol compatibility.


A repeat without LiteOptimizer also completed with zero failures on this exact artifact: all three world player-chunk maps and PlayerList used 3 while client distance stayed 8, cached target terrain compiled in vanilla beyond the live range, and override reload, disabling, re-enabling and Auto restored the expected distances. The isolated profile was restored to Sodium enabled and automatic live distance afterward.
