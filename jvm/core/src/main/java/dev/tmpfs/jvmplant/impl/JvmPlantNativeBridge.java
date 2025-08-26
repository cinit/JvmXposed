package dev.tmpfs.jvmplant.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;

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
     * Dump the class file of the given class.
     *
     * @param clazz the class to dump, must not be null
     * @return the class file as a byte array
     * @throws RuntimeException if any error occurs
     */
    static native byte @NotNull [] nativeGetClassFile(@NotNull Class<?> clazz) throws RuntimeException;

    /**
     * Redefine the class with the given class file.
     *
     * @param clazz     the class to redefine, must not be null
     * @param classFile the class file as a byte array, must not be null
     * @throws RuntimeException if any error occurs
     */
    static native void nativeRedefineClassV2(@NotNull Class<?> clazz, byte @NotNull [] classFile, boolean skipVerification) throws RuntimeException;

    /**
     * Get the class initializer, aka, the "<clinit>" method, which is a static constructor without parameters.
     *
     * @param clazz the class to get the class initializer, must not be null
     * @return the class initializer, or null if the class has no class initializer
     */
    static native Constructor<?> nativeGetClassInitializer(@NotNull Class<?> clazz);

    static native <T> T nativeAllocateInstance(@NotNull Class<T> clazz) throws InstantiationException;

    // If the method signature does not match the actual method signature, the behavior is undefined, e.g., ART runtime aborts.
    static native Object invokeNonVirtualArtMethodImpl(@NotNull Member member, @NotNull String signature, @NotNull Class<?> klass,
                                                       boolean isStatic, @Nullable Object obj, Object @NotNull [] args) throws InvocationTargetException;

}
