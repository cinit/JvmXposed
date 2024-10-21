package dev.tmpfs.jvmplant.test;

import dev.tmpfs.jvmplant.api.JvmPlant;
import dev.tmpfs.jvmplant.api.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class TestEnvironments {

    private TestEnvironments() {
        throw new AssertionError("No instances for you!");
    }

    private static boolean sInitialized = false;

    @Nullable
    private static NativeLibraryLoader getTestNativeLibraryLoader() {
        String debugPath = System.getProperty("jvmplant.test.native.dir");
        if (debugPath == null) {
            debugPath = System.getenv("JVMPLANT_TEST_NATIVE_DIR");
        }
        if (debugPath != null) {
            File file = new File(debugPath);
            if (file.exists() && file.isDirectory()) {
                return (name, caller) -> System.load(new File(file, System.mapLibraryName(name)).getAbsolutePath());
            } else {
                throw new IllegalStateException("Invalid native library directory: " + debugPath);
            }
        }
        return null;
    }

    private static synchronized void initializeInternal() {
        if (sInitialized) {
            return;
        }
        NativeLibraryLoader loader = getTestNativeLibraryLoader();
        if (loader != null) {
            JvmPlant.initialize(loader);
        } else {
            JvmPlant.initialize();
        }
        sInitialized = true;
    }

    public static void ensureInitialized() {
        if (!sInitialized) {
            initializeInternal();
        }
    }

}
