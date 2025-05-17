package dev.tmpfs.jvmplant.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class DefaultHookBridge {

    private DefaultHookBridge() {
        throw new AssertionError("No instances for you!");
    }

    private static IHookBridge sHookBridge;

    @Nullable
    public static IHookBridge getHookBridge() {
        return sHookBridge;
    }

    @NotNull
    public static IHookBridge requireHookBridge() {
        if (sHookBridge == null) {
            throw new IllegalStateException("HookBridge is not initialized");
        }
        return sHookBridge;
    }

    public static void setHookBridge(@Nullable IHookBridge hookBridge) {
        Objects.requireNonNull(hookBridge, "hookBridge");
        if (sHookBridge != null) {
            throw new IllegalStateException("HookBridge is already initialized");
        }
        sHookBridge = hookBridge;
    }

}
