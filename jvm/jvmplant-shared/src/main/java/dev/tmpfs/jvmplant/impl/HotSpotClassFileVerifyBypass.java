package dev.tmpfs.jvmplant.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class provides methods to bypass class file verification in the HotSpot JVM.
 * See <a href="https://char.lt/blog/2019/12/jvm-hackery-noverify/">jvm-hackery-noverify</a>
 * and <a href="https://github.com/luelueking/Bypass_JVM_Verifier">Bypass_JVM_Verifier</a> for more details.
 */
public class HotSpotClassFileVerifyBypass {

    private HotSpotClassFileVerifyBypass() {
    }

    private static Method sFindNativeMethod = null;

    public static long findNative(@NotNull String name, @Nullable ClassLoader loader) {
        if (sFindNativeMethod == null) {
            try {
                Method m = Class.forName("java.lang.ClassLoader").getDeclaredMethod("findNative", ClassLoader.class, String.class);
                ReflectHelper.makeAccessible(m);
                sFindNativeMethod = m;
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                throw ReflectHelper.unsafeThrow(e);
            }
        }
        try {
            return (Long) sFindNativeMethod.invoke(null, loader, name);
        } catch (ReflectiveOperationException e) {
            throw ReflectHelper.unsafeThrowForIteCause(e);
        }
    }

    public static class Fld {
        private final String name;
        private final String type;
        private final long offset;
        private final boolean isStatic;

        public Fld(String name, String type, long offset, boolean isStatic) {
            this.name = name;
            this.type = type;
            this.offset = offset;
            this.isStatic = isStatic;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public long getOffset() {
            return offset;
        }

        public boolean isStatic() {
            return isStatic;
        }

    }

    public static class JVMFlag {
        private final String name;
        private final long address;

        public JVMFlag(String name, long address) {
            this.name = name;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public long getAddress() {
            return address;
        }
    }

    public static class JVMStruct {
        private final String name;
        private final Map<String, Fld> fields;

        public JVMStruct(String name) {
            this.name = name;
            this.fields = new HashMap<>();
        }

        public void setField(String fieldName, Fld value) {
            fields.put(fieldName, value);
        }

        public Fld getField(String fieldName) {
            return fields.get(fieldName);
        }

        public String getName() {
            return name;
        }

        public Map<String, Fld> getFields() {
            return fields;
        }
    }

    public static class JVMType {
        private final String type;
        private final String superClass;
        private final int size;
        private final boolean oop;
        private final boolean intType;
        private final boolean unsigned;
        private final Map<String, Fld> fields;

        public JVMType(String type, String superClass, int size, boolean oop, boolean intType, boolean unsigned) {
            this.type = type;
            this.superClass = superClass;
            this.size = size;
            this.oop = oop;
            this.intType = intType;
            this.unsigned = unsigned;
            this.fields = new HashMap<>();
        }

        public Map<String, Fld> getFields() {
            return fields;
        }

        public String getType() {
            return type;
        }

        public String getSuperClass() {
            return superClass;
        }

        public int getSize() {
            return size;
        }

        public boolean isOop() {
            return oop;
        }

        public boolean isIntType() {
            return intType;
        }

        public boolean isUnsigned() {
            return unsigned;
        }
    }

    public static List<JVMFlag> getFlags(Map<String, JVMType> types) {
        Unsafe unsafe = ReflectHelper.getUnsafe();
        List<JVMFlag> jvmFlags = new ArrayList<>();

        JVMType flagType = types.get("Flag");
        if (flagType == null) {
            flagType = types.get("JVMFlag");
            if (flagType == null) {
                throw new RuntimeException("Could not resolve type 'Flag'");
            }
        }

        Fld flagsField = flagType.getFields().get("flags");
        if (flagsField == null) {
            throw new RuntimeException("Could not resolve field 'Flag.flags'");
        }
        long flags = unsafe.getAddress(flagsField.getOffset());

        Fld numFlagsField = flagType.getFields().get("numFlags");
        if (numFlagsField == null) {
            throw new RuntimeException("Could not resolve field 'Flag.numFlags'");
        }
        int numFlags = unsafe.getInt(numFlagsField.getOffset());

        Fld nameField = flagType.getFields().get("_name");
        if (nameField == null) {
            throw new RuntimeException("Could not resolve field 'Flag._name'");
        }

        Fld addrField = flagType.getFields().get("_addr");
        if (addrField == null) {
            throw new RuntimeException("Could not resolve field 'Flag._addr'");
        }

        for (int i = 0; i < numFlags; i++) {
            long flagAddress = flags + ((long) i * flagType.getSize());
            long flagNameAddress = unsafe.getAddress(flagAddress + nameField.getOffset());
            long flagValueAddress = unsafe.getAddress(flagAddress + addrField.getOffset());

            String flagName = getString(flagNameAddress);
            if (flagName != null) {
                JVMFlag flag = new JVMFlag(flagName, flagValueAddress);
                jvmFlags.add(flag);
            }
        }

        return jvmFlags;
    }

