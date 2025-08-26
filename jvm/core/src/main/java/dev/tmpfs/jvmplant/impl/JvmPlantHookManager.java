package dev.tmpfs.jvmplant.impl;

import net.bytebuddy.dynamic.loading.ClassInjector;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

class JvmPlantHookManager {

    private JvmPlantHookManager() {
        throw new AssertionError("No instances for you!");
    }

    private static final Object sHookLock = new Object();

    private static final ConcurrentHashMap<Long, HookInfo> sHookInfoMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Member, Long> sMethodToHookIdMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, byte[]> sHookedClasses = new ConcurrentHashMap<>();
    private static final Set<Class<?>> sHookedInsatnceConstructorDeclaringClassSet = ConcurrentHashMap.newKeySet();

    private static final AtomicLong sNextHookId = new AtomicLong(1);

    public static class HookInfo {
        public long id;
        public Member method;
        public JvmPlantCallbackToken token;
    }

    static boolean sInitialized = false;
    static Class<?> sGlobalStaticFieldsClass = null;

    static synchronized void doInitialize() {
        if (sInitialized) {
            return;
        }
        JvmPlantNativeBridge.nativeInitializeJvmPlant();
        initGlobalStaticFieldsClass();
        sInitialized = true;
    }

    private synchronized static void initGlobalStaticFieldsClass() {
        if (sGlobalStaticFieldsClass != null) {
            return;
        }
        String packageName = "dev.tmpfs.jvmplant.generated.u" + UUID.randomUUID().toString().replace("-", "");
        String className = packageName + ".JvmPlantGlobalStaticFields";
        // inject the class file into the JVM bootstrap classloader
        Map<String, byte[]> types = new HashMap<>(1);
        byte[] bytecode = TheGlobalStaticFieldsGenerator.generateBytecode(className);
        ClassFileVerifier.verifyClassFile(bytecode);
        types.put(className, bytecode);
        Map<String, Class<?>> result = ClassInjector.UsingUnsafe.ofBootLoader().injectRaw(types);
        sGlobalStaticFieldsClass = Objects.requireNonNull(result.get(className));
        // set the entry method
        try {
            Method entryMethod = JvmPlantTrampoline0.THE_ENTRY_METHOD;
            sGlobalStaticFieldsClass.getField("sEntry").set(null, entryMethod);
        } catch (ReflectiveOperationException e) {
            throw ReflectHelper.unsafeThrow(e);
        }
    }

    static Object handleHookedMethod(Object[] idThisArgs) throws Throwable {
        long id = (Long) idThisArgs[0];
        HookInfo hookInfo = sHookInfoMap.get(id);
        if (hookInfo == null) {
            throw new IllegalStateException("Hook info not found for id: " + id);
        }
        JvmPlantCallbackToken token = hookInfo.token;
        if (token == null) {
            throw new IllegalStateException("Hook token not found for id: " + id);
        }
        return token.callback(idThisArgs);
    }

    private static final HashSet<Class<?>> sShouldNotBeHookClasses = new HashSet<>();

    static {
        sShouldNotBeHookClasses.add(Object.class);
        sShouldNotBeHookClasses.add(String.class);
        sShouldNotBeHookClasses.add(Class.class);
        sShouldNotBeHookClasses.add(ThreadLocal.class);
        sShouldNotBeHookClasses.add(Integer.class);
        sShouldNotBeHookClasses.add(Long.class);
        sShouldNotBeHookClasses.add(Double.class);
        sShouldNotBeHookClasses.add(Float.class);
        sShouldNotBeHookClasses.add(Short.class);
        sShouldNotBeHookClasses.add(Byte.class);
        sShouldNotBeHookClasses.add(Character.class);
        sShouldNotBeHookClasses.add(Boolean.class);
    }

