# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-09-09

First stable release for **Minecraft 1.21.1 / NeoForge 21.1.249** (Java 21). Same code as
`0.0.0-beta.13`; promoted to stable after running server-side in the *(Develop) Mystical Realms*
modded-server pack.

### Summary of the beta line

- **Datapack loader** — `/una datapack load|unload|reload|loadall|validate|list` for global packs
  in `<gameDir>/datapacks/`, with pack selection during `ServerAboutToStartEvent` so there is no
  extra startup resource reload.
- **Chunk pregeneration** — reworked over beta.9 → beta.13 into a load-safe design: async
  generation via `ServerChunkCache.getChunkFuture` with a bounded in-flight count, a per-tick
  wall-time budget, an MSPT throttle, a work/rest duty cycle, login-safe gating
  (`PlayerNegotiationEvent` + connection phase), and a deferred, dedicated-server-only auto-start.
  Progress persists as a compact counter (`pregen_progress.json` no longer grows unbounded).
  Traversal is an outward square spiral from the center. `pregen.radius` semantics: `0` disabled,
  `-1` infinite spiral, `>0` finite Chebyshev radius. **`pregen.enabled` now defaults to `false`**
  — pregen never auto-starts unless explicitly enabled.
- **`[schedule]` section** — optional daily pregen burst window (default 08:00–10:00 server time)
  that bypasses the throttle and player-pause, saves progress and emits a restart warning on
  window close.
- **Server-only pregen config** — `[pregen]` / `[schedule]` live in a `SERVER` spec
  (`utility_nexus/admin/pregen.toml`); the common config keeps `[datapack]`, `[commands]`,
  `[logging]`. beta.13 guards `ModConfig#getFullPath()` so server shutdown / integrated-client
  sync no longer throw.

### Notes

- No code change relative to `0.0.0-beta.13`. Verified: `./gradlew clean build` is green.

## [0.0.0-beta.13] - 2026-09-03

### Fixed
- **`onModConfig` crashed the mod-config event on a non-file SERVER config.** beta.12 added a `ModConfigEvent` listener that called `ModConfig#getFullPath()` unconditionally to capture the on-disk path of the `pregen.toml` SERVER config. That method throws `IllegalStateException` when the config is not file-backed, which happens when the SERVER spec is torn down on dedicated-server shutdown (`ModConfig.setConfig(null)` still dispatches the event) and when an integrated client receives the SERVER config over the network. The exception surfaced as `Exception caught during firing event: Cannot call getFullPath() on non-file config null at path utility_nexus/admin/pregen.toml` on every server stop. The listener now guards the call with a try/catch and a null check; when no real path is available it keeps the existing `<world>/serverconfig/utility_nexus/admin/pregen.toml` fallback.
- Updated mod version to 0.0.0-beta.13

## [0.0.0-beta.12] - 2026-09-03

### Added
- **Outward square spiral traversal.** Chunk pregeneration now walks an Ulam-style spiral outward from the center instead of a raster from the corner, giving better early-game coverage around spawn. Progress persists as a single `spiralIndex` in `pregen_progress.json`; resume restores the exact spiral position. Old raster progress files (no `mode="spiral"` marker, or mismatched config) are discarded and regeneration starts fresh from the center.
- **New `[schedule]` config section — daily pregen burst window.** With `schedule.enabled = true`, pregeneration activates during a configurable hour window (default 08:00–10:00 local server time) in aggressive burst mode that bypasses the MSPT throttle, the work/rest cycle, and player-pause. The window wraps past midnight if `endHour <= startHour`. On window close, progress is saved and a prominent restart warning is emitted three times. Burst params: `schedule.burstChunksPerTick` (64), `schedule.burstMaxConcurrentChunks` (16), `schedule.burstMaxMillisPerTick` (30), `schedule.burstMaxServerMspt` (500), plus `schedule.ignorePlayers` (true), `schedule.autoStopAtWindowEnd` (true), `schedule.requireRestartAfter` (true).
- **Pregeneration config is now server-only.** `[pregen]` and `[schedule]` moved into a separate `ModConfig.Type.SERVER` spec registered as `utility_nexus/admin/pregen.toml`. On a dedicated server this file lives in `<world>/serverconfig/`; on an integrated server it is not generated at all. The common `config/utility_nexus/admin/config.toml` keeps only `[datapack]`, `[commands]`, `[logging]`. `/una config reload` now reads both files (the real SERVER path is captured from a `ModConfigEvent`).
- Pregeneration auto-start and the scheduled burst window are gated behind `server.isDedicatedServer()` — single-player / integrated servers never auto-start pregen.
- `/una pregen status` now reports burst mode and the schedule state (`idle | burst (ends in Ns) | waiting (window in Ns)`).
- The restart-warning thread is a daemon thread so it cannot delay JVM shutdown.

