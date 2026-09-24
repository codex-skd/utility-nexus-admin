package com.skd.utilitynexusadmin.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skd.utilitynexusadmin.UtilityNexusAdmin;
import com.skd.utilitynexusadmin.command.datapack.DatapackCommand;
import com.skd.utilitynexusadmin.command.pregen.PregenerationCommand;
import com.skd.utilitynexusadmin.config.UNAConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class UNACommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("una")
                .requires(source -> source.hasPermission(UNAConfig.opLevel()))
                .executes(UNACommand::showHelp)
                .then(Commands.literal("help")
                        .executes(UNACommand::showHelp));

        if (UNAConfig.datapackCommandsEnabled()) {
            root.then(Commands.literal("datapack")
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
                            .executes(DatapackCommand::loadAllDatapacks)));
        }

        root.then(Commands.literal("pregen")
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
                                .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1))
                                        .executes(ctx -> PregenerationCommand.setEnabled(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("center")
                                .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                .executes(ctx -> PregenerationCommand.setCenter(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z"))))))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(ctx -> PregenerationCommand.setRadius(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("chunksPerTick")
                                .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> PregenerationCommand.setChunksPerTick(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("dimension")
                                .then(Commands.argument("value", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .executes(ctx -> PregenerationCommand.setDimension(ctx, net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "value")))))));

        if (UNAConfig.configCommandsEnabled()) {
            root.then(Commands.literal("config")
                    .then(Commands.literal("reload")
                            .executes(UNACommand::reloadConfig))
                    .then(Commands.literal("get")
                            .then(Commands.argument("key", StringArgumentType.greedyString())
                                    .executes(UNACommand::getConfig)))
                    .then(Commands.literal("set")
                            .then(Commands.argument("key", StringArgumentType.greedyString())
                                    .then(Commands.argument("value", StringArgumentType.greedyString())
                                            .executes(UNACommand::setConfig)))));
        }

        root.then(Commands.literal("version")
                .executes(UNACommand::showVersion));

        dispatcher.register(root);
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        MutableComponent help = Component.literal("§6=== Utility Nexus Admin Commands ===")
                .append(Component.literal("\n§e/una help §7- Show this help"))
                .append(Component.literal("\n§e/una version §7- Show mod version"));

        if (UNAConfig.datapackCommandsEnabled()) {
            help.append(Component.literal("\n§e/una datapack §7- Datapack management"))
                    .append(Component.literal("\n  §e/una datapack list §7- List loaded/available datapacks"))
                    .append(Component.literal("\n  §e/una datapack load <name> §7- Load a datapack"))
                    .append(Component.literal("\n  §e/una datapack unload <name> §7- Unload a datapack"))
                    .append(Component.literal("\n  §e/una datapack reload <name> §7- Reload a datapack"))
                    .append(Component.literal("\n  §e/una datapack validate <name> §7- Validate a datapack"))
                    .append(Component.literal("\n  §e/una datapack loadall §7- Load all datapacks from global dir"));
        }

        help.append(Component.literal("\n§e/una pregen §7- Chunk pregeneration"))
                .append(Component.literal("\n  §e/una pregen start §7- Start pregeneration"))
                .append(Component.literal("\n  §e/una pregen stop §7- Stop pregeneration"))
                .append(Component.literal("\n  §e/una pregen pause §7- Pause pregeneration"))
                .append(Component.literal("\n  §e/una pregen resume §7- Resume pregeneration"))
                .append(Component.literal("\n  §e/una pregen status §7- Show pregeneration status"))
                .append(Component.literal("\n  §e/una pregen config enabled <0|1> §7- Enable/disable pregeneration"))
                .append(Component.literal("\n  §e/una pregen config center <x> <z> §7- Set center chunk"))
                .append(Component.literal("\n  §e/una pregen config radius <value> §7- Set radius (0=infinite)"))
                .append(Component.literal("\n  §e/una pregen config chunksPerTick <value> §7- Chunks per tick (1-200)"))
                .append(Component.literal("\n  §e/una pregen config dimension <namespace:id> §7- Set target dimension"));

        if (UNAConfig.configCommandsEnabled()) {
            help.append(Component.literal("\n§e/una config reload|get|set §7- Configuration"))
                    .append(Component.literal("\n  §7Keys use section.key form, e.g. §epregen.chunksPerTick"));
        }

        context.getSource().sendSuccess(() -> help, false);
        return 1;
    }

    private static int showVersion(CommandContext<CommandSourceStack> context) {
        String version = UtilityNexusAdmin.VERSION;
        context.getSource().sendSuccess(() -> Component.literal("§6Utility Nexus Admin §ev" + version), false);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        UNAConfig.reload();
        context.getSource().sendSuccess(() -> Component.literal("§aConfiguration reloaded"), false);
        return 1;
    }

    private static int getConfig(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String key = StringArgumentType.getString(context, "key");
        Object value = UNAConfig.get(key);
        if (value != null) {
            context.getSource().sendSuccess(() -> Component.literal("§e" + key + " §7= §a" + value), false);
        } else {
            context.getSource().sendFailure(Component.literal("§cConfig key not found: " + key));
        }
        return 1;
    }

    private static int setConfig(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String key = StringArgumentType.getString(context, "key");
        String value = StringArgumentType.getString(context, "value");
        try {
            UNAConfig.set(key, value);
            context.getSource().sendSuccess(() -> Component.literal("§aSet " + key + " = " + value), false);
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("§c" + e.getMessage()));
        }
        return 1;
    }
}
