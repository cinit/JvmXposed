package dev.tmpfs.jvmplant.impl;

import dev.tmpfs.jvmplant.api.JvmPlant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import java.lang.reflect.*;
import java.util.Objects;
import java.util.logging.Level;

/**
 * The helper class for JVM plant reflection.
 */
class ReflectHelper {

    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    private ReflectHelper() {
        throw new AssertionError("No instances for you!");
    }

    public static AssertionError unsafeThrow(@NotNull Throwable t) {
        Objects.requireNonNull(t, "t == null");
        unsafeThrowImpl(t);
        throw new AssertionError("unreachable");
    }

    public static AssertionError unsafeThrowForIteCause(@NotNull Throwable t) {
        Objects.requireNonNull(t, "t == null");
        unsafeThrowImpl(getIteCauseOrSelf(t));
        throw new AssertionError("unreachable");
    }

    @NotNull
    public static Throwable getIteCauseOrSelf(@NotNull Throwable t) {
        Objects.requireNonNull(t, "t == null");
        Throwable cause;
        if (t instanceof InvocationTargetException && (cause = t.getCause()) != null) {
            return cause;
        } else {
            return t;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void unsafeThrowImpl(@NotNull Throwable t) throws T {
        throw (T) t;
    }

    public static <T> T allocateInstance(@NotNull Class<T> clazz) throws InstantiationException {
        Objects.requireNonNull(clazz, "clazz == null");
        // not allow primitive type, array type, and abstract class
        if (clazz.isPrimitive() || clazz.isArray() || (clazz.getModifiers() & Modifier.ABSTRACT) != 0) {
            throw new IllegalArgumentException("clazz is not instantiable: " + clazz);
        }
        return JvmPlantNativeBridge.nativeAllocateInstance(clazz);
    }

    @NotNull
    public static Member virtualMethodLookup(@NotNull Member member, @Nullable Object thiz) {
        Objects.requireNonNull(member, "member == null");
        if (member instanceof Method) {
            return virtualMethodLookup((Method) member, thiz);
        } else {
            return member;
        }
    }

    @NotNull
    public static Method virtualMethodLookup(@NotNull Method method, @Nullable Object thiz) {
        if ((method.getModifiers() & (Modifier.STATIC | Modifier.PRIVATE)) != 0) {
            // direct method
            return method;
        }
        if (thiz == null) {
            throw new NullPointerException("thiz == null");
        }
        Class<?> current = thiz.getClass();
        Class<?> declaringClass = method.getDeclaringClass();
        // check class
        declaringClass.cast(thiz);
        Class<?> returnType = method.getReturnType();
        String name = method.getName();
        Class<?>[] argt = method.getParameterTypes();
        // start lookup
        do {
            Method[] methods = current.getDeclaredMethods();
            // only compare virtual methods(non-static and non-private)
            for (Method value : methods) {
                boolean isVirtual = !Modifier.isStatic(value.getModifiers()) && !Modifier.isPrivate(value.getModifiers());
                if (isVirtual && value.getName().equals(name) && returnType == value.getReturnType()) {
                    if (isClassArrayEquals(value.getParameterTypes(), argt)) {
                        return value;
                    }
                }
            }
            // TODO: 2024-08-04 support interface default method
            // stop at declaring class
        } while ((current = current.getSuperclass()) != null && current != declaringClass);
        return method;
    }

    public static boolean isClassArrayEquals(Class<?>[] a, Class<?>[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Invoke an instance method non-virtually, no CHA lookup is performed. No declaring class check is performed.
     * <p>
     * Caller is responsible for checking that the method declaration class matches the receiver object (aka "this").
     *
     * @param member         the method or constructor to invoke, method may be static or non-static method
     * @param declaringClass the "effective" declaring class of the method
     * @param obj            the object to invoke the method on, may be null if the method is static
     * @param args           the arguments to pass to the method. may be null if no arguments are passed
     * @return the return value of the method
     * @throws InvocationTargetException if the method threw an exception
     */
    public static Object invokeNonVirtualArtMethodNoDeclaringClassCheck(@NotNull Member member, @NotNull Class<?> declaringClass,
                                                                        @Nullable Object obj, @Nullable Object[] args,
                                                                        boolean setInvokeOrigin) throws InvocationTargetException {
        Objects.requireNonNull(member, "member must not be null");
        Objects.requireNonNull(declaringClass, "declaringClass must not be null");
        if (args == null) {
            args = EMPTY_OBJECT_ARRAY;
        }
        // perform some basic checks
        if (obj != null) {
            if (!declaringClass.isInstance(obj)) {
                throw new IllegalArgumentException("object class mismatch, expected " + declaringClass + ", got " + obj.getClass());
            }
        }
        if (member instanceof Method) {
            Method method = (Method) member;
            if (method.getParameterTypes().length != args.length) {
                throw new IllegalArgumentException("args length mismatch, expected " + method.getParameterTypes().length + ", got " + args.length);
            }
            // abstract method is not allowed
            if ((method.getModifiers() & (Modifier.ABSTRACT)) != 0) {
                throw new IllegalArgumentException("abstract method is not allowed");
            }
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            String signature = getMethodTypeSignature(method);
            if (setInvokeOrigin) {
                JvmPlantTrampoline0.invokeOriginalMethodForNextInvocation(true);
            }
            return JvmPlantNativeBridge.invokeNonVirtualArtMethodImpl(member, signature, declaringClass, isStatic, obj, args);
        } else if (member instanceof Constructor) {
            Constructor<?> constructor = (Constructor<?>) member;
            if (constructor.getParameterTypes().length != args.length) {
                throw new IllegalArgumentException("args length mismatch, expected " + constructor.getParameterTypes().length + ", got " + args.length);
            }
            String signature = getConstructorTypeSignature(constructor);
            if (setInvokeOrigin) {
                JvmPlantTrampoline0.invokeOriginalMethodForNextInvocation(true);
            }
            return JvmPlantNativeBridge.invokeNonVirtualArtMethodImpl(member, signature, declaringClass, false, obj, args);
        } else {
            throw new IllegalArgumentException("member must be a method or constructor");
        }
    }

    /**
     * Invoke an instance method non-virtually, no CHA lookup is performed. No declaring class check is performed.
     * <p>
     * Caller is responsible for checking that the method declaration class matches the receiver object (aka "this").
     *
     * @param member         the method or constructor to invoke, method may be static or non-static method
     * @param declaringClass the "effective" declaring class of the method
     * @param obj            the object to invoke the method on, may be null if the method is static
     * @param args           the arguments to pass to the method. may be null if no arguments are passed
     * @return the return value of the method
     * @throws InvocationTargetException if the method threw an exception
     */
    public static Object invokeNonVirtualArtMethodNoDeclaringClassCheck(@NotNull Member member, @NotNull Class<?> declaringClass,
                                                                        @Nullable Object obj, @Nullable Object[] args) throws InvocationTargetException {
        return invokeNonVirtualArtMethodNoDeclaringClassCheck(member, declaringClass, obj, args, false);
    }

    public static String getMethodTypeSignature(final Method method) {
        final StringBuilder buf = new StringBuilder();
        buf.append("(");
        final Class<?>[] types = method.getParameterTypes();
        for (Class<?> type : types) {
            buf.append(getTypeSignature(type));
        }
        buf.append(")");
        buf.append(getTypeSignature(method.getReturnType()));
        return buf.toString();
    }

    public static String getConstructorTypeSignature(final Constructor<?> ctor) {
        final StringBuilder buf = new StringBuilder();
        buf.append("(");
        final Class<?>[] types = ctor.getParameterTypes();
        for (Class<?> type : types) {
            buf.append(getTypeSignature(type));
        }
        buf.append(")");
        buf.append("V");
        return buf.toString();
    }

    public static String getTypeSignature(final Class<?> type) {
        if (type.isPrimitive()) {
            if (Integer.TYPE.equals(type)) {
                return "I";
            }
            if (Void.TYPE.equals(type)) {
                return "V";
            }
            if (Boolean.TYPE.equals(type)) {
                return "Z";
            }
            if (Character.TYPE.equals(type)) {
                return "C";
            }
            if (Byte.TYPE.equals(type)) {
                return "B";
            }
            if (Short.TYPE.equals(type)) {
                return "S";
            }
            if (Float.TYPE.equals(type)) {
                return "F";
            }
            if (Long.TYPE.equals(type)) {
                return "J";
            }
            if (Double.TYPE.equals(type)) {
                return "D";
            }
            throw new IllegalStateException("Type: " + type.getName() + " is not a primitive type");
        }
        if (type.isArray()) {
            return "[" + getTypeSignature(type.getComponentType());
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    public static void logError(@NotNull String tag, @NotNull Throwable th) {
        JvmPlant.getLogHandler().log(tag, Level.WARNING, null, th);
    }

    private static Unsafe theUnsafe;

    public static Unsafe getUnsafe() {
        if (theUnsafe == null) {
            try {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                theUnsafe = (Unsafe) field.get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw unsafeThrow(e);
            }
        }
        return theUnsafe;
    }

    public static void makeAccessible(@NotNull AccessibleObject ao) {
        Objects.requireNonNull(ao, "ao == null");
        try {
            ao.setAccessible(true);
            // fast path
            return;
        } catch (RuntimeException ignored) {
            // ignore
        }
        AccessibleHelper.makeSelfJavaBase();
        AccessibleHelper.makeAccessible(ao);
    }

    static class AccessibleHelper {

        private AccessibleHelper() {
        }

        private static boolean sSuccessful = false;

        public static void makeSelfJavaBase() {
            if (sSuccessful) {
                return;
            }
            Unsafe unsafe = getUnsafe();
            try {
                Class.forName("java.lang.Module");
            } catch (ClassNotFoundException e) {
                // Java 8 or lower
                sSuccessful = true;
                return;
            }
            try {
                Method getModule = Class.class.getDeclaredMethod("getModule");
                Object target = getModule.invoke(Object.class);
                if (target != null) {
                    Class<?> selfClass = AccessibleHelper.class;
                    Field module = Class.class.getDeclaredField("module");
                    long offset = unsafe.objectFieldOffset(module);
                    // set the module field of Object class to the module of this class
                    unsafe.putObject(selfClass, offset, target);
                }
                sSuccessful = true;
            } catch (ReflectiveOperationException e) {
                throw unsafeThrow(e);
            }
        }

        public static void makeAccessible(@NotNull AccessibleObject ao) {
            ao.setAccessible(true);
        }

    }

    public static void reachabilityFence(Object ref) {
        // This code is usually replaced by much faster intrinsic implementations.
        // It will be executed for tests run with the access checks interpreter in
        // ART, e.g. with --verify-soft-fail.  Since this is a volatile store, it
        // cannot easily be moved up past prior accesses, even if this method is
        // inlined.
        SinkHolder.sink = ref;
        // Leaving SinkHolder set to ref is unpleasant, since it keeps ref live
        // until the next reachabilityFence call. This causes e.g. 036-finalizer
        // to fail. Clear it again in a way that's unlikely to be optimizable.
        // The fact that finalize_count is volatile makes it hard to move the test up.
        if (SinkHolder.finalize_count == 0) {
            SinkHolder.sink = null;
        }
    }

    private static class SinkHolder {
        static volatile Object sink;
        // Ensure that sink looks live to even a reasonably clever compiler.
        private static volatile int finalize_count = 0;
        private static Object sinkUser = new Object() {
            protected void finalize() {
                if (sink == null && finalize_count > 0) {
                    throw new AssertionError("Can't get here");
                }
                finalize_count++;
            }
        };
    }

    /**
     * Get the class static initializer, aka, the "<clinit>" method, which is a static constructor without parameters.
     * <p>
     * Calling this method shall not trigger the class static initializer.
     *
     * @param clazz the class to get the class static initializer, must not be null
     * @return the class static initializer, or null if the class has no class static initializer
     */
    @Nullable
    public static Constructor<?> getClassStaticInitializer(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz == null");
        // force make JVM prepare the class
        reachabilityFence(clazz.getDeclaredMethods());
        return JvmPlantNativeBridge.nativeGetClassInitializer(clazz);
    }

}
