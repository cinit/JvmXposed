package dev.tmpfs.jvmplant.impl;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public class JvmPlantTrampoline0 {

    private JvmPlantTrampoline0() {
        throw new AssertionError("No instance for you!");
    }

    public static final ThreadLocal<Boolean> sInvokeOrigin = ThreadLocal.withInitial(() -> false);

    public static Object[] entry(Object[] idThisArgs) throws Throwable {
        // check if we should invoke the origin method
        if (sInvokeOrigin.get()) {
            sInvokeOrigin.set(false);
            // return null so the caller trampoline will not invoke the origin method
            return null;
        }
        // invoke the hook callback
        return new Object[]{JvmPlantHookManager.handleHookedMethod(idThisArgs)};
    }

    public static final Method THE_ENTRY_METHOD;

    static {
        try {
            THE_ENTRY_METHOD = JvmPlantTrampoline0.class.getDeclaredMethod("entry", Object[].class);
        } catch (NoSuchMethodException e) {
            throw ReflectHelper.unsafeThrow(e);
        }
    }

    static void invokeOriginalMethodForNextInvocation(boolean origin) {
        sInvokeOrigin.set(origin);
    }

}
