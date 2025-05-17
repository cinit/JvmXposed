package dev.tmpfs.jvmplant.impl;

import dev.tmpfs.jvmplant.api.NativeLibraryLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

public class DefaultNativeLoaders {

    private DefaultNativeLoaders() {
        throw new AssertionError("No instance for you!");
    }

    public enum JvmType {
        /**
         * Unknown JVM type.
         */
        UNKNOWN,
        /**
         * OpenJDK JVM, the most common JVM.
         */
        OPENJDK,
        /**
         * GraalVM JVM, this implementation does not support full reflection.
         * So we have no plan to support it.
         */
        GRAALVM,
        /**
         * Dalvik/ART JVM, the Android JVM.
         * Dalvik 1.x is Dalvik VM, Dalvik 2.x is ART.
         */
        DALVIK_ART;

        public String getShortName() {
            switch (this) {
                case OPENJDK:
                    return "openjdk";
                case GRAALVM:
                    return "graalvm";
                case DALVIK_ART:
                    return "dalvik";
                default:
                    return "unknown";
            }
        }
    }

    public enum OsType {
        UNKNOWN, WINDOWS, LINUX, MACOS, ANDROID, BSD;

        public String getShortName() {
            switch (this) {
                case WINDOWS:
                    return "win";
                case LINUX:
                    return "linux";
                case MACOS:
                    return "mac";
                case ANDROID:
                    return "android";
                case BSD:
                    return "bsd";
                default:
                    return "unknown";
            }
        }
    }

    public enum IsaType {
        /**
         * Unknown ISA type.
         */
        UNKNOWN,
        /**
         * AArch32, the 32-bit ARM ISA.
         */
        ARM,
        /**
         * AArch64, the 64-bit ARM ISA.
         */
        ARM64,
        /**
         * x86/i386, the 32-bit x86 ISA.
         */
        X86,
        /**
         * x86_64/amd64, the 64-bit x86 ISA.
         */
        X86_64,
        /**
         * The 32-bit MIPS ISA.
         */
        MIPS,
        /**
         * The 64-bit MIPS ISA.
         */
        MIPS64,
        /**
         * The 32-bit RISC-V ISA.
         */
        RISCV32,
        /**
         * The 64-bit RISC-V ISA.
         */
        RISCV64;

        public String getShortName() {
            switch (this) {
                case ARM:
                    return "arm";
                case ARM64:
                    return "arm64";
                case X86:
                    return "x86";
                case X86_64:
                    return "x86_64";
                case MIPS:
                    return "mips";
                case MIPS64:
                    return "mips64";
                case RISCV32:
                    return "riscv32";
                case RISCV64:
                    return "riscv64";
                default:
                    return "unknown";
            }
        }
    }

    public enum LibcType {
        /**
         * Unknown libc type.
         */
        UNKNOWN,
        /**
         * The GNU libc, the most common libc for Linux.
         */
        GNU,
        /**
         * The musl libc, a lightweight libc for Linux.
         * Distros like Alpine Linux and OpenWrt use it.
         */
        MUSL,
        /**
         * The Bionic libc, the libc for Android.
         */
        BIONIC,
        /**
         * The UCRT and MSVCRT implement C runtime for Windows.
         */
        UCRT_VCRT;

        public String getShortName() {
            switch (this) {
                case GNU:
                    return "gnu";
                case MUSL:
                    return "musl";
                case BIONIC:
                    return "bionic";
                case UCRT_VCRT:
                    return "vcrt";
                default:
                    return "unknown";
            }
        }
    }

    public static class SystemInfo {
        public final JvmType jvmType;
        public final OsType osType;
        public final IsaType isaType;
        public final LibcType libcType;

        public SystemInfo(JvmType jvmType, OsType systemType, IsaType isaType, LibcType libcType) {
            this.jvmType = jvmType;
            this.osType = systemType;
            this.isaType = isaType;
            this.libcType = libcType;
        }

        @Override
        public String toString() {
            return "SystemInfo{" + "jvmType=" + jvmType + ", systemType=" + osType + ", isaType=" + isaType + ", libcType=" + libcType + '}';
        }
    }

    private static final String PROTECT_NAME = "jvmplant";

