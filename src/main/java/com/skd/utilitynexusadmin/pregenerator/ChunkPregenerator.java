package com.skd.utilitynexusadmin.pregenerator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.skd.utilitynexusadmin.config.UNAConfig;
import com.skd.utilitynexusadmin.logging.UNALog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.server.level.ChunkResult;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChunkPregenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Phase { WORK, REST }

    private final MinecraftServer server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UtilityNexusAdmin-ChunkPregenerator");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private PregenerationProgress progress;
    private ServerLevel targetLevel;

    private int totalChunksToGenerate;
    private int chunksGeneratedThisSession;
    private long lastLogTime;

    // --- A1: async in-flight tracking ---
    private final ConcurrentHashMap<Long, CompletableFuture<ChunkResult<ChunkAccess>>> inFlightFutures = new ConcurrentHashMap<>();
    private int chunksSubmittedThisTick;

    // --- A2: wall-time budget ---
    private long tickStartNanos;

    // --- A3: MSPT throttle ---
    private boolean msptThrottledLastTick;

    // --- A4: work/rest duty cycle ---
    private Phase currentPhase = Phase.WORK;
    private long phaseStartMillis;
    private long serverStartedMillis;

    // --- A6: deferred auto-start ---
    private boolean autoStartScheduled;
    private long serverStartedAtMillis;

    // --- Schedule: burst mode ---
    private boolean burstModeActive;
    private boolean wasInWindow;
    private int restartWarningCount;

    public ChunkPregenerator(MinecraftServer server) {
        this.server = server;
        this.progress = new PregenerationProgress();
        this.lastLogTime = System.currentTimeMillis();
        this.phaseStartMillis = System.currentTimeMillis();
        this.serverStartedMillis = System.currentTimeMillis();
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    public CompletableFuture<PregenerationResult> start() {
        return CompletableFuture.supplyAsync(() -> {
            if (running.get()) {
                return PregenerationResult.failure("Pregeneration already running");
            }

            if (!UNAConfig.pregenEnabled()) {
                return PregenerationResult.failure("Pregeneration is disabled in config");
            }

            int configuredRadius = UNAConfig.pregenRadius();
            if (configuredRadius == 0) {
                return PregenerationResult.failure("radius 0 = disabled");
            }

            try {
                initialize();
                loadProgress();

                running.set(true);
                paused.set(false);
                cancelled.set(false);
                chunksGeneratedThisSession = 0;
                lastLogTime = System.currentTimeMillis();
                currentPhase = Phase.WORK;
                phaseStartMillis = System.currentTimeMillis();
                autoStartScheduled = false;

                UNALog.info("Starting chunk pregeneration for dimension: {}", progress.dimension());
                UNALog.info("Center: ({}, {}), Radius: {}", progress.startX(), progress.startZ(),
                        progress.radius() < 0 ? "infinite" : progress.radius() + " chunks");

                return PregenerationResult.success("Pregeneration started", progress);
            } catch (Exception e) {
                UNALog.error("Failed to start pregeneration", e);
                running.set(false);
                return PregenerationResult.failure("Error starting pregeneration: " + e.getMessage());
            }
        }, executor);
    }

    /**
     * Start pregeneration in burst mode using schedule config values.
     * Bypasses MSPT throttle, work/rest cycle, and player-pause.
     */
    public CompletableFuture<PregenerationResult> startBurst() {
        return CompletableFuture.supplyAsync(() -> {
            if (running.get()) {
                return PregenerationResult.failure("Pregeneration already running");
            }

            int configuredRadius = UNAConfig.pregenRadius();
            if (configuredRadius == 0) {
                return PregenerationResult.failure("radius 0 = disabled");
            }

            try {
                initialize();
                loadProgress();

                running.set(true);
                paused.set(false);
                cancelled.set(false);
                chunksGeneratedThisSession = 0;
                lastLogTime = System.currentTimeMillis();
                currentPhase = Phase.WORK;
                phaseStartMillis = System.currentTimeMillis();
                autoStartScheduled = false;
                burstModeActive = true;

                UNALog.info("Starting chunk pregeneration BURST for dimension: {}", progress.dimension());
                UNALog.info("Center: ({}, {}), Radius: {}", progress.startX(), progress.startZ(),
                        progress.radius() < 0 ? "infinite" : progress.radius() + " chunks");

                return PregenerationResult.success("Pregeneration burst started", progress);
            } catch (Exception e) {
                UNALog.error("Failed to start pregeneration burst", e);
                running.set(false);
                burstModeActive = false;
                return PregenerationResult.failure("Error starting pregeneration burst: " + e.getMessage());
            }
        }, executor);
    }

    public CompletableFuture<PregenerationResult> stop() {
        return CompletableFuture.supplyAsync(() -> {
            if (!running.get()) {
                return PregenerationResult.failure("Pregeneration not running");
            }

            cancelled.set(true);
            running.set(false);
            burstModeActive = false;
            inFlightFutures.clear();
            saveProgress();

            UNALog.info("Chunk pregeneration stopped. Progress saved. Total chunks generated: {}", progress.generatedChunksCount());
            return PregenerationResult.success("Pregeneration stopped. Progress saved.", progress);
        }, executor);
    }

    public CompletableFuture<PregenerationResult> pause() {
        return CompletableFuture.supplyAsync(() -> {
            if (!running.get() || paused.get()) {
                return PregenerationResult.failure("Pregeneration not running or already paused");
            }

            paused.set(true);
            saveProgress();

            UNALog.info("Chunk pregeneration paused at chunk ({}, {})", progress.currentChunkX(), progress.currentChunkZ());
            return PregenerationResult.success("Pregeneration paused. Progress saved.", progress);
        }, executor);
    }

    public CompletableFuture<PregenerationResult> resume() {
        return CompletableFuture.supplyAsync(() -> {
            if (!running.get() || !paused.get()) {
                return PregenerationResult.failure("Pregeneration not paused");
            }

            paused.set(false);
            currentPhase = Phase.WORK;
            phaseStartMillis = System.currentTimeMillis();

            UNALog.info("Chunk pregeneration resumed from chunk ({}, {})", progress.currentChunkX(), progress.currentChunkZ());
            return PregenerationResult.success("Pregeneration resumed.", progress);
        }, executor);
    }

    public void shutdown() {
        if (running.get()) {
            cancelled.set(true);
            running.set(false);
            saveProgress();
        }
        executor.shutdown();
    }

    // ========================================================================
    // Deferred auto-start (A6)
    // ========================================================================

    public void markServerStarted() {
        this.serverStartedMillis = System.currentTimeMillis();
        this.autoStartScheduled = true;
        UNALog.info("Pregen auto-start scheduled (delay: {}s)", UNAConfig.startupDelaySeconds());
    }

    // ========================================================================
    // Tick handler (called by UtilityNexusAdmin)
    // ========================================================================

    public void onServerTick(ServerTickEvent.Post event) {
        // Auto-start and scheduled burst only on dedicated servers
        if (server.isDedicatedServer()) {
            handleScheduleWindow();
        }

        // --- A6: deferred auto-start check (dedicated only) ---
        if (server.isDedicatedServer() && autoStartScheduled && !running.get() && !cancelled.get()) {
            long elapsed = System.currentTimeMillis() - serverStartedMillis;
            if (elapsed >= UNAConfig.startupDelaySeconds() * 1000L) {
                autoStartScheduled = false;
                if (UNAConfig.pregenEnabled() && server.getPlayerList().getPlayers().isEmpty()) {
                    start().thenAccept(result -> {
                        if (result.success()) {
                            UNALog.info("Auto-started chunk pregeneration (delayed)");
                        } else {
                            UNALog.info("Chunk pregeneration not started: {}", result.message());
                        }
                    });
                }
            }
            return;
        }

        if (!running.get() || paused.get() || cancelled.get()) {
            return;
        }

        // --- Burst mode: bypass MSPT throttle, work/rest, player-pause ---
        if (!burstModeActive) {
            // --- A4: work/rest duty cycle ---
            updatePhase();

            if (currentPhase == Phase.REST) {
                return;
            }

            // --- A3: MSPT throttle ---
            float currentMspt = server.getCurrentSmoothedTickTime();
            if (currentMspt > UNAConfig.maxServerMspt()) {
                msptThrottledLastTick = true;
                return;
            }
            msptThrottledLastTick = false;
        }

        // --- A2: wall-time budget ---
        tickStartNanos = System.nanoTime();
        int effectiveMaxMillis = burstModeActive ? UNAConfig.burstMaxMillisPerTick() : UNAConfig.maxMillisPerTick();
        long budgetNanos = effectiveMaxMillis * 1_000_000L;

        // Drain completed futures first
        drainCompletedFutures();

        // --- A1: submit new async chunk requests ---
        int effectiveChunksPerTick = burstModeActive ? UNAConfig.burstChunksPerTick() : UNAConfig.chunksPerTick();
        int effectiveMaxConcurrent = burstModeActive ? UNAConfig.burstMaxConcurrentChunks() : UNAConfig.maxConcurrentChunks();
        int maxSubmit = Math.min(effectiveChunksPerTick, effectiveMaxConcurrent - inFlightFutures.size());
        chunksSubmittedThisTick = 0;

        while (chunksSubmittedThisTick < maxSubmit && !progress.isComplete()) {
            if ((System.nanoTime() - tickStartNanos) > budgetNanos) {
                break;
            }

            ChunkPos chunkPos = getNextChunkPos();
            if (chunkPos == null) {
                break;
            }

            submitChunkFuture(chunkPos.x, chunkPos.z);
            progress.advanceToNextChunk();
            chunksSubmittedThisTick++;
        }

        logProgress();
    }

    // ========================================================================
    // Schedule window handling
    // ========================================================================

    private void handleScheduleWindow() {
        if (!UNAConfig.scheduleEnabled()) {
            wasInWindow = false;
            return;
        }

        LocalTime now = LocalTime.now();
        int startHour = UNAConfig.scheduleStartHour();
        int endHour = UNAConfig.scheduleEndHour();
        boolean inWindow = isWithinWindow(now, startHour, endHour);

        if (inWindow && !wasInWindow) {
            // Entering window — start burst mode
            wasInWindow = true;
            restartWarningCount = 0;
            if (!running.get()) {
                UNALog.info("Scheduled pregen burst STARTED (window {})",
                        String.format("%02d:00-%02d:00", startHour, endHour));
                startBurst().thenAccept(result -> {
                    if (result.success()) {
                        UNALog.info("Scheduled pregen burst started successfully");
                    } else {
                        UNALog.info("Scheduled pregen burst failed: {}", result.message());
                    }
                });
            }
        } else if (!inWindow && wasInWindow) {
            // Leaving window — stop burst if active
            wasInWindow = false;
            if (burstModeActive && running.get()) {
                UNALog.info("Scheduled pregen burst window ended, stopping...");
                stop().thenAccept(result -> {
                    if (UNAConfig.scheduleAutoStopAtWindowEnd() && UNAConfig.scheduleRequireRestartAfter()) {
                        emitRestartWarnings();
                    }
                });
            }
        }
    }

    private static boolean isWithinWindow(LocalTime now, int startHour, int endHour) {
        int nowMinutes = now.getHour() * 60 + now.getMinute();
        int startMinutes = startHour * 60;
        int endMinutes = endHour * 60;

        if (startMinutes <= endMinutes) {
            // Normal window: e.g. 08:00 - 10:00
            return nowMinutes >= startMinutes && nowMinutes < endMinutes;
        } else {
            // Wraps past midnight: e.g. 22:00 - 06:00
            return nowMinutes >= startMinutes || nowMinutes < endMinutes;
        }
    }

    private void emitRestartWarnings() {
        Thread t = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                UNALog.warn("=== VENTANA DE PREGEN CERRADA. REINICIA EL SERVIDOR AHORA para liberar memoria/caches de generacion. ===");
                try { Thread.sleep(5000); } catch (InterruptedException ignored) { break; }
            }
        }, "UtilityNexusAdmin-RestartWarning");
        t.setDaemon(true);
        t.start();
    }

    // ========================================================================
    // Async chunk generation (A1)
    // ========================================================================

    private void submitChunkFuture(int x, int z) {
        long key = ChunkPos.asLong(x, z);
        CompletableFuture<ChunkResult<ChunkAccess>> future =
                targetLevel.getChunkSource().getChunkFuture(x, z, ChunkStatus.FULL, true);

        inFlightFutures.put(key, future);

        future.whenComplete((result, throwable) -> {
            server.execute(() -> {
                inFlightFutures.remove(key);
                if (throwable != null) {
                    UNALog.warn("Failed to generate chunk ({}, {}): {}", x, z, throwable.getMessage());
                } else {
                    progress.markChunkGenerated(x, z);
                    chunksGeneratedThisSession++;
                }

                if (progress.isComplete() && inFlightFutures.isEmpty()) {
                    finishPregeneration();
                }
            });
        });
    }

    private void drainCompletedFutures() {
        Iterator<Map.Entry<Long, CompletableFuture<ChunkResult<ChunkAccess>>>> it =
                inFlightFutures.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, CompletableFuture<ChunkResult<ChunkAccess>>> entry = it.next();
            CompletableFuture<ChunkResult<ChunkAccess>> future = entry.getValue();
            if (future.isDone()) {
                it.remove();
                try {
                    ChunkResult<ChunkAccess> result = future.join();
                    if (!result.isSuccess()) {
                        ChunkPos pos = new ChunkPos(entry.getKey());
                        UNALog.warn("Chunk ({}, {}) generation error: {}", pos.x, pos.z, result.getError());
                    }
                } catch (Exception e) {
                    // Already handled in whenComplete
                }
            }
        }
    }

    // ========================================================================
    // Work/rest duty cycle (A4)
    // ========================================================================

    private void updatePhase() {
        int workSec = UNAConfig.workSeconds();
        int restSec = UNAConfig.restSeconds();

        if (restSec <= 0) {
            currentPhase = Phase.WORK;
            return;
        }

        long elapsedSec = (System.currentTimeMillis() - phaseStartMillis) / 1000;

        if (currentPhase == Phase.WORK && elapsedSec >= workSec) {
            UNALog.info("Pregen: switching to REST phase ({}s)", restSec);
            currentPhase = Phase.REST;
            phaseStartMillis = System.currentTimeMillis();
        } else if (currentPhase == Phase.REST && elapsedSec >= restSec) {
            UNALog.info("Pregen: switching to WORK phase ({}s)", workSec);
            currentPhase = Phase.WORK;
            phaseStartMillis = System.currentTimeMillis();
        }
    }

    // ========================================================================
    // Getters for status (A8)
    // ========================================================================

    public Phase getCurrentPhase() { return currentPhase; }
    public int getInFlightCount() { return inFlightFutures.size(); }
    public boolean isMsptThrottled() { return msptThrottledLastTick; }
    public boolean isBurstMode() { return burstModeActive; }

    public long getSecondsUntilNextPhaseSwitch() {
        int currentPhaseDuration = (currentPhase == Phase.WORK) ? UNAConfig.workSeconds() : UNAConfig.restSeconds();
        if (currentPhaseDuration <= 0) return 0;
        long elapsed = (System.currentTimeMillis() - phaseStartMillis) / 1000;
        return Math.max(0, currentPhaseDuration - elapsed);
    }

    /**
     * Seconds remaining in the current schedule window, or -1 if outside any window.
     */
    public int getScheduleSecondsRemaining() {
        if (!UNAConfig.scheduleEnabled()) return -1;
        LocalTime now = LocalTime.now();
        int startHour = UNAConfig.scheduleStartHour();
        int endHour = UNAConfig.scheduleEndHour();
        if (!isWithinWindow(now, startHour, endHour)) return -1;

        int nowMinutes = now.getHour() * 60 + now.getMinute();
        int endMinutes = endHour * 60;
        if (startHour <= endHour) {
            return Math.max(0, (endMinutes - nowMinutes) * 60 - now.getSecond());
        } else {
            // wraps midnight
            if (nowMinutes >= startHour * 60) {
                return Math.max(0, ((24 * 60 - nowMinutes) + endMinutes) * 60 - now.getSecond());
            } else {
                return Math.max(0, (endMinutes - nowMinutes) * 60 - now.getSecond());
            }
        }
    }

    /**
     * Returns a human-readable schedule status string for the command.
     */
    public String getScheduleStatusString() {
        if (!UNAConfig.scheduleEnabled()) return "idle (disabled)";
        if (burstModeActive) {
            int secs = getScheduleSecondsRemaining();
            if (secs >= 0) {
                return "burst (ends in " + secs + "s)";
            }
            return "burst";
        }
        int secs = getScheduleSecondsRemaining();
        if (secs >= 0) {
            return "waiting (window in " + secs + "s)";
        }
        return "idle";
    }

    public PregenerationProgress getProgress() { return progress; }
    public boolean isRunning() { return running.get(); }
    public boolean isPaused() { return paused.get(); }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private void initialize() {
        ResourceLocation dimensionId = ResourceLocation.parse(UNAConfig.dimension());
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);

        this.targetLevel = server.getLevel(dimensionKey);
        if (targetLevel == null) {
            throw new IllegalStateException("Dimension not found: " + dimensionId);
        }

        int configuredRadius = UNAConfig.pregenRadius();

        progress = new PregenerationProgress(
            UNAConfig.pregenStartX(),
            UNAConfig.pregenStartZ(),
            configuredRadius,
            dimensionId.toString()
        );

        calculateTotalChunks();
    }

    private void calculateTotalChunks() {
        int radius = progress.radius();
        if (radius <= 0) {
            totalChunksToGenerate = -1;
        } else {
            int diameter = radius * 2 + 1;
            totalChunksToGenerate = diameter * diameter;
        }
    }

    private ChunkPos getNextChunkPos() {
        if (progress.isComplete()) {
            return null;
        }
        return new ChunkPos(progress.currentChunkX(), progress.currentChunkZ());
    }

    private void logProgress() {
        long now = System.currentTimeMillis();
        if (now - lastLogTime >= 30000) {
            double percent = totalChunksToGenerate > 0 ? (progress.generatedChunksCount() * 100.0 / totalChunksToGenerate) : 0;
            UNALog.info("Pregen [{}, burst={}, inFlight={}, msptThrottled={}, nextPhase {}s]: {}/{} chunks ({}%) - Current: ({}, {}) - Session: {}",
                currentPhase, burstModeActive, inFlightFutures.size(), msptThrottledLastTick, getSecondsUntilNextPhaseSwitch(),
                progress.generatedChunksCount(),
                totalChunksToGenerate > 0 ? totalChunksToGenerate : "\u221e",
                totalChunksToGenerate > 0 ? String.format("%.2f", percent) : "N/A",
                progress.currentChunkX(), progress.currentChunkZ(),
                chunksGeneratedThisSession);
            lastLogTime = now;
        }
    }

    private void finishPregeneration() {
        running.set(false);
        cancelled.set(false);
        burstModeActive = false;
        saveProgress();

        UNALog.info("Chunk pregeneration completed! Total chunks: {}, Session: {}",
            progress.generatedChunksCount(), chunksGeneratedThisSession);
    }

    private void loadProgress() {
        Path progressFile = UNAConfig.getPregenProgressFile();
        if (!Files.exists(progressFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(progressFile)) {
            Gson gson = new Gson();
            PregenerationProgress saved = gson.fromJson(reader, PregenerationProgress.class);
            if (saved != null
                    && "spiral".equals(saved.mode())
                    && saved.dimension() != null
                    && saved.dimension().equals(progress.dimension())
                    && saved.startX() == progress.startX()
                    && saved.startZ() == progress.startZ()
                    && saved.radius() == progress.radius()) {
                progress.restoreFromSpiralIndex(saved.spiralIndex(), saved.generatedCount());
                UNALog.info("Loaded pregeneration progress: {} chunks done, spiralIndex={}",
                    progress.generatedCount(), progress.spiralIndex());
            } else {
                if (saved != null && !"spiral".equals(saved.mode())) {
                    UNALog.info("Saved pregeneration progress has mode='{}' (expected 'spiral') - starting fresh", saved.mode());
                } else {
                    UNALog.info("Saved pregeneration progress does not match current config - starting fresh");
                }
            }
        } catch (Exception e) {
            UNALog.warn("Failed to load pregeneration progress, starting fresh: {}", e.getMessage());
        }
    }

    private void saveProgress() {
        Path progressFile = UNAConfig.getPregenProgressFile();
        try {
            Files.createDirectories(progressFile.getParent());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(progress);
            Files.writeString(progressFile, json);
        } catch (IOException e) {
            UNALog.error("Failed to save pregeneration progress: {}", e.getMessage());
        }
    }

    // ========================================================================
    // Inner classes
    // ========================================================================

    public static class PregenerationProgress {
        private String mode = "spiral";
        private int startX;
        private int startZ;
        private int radius;
        private String dimension;
        private int currentChunkX;
        private int currentChunkZ;
        private int generatedCount;
        private long spiralIndex;
        private long lastUpdated;

        public PregenerationProgress() {
            this(0, 0, 1, "minecraft:overworld");
        }

        public PregenerationProgress(int startX, int startZ, int radius, String dimension) {
            this.startX = startX;
            this.startZ = startZ;
            this.radius = radius;
            this.dimension = dimension;
            ChunkPos start = indexToChunk(startX, startZ, 0);
            this.currentChunkX = start.x;
            this.currentChunkZ = start.z;
            this.spiralIndex = 0;
            this.lastUpdated = System.currentTimeMillis();
        }

        public String mode() { return mode; }
        public int startX() { return startX; }
        public int startZ() { return startZ; }
        public int radius() { return radius; }
        public String dimension() { return dimension; }
        public int currentChunkX() { return currentChunkX; }
        public int currentChunkZ() { return currentChunkZ; }
        public long spiralIndex() { return spiralIndex; }
        public long lastUpdated() { return lastUpdated; }

        public void markChunkGenerated(int x, int z) {
            generatedCount++;
            lastUpdated = System.currentTimeMillis();
        }

        public int generatedChunksCount() { return generatedCount; }
        public int generatedCount() { return generatedCount; }

        public void restoreFromSpiralIndex(long spiralIndex, int generatedCount) {
            this.spiralIndex = spiralIndex;
            this.generatedCount = generatedCount;
            ChunkPos pos = indexToChunk(startX, startZ, spiralIndex);
            this.currentChunkX = pos.x;
            this.currentChunkZ = pos.z;
            this.lastUpdated = System.currentTimeMillis();
        }

        public void advanceToNextChunk() {
            spiralIndex++;
            ChunkPos next = indexToChunk(startX, startZ, spiralIndex);
            this.currentChunkX = next.x;
            this.currentChunkZ = next.z;
            this.lastUpdated = System.currentTimeMillis();
        }

        public boolean isComplete() {
            if (radius < 0) {
                // Infinite spiral: never completes
                return false;
            }
            if (radius == 0) {
                // Disabled: should never be called
                return true;
            }
            int dx = Math.abs(currentChunkX - startX);
            int dz = Math.abs(currentChunkZ - startZ);
            return Math.max(dx, dz) > radius;
        }

        /**
         * Pure function: maps a spiral index to a chunk position using an outward
         * square spiral (Ulam-style) centered on (centerX, centerZ).
         *
         * Index 0 = center, then spirals outward ring by ring.
         */
        public static ChunkPos indexToChunk(int centerX, int centerZ, long n) {
            if (n == 0) {
                return new ChunkPos(centerX, centerZ);
            }

            // Determine which ring (layer) n falls in
            long ring = (long) Math.ceil((Math.sqrt((double) n + 1) - 1) / 2.0);
            long prevRingEnd = (2 * ring - 1) * (2 * ring - 1);
            long offset = n - prevRingEnd;
            long sideLen = 2 * (int) ring;

            long x, z;
            if (offset < sideLen) {
                // Bottom side: left to right
                x = -ring + offset;
                z = ring;
            } else if (offset < 2 * sideLen) {
                // Right side: bottom to top
                x = ring;
                z = ring - (offset - sideLen);
            } else if (offset < 3 * sideLen) {
                // Top side: right to left
                x = ring - (offset - 2 * sideLen);
                z = -ring;
            } else {
                // Left side: top to bottom
                x = -ring;
                z = -ring + (offset - 3 * sideLen);
            }

            return new ChunkPos(centerX + (int) x, centerZ + (int) z);
        }
    }

    public static class PregenerationResult {
        private final boolean success;
        private final String message;
        private final PregenerationProgress data;

        private PregenerationResult(boolean success, String message, PregenerationProgress data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public static PregenerationResult success(String message) {
            return new PregenerationResult(true, message, null);
        }

        public static PregenerationResult success(String message, PregenerationProgress data) {
            return new PregenerationResult(true, message, data);
        }

        public static PregenerationResult failure(String message) {
            return new PregenerationResult(false, message, null);
        }

        public boolean success() { return success; }
        public String message() { return message; }
        public PregenerationProgress data() { return data; }
    }
}
