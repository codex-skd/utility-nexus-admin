package com.skd.utilitynexusadmin;

import com.skd.utilitynexusadmin.command.UNACommand;
import com.skd.utilitynexusadmin.config.UNAConfig;
import com.skd.utilitynexusadmin.datapack.DatapackManager;
import com.skd.utilitynexusadmin.logging.UNALog;
import com.skd.utilitynexusadmin.pregenerator.ChunkPregenerator;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConnectionListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerNegotiationEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(UtilityNexusAdmin.MODID)
public class UtilityNexusAdmin {
    public static final String MODID = "utility_nexus_admin";
    public static final String VERSION = "0.0.0-beta.13";
    public static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static DatapackManager datapackManager;
    private static ChunkPregenerator chunkPregenerator;

    // A5: track when the server last became empty of players
    private static long playersEmptySinceMillis = 0;

    public UtilityNexusAdmin(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, UNAConfig.COMMON_SPEC, "utility_nexus/admin/config.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, UNAConfig.SERVER_SPEC, "utility_nexus/admin/pregen.toml");

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onAddPackFinders);
        modEventBus.addListener(this::onModConfig);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onPlayerNegotiation);

        LOGGER.info("Utility Nexus Admin loaded! v{}", VERSION);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        UNALog.info("Utility Nexus Admin common setup complete");
    }

    // Capture the real on-disk path of the SERVER config (NeoForge places it under
    // <world>/serverconfig/) so /una config reload can re-read it after external edits.
    private void onModConfig(net.neoforged.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getSpec() != UNAConfig.SERVER_SPEC) {
            return;
        }
        // ModConfig#getFullPath() throws IllegalStateException on a non-file config.
        // This event ALSO fires with an in-memory / null config when the SERVER spec
        // is torn down (dedicated server stop -> ModConfig.setConfig(null)) or synced
        // from the network (integrated client joining a server). In those cases there
        // is nothing to capture; getPregenConfigPath() keeps its <world>/serverconfig/
        // fallback. Previously this threw and was logged as an uncaught event error.
        try {
            java.nio.file.Path path = event.getConfig().getFullPath();
            if (path != null) {
                UNAConfig.setPregenConfigPath(path);
            }
        } catch (IllegalStateException ignored) {
            // non-file config — keep the fallback path
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        UNALog.info("Utility Nexus Admin client setup complete");
    }

    private void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == net.minecraft.server.packs.PackType.SERVER_DATA
                && UNAConfig.datapackAutoLoad()) {
            DatapackManager.registerPackSource(event);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher = event.getDispatcher();
        UNACommand.register(dispatcher);
        UNALog.info("Commands registered");
    }

    // B1: Enable global packs during the initial world data load, before the first datapack load
    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();

        if (UNAConfig.datapackAutoLoad()) {
            java.util.Set<String> discovered = DatapackManager.discoverPackIds();
            if (!discovered.isEmpty()) {
                java.util.Set<String> enabled = new java.util.HashSet<>(server.getPackRepository().getSelectedIds());
                int newlyEnabled = 0;
                for (String id : discovered) {
                    if (enabled.add(id)) {
                        newlyEnabled++;
                    }
                }
                server.getPackRepository().setSelected(enabled);
                UNALog.info("Global datapacks: {} discovered, {} enabled at load (no reload)",
                        discovered.size(), newlyEnabled);
            } else {
                UNALog.info("Global datapacks: 0 discovered, 0 enabled at load (no reload)");
            }
        }
    }

    // A6: Create pregenerator, but do NOT auto-start yet
    private void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        datapackManager = new DatapackManager(server);
        chunkPregenerator = new ChunkPregenerator(server);

        // B2: Removed the unconditional loadAllFromDirectory() -> reloadResources().join() call.
        // Global packs are now selected during ServerAboutToStartEvent (B1) without a reload.
        // The loadAllFromDirectory() method remains available for the /una datapack loadall command.

        UNALog.info("Server starting - Managers initialized");
    }

    // A6: Schedule deferred auto-start after server has fully started (dedicated only)
    private void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!server.isDedicatedServer()) {
            return;
        }
        if (chunkPregenerator != null && UNAConfig.pregenEnabled()) {
            chunkPregenerator.markServerStarted();
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        if (datapackManager != null) {
            datapackManager.shutdown();
            datapackManager = null;
        }
        if (chunkPregenerator != null) {
            chunkPregenerator.shutdown();
            chunkPregenerator = null;
        }
        playersEmptySinceMillis = 0;
        UNALog.info("Server stopped - All managers shut down");
    }

    // A5: Pause pregen on player negotiation (fires during login before PlayerLoggedInEvent)
    // Bypass pause during scheduled burst if schedule.ignorePlayers is true
    private void onPlayerNegotiation(PlayerNegotiationEvent event) {
        if (chunkPregenerator != null && chunkPregenerator.isRunning() && !chunkPregenerator.isPaused()) {
            if (chunkPregenerator.isBurstMode() && UNAConfig.scheduleIgnorePlayers()) {
                return;
            }
            UNALog.info("Player negotiation ({}) - pausing chunk pregeneration", event.getProfile().getName());
            chunkPregenerator.pause();
        }
    }

    // A5 + A6: Connection gate + resume grace period
    private void onServerTick(ServerTickEvent.Post event) {
        if (chunkPregenerator == null) return;

        MinecraftServer server = event.getServer();

        // Burst mode bypasses all player-pause logic
        boolean burstBypass = chunkPregenerator.isBurstMode() && UNAConfig.scheduleIgnorePlayers();

        // A5: If pregen is paused, check if it's safe to resume (no players AND no mid-handshake connections)
        if (!burstBypass && chunkPregenerator.isRunning() && chunkPregenerator.isPaused()) {
            if (server.getPlayerList().getPlayers().isEmpty() && !hasConnectingPlayer(server)) {
                long emptySince = playersEmptySinceMillis;
                if (emptySince == 0) {
                    playersEmptySinceMillis = System.currentTimeMillis();
                    emptySince = playersEmptySinceMillis;
                }
                long emptyForSeconds = (System.currentTimeMillis() - emptySince) / 1000;
                if (emptyForSeconds >= UNAConfig.resumeGraceSeconds()) {
                    UNALog.info("Server empty for {}s (grace: {}s) - resuming chunk pregeneration",
                            emptyForSeconds, UNAConfig.resumeGraceSeconds());
                    chunkPregenerator.resume();
                }
            } else {
                playersEmptySinceMillis = 0;
            }
            return;
        }

        // A5: If running and not paused, but a player is connecting or online, pause (unless burst mode)
        if (!burstBypass && chunkPregenerator.isRunning() && !chunkPregenerator.isPaused()) {
            if (!server.getPlayerList().getPlayers().isEmpty() || hasConnectingPlayer(server)) {
                chunkPregenerator.pause();
                playersEmptySinceMillis = 0;
                return;
            }
        }

        // Always forward the tick: ChunkPregenerator.onServerTick also drives the
        // deferred auto-start check (A6) and schedule window handling, which must
        // run while pregen is NOT yet running or while burst mode is active.
        chunkPregenerator.onServerTick(event);
    }

    // A5: True only while a real player is mid-join (LOGIN or CONFIGURATION protocol phase).
    // Server-list pings (STATUS) and already-playing connections (PLAY) are ignored, so a
    // frequently-pinged public server does not keep pausing pregen forever.
    private boolean hasConnectingPlayer(MinecraftServer server) {
        ServerConnectionListener connectionListener = server.getConnection();
        if (connectionListener == null) return false;
        for (Connection conn : connectionListener.getConnections()) {
            if (!conn.isConnected()) continue;
            net.minecraft.network.PacketListener listener = conn.getPacketListener();
            if (listener == null) continue;
            net.minecraft.network.ConnectionProtocol phase = listener.protocol();
            if (phase == net.minecraft.network.ConnectionProtocol.LOGIN
                    || phase == net.minecraft.network.ConnectionProtocol.CONFIGURATION) {
                return true;
            }
        }
        return false;
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (chunkPregenerator != null && chunkPregenerator.isRunning() && !chunkPregenerator.isPaused()) {
            if (chunkPregenerator.isBurstMode() && UNAConfig.scheduleIgnorePlayers()) {
                return;
            }
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UNALog.info("Player {} joined - pausing chunk pregeneration", player.getName().getString());
            chunkPregenerator.pause();
        }
        playersEmptySinceMillis = 0;
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // A5: Just record when the server became empty; resume is handled in onServerTick with grace period
        if (chunkPregenerator != null && chunkPregenerator.isRunning() && chunkPregenerator.isPaused()) {
            MinecraftServer server = event.getEntity().getServer();
            if (server != null && server.getPlayerList().getPlayers().isEmpty()) {
                playersEmptySinceMillis = System.currentTimeMillis();
                UNALog.info("Player {} left - server empty, grace period started ({}s)",
                        event.getEntity().getName().getString(), UNAConfig.resumeGraceSeconds());
            }
        }
    }

    public static DatapackManager getDatapackManager() {
        return datapackManager;
    }

    public static ChunkPregenerator getChunkPregenerator() {
        return chunkPregenerator;
    }
}
