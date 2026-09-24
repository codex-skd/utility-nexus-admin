package com.skd.utilitynexusadmin.config;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class UNAConfig {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    // ========================================================================
    // COMMON spec — [datapack], [commands], [logging]
    // ========================================================================

    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    // ── [datapack] ──
    public static final ModConfigSpec.BooleanValue DATAPACK_AUTO_LOAD = COMMON_BUILDER
            .comment("Inject datapacks found in <gameDir>/datapacks/ into every world (client & server). If false, the global datapacks folder is ignored entirely.")
            .define("datapack.autoLoad", true);
    public static final ModConfigSpec.BooleanValue DATAPACK_VALIDATE_ON_LOAD = COMMON_BUILDER
            .comment("Validate pack structure (pack.mcmeta parses, data/ dir present) before injecting; invalid packs are skipped with a WARN.")
            .define("datapack.validateOnLoad", true);
    public static final ModConfigSpec.BooleanValue DATAPACK_CREATE_MISSING_METADATA = COMMON_BUILDER
            .comment("If a folder datapack has no pack.mcmeta, generate a default one (pack_format 48). ZIP packs without pack.mcmeta cannot be auto-fixed and are skipped.")
            .define("datapack.createMissingMetadata", true);

    // ── [commands] ──
    public static final ModConfigSpec.IntValue COMMANDS_OP_LEVEL = COMMON_BUILDER
            .comment("Minimum vanilla OP permission level required to use /una. Applied when the command tree is built (server start / after a datapack reload).")
            .defineInRange("commands.opLevel", 2, 0, 4);
    public static final ModConfigSpec.BooleanValue COMMANDS_ENABLE_DATAPACK = COMMON_BUILDER
            .comment("Register the /una datapack ... subtree.")
            .define("commands.enableDatapackCommands", true);
    public static final ModConfigSpec.BooleanValue COMMANDS_ENABLE_CONFIG = COMMON_BUILDER
            .comment("Register the /una config ... subtree.")
            .define("commands.enableConfigCommands", true);

    // ── [logging] ──
    public static final ModConfigSpec.EnumValue<LogLevel> LOGGING_LEVEL = COMMON_BUILDER
            .comment("Minimum level the mod emits to the standard NeoForge log. One of: DEBUG, INFO, WARN, ERROR.")
            .defineEnum("logging.level", LogLevel.INFO);

    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    // ========================================================================
    // SERVER spec — [pregen], [schedule]
    // ========================================================================

    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    // ── [pregen] ──
    public static final ModConfigSpec.BooleanValue PREGEN_ENABLED = SERVER_BUILDER
            .comment("If true, chunk pregeneration auto-starts on server start. It only makes progress while 0 players are online (pauses on join, resumes when the last player leaves after a grace period).")
            .define("pregen.enabled", false);
    public static final ModConfigSpec.IntValue PREGEN_START_X = SERVER_BUILDER
            .comment("Center chunk X (chunk coords, not blocks).")
            .defineInRange("pregen.startX", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue PREGEN_START_Z = SERVER_BUILDER
            .comment("Center chunk Z.")
            .defineInRange("pregen.startZ", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue PREGEN_RADIUS = SERVER_BUILDER
            .comment("0 = disabled (start returns failure). -1 = infinite outward spiral (never completes). >0 = finite Chebyshev radius in chunks; spiral from center, complete when max(abs(dx),abs(dz)) > radius.")
            .defineInRange("pregen.radius", 0, -1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue PREGEN_CHUNKS_PER_TICK = SERVER_BUILDER
            .comment("Maximum chunk submissions per server tick (per-tick submission cap). Higher = faster but more load. Command clamps to 1..200; file may hold other values.")
            .defineInRange("pregen.chunksPerTick", 8, 1, 200);
    public static final ModConfigSpec.IntValue PREGEN_MAX_CONCURRENT_CHUNKS = SERVER_BUILDER
            .comment("Maximum number of chunk generation futures in flight at any time. Lower values reduce server load spikes.")
            .defineInRange("pregen.maxConcurrentChunks", 4, 1, 64);
    public static final ModConfigSpec.IntValue PREGEN_MAX_MILLIS_PER_TICK = SERVER_BUILDER
            .comment("Wall-time budget in milliseconds per server tick for pregen work. Work stops once this budget is exceeded.")
            .defineInRange("pregen.maxMillisPerTick", 8, 1, 40);
    public static final ModConfigSpec.IntValue PREGEN_MAX_SERVER_MSPT = SERVER_BUILDER
            .comment("Mean server tick time in milliseconds above which pregen pauses for the tick. Helps avoid overloading a busy server.")
            .defineInRange("pregen.maxServerMspt", 45, 10, 500);
    public static final ModConfigSpec.IntValue PREGEN_WORK_SECONDS = SERVER_BUILDER
            .comment("Seconds of active generation in each work/rest cycle. 0 = always work (no rest phase).")
            .defineInRange("pregen.workSeconds", 300, 5, 86400);
    public static final ModConfigSpec.IntValue PREGEN_REST_SECONDS = SERVER_BUILDER
            .comment("Seconds of rest between work phases. During rest, no generation occurs but progress and phase timers keep running. 0 = always work.")
            .defineInRange("pregen.restSeconds", 120, 0, 86400);
    public static final ModConfigSpec.IntValue PREGEN_RESUME_GRACE_SECONDS = SERVER_BUILDER
            .comment("Seconds the server must be continuously empty of players after the last player leaves before pregen resumes.")
            .defineInRange("pregen.resumeGraceSeconds", 30, 0, 3600);
    public static final ModConfigSpec.IntValue PREGEN_STARTUP_DELAY_SECONDS = SERVER_BUILDER
            .comment("Seconds to wait after ServerStartedEvent before auto-starting pregen. Gives the server time to stabilise.")
            .defineInRange("pregen.startupDelaySeconds", 60, 0, 3600);
    public static final ModConfigSpec.ConfigValue<String> PREGEN_DIMENSION = SERVER_BUILDER
            .comment("Target dimension id.")
            .define("pregen.dimension", "minecraft:overworld");

    // ── [schedule] ──
    public static final ModConfigSpec.BooleanValue SCHEDULE_ENABLED = SERVER_BUILDER
            .comment("Master toggle for the daily pregen burst window. Works independently of pregen.enabled.")
            .define("schedule.enabled", false);
    public static final ModConfigSpec.IntValue SCHEDULE_START_HOUR = SERVER_BUILDER
            .comment("Burst window start hour (local server time, 0-23).")
            .defineInRange("schedule.startHour", 8, 0, 23);
    public static final ModConfigSpec.IntValue SCHEDULE_END_HOUR = SERVER_BUILDER
            .comment("Burst window end hour (local server time, 0-23). If end <= start the window wraps past midnight.")
            .defineInRange("schedule.endHour", 10, 0, 23);
    public static final ModConfigSpec.IntValue SCHEDULE_BURST_CHUNKS_PER_TICK = SERVER_BUILDER
            .comment("Chunk submissions per tick during burst window.")
            .defineInRange("schedule.burstChunksPerTick", 64, 1, 200);
    public static final ModConfigSpec.IntValue SCHEDULE_BURST_MAX_CONCURRENT_CHUNKS = SERVER_BUILDER
            .comment("Max in-flight chunk futures during burst window.")
            .defineInRange("schedule.burstMaxConcurrentChunks", 16, 1, 64);
    public static final ModConfigSpec.IntValue SCHEDULE_BURST_MAX_MILLIS_PER_TICK = SERVER_BUILDER
            .comment("Wall-time budget (ms) per tick during burst window.")
            .defineInRange("schedule.burstMaxMillisPerTick", 30, 1, 40);
    public static final ModConfigSpec.IntValue SCHEDULE_BURST_MAX_SERVER_MSPT = SERVER_BUILDER
            .comment("MSPT threshold during burst. 500 = no effective throttle.")
            .defineInRange("schedule.burstMaxServerMspt", 500, 10, 500);
    public static final ModConfigSpec.BooleanValue SCHEDULE_IGNORE_PLAYERS = SERVER_BUILDER
            .comment("During the burst window, generate chunks even when players are online (assumed lag-tolerant).")
            .define("schedule.ignorePlayers", true);
    public static final ModConfigSpec.BooleanValue SCHEDULE_AUTO_STOP_AT_WINDOW_END = SERVER_BUILDER
            .comment("Stop pregeneration and save progress when the burst window closes.")
            .define("schedule.autoStopAtWindowEnd", true);
    public static final ModConfigSpec.BooleanValue SCHEDULE_REQUIRE_RESTART_AFTER = SERVER_BUILDER
            .comment("Emit a prominent repeated warning to restart the server after the burst window closes.")
            .define("schedule.requireRestartAfter", true);

    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    // ========================================================================
    // CONFIG_VALUES map — unified for /una config get|set|reload
    // ========================================================================

    private static final Map<String, ModConfigSpec.ConfigValue<?>> CONFIG_VALUES = new HashMap<>();

    static {
        // datapack
        CONFIG_VALUES.put("datapack.autoLoad", DATAPACK_AUTO_LOAD);
        CONFIG_VALUES.put("datapack.validateOnLoad", DATAPACK_VALIDATE_ON_LOAD);
        CONFIG_VALUES.put("datapack.createMissingMetadata", DATAPACK_CREATE_MISSING_METADATA);
        // commands
        CONFIG_VALUES.put("commands.opLevel", COMMANDS_OP_LEVEL);
        CONFIG_VALUES.put("commands.enableDatapackCommands", COMMANDS_ENABLE_DATAPACK);
        CONFIG_VALUES.put("commands.enableConfigCommands", COMMANDS_ENABLE_CONFIG);
        // logging
        CONFIG_VALUES.put("logging.level", LOGGING_LEVEL);
        // pregen
        CONFIG_VALUES.put("pregen.enabled", PREGEN_ENABLED);
        CONFIG_VALUES.put("pregen.startX", PREGEN_START_X);
        CONFIG_VALUES.put("pregen.startZ", PREGEN_START_Z);
        CONFIG_VALUES.put("pregen.radius", PREGEN_RADIUS);
        CONFIG_VALUES.put("pregen.chunksPerTick", PREGEN_CHUNKS_PER_TICK);
        CONFIG_VALUES.put("pregen.maxConcurrentChunks", PREGEN_MAX_CONCURRENT_CHUNKS);
        CONFIG_VALUES.put("pregen.maxMillisPerTick", PREGEN_MAX_MILLIS_PER_TICK);
        CONFIG_VALUES.put("pregen.maxServerMspt", PREGEN_MAX_SERVER_MSPT);
        CONFIG_VALUES.put("pregen.workSeconds", PREGEN_WORK_SECONDS);
        CONFIG_VALUES.put("pregen.restSeconds", PREGEN_REST_SECONDS);
        CONFIG_VALUES.put("pregen.resumeGraceSeconds", PREGEN_RESUME_GRACE_SECONDS);
        CONFIG_VALUES.put("pregen.startupDelaySeconds", PREGEN_STARTUP_DELAY_SECONDS);
        CONFIG_VALUES.put("pregen.dimension", PREGEN_DIMENSION);
        // schedule
        CONFIG_VALUES.put("schedule.enabled", SCHEDULE_ENABLED);
        CONFIG_VALUES.put("schedule.startHour", SCHEDULE_START_HOUR);
        CONFIG_VALUES.put("schedule.endHour", SCHEDULE_END_HOUR);
        CONFIG_VALUES.put("schedule.burstChunksPerTick", SCHEDULE_BURST_CHUNKS_PER_TICK);
        CONFIG_VALUES.put("schedule.burstMaxConcurrentChunks", SCHEDULE_BURST_MAX_CONCURRENT_CHUNKS);
        CONFIG_VALUES.put("schedule.burstMaxMillisPerTick", SCHEDULE_BURST_MAX_MILLIS_PER_TICK);
        CONFIG_VALUES.put("schedule.burstMaxServerMspt", SCHEDULE_BURST_MAX_SERVER_MSPT);
        CONFIG_VALUES.put("schedule.ignorePlayers", SCHEDULE_IGNORE_PLAYERS);
        CONFIG_VALUES.put("schedule.autoStopAtWindowEnd", SCHEDULE_AUTO_STOP_AT_WINDOW_END);
        CONFIG_VALUES.put("schedule.requireRestartAfter", SCHEDULE_REQUIRE_RESTART_AFTER);
    }

    // ========================================================================
    // Typed accessors
    // ========================================================================

    public static boolean datapackAutoLoad() { return DATAPACK_AUTO_LOAD.get(); }
    public static boolean validateOnLoad() { return DATAPACK_VALIDATE_ON_LOAD.get(); }
    public static boolean createMissingMetadata() { return DATAPACK_CREATE_MISSING_METADATA.get(); }

    public static int opLevel() { return COMMANDS_OP_LEVEL.get(); }
    public static boolean datapackCommandsEnabled() { return COMMANDS_ENABLE_DATAPACK.get(); }
    public static boolean configCommandsEnabled() { return COMMANDS_ENABLE_CONFIG.get(); }

    public static LogLevel logLevel() { return LOGGING_LEVEL.get(); }

    public static boolean pregenEnabled() { return PREGEN_ENABLED.get(); }
    public static int pregenStartX() { return PREGEN_START_X.get(); }
    public static int pregenStartZ() { return PREGEN_START_Z.get(); }
    public static int pregenRadius() { return PREGEN_RADIUS.get(); }
    public static int chunksPerTick() { return PREGEN_CHUNKS_PER_TICK.get(); }
    public static int maxConcurrentChunks() { return PREGEN_MAX_CONCURRENT_CHUNKS.get(); }
    public static int maxMillisPerTick() { return PREGEN_MAX_MILLIS_PER_TICK.get(); }
    public static int maxServerMspt() { return PREGEN_MAX_SERVER_MSPT.get(); }
    public static int workSeconds() { return PREGEN_WORK_SECONDS.get(); }
    public static int restSeconds() { return PREGEN_REST_SECONDS.get(); }
    public static int resumeGraceSeconds() { return PREGEN_RESUME_GRACE_SECONDS.get(); }
    public static int startupDelaySeconds() { return PREGEN_STARTUP_DELAY_SECONDS.get(); }
    public static String dimension() { return PREGEN_DIMENSION.get(); }

    // schedule
    public static boolean scheduleEnabled() { return SCHEDULE_ENABLED.get(); }
    public static int scheduleStartHour() { return SCHEDULE_START_HOUR.get(); }
    public static int scheduleEndHour() { return SCHEDULE_END_HOUR.get(); }
    public static int burstChunksPerTick() { return SCHEDULE_BURST_CHUNKS_PER_TICK.get(); }
    public static int burstMaxConcurrentChunks() { return SCHEDULE_BURST_MAX_CONCURRENT_CHUNKS.get(); }
    public static int burstMaxMillisPerTick() { return SCHEDULE_BURST_MAX_MILLIS_PER_TICK.get(); }
    public static int burstMaxServerMspt() { return SCHEDULE_BURST_MAX_SERVER_MSPT.get(); }
    public static boolean scheduleIgnorePlayers() { return SCHEDULE_IGNORE_PLAYERS.get(); }
    public static boolean scheduleAutoStopAtWindowEnd() { return SCHEDULE_AUTO_STOP_AT_WINDOW_END.get(); }
    public static boolean scheduleRequireRestartAfter() { return SCHEDULE_REQUIRE_RESTART_AFTER.get(); }

    // ========================================================================
    // String-keyed accessors (kept for /una config get|set|reload)
    // ========================================================================

    public static Object get(String key) {
        ModConfigSpec.ConfigValue<?> cv = CONFIG_VALUES.get(key);
        return cv != null ? cv.get() : null;
    }

    public static String getString(String key) {
        Object v = get(key);
        return v != null ? v.toString() : null;
    }

    public static boolean getBoolean(String key) {
        Object v = get(key);
        return v instanceof Boolean b ? b : Boolean.parseBoolean(v != null ? v.toString() : "false");
    }

    public static int getInt(String key) {
        Object v = get(key);
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v != null ? v.toString() : "0"); }
        catch (NumberFormatException e) { return 0; }
    }

    @SuppressWarnings("unchecked")
    public static void set(String key, String value) {
        ModConfigSpec.ConfigValue<?> cv = CONFIG_VALUES.get(key);
        if (cv == null) throw new IllegalArgumentException("Unknown config key: " + key);
        if (cv == LOGGING_LEVEL) {
            LOGGING_LEVEL.set(LogLevel.valueOf(value.trim().toUpperCase()));
        } else if (cv instanceof ModConfigSpec.BooleanValue bv) {
            bv.set(Boolean.parseBoolean(value.trim()));
        } else if (cv instanceof ModConfigSpec.IntValue iv) {
            iv.set(Integer.parseInt(value.trim()));
        } else {
            ((ModConfigSpec.ConfigValue<String>) cv).set(value);
        }
        // Persist to the .toml on disk so the change survives a restart / reload().
        cv.save();
    }

    public static void set(String key, boolean value) {
        set(key, String.valueOf(value));
    }

    public static void set(String key, int value) {
        set(key, String.valueOf(value));
    }

    // ========================================================================
    // Reload — reads both TOML files and applies each value
    // ========================================================================

    @SuppressWarnings("unchecked")
    public static void reload() {
        Path configPath = getConfigPath();
        if (!java.nio.file.Files.exists(configPath)) {
            LOGGER.warn("Config file does not exist: {}", configPath);
            return;
        }
        try (com.electronwill.nightconfig.core.file.FileConfig fc =
                     com.electronwill.nightconfig.core.file.FileConfig.of(configPath)) {
            fc.load();
            applyValuesFromFile(fc);
            LOGGER.info("Reloaded COMMON config from {}", configPath);
        } catch (Exception e) {
            LOGGER.error("Failed to reload config from {}", configPath, e);
        }

        Path pregenPath = getPregenConfigPath();
        if (!java.nio.file.Files.exists(pregenPath)) {
            return;
        }
        try (com.electronwill.nightconfig.core.file.FileConfig fc =
                     com.electronwill.nightconfig.core.file.FileConfig.of(pregenPath)) {
            fc.load();
            applyValuesFromFile(fc);
            LOGGER.info("Reloaded SERVER config from {}", pregenPath);
        } catch (Exception e) {
            LOGGER.error("Failed to reload config from {}", pregenPath, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyValuesFromFile(com.electronwill.nightconfig.core.file.FileConfig fc) {
        for (Map.Entry<String, ModConfigSpec.ConfigValue<?>> entry : CONFIG_VALUES.entrySet()) {
            String[] parts = entry.getKey().split("\\.", 2);
            com.electronwill.nightconfig.core.UnmodifiableConfig section = fc;
            for (int i = 0; i < parts.length - 1; i++) {
                Object sub = section.get(parts[i]);
                if (sub instanceof com.electronwill.nightconfig.core.UnmodifiableConfig uc) {
                    section = uc;
                } else {
                    section = null;
                    break;
                }
            }
            if (section == null) continue;
            String leafKey = parts[parts.length - 1];
            Object raw = section.get(leafKey);
            if (raw == null) continue;
            ModConfigSpec.ConfigValue<?> cv = entry.getValue();
            if (cv == LOGGING_LEVEL) {
                try {
                    LOGGING_LEVEL.set(LogLevel.valueOf(raw.toString().trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                }
            } else if (cv instanceof ModConfigSpec.BooleanValue bv) {
                bv.set(raw instanceof Boolean b ? b : Boolean.parseBoolean(raw.toString()));
            } else if (cv instanceof ModConfigSpec.IntValue iv) {
                iv.set(raw instanceof Number n ? n.intValue() : Integer.parseInt(raw.toString()));
            } else {
                ((ModConfigSpec.ConfigValue<String>) cv).set(raw.toString());
            }
        }
    }

    // ========================================================================
    // Path helpers
    // ========================================================================

    /** COMMON config: config/utility_nexus/admin/config.toml */
    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("utility_nexus/admin/config.toml");
    }

    /**
     * SERVER config: utility_nexus/admin/pregen.toml. NeoForge places SERVER-type
     * configs under &lt;world&gt;/serverconfig/, so the real path is only known once
     * the config has loaded — captured by {@link #setPregenConfigPath} from a
     * ModConfigEvent. Falls back to a gamedir guess before that fires.
     */
    private static volatile Path pregenConfigPath;

    public static void setPregenConfigPath(Path path) {
        pregenConfigPath = path;
    }

    public static Path getPregenConfigPath() {
        Path p = pregenConfigPath;
        return p != null ? p : FMLPaths.GAMEDIR.get().resolve("serverconfig/utility_nexus/admin/pregen.toml");
    }

    public static Path getPregenDir() {
        return FMLPaths.GAMEDIR.get().resolve("utility_nexus_admin");
    }

    public static Path getPregenProgressFile() {
        return getPregenDir().resolve("pregen_progress.json");
    }
}