    public static SystemInfo getSystemInfo() {
        // 1. check JVM type
        JvmType jvmType = JvmType.UNKNOWN;
        try {
            Class.forName("dalvik.system.BaseDexClassLoader");
            jvmType = JvmType.DALVIK_ART;
        } catch (ClassNotFoundException ignored) {
        }
        if (jvmType == JvmType.UNKNOWN) {
            try {
                Class.forName("com.oracle.svm.core.hub.DynamicHub");
                jvmType = JvmType.GRAALVM;
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (jvmType == JvmType.UNKNOWN) {
            try {
                Class.forName("jdk.internal.misc.Unsafe");
                jvmType = JvmType.OPENJDK;
            } catch (ClassNotFoundException ignored) {
            }
        }
        // 2. check system type
        OsType systemType = OsType.UNKNOWN;
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            systemType = OsType.WINDOWS;
        } else if (osName.contains("linux")) {
            systemType = OsType.LINUX;
        } else if (osName.contains("mac")) {
            systemType = OsType.MACOS;
        } else if (osName.contains("bsd")) {
            systemType = OsType.BSD;
        } else if (osName.contains("android")) {
            systemType = OsType.ANDROID;
        }
        // 3. check isa type
        IsaType isaType = IsaType.UNKNOWN;
        String osArch = System.getProperty("os.arch").toLowerCase();
        if (osArch.contains("arm")) {
            isaType = (osArch.contains("64") || osArch.contains("v8a")) ? IsaType.ARM64 : IsaType.ARM;
        } else if (osArch.contains("x86") || osArch.contains("amd64")) {
            isaType = osArch.contains("64") ? IsaType.X86_64 : IsaType.X86;
        } else if (osArch.contains("mips")) {
            isaType = osArch.contains("64") ? IsaType.MIPS64 : IsaType.MIPS;
        } else if (osArch.contains("riscv")) {
            isaType = osArch.contains("64") ? IsaType.RISCV64 : IsaType.RISCV32;
        }
        // 4. check libc type, for linux only
        LibcType libcType = LibcType.UNKNOWN;
        if (systemType == OsType.LINUX) {
            // dirty, need to improve
            HashSet<String> files = new HashSet<>();
            // scan /lib and /lib64, starting with ld-
            File[] dirs = new File[]{new File("/lib"), new File("/lib64")};
            for (File dir : dirs) {
                if (dir.exists() && dir.isDirectory()) {
                    for (File file : dir.listFiles()) {
                        if (file.isFile() && file.getName().startsWith("ld-")) {
                            files.add(file.getName());
                        }
                    }
                }
            }
            // check the libc type
            boolean isMusl = false;
            for (String file : files) {
                if (file.contains("musl")) {
                    isMusl = true;
                    break;
                }
            }
            if (isMusl) {
                libcType = LibcType.MUSL;
            } else {
                libcType = LibcType.GNU;
            }
        } else if (systemType == OsType.ANDROID) {
            libcType = LibcType.BIONIC;
        } else if (systemType == OsType.WINDOWS) {
            libcType = LibcType.UCRT_VCRT;
        }
        return new SystemInfo(jvmType, systemType, isaType, libcType);
    }

    public static File findTemporarilyDirectory() {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        if (!tmpDir.exists() && !tmpDir.mkdirs()) {
            throw new IllegalStateException("Cannot create temp directory: " + tmpDir);
        }
        // check if the directory is writable
        if (!tmpDir.canWrite()) {
            throw new IllegalStateException("Temp directory is not writable: " + tmpDir);
        }
        return tmpDir;
    }

    /**
     * Get the library directory name for the system.
     * <p>
     * e.g. "openjdk-linux-x86_64-gnu", "dalvik-android-arm64-bionic"
     * "openjdk-win-x86-gnu", "graalvm-macos-arm64-gnu", "openjdk-win-x86-vcrt"
     */
    @NotNull
    public static String getLibraryDirectoryNameForSystem(SystemInfo info) {
        return info.jvmType.getShortName() + "-" + info.osType.getShortName() + "-" + info.isaType.getShortName() + "-" + info.libcType.getShortName();
    }

    /**
     * Find the library resource in the classpath.
     *
     * @param libName the library name without the prefix and suffix
     * @param info    the system information
     * @return the input stream of the library resource, or null if not found
     */
    @Nullable
    private static InputStream findLibraryResource(String libName, SystemInfo info) {
        String libDir = getLibraryDirectoryNameForSystem(info);
        String libPath = "/" + libDir + "/" + System.mapLibraryName(libName);
        return DefaultNativeLoaders.class.getResourceAsStream(libPath);
    }

    private static void copyToFile(@NotNull InputStream is, @NotNull File file) throws IOException {
        // create the parent directory
        File parentDir = file.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Cannot create directory: " + parentDir);
        }
        // copy the library to the temp directory
        try (InputStream fis = is; FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
        } catch (IOException e) {
            // delete the file if copy failed
            if (file.exists()) {
                file.delete();
                // silently ignore the exception if the file cannot be deleted
            }
            throw e;
        }
    }

    private static final class CopyToTempDirLoader implements NativeLibraryLoader {

        @NotNull
        public final File mTempDir;


        public CopyToTempDirLoader(@NotNull File tempDir) {
            if (!tempDir.isAbsolute()) {
                throw new IllegalArgumentException("tempDir must be an absolute path");
            }
            mTempDir = tempDir;
        }

        private File makeTempFileLocation(String libName) {
            String subDir = PROTECT_NAME + "-" + VersionConfig.VERSION_NAME + "-" + VersionConfig.VERSION_CODE;
            String fileName = System.mapLibraryName(libName);
            return new File(mTempDir, subDir + File.separator + fileName);
        }

        @Override
        public void loadLibrary(@NotNull String name, @NotNull Class<?> caller) throws UnsatisfiedLinkError {
            File parentDir = makeTempFileLocation(name).getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new UnsatisfiedLinkError("Cannot create directory: " + parentDir);
            }
            // check if the file exists
            File libFile = makeTempFileLocation(name);
            if (!libFile.exists()) {
                SystemInfo info = getSystemInfo();
                // copy the library to the temp directory
                try (InputStream is = findLibraryResource(name, info)) {
                    if (is == null) {
                        throw new UnsatisfiedLinkError("Cannot find library: " + name + " for system: " + info);
                    }
                    copyToFile(is, libFile);
                } catch (IOException e) {
                    throw new UnsatisfiedLinkError("Cannot copy library: " + name + " to " + libFile + ", " + e);
                }
            }
        }
    }

    private static final class AndroidNativeLoader implements NativeLibraryLoader {

        @Override
        public void loadLibrary(@NotNull String name, @NotNull Class<?> caller) throws UnsatisfiedLinkError {
            System.loadLibrary(name);
        }
    }

    public static NativeLibraryLoader getDefaultNativeLibraryLoader() {
        SystemInfo info = getSystemInfo();
        if (info.osType == OsType.ANDROID) {
            return new AndroidNativeLoader();
        } else {
            return new CopyToTempDirLoader(findTemporarilyDirectory());
        }
    }

}