    /**
     * Hook a method. This method will replace the target method with the callback method.
     * <p>
     * JvmPlant backup is always a method, regardless of the target type being method or constructor.
     * <p>
     * The callback method must be a public instance (virtual) method of the context object class,
     * with Object[] as parameters, and the return type must be Object.
     *
     * @param target  the method to hook, must not be null
     * @param context the context object associated with the hook, must not be null
     * @throws RuntimeException if any error occurs
     */
    static void doHookMethod(@NotNull Member target, @NotNull JvmPlantCallbackToken context) throws RuntimeException {
        Class<?> targetClass = target.getDeclaringClass();
        // array and primitive types are not supported
        if (targetClass.isArray() || targetClass.isPrimitive()) {
            throw new IllegalArgumentException("target class is array or primitive type: " + targetClass.getName());
        }
        if (sShouldNotBeHookClasses.contains(targetClass)) {
            throw new IllegalArgumentException("hook not allowed for class: " + targetClass.getName());
        }
        // currently, native methods are not supported
        if (Modifier.isNative(target.getModifiers())) {
            throw new IllegalArgumentException("hook native method not supported: " + target);
        }
        synchronized (sHookLock) {
            // check if the method is already hooked
            if (sMethodToHookIdMap.containsKey(target)) {
                throw new IllegalStateException("Method is already hooked: " + target);
            }
            byte[] currentBytecode;
            if (!sHookedClasses.containsKey(targetClass)) {
                // fetch the current bytecode of the class
                currentBytecode = JvmPlantNativeBridge.nativeGetClassFile(targetClass);
            } else {
                // use the already hooked class bytecode
                currentBytecode = sHookedClasses.get(targetClass);
            }
            // apply method hook to class
            // pick a hook id
            long hookId = sNextHookId.getAndIncrement();
            byte[] newBytecode = applyMethodHookToClassLocked(target, currentBytecode, hookId);
            ClassFileVerifier.verifyClassFile(newBytecode);

            if ((target instanceof Constructor) && !Modifier.isStatic(target.getModifiers())) {
                // if the target is a constructor, we need to add the class to the hooked instance constructor set
                sHookedInsatnceConstructorDeclaringClassSet.add(targetClass);
            }
            boolean shouldBypassVerification = sHookedInsatnceConstructorDeclaringClassSet.contains(targetClass);
            JvmPlantNativeBridge.nativeRedefineClassV2(targetClass, newBytecode, shouldBypassVerification);
            // redefine completed, now we can set the hook info
            HookInfo hookInfo = new HookInfo();
            hookInfo.id = hookId;
            hookInfo.method = target;
            hookInfo.token = context;
            sHookInfoMap.put(hookId, hookInfo);
            sMethodToHookIdMap.put(target, hookId);
            sHookedClasses.put(targetClass, newBytecode);
        }
    }

    /**
     * Check if a method is hooked.
     *
     * @param target the method to check, must not be null
     * @return true if the method is hooked, false otherwise
     * @throws RuntimeException if any error occurs
     */
    static boolean isMethodHooked(@NotNull Member target) {
        return sMethodToHookIdMap.containsKey(target);
    }

    // unhook not implemented yet

    private static byte[] applyMethodHookToClassLocked(@NotNull Member target, byte @NotNull [] currentBytecode, long hookId) {
        boolean isStatic = (target.getModifiers() & Modifier.STATIC) != 0;
        boolean isConstructor = target instanceof Constructor;
        // check if we overflowed
        if (hookId == Long.MAX_VALUE) {
            throw new IllegalStateException("Hook ID overflow");
        }
        // insert the hook info
        JvmPlantClassTransformer.HookTargetInfo hookTargetInfo = new JvmPlantClassTransformer.HookTargetInfo();
        if (isConstructor) {
            hookTargetInfo.methodName = isStatic ? "<clinit>" : "<init>";
            hookTargetInfo.methodDescriptor = Type.getConstructorDescriptor(((Constructor<?>) target));
        } else {
            hookTargetInfo.methodName = target.getName();
            hookTargetInfo.methodDescriptor = Type.getMethodDescriptor((Method) target);
        }
        hookTargetInfo.hookId = hookId;
        hookTargetInfo.globalStaticFieldsClassType = sGlobalStaticFieldsClass.getName().replace('.', '/');
        return JvmPlantClassTransformer.installHookPrologueToClass(currentBytecode, hookTargetInfo);
    }

//    private static void debugDumpClassFile(String name, byte[] bytecode) {
//        // dump the class file for debugging
//        String className = name.replace('.', '-').replace('$', '-');
//        String fileName = className + ".class";
//        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(fileName)) {
//            fos.write(bytecode);
//            fos.flush();
//        } catch (java.io.IOException e) {
//            e.printStackTrace();
//        }
//    }

}
