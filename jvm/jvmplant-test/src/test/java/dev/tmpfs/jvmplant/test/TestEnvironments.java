package dev.tmpfs.jvmplant.test;

import dev.tmpfs.jvmplant.api.JvmPlant;

public class TestEnvironments {

    private TestEnvironments() {
        throw new AssertionError("No instances for you!");
    }

    private static boolean sInitialized = false;

    private static synchronized void initializeInternal() {
        if (sInitialized) {
            return;
        }
        sInitialized = true;
        JvmPlant.initialize();
    }

    public static void ensureInitialized() {
        if (!sInitialized) {
            initializeInternal();
        }
    }

}
