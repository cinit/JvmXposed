package dev.tmpfs.jvmplant.api;

import dev.tmpfs.jvmplant.impl.DefaultLogHandler;
import dev.tmpfs.jvmplant.impl.DefaultNativeLoaders;
import dev.tmpfs.jvmplant.impl.JvmPlantHookImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class JvmPlant {

    private JvmPlant() {
        throw new AssertionError("No instances for you!");
    }

    private static final String LIBRARY_NAME = "jvmplant";

    private static LogHandler sLogHandler = DefaultLogHandler.getInstance();

    /**
     * Initialize the hook bridge.
     *
     * @param loader the native library loader, if null, the default loader will be used.
     * @return the hook bridge.
     */
    @NotNull
    public static IHookBridge initialize(@NotNull NativeLibraryLoader loader) {
        IHookBridge bridge = DefaultHookBridge.getHookBridge();
        if (bridge != null) {
            return bridge;
        }
        Objects.requireNonNull(loader, "loader");
        return initializeInternal(loader);
    }

    /**
     * Initialize the hook bridge with the default native library loader.
     *
     * @return the hook bridge.
     */
    @NotNull
    public static IHookBridge initialize() {
        return initialize(DefaultNativeLoaders.getDefaultNativeLibraryLoader());
    }

    @NotNull
    private static IHookBridge initializeInternal(@NotNull NativeLibraryLoader loader) {
        Objects.requireNonNull(loader, "loader");
        // race condition is fine here
        loader.loadLibrary(LIBRARY_NAME, JvmPlant.class);
        // JvmPlantHookImpl.initializeJvmPlantHookBridge will set the DefaultHookBridge
        JvmPlantHookImpl.initializeJvmPlantHookBridge();
        return DefaultHookBridge.requireHookBridge();
    }

    public static void setLogHandler(@NotNull LogHandler handler) {
        sLogHandler = Objects.requireNonNull(handler, "handler");
    }

    @NotNull
    public static LogHandler getLogHandler() {
        return sLogHandler;
    }

}
