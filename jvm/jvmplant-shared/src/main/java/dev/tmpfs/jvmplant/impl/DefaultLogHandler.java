package dev.tmpfs.jvmplant.impl;

import dev.tmpfs.jvmplant.api.LogHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultLogHandler implements LogHandler {

    private DefaultLogHandler() {
    }

    private static final DefaultLogHandler INSTANCE = new DefaultLogHandler();

    private static final ConcurrentHashMap<String, Logger> loggers = new ConcurrentHashMap<>();

    public static DefaultLogHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void log(@NotNull String tag, Level level, @Nullable String message, @Nullable Throwable throwable) {
        if (message == null && throwable == null) {
            return;
        }
        Logger logger = loggers.computeIfAbsent(tag, Logger::getLogger);
        if (throwable != null) {
            if (message == null) {
                message = throwable.toString();
            }
            logger.log(level, message, throwable);
        } else {
            logger.log(level, message);
        }
    }

}
