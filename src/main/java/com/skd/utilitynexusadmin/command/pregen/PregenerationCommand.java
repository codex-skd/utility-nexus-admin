package com.skd.utilitynexusadmin.command.pregen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skd.utilitynexusadmin.UtilityNexusAdmin;
import com.skd.utilitynexusadmin.config.UNAConfig;
import com.skd.utilitynexusadmin.pregenerator.ChunkPregenerator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

public class PregenerationCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pregen")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(PregenerationCommand::start))
                .then(Commands.literal("stop")
                        .executes(PregenerationCommand::stop))
                .then(Commands.literal("pause")
                        .executes(PregenerationCommand::pause))
                .then(Commands.literal("resume")
                        .executes(PregenerationCommand::resume))
                .then(Commands.literal("status")
                        .executes(PregenerationCommand::status))
                .then(Commands.literal("config")
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 1))
                                        .executes(ctx -> setEnabled(ctx, IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("center")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> setCenter(ctx, IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z"))))))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("value", IntegerArgumentType.integer(-1))
                                        .executes(ctx -> setRadius(ctx, IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("chunksPerTick")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> setChunksPerTick(ctx, IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("dimension")
                                .then(Commands.argument("value", ResourceLocationArgument.id())
                                        .executes(ctx -> setDimension(ctx, ResourceLocationArgument.getId(ctx, "value"))))));

        dispatcher.register(root);
    }

    public static int start(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = getServer(ctx);
        ChunkPregenerator pregen = getPregenerator(server);
        if (pregen == null) {
            ctx.getSource().sendFailure(Component.literal("§cPregenerator not initialized"));
            return 0;
        }

        if (pregen.isRunning()) {
            ctx.getSource().sendFailure(Component.literal("§cPregeneration already running"));
            return 0;
        }

        if (!UNAConfig.pregenEnabled()) {
            ctx.getSource().sendFailure(Component.literal("§cPregeneration is disabled in config. Use /una pregen config enabled 1 to enable"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§eStarting chunk pregeneration..."), false);

        CompletableFuture<ChunkPregenerator.PregenerationResult> future = pregen.start();
        future.thenAccept(result -> {
            if (result.success()) {
                ctx.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            } else {
                ctx.getSource().sendFailure(Component.literal("§c" + result.message()));
            }
        }).exceptionally(ex -> {
            ctx.getSource().sendFailure(Component.literal("§cError: " + ex.getMessage()));
            return null;
        });

        return 1;
    }

    public static int stop(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = getServer(ctx);
        ChunkPregenerator pregen = getPregenerator(server);
        if (pregen == null) {
            ctx.getSource().sendFailure(Component.literal("§cPregenerator not initialized"));
            return 0;
        }

        if (!pregen.isRunning()) {
            ctx.getSource().sendFailure(Component.literal("§cPregeneration not running"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§eStopping chunk pregeneration..."), false);

        CompletableFuture<ChunkPregenerator.PregenerationResult> future = pregen.stop();
        future.thenAccept(result -> {
            if (result.success()) {
                ctx.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            } else {
                ctx.getSource().sendFailure(Component.literal("§c" + result.message()));
            }
        }).exceptionally(ex -> {
            ctx.getSource().sendFailure(Component.literal("§cError: " + ex.getMessage()));
            return null;
        });

        return 1;
    }

    public static int pause(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = getServer(ctx);
        ChunkPregenerator pregen = getPregenerator(server);
        if (pregen == null) {
            ctx.getSource().sendFailure(Component.literal("§cPregenerator not initialized"));
            return 0;
        }

        if (!pregen.isRunning() || pregen.isPaused()) {
            ctx.getSource().sendFailure(Component.literal("§cPregeneration not running or already paused"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§ePausing chunk pregeneration..."), false);

        CompletableFuture<ChunkPregenerator.PregenerationResult> future = pregen.pause();
        future.thenAccept(result -> {
            if (result.success()) {
                ctx.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            } else {
                ctx.getSource().sendFailure(Component.literal("§c" + result.message()));
            }
        }).exceptionally(ex -> {
            ctx.getSource().sendFailure(Component.literal("§cError: " + ex.getMessage()));
            return null;
        });

        return 1;
    }

    public static int resume(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = getServer(ctx);
        ChunkPregenerator pregen = getPregenerator(server);
        if (pregen == null) {
            ctx.getSource().sendFailure(Component.literal("§cPregenerator not initialized"));
            return 0;
        }

        if (!pregen.isRunning() || !pregen.isPaused()) {
            ctx.getSource().sendFailure(Component.literal("§cPregeneration not paused"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§eResuming chunk pregeneration..."), false);

        CompletableFuture<ChunkPregenerator.PregenerationResult> future = pregen.resume();
        future.thenAccept(result -> {
            if (result.success()) {
                ctx.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            } else {
                ctx.getSource().sendFailure(Component.literal("§c" + result.message()));
            }
        }).exceptionally(ex -> {
            ctx.getSource().sendFailure(Component.literal("§cError: " + ex.getMessage()));
            return null;
        });

        return 1;
    }

    public static int status(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = getServer(ctx);
        ChunkPregenerator pregen = getPregenerator(server);
        if (pregen == null) {
            ctx.getSource().sendFailure(Component.literal("§cPregenerator not initialized"));
            return 0;
        }

        var progress = pregen.getProgress();
        String status = pregen.isRunning() ? (pregen.isPaused() ? "§ePAUSED" : "§aRUNNING") : "§cSTOPPED";

        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Chunk Pregeneration Status ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eStatus: " + status), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§ePhase: §a" + pregen.getCurrentPhase()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eIn-Flight: §a" + pregen.getInFlightCount()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eMSPT-Throttled: §a" + (pregen.isMsptThrottled() ? "Yes" : "No")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eBurst Mode: §a" + (pregen.isBurstMode() ? "Yes" : "No")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eSchedule: §a" + pregen.getScheduleStatusString()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eNext Phase Switch: §a" + pregen.getSecondsUntilNextPhaseSwitch() + "s"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eDimension: §a" + progress.dimension()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eCenter: §a(" + progress.startX() + ", " + progress.startZ() + ")"), false);
        String radiusStr;
        if (progress.radius() == 0) {
            radiusStr = "0 (disabled)";
        } else if (progress.radius() < 0) {
            radiusStr = "-1 (infinite)";
        } else {
            radiusStr = progress.radius() + " chunks";
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§eRadius: §a" + radiusStr), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eCurrent Chunk: §a(" + progress.currentChunkX() + ", " + progress.currentChunkZ() + ")"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eChunks Generated: §a" + progress.generatedChunksCount()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eChunks Per Tick (cap): §a" + UNAConfig.chunksPerTick()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eMax Concurrent: §a" + UNAConfig.maxConcurrentChunks()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eMax Ms/Tick: §a" + UNAConfig.maxMillisPerTick()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eMax Server MSPT: §a" + UNAConfig.maxServerMspt()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eConfig Enabled: §a" + (UNAConfig.pregenEnabled() ? "Yes" : "No")), false);

        if (pregen.isRunning() && !pregen.isPaused()) {
            int radius = progress.radius();
            if (radius > 0) {
                int total = (radius * 2 + 1) * (radius * 2 + 1);
                double percent = progress.generatedChunksCount() * 100.0 / total;
                ctx.getSource().sendSuccess(() -> Component.literal("§eProgress: §a" + String.format("%.2f", percent) + "%"), false);
            }
        }

        return 1;
    }

    public static int setEnabled(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        boolean enabled = value == 1;
        UNAConfig.set("pregen.enabled", enabled);
        ctx.getSource().sendSuccess(() -> Component.literal("§aPregeneration " + (enabled ? "enabled" : "disabled")), false);
        return 1;
    }

    public static int setCenter(CommandContext<CommandSourceStack> ctx, int x, int z) throws CommandSyntaxException {
        UNAConfig.set("pregen.startX", x);
        UNAConfig.set("pregen.startZ", z);
        ctx.getSource().sendSuccess(() -> Component.literal("§aPregeneration center set to (" + x + ", " + z + ")"), false);
        return 1;
    }

    public static int setRadius(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        UNAConfig.set("pregen.radius", value);
        String radiusStr;
        if (value == 0) {
            radiusStr = "0 (disabled)";
        } else if (value < 0) {
            radiusStr = "-1 (infinite spiral)";
        } else {
            radiusStr = value + " chunks";
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§aPregeneration radius set to " + radiusStr), false);
        return 1;
    }

    public static int setChunksPerTick(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        UNAConfig.set("pregen.chunksPerTick", value);
        ctx.getSource().sendSuccess(() -> Component.literal("§aChunks per tick set to " + value), false);
        return 1;
    }

    public static int setDimension(CommandContext<CommandSourceStack> ctx, ResourceLocation value) throws CommandSyntaxException {
        UNAConfig.set("pregen.dimension", value.toString());
        ctx.getSource().sendSuccess(() -> Component.literal("§aPregeneration dimension set to " + value), false);
        return 1;
    }

    private static MinecraftServer getServer(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getServer();
    }

    private static ChunkPregenerator getPregenerator(MinecraftServer server) {
        return UtilityNexusAdmin.getChunkPregenerator();
    }
}