### Changed
- **`pregen.radius` semantics rewritten.** `0` = disabled (`start()` returns a failure); `-1` = infinite outward spiral (never completes, until `/una pregen stop`); `>0` = finite Chebyshev radius, complete once `max(|dx|,|dz|) > radius`. The old `INFINITE_RADIUS_FALLBACK` constant (12,500) has been removed.
- `pregen.enabled` default changed from `true` to `false` — pregeneration no longer auto-starts unless explicitly enabled.
- `pregen.chunksPerTick` default changed from `50` to `8` (range `1..200` unchanged). `/una pregen config radius` now accepts `-1`.
- Fixed an SLF4J placeholder bug in the burst-window log message (unsupported `{:02d}` replaced with `String.format`).
- Updated mod version to 0.0.0-beta.12

## [0.0.0-beta.11] - 2026-09-02

### Fixed
- **Chunk pregeneration auto-start never fired.** beta.10 moved the deferred auto-start into `ChunkPregenerator.onServerTick(...)`, but `UtilityNexusAdmin.onServerTick` only called that method when `chunkPregenerator.isRunning()` was already true. Since pregen is not running before it starts, the auto-start branch was unreachable and `Pregen auto-start scheduled (delay: 60s)` was never followed by anything. On a live dedicated server the pregen simply never began. `/una pregen start` was unaffected (it calls `start()` directly). The tick is now always forwarded to `ChunkPregenerator`, which early-returns internally when pregen is neither scheduled nor running.

### Changed
- Updated mod version to 0.0.0-beta.11

## [0.0.0-beta.10] - 2026-09-02

### Fixed
- **Chunk pregeneration could saturate the server thread and leave players stuck on "Loading terrain" forever.** `ChunkPregenerator` generated chunks *synchronously on the main server thread* inside `ServerTickEvent.Post` (up to `chunksPerTick` = 50 blocking `getChunk` calls per tick, no time budget). On a real server this produced `Can't keep up! Running 30000ms+ behind`, and a player connecting never completed login because the thread never caught up — so the client sat on the dirt screen until it was killed.

### Changed
- **Async generation.** Chunks are now requested via `ServerChunkCache.getChunkFuture(..., FULL, true)` with a bounded number of in-flight futures (`pregen.maxConcurrentChunks`, default 4). The main thread only submits and drains completed futures; it never blocks on generation.
- **Per-tick wall-time budget.** `pregen.maxMillisPerTick` (default 8) caps how long pregen bookkeeping may run each tick.
- **Load-aware throttle.** When the mean tick time exceeds `pregen.maxServerMspt` (default 45) the pregenerator skips that tick entirely.
- **Work / rest duty cycle.** `pregen.workSeconds` (default 300) of generation followed by `pregen.restSeconds` (default 120) of pause, repeating; `restSeconds = 0` disables resting.
- **Login-safe gating.** Pregen now pauses on `PlayerNegotiationEvent` (fires during login, before the player exists) and while any connection is in the `LOGIN` or `CONFIGURATION` phase; server-list pings (`STATUS`) are ignored so a frequently-pinged public server is not starved. After the last player leaves it waits `pregen.resumeGraceSeconds` (default 30) of continuous emptiness before resuming.
- **Deferred auto-start.** Pregen no longer auto-starts in `ServerStartingEvent`. It starts from `ServerStartedEvent` plus a `pregen.startupDelaySeconds` (default 60) grace delay, and only while the server is empty. `/una pregen start` still starts immediately.
- **Datapacks: no startup resource reload.** Global packs from `<gameDir>/datapacks/` are now selected during `ServerAboutToStartEvent`, before the world's first datapack load, so the unconditional `server.reloadResources().join()` that used to run right after `Done` (colliding with pregen) is gone. The `/una datapack load|unload|reload|loadall|validate|list` runtime commands are unchanged.
- `/una pregen status` now reports the current phase (WORK/REST), in-flight chunk count, whether the last tick was MSPT-throttled, and seconds until the next phase switch.
- Seven new `[pregen]` config keys: `maxConcurrentChunks`, `maxMillisPerTick`, `maxServerMspt`, `workSeconds`, `restSeconds`, `resumeGraceSeconds`, `startupDelaySeconds`.
- Updated mod version to 0.0.0-beta.10

## [0.0.0-beta.9] - 2026-09-01

### Fixed
- **`pregen_progress.json` growing to millions of lines without pregenerating anything** — `PregenerationProgress` stored one JSON entry per generated chunk in a `List<String>`, and with pretty-printing every entry was its own line. Two cursor bugs made it far worse:
  - With `pregen.radius <= 0` ("infinite"), `advanceToNextChunk()` returned immediately without moving the cursor, so the scan re-processed chunk `(0,0)` every tick forever, appending `"0,0"` each time
  - Even with a positive radius, when the cursor reached the last chunk `(maxX, maxZ)` it stopped advancing `currentChunkZ`, so `isComplete()` never became true and the last chunk was regenerated forever
