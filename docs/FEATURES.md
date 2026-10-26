# Features and limits

This is a Minecraft 1.12.2 LiteLoader implementation inspired by Bobby. It does not import modern Bobby caches or claim full upstream feature parity. See [compatibility evidence](COMPATIBILITY.md) for the exact builds and scenarios checked.

| Feature | Current behavior |
| --- | --- |
| Persistent terrain | Detached block, biome, height and light snapshots in a separate Anvil cache |
| Extended terrain distance | Independent `cacheRenderDistance`, default 16, limited to 2–32 chunks; live terrain retains priority |
| Restart/session recovery | Recorded terrain reloads asynchronously; accepted writes drain on orderly shutdown |
| Fallback world | Opt-in read-only Minecraft 1.12.2 `saves/bobby-fallback`, including dimension directories; live > recorded > fallback |
| Configuration reload | JSON changes reload while running; enable/disable and fallback changes clear stale render state |
| Configuration UI | Native LiteLoader panel edits caching, distance, fallback and next-connection profile; an optional Controls key toggles caching |
| Corrupt terrain records | Bounded NBT reads and metadata validation; rejected recorded data can use enabled fallback terrain |
| Cache retention | Fixed 2 GiB budget with startup pruning and write headroom; no configurable quota yet |
| World identity | Server address plus server-list name, or integrated save folder; optional named profile namespace, separated by dimension |
| Automatic multiworld detection | Not included; users select separate profiles and reconnect when switching proxy worlds or resets |
| Block entities and entities | Excluded from snapshots/fallback decoding; no cached simulation or interactions |
| Cached terrain tint | Not included |
| Unload delay | No configurable delay; terrain is captured when live chunks unload |
| Integrated-server distance override | Opt-in `integratedServerDistance`: 0 follows Minecraft; 3–32 overrides singleplayer/LAN loading and tracking while Bobby is enabled |
| World selection/merge/upgrade commands | Manual profile selection through settings/JSON; merge and upgrade commands are not included |
| Modern world/cache import | Not supported; no conversion or mutation of source saves |

Only enable the shared fallback source while connected to its matching world. Cached terrain can be stale and cannot keep farms running or extend interaction distance. Force-killing Minecraft or a persistently unavailable disk can still lose unsaved terrain.
