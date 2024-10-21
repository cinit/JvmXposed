package dev.tmpfs.jvmplant.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

class JvmPlantNativeBridge {

    private JvmPlantNativeBridge() {
        throw new AssertionError("No instances for you!");
    }

    /**
     * Initialize the JVM plant.
     *
     * @throws RuntimeException if any error occurs
     */
    static native void nativeInitializeJvmPlant() throws RuntimeException;

    /**
     * Hook a method. This method will replace the target method with the callback method.
     * <p>
     * JvmPlant backup is always a method, regardless of the target type being method or constructor.
     * <p>
     * The callback method must be a public instance (virtual) method of the context object class,
     * with Object[] as parameters, and the return type must be Object.
     *
     * @param target   the method to hook, must not be null
     * @param callback the method to replace the target method, must not be null
     * @param context  the context object associated with the hook, must not be null
     * @return the backup method if the method is hooked successfully, null otherwise
     * @throws RuntimeException if any error occurs
     */
    @Nullable
    static native Method nativeHookMethod(@NotNull Member target, @NotNull Member callback, @NotNull Object context) throws RuntimeException;

    /**
     * Check if a method is hooked.
     *
     * @param target the method to check, must not be null
     * @return true if the method is hooked, false otherwise
     * @throws RuntimeException if any error occurs
     */
    static native boolean nativeIsMethodHooked(@NotNull Member target) throws RuntimeException;

    /**
     * Unhook a method, which means the method will be restored to its original implementation.
     *
     * @param target the method to unhook, must not be null
     * @return true if the method is unhooked successfully, false otherwise (e.g., the method is not hooked)
     * @throws RuntimeException if any error occurs
     */
    static native boolean nativeUnhookMethod(@NotNull Member target) throws RuntimeException;

    /**
     * Deoptimize a method, which means the method will be restored to interpreter mode.
     *
     * @param target the method to deoptimize, must not be null
     * @return true if the method is deoptimized successfully, false otherwise
     * @throws RuntimeException if any error occurs
     */
    static native boolean nativeDeoptimizeMethod(@NotNull Member target) throws RuntimeException;

    /**
     * Get the class initializer, aka, the "<clinit>" method, which is a static constructor without parameters.
     *
     * @param clazz the class to get the class initializer, must not be null
     * @return the class initializer, or null if the class has no class initializer
     */
    static native Executable nativeGetClassInitializer(@NotNull Class<?> clazz);

    static native <T> T nativeAllocateInstance(@NotNull Class<T> clazz) throws InstantiationException;

    // If the method signature does not match the actual method signature, the behavior is undefined, e.g., ART runtime aborts.
    static native Object invokeNonVirtualArtMethodImpl(@NotNull Member member, @NotNull String signature, @NotNull Class<?> klass,
                                                       boolean isStatic, @Nullable Object obj, @NotNull Object[] args) throws InvocationTargetException;

}
