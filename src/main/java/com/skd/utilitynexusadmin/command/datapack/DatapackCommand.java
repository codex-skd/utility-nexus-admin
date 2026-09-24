package com.skd.utilitynexusadmin.command.datapack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skd.utilitynexusadmin.UtilityNexusAdmin;
import com.skd.utilitynexusadmin.datapack.DatapackManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

public class DatapackCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("datapack")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(DatapackCommand::listDatapacks))
                .then(Commands.literal("load")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(DatapackCommand::loadDatapack)))
                .then(Commands.literal("unload")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(DatapackCommand::unloadDatapack)))
                .then(Commands.literal("reload")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(DatapackCommand::reloadDatapack)))
                .then(Commands.literal("validate")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(DatapackCommand::validateDatapack)))
                .then(Commands.literal("loadall")
                        .executes(DatapackCommand::loadAllDatapacks));

        dispatcher.register(root);
    }

    public static int listDatapacks(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = getServer(context);
        DatapackManager manager = getDatapackManager(server);
        if (manager == null) {
            context.getSource().sendFailure(Component.literal("§cDatapack manager not initialized"));
            return 0;
        }

        var loaded = manager.getLoadedDatapacks();
        var available = manager.getAvailableDatapacks();

        context.getSource().sendSuccess(() -> Component.literal("§6=== Global Datapacks (" + available.size() + " found) ==="), false);
        if (available.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§7  (none in <gameDir>/datapacks/)"), false);
        } else {
            for (String name : available) {
                boolean isEnabled = loaded.stream().anyMatch(i -> i.name().equals(name));
                String prefix = isEnabled ? "§a  ✓" : "§7  ○";
                context.getSource().sendSuccess(() -> Component.literal(prefix + " " + name), false);
            }
        }

        context.getSource().sendSuccess(() -> Component.literal("§6=== Enabled (" + loaded.size() + ") ==="), false);
        if (loaded.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§7  (none)"), false);
        } else {
            for (var info : loaded) {
                context.getSource().sendSuccess(() -> Component.literal("§a  • " + info.name()), false);
            }
        }
        return 1;
    }

    public static int loadDatapack(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        MinecraftServer server = getServer(context);
        DatapackManager manager = getDatapackManager(server);
        if (manager == null) {
            context.getSource().sendFailure(Component.literal("§cDatapack manager not initialized"));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("§eEnabling datapack: " + name + "..."), false);

        CompletableFuture<DatapackManager.DatapackResult> future = manager.loadDatapack(name);
        future.thenAccept(result -> {
            if (result.success()) {
                context.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            } else {
                context.getSource().sendFailure(Component.literal("§c" + result.message()));
            }
        }).exceptionally(ex -> {
            context.getSource().sendFailure(Component.literal("§cError: " + ex.getMessage()));
            return null;
        });

        return 1;
    }

    public static int unloadDatapack(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        MinecraftServer server = getServer(context);
        DatapackManager manager = getDatapackManager(server);
        if (manager == null) {
            context.getSource().sendFailure(Component.literal("§cDatapack manager not initialized"));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("§eDisabling datapack: " + name + "..."), false);

        CompletableFuture<DatapackManager.DatapackResult> future = manager.unloadDatapack(name);
        future.thenAccept(result -> {
            if (result.success()) {
                context.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            } else {
                context.getSource().sendFailure(Component.literal("§c" + result.message()));
            }
        }).exceptionally(ex -> {
            context.getSource().sendFailure(Component.literal("§cError: " + ex.getMessage()));
            return null;
        });

        return 1;
    }

    public static int reloadDatapack(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        MinecraftServer server = getServer(context);
        DatapackManager manager = getDatapackManager(server);
        if (manager == null) {
            context.getSource().sendFailure(Component.literal("§cDatapack manager not initialized"));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("§eReloading datapack: " + name + "..."), false);

        CompletableFuture<DatapackManager.DatapackResult> future = manager.reloadDatapack(name);
        future.thenAccept(result -> {
            if (result.success()) {
                context.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            } else {
                context.getSource().sendFailure(Component.literal("§c" + result.message()));
            }
        }).exceptionally(ex -> {
            context.getSource().sendFailure(Component.literal("§cError: " + ex.getMessage()));
            return null;
        });

        return 1;
    }

    public static int validateDatapack(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        MinecraftServer server = getServer(context);
        DatapackManager manager = getDatapackManager(server);
        if (manager == null) {
            context.getSource().sendFailure(Component.literal("§cDatapack manager not initialized"));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("§eValidating datapack: " + name + "..."), false);

        CompletableFuture<DatapackManager.DatapackResult> future = manager.validateDatapack(name);
        future.thenAccept(result -> {
            if (result.success()) {
                context.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            } else {
                context.getSource().sendFailure(Component.literal("§c" + result.message()));
            }
        }).exceptionally(ex -> {
            context.getSource().sendFailure(Component.literal("§cError: " + ex.getMessage()));
            return null;
        });

        return 1;
    }

    public static int loadAllDatapacks(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = getServer(context);
        DatapackManager manager = getDatapackManager(server);
        if (manager == null) {
            context.getSource().sendFailure(Component.literal("§cDatapack manager not initialized"));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("§eEnabling all global datapacks..."), false);

        CompletableFuture<DatapackManager.DatapackResult> future = manager.loadAllFromDirectory();
        future.thenAccept(result -> {
            if (result.success()) {
                context.getSource().sendSuccess(() -> Component.literal("§a" + result.message()), false);
            } else {
                context.getSource().sendFailure(Component.literal("§c" + result.message()));
            }
        }).exceptionally(ex -> {
            context.getSource().sendFailure(Component.literal("§cError: " + ex.getMessage()));
            return null;
        });

        return 1;
    }

    private static MinecraftServer getServer(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer();
    }

    private static DatapackManager getDatapackManager(MinecraftServer server) {
        return UtilityNexusAdmin.getDatapackManager();
    }
}