- The progress model now stores a plain `int` counter instead of the chunk list; the file stays a fixed ~10 lines
- `pregen.radius <= 0` is now treated as a fixed large finite radius (`INFINITE_RADIUS_FALLBACK = 12500` chunks / 200,000 blocks); a true unbounded scan is not supported
- `advanceToNextChunk()` / `isComplete()` rewritten as a correct row-major raster that terminates
- `loadProgress()` now only restores the cursor when the saved dimension/center/radius still match the current config (instead of replacing the whole progress object, which could reintroduce a stale radius of 0), and catches any parse error so a corrupt or oversized legacy file cannot crash startup

### Changed
- Updated mod version to 0.0.0-beta.9

## [0.0.0-beta.8] - 2026-09-01

### Removed
- **Shader system** — the whole `com.skd.utilitynexusadmin.shader` package (`ShaderConfig`, `ShaderPackManager`, `ShaderPipeline`, `ShaderSystem`) is gone, along with the `shader.activePack` / `shader.autoScan` config keys. It was an unfinished skeleton (compiled GLSL programs but never applied them to rendering, no GUI) and the feature is retired in favor of an external shader-compat mod instead of a home-grown engine
- Also drops the note in `UtilityNexusAdmin.VERSION` that was still hardcoded to `0.0.0-beta.6`

### Changed
- Updated mod version to 0.0.0-beta.8

## [0.0.0-beta.7] - 2026-09-01

### Fixed
- **Startup crash `IllegalStateException: Cannot get config value before config is loaded`** — the mod constructor logged through `UNALog.info()`, which reads the `logging.level` config value; NeoForge has not loaded config values yet at construction time, so the whole mod failed to load (with a secondary "Sodium's config could not be found" client crash as fallout)
- The constructor now logs via the raw logger, and `UNALog` falls back to `INFO` when queried before the config is available

### Changed
- Updated mod version to 0.0.0-beta.7

## [0.0.0-beta.6] - 2026-09-01

### Added
- **Global datapacks** — datapacks (folders or `.zip`) placed in `<gameDir>/datapacks/` are now injected into every world on both client and server, via `AddPackFindersEvent` + auto-selection on server start (vanilla only loads per-world `saves/<world>/datapacks/`)
- `logging.level` config key (`DEBUG`/`INFO`/`WARN`/`ERROR`) with a `UNALog` helper that filters the mod's own log output; `ERROR` is always emitted

### Changed
- **Config engine migrated to NeoForge `ModConfigSpec`** — the config is now a real commented TOML at `config/utility_nexus/admin/config.toml` (previously a Java Properties file with a `.toml` extension)
- `/una config get|set|reload` keep working against `section.key` names; `set` now persists to disk immediately
- Datapack subsystem no longer copies packs or keeps backups; the 6 `/una datapack` commands (`list`/`load`/`unload`/`reload`/`validate`/`loadall`) now operate on the running `PackRepository` (`load` = enable, `unload` = disable)
- Chunk pregeneration only advances while the server has **zero players online** — auto-starts on server start when `pregen.enabled`, pauses on player join, resumes when the last player leaves
- `/una` is now gated by the configurable `commands.opLevel`; `commands.enableDatapackCommands` / `commands.enableConfigCommands` actually register or skip their subtrees
- Updated mod version to 0.0.0-beta.6

### Removed
- Config keys `datapack.configDirectory`, `datapack.backupDirectory`, `logging.fileEnabled`, `logging.discordWebhook`, `permissions.useLuckPerms`, `permissions.defaultOpLevel` (unused placeholders or dropped features — there is no LuckPerms integration)

## [0.0.0-beta.5] - 2026-09-01

### Fixed
- **Missing mod loader declaration** — the `neoforge.mods.toml` template omitted the required `modLoader` and `loaderVersion` keys, so NeoForge rejected the jar with `InvalidModFileException: Missing ModLoader in file` and the game crashed on startup (secondary Quark/Zeta `RuntimeException: Where is minecraft???!` while rendering the error screen)
- Added `modLoader="javafml"` and `loaderVersion="${loader_version_range}"` to the template, `loader_version_range=[4,)` to `gradle.properties`, and wired the new property into the `generateModMetadata` task

### Changed
- Updated mod version to 0.0.0-beta.5

## [0.0.0-beta.4] - 2026-09-01

