package dev.tmpfs.jvmplant.api;

import org.jetbrains.annotations.NotNull;

public interface NativeLibraryLoader {

    /**
     * Load the specified library.
     *
     * @param name   the name of the library WITHOUT the prefix 'lib' and suffix '.so', '.dll' or '.dylib'.
     * @param caller the class that calls this method.
     * @throws UnsatisfiedLinkError if the library cannot be loaded.
     */
    void loadLibrary(@NotNull String name, @NotNull Class<?> caller) throws UnsatisfiedLinkError;

}
