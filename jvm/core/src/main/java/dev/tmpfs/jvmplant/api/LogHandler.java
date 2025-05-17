package dev.tmpfs.jvmplant.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;

public interface LogHandler {

    /**
     * Log the message with the specified tag and level.
     *
     * @param tag       the tag
     * @param level     the level
     * @param message   the message, optional
     * @param throwable the throwable, optional
     */
    void log(@NotNull String tag, Level level, @Nullable String message, @Nullable Throwable throwable);

}