### Added
- Shader system compatible with Sodium 0.8.13+ on NeoForge 1.21.1
- `ShaderPackManager` — loads `.zip`/`.jar` shaderpacks from `shaderpacks/` folder
- `ShaderPipeline` — compiles GLSL shaders (vertex, fragment, geometry) via LWJGL/OpenGL
- `ShaderSystem` — reload listener registration and active-pack persistence
- `ShaderConfig` — configuration in `utility_nexus_admin.toml` (active pack, auto-scan)
- Iris-style external config support (`<shaderpack>.zip.txt` — JSON and key=value properties)
- Shaderpack metadata support via `pack.mcmeta`
- Built-in shader uniforms (ProjectionMatrix, ViewMatrix, CameraPos, SunPos, Time, FogColor, FogStart, FogEnd, RenderStage)
- New mod icon

### Changed
- Updated mod version to 0.0.0-beta.4

### Fixed
- N/A

## [0.0.0-beta.3] - 2025-08-31

### Added
- Chunk pregeneration system with progress persistence
- Commands: `/una pregen start|stop|pause|resume|status`
- Configuration: `/una pregen config enabled|center|radius|chunksPerTick|dimension`
- Auto-pause when players join, auto-resume when last player leaves
- Progress saved to `utility_nexus_admin/pregen_progress.json` (persists across restarts)
- Configurable center (default 0,0), radius (0=infinite), chunks per tick, target dimension
- Logging every 30s with chunks generated, current position, percentage

### Changed
- Updated mod version to 0.0.0-beta.3
- Moved pregen progress file to root directory (`utility_nexus_admin/`)

### Fixed
- N/A

## [0.0.0-beta.2] - 2025-08-31

### Added
- Datapack loader functionality with hot-reload support
- Commands: `/una datapack list|load|unload|reload|validate|loadall`
- Configuration system with TOML config file (`config/utility_nexus_admin.toml`)
- Commands: `/una config reload|get|set`
- Auto-load datapacks on server start (configurable)
- Validation and auto-creation of missing `pack.mcmeta`
- Support for both directory and ZIP datapacks

### Changed
- Updated mod version to 0.0.0-beta.2

### Fixed
- N/A

## [0.0.0-beta.1] - 2025-08-31

### Added
- Initial project setup for Minecraft 1.21.1 with NeoForge 21.1.249
- Basic mod structure with UtilityNexusAdmin main class
- Datapack loader foundation
- Gradle build configuration with NeoForge moddev plugin
- CI/CD pipeline with GitLab CI
- Documentation structure for CurseForge publishing

### Changed
- N/A

### Fixed
- N/A

## [Unreleased]

### Added
- **Outward square spiral traversal** — chunk pregeneration now walks an Ulam-style spiral from the center instead of a raster from the corner. Each generated chunk expands outward ring-by-ring, producing better early-game coverage around spawn. Progress persists as a single `spiralIndex` in `pregen_progress.json`; resume restores the exact spiral position. Old raster progress files are rejected (start fresh).
- **New `[schedule]` config section** for a daily pregen burst window. When `schedule.enabled = true`, pregeneration activates during the configured hour window (default 08:00–10:00 local server time) in aggressive burst mode that bypasses MSPT throttle, work/rest cycle, and player-pause. The window wraps past midnight if `endHour <= startHour`. On window close, progress is saved and a prominent restart warning is emitted 3 times.
- `schedule.burstChunksPerTick` (64), `schedule.burstMaxConcurrentChunks` (16), `schedule.burstMaxMillisPerTick` (30), `schedule.burstMaxServerMspt` (500) — burst-specific throughput params.
- `schedule.ignorePlayers` (true) — generate even when players are online during burst window.
- `schedule.autoStopAtWindowEnd` (true), `schedule.requireRestartAfter` (true).
- `/una pregen status` now shows burst mode and schedule status (`idle | burst (ends in Ns) | waiting (window in Ns)`).

### Changed
- **`pregen.radius` semantics rewritten.** `0` = disabled (start returns failure); `-1` = infinite outward spiral (never completes, until `/una pregen stop`); `>0` = finite Chebyshev radius. The old `INFINITE_RADIUS_FALLBACK` constant (12500) has been removed.
- `pregen.enabled` default changed from `true` to `false` — no longer auto-starts on server launch unless explicitly enabled.
- `pregen.chunksPerTick` default changed from `50` to `8` (range 1..200 unchanged).
- Progress file now includes `mode="spiral"` field for version tracking; old progress files without this field or with mismatched config are discarded.
- `/una pregen config radius` now accepts `-1` (infinite) and correctly labels `0` as disabled.
- Burst mode bypasses all player-pause logic when `schedule.ignorePlayers = true`.

### Planned
- Admin command system (player, world, server management)
- LuckPerms permission integration
- Discord webhook logging