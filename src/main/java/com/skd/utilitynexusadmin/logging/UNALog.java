package com.skd.utilitynexusadmin.logging;

import com.mojang.logging.LogUtils;
import com.skd.utilitynexusadmin.config.UNAConfig;
import org.slf4j.Logger;

public class UNALog {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Configured minimum level, tolerating calls made before NeoForge has loaded the
     * config (e.g. from the mod constructor). Defaults to INFO until the config is ready.
     */
    private static UNAConfig.LogLevel currentLevel() {
        try {
            return UNAConfig.logLevel();
        } catch (IllegalStateException notLoadedYet) {
            return UNAConfig.LogLevel.INFO;
        }
    }

    public static void debug(String msg, Object... args) {
        if (currentLevel().ordinal() <= UNAConfig.LogLevel.DEBUG.ordinal()) {
            LOGGER.debug(msg, args);
        }
    }

    public static void info(String msg, Object... args) {
        if (currentLevel().ordinal() <= UNAConfig.LogLevel.INFO.ordinal()) {
            LOGGER.info(msg, args);
        }
    }

    public static void warn(String msg, Object... args) {
        if (currentLevel().ordinal() <= UNAConfig.LogLevel.WARN.ordinal()) {
            LOGGER.warn(msg, args);
        }
    }

    public static void error(String msg, Object... args) {
        LOGGER.error(msg, args);
    }

    public static void error(String msg, Throwable t) {
        LOGGER.error(msg, t);
    }
}