    public static Map<String, JVMType> getTypes(Map<String, JVMStruct> structs) {
        Unsafe unsafe = ReflectHelper.getUnsafe();
        Map<String, JVMType> types = new HashMap<>();

        long entry = symbol("gHotSpotVMTypes");
        long arrayStride = symbol("gHotSpotVMTypeEntryArrayStride");

        while (true) {
            String typeName = derefReadString(entry + offsetTypeSymbol("TypeName"));
            if (typeName == null) {
                break;
            }

            String superClassName = derefReadString(entry + offsetTypeSymbol("SuperclassName"));

            int size = unsafe.getInt(entry + offsetTypeSymbol("Size"));
            boolean oop = unsafe.getInt(entry + offsetTypeSymbol("IsOopType")) != 0;
            boolean intType = unsafe.getInt(entry + offsetTypeSymbol("IsIntegerType")) != 0;
            boolean unsigned = unsafe.getInt(entry + offsetTypeSymbol("IsUnsigned")) != 0;

            Map<String, Fld> structFields = null;
            JVMStruct struct = structs.get(typeName);
            if (struct != null) {
                structFields = struct.getFields();
            }
//            Map<String, Fld> structFields = structs.get(typeName).getFields();
            JVMType jvmType = new JVMType(typeName, superClassName, size, oop, intType, unsigned);
            if (structFields != null) {
                jvmType.getFields().putAll(structFields);
            }

            types.put(typeName, jvmType);

            entry += arrayStride;
        }

        return types;
    }


    public static Map<String, JVMStruct> getStructs() {
        Unsafe unsafe = ReflectHelper.getUnsafe();
        Map<String, JVMStruct> structs = new HashMap<>();

        long currentEntry = symbol("gHotSpotVMStructs");
        long arrayStride = symbol("gHotSpotVMStructEntryArrayStride");

        while (true) {
            String typeName = derefReadString(currentEntry + offsetStructSymbol("TypeName"));
            String fieldName = derefReadString(currentEntry + offsetStructSymbol("FieldName"));
            if (typeName == null || fieldName == null) {
                break;
            }

            String typeString = derefReadString(currentEntry + offsetStructSymbol("TypeString"));
            boolean staticField = unsafe.getInt(currentEntry + offsetStructSymbol("IsStatic")) != 0;

            long offsetOffset = staticField ? offsetStructSymbol("Address") : offsetStructSymbol("Offset");
            long offset = unsafe.getLong(currentEntry + offsetOffset);

            JVMStruct struct = structs.computeIfAbsent(typeName, JVMStruct::new);
            struct.setField(fieldName, new Fld(fieldName, typeString, offset, staticField));

            currentEntry += arrayStride;
        }

        return structs;
    }

    public static long symbol(String name) {
        Unsafe unsafe = ReflectHelper.getUnsafe();
        return unsafe.getLong(findNative(name, null));
    }

    public static long offsetStructSymbol(String name) {
        return symbol("gHotSpotVMStructEntry" + name + "Offset");
    }

    public static long offsetTypeSymbol(String name) {
        return symbol("gHotSpotVMTypeEntry" + name + "Offset");
    }

    public static String derefReadString(long addr) {
        Unsafe unsafe = ReflectHelper.getUnsafe();
        return getString(unsafe.getLong(addr));
    }

    public static String getString(long addr) {
        if (addr == 0L) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        int offset = 0;
        Unsafe unsafe = ReflectHelper.getUnsafe();
        while (true) {
            byte b = unsafe.getByte(addr + offset);
            char ch = (char) b;
            if (ch == '\u0000') {
                break;
            }
            stringBuilder.append(ch);
            offset++;
        }
        return stringBuilder.toString();
    }

    private static long pBytecodeVerificationLocal = 0;
    private static long pBytecodeVerificationRemote = 0;

    public static void findBytecodeVerificationFlagAddress() {
        Map<String, JVMStruct> structs = getStructs();
        Map<String, JVMType> types = getTypes(structs);
        List<JVMFlag> flags = getFlags(types);
        for (JVMFlag flag : flags) {
            if (flag.getName().equals("BytecodeVerificationLocal")) {
                pBytecodeVerificationLocal = flag.getAddress();
            } else if (flag.getName().equals("BytecodeVerificationRemote")) {
                pBytecodeVerificationRemote = flag.getAddress();
            }
        }
        if (pBytecodeVerificationLocal == 0) {
            throw new RuntimeException("Could not find BytecodeVerificationLocal flag");
        }
        if (pBytecodeVerificationRemote == 0) {
            throw new RuntimeException("Could not find BytecodeVerificationRemote flag");
        }
    }

    public static void setBytecodeVerification(boolean local, boolean remote) {
        if (pBytecodeVerificationLocal == 0 || pBytecodeVerificationRemote == 0) {
            findBytecodeVerificationFlagAddress();
        }
        Unsafe unsafe = ReflectHelper.getUnsafe();
        unsafe.putInt(pBytecodeVerificationLocal, local ? 1 : 0);
        unsafe.putInt(pBytecodeVerificationRemote, remote ? 1 : 0);
    }

    public static boolean getBytecodeVerificationLocal() {
        if (pBytecodeVerificationLocal == 0) {
            findBytecodeVerificationFlagAddress();
        }
        Unsafe unsafe = ReflectHelper.getUnsafe();
        return unsafe.getInt(pBytecodeVerificationLocal) != 0;
    }

    public static boolean getBytecodeVerificationRemote() {
        if (pBytecodeVerificationRemote == 0) {
            findBytecodeVerificationFlagAddress();
        }
        Unsafe unsafe = ReflectHelper.getUnsafe();
        return unsafe.getInt(pBytecodeVerificationRemote) != 0;
    }

}
