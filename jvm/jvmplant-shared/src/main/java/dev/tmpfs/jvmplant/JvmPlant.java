package dev.tmpfs.jvmplant;

import dev.tmpfs.jvmplant.api.DefaultHookBridge;
import dev.tmpfs.jvmplant.api.IHookBridge;
import dev.tmpfs.jvmplant.impl.DefaultNativeLoaders;
import dev.tmpfs.jvmplant.impl.JvmPlantHookImpl;
import dev.tmpfs.jvmplant.nativeloader.NativeLibraryLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JvmPlant {

    private JvmPlant() {
        throw new AssertionError("No instances for you!");
    }

    private static final String LIBRARY_NAME = "jvmplant";

    /**
     * Initialize the hook bridge.
     *
     * @param loader the native library loader, if null, the default loader will be used.
     * @return the hook bridge.
     */
    @NotNull
    public static IHookBridge initialize(@Nullable NativeLibraryLoader loader) {
        IHookBridge bridge = DefaultHookBridge.getHookBridge();
        if (bridge != null) {
            return bridge;
        }
        return initializeInternal(loader);
    }

    /**
     * Initialize the hook bridge with the default native library loader.
     *
     * @return the hook bridge.
     */
    @NotNull
    public static IHookBridge initialize() {
        return initialize(null);
    }

    @NotNull
    private static IHookBridge initializeInternal(@Nullable NativeLibraryLoader suppliedLoader) {
        // check security manager, if a security manager exists,
        // we do not allow initializing the hook bridge because allowing an
        //  arbitrary method makes security manager useless
        if (System.getSecurityManager() != null) {
            throw new SecurityException("Cannot initialize hook bridge with security manager");
        }
        // race condition is fine here
        NativeLibraryLoader nativeLoader = suppliedLoader != null
                ? suppliedLoader : DefaultNativeLoaders.getDefaultNativeLibraryLoader();
        nativeLoader.loadLibrary(LIBRARY_NAME, JvmPlant.class);
        // JvmPlantHookImpl.initializeJvmPlantHookBridge will set the DefaultHookBridge
        JvmPlantHookImpl.initializeJvmPlantHookBridge();
        return DefaultHookBridge.requireHookBridge();
    }

}
