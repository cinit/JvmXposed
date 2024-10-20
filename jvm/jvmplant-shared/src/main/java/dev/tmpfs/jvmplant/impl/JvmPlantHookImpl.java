package dev.tmpfs.jvmplant.impl;

import dev.tmpfs.jvmplant.api.DefaultHookBridge;
import dev.tmpfs.jvmplant.api.IHookBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class JvmPlantHookImpl {

    private static boolean isInitialized = false;

    private JvmPlantHookImpl() {
    }

    public static void initializeJvmPlantHookBridge() {
        if (DefaultHookBridge.getHookBridge() != null) {
            // do nothing
            return;
        }
        // race condition is fine here
        initializeJvmPlantInternal();
        try {
            DefaultHookBridge.setHookBridge(JvmPlantHookBridge.INSTANCE);
        } catch (IllegalStateException e) {
            if (DefaultHookBridge.getHookBridge() == null) {
                throw e;
            }
            // else: fine, race condition with another thread
        }
    }

    private static class JvmPlantHookBridge implements IHookBridge {

        private JvmPlantHookBridge() {
        }

        public static final JvmPlantHookBridge INSTANCE = new JvmPlantHookBridge();

        @Override
        public int getApiLevel() {
            // because we do not support the Xposed standard, so we return 0
            return 0;
        }

        @NotNull
        @Override
        public String getFrameworkName() {
            return "JvmPlant";
        }

        @NotNull
        @Override
        public String getFrameworkVersion() {
            return VersionConfig.VERSION_NAME;
        }

        @Override
        public long getFrameworkVersionCode() {
            return VersionConfig.VERSION_CODE;
        }

        @NotNull
        @Override
        public MemberUnhookHandle hookMethod(@NotNull Member member, @NotNull IMemberHookCallback callback, int priority) {
            return hookMethodImpl(member, callback, priority);
        }

        @Override
        public boolean isDeoptimizationSupported() {
            return true;
        }

        @Override
        public boolean deoptimize(@NotNull Member member) {
            checkMemberValid(member);
            return JvmPlantNativeBridge.nativeDeoptimizeMethod(member);
        }

        @Nullable
        @Override
        public Object invokeOriginalMethod(@NotNull Method method, @Nullable Object thisObject, @NotNull Object[] args)
                throws NullPointerException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            return invokeOriginalMemberImpl(method, thisObject, args);
        }

        @Override
        public <T> void invokeOriginalConstructor(@NotNull Constructor<T> ctor, @NotNull T thisObject, @NotNull Object[] args)
                throws NullPointerException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            invokeOriginalMemberImpl(ctor, thisObject, args);
        }

        @NotNull
        @Override
        public <T> T newInstanceOrigin(@NotNull Constructor<T> constructor, @NotNull Object... args)
                throws InvocationTargetException, IllegalArgumentException, IllegalAccessException, InstantiationException {
            checkMemberValid(constructor);
            T instance = (T) ReflectHelper.allocateInstance(constructor.getDeclaringClass());
            invokeOriginalMemberImpl(constructor, instance, args);
            return instance;
        }

        @Override
        public long getHookCounter() {
            return sHookCounter.get() - 1;
        }
    }

    private static Member sCallbackMethod;
    private static AtomicLong sHookCounter = new AtomicLong(0);

    private static synchronized void initializeJvmPlantInternal() {
        if (isInitialized) {
            return;
        }
        try {
            sCallbackMethod = JvmPlantCallbackToken.class.getDeclaredMethod("callback", Object[].class);
        } catch (NoSuchMethodException e) {
            // should not happen, unless R8 is doing something wired
            throw ReflectHelper.unsafeThrow(e);
        }
        JvmPlantNativeBridge.nativeInitializeJvmPlant();
        isInitialized = true;
    }

    private static void checkMemberValid(@NotNull Member target) {
        // must be method or constructor
        if (!(target instanceof Method) && !(target instanceof Constructor)) {
            throw new IllegalArgumentException("Only method or constructor is supported, got " + target);
        }
        if (target instanceof Method) {
            // non abstract
            if ((target.getModifiers() & Modifier.ABSTRACT) != 0) {
                throw new IllegalArgumentException("method must not be abstract");
            }
        }
    }

    private static HashSet<Member> sExemptMembers = null;
    private static HashSet<Class<?>> sExemptClasses = null;

    private static HashSet<Member> getExemptMembers() {
        if (sExemptMembers == null) {
            synchronized (JvmPlantHookImpl.class) {
                if (sExemptMembers == null) {
                    try {
                        HashSet<Member> h = new HashSet<>(16);
                        h.add(System.class.getDeclaredMethod("load", String.class));
                        h.add(System.class.getDeclaredMethod("loadLibrary", String.class));
                        // add more if you need
                        sExemptMembers = h;
                    } catch (NoSuchMethodException e) {
                        throw ReflectHelper.unsafeThrow(e);
                    }
                }
            }
        }
        return sExemptMembers;
    }

    private static HashSet<Class<?>> getExemptClasses() {
        if (sExemptClasses == null) {
            synchronized (JvmPlantHookImpl.class) {
                if (sExemptClasses == null) {
                    HashSet<Class<?>> h = new HashSet<>(16);
                    // add more if you need
                    h.add(Runtime.class);
                    sExemptClasses = h;
                }
            }
        }
        return sExemptClasses;
    }

    private static void checkHookTarget(@NotNull Member target) {
        checkMemberValid(target);
        Class<?> clazz = target.getDeclaringClass();
        if (clazz.getClassLoader() == Runnable.class.getClassLoader()) {
            if (getExemptClasses().contains(clazz)) {
                return;
            }
            if (getExemptMembers().contains(target)) {
                return;
            }
            if (clazz.getName().startsWith("java.lang.")) {
                throw new IllegalArgumentException("Cannot hook java.lang.* classes");
            } else if (clazz.getName().startsWith("java.util.")) {
                throw new IllegalArgumentException("Cannot hook java.util.* classes");
            }
        }
        if (clazz.getName().startsWith("io.github.qauxv.")) {
            throw new IllegalArgumentException("Cannot hook io.github.qauxv.* classes");
        }
    }

    public static class CallbackWrapper implements IHookBridge.MemberUnhookHandle {

        private CallbackWrapper(@NotNull IHookBridge.IMemberHookCallback callback, @NotNull Member target, int priority) {
            this.callback = callback;
            this.target = target;
            this.priority = priority;
        }

        public final IHookBridge.IMemberHookCallback callback;
        public final Member target;
        public final int priority;
        public final long hookId = sHookCounter.getAndIncrement();
        private boolean active = true;

        @NotNull
        @Override
        public Member getMember() {
            return target;
        }

        @NotNull
        @Override
        public IHookBridge.IMemberHookCallback getCallback() {
            return callback;
        }

        @Override
        public boolean isHookActive() {
            return active;
        }

        @Override
        public void unhook() {
            unhookMethodImpl(this);
            active = false;
        }
    }

    // -------------- implementation dividing line --------------

    private static final CallbackWrapper[] EMPTY_CALLBACKS = new CallbackWrapper[0];

    // WARNING: This will only work for Android 7.0 and above.
    // Since SDK 24, Method.equals() and Method.hashCode() can correctly compare hooked methods.
    // Before SDK 24, equals() uses AbstractMethod which is not safe for hooked methods.
    // If you need to support lower versions, go and read cs.android.com.
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<Member, CallbackListHolder>> sCallbackRegistry = new ConcurrentHashMap<>();
    private static final Object sRegistryWriteLock = new Object();

    public static class CallbackListHolder {

        @NotNull
        public final Object lock = new Object();
        // sorted by priority, descending
        @NotNull
        public CallbackWrapper[] callbacks = EMPTY_CALLBACKS;
        // token for JvmPlant, after method is hooked, this will be set
        @Nullable
        public JvmPlantCallbackToken token = null;

    }

    private static IHookBridge.MemberUnhookHandle hookMethodImpl(@NotNull Member member, @NotNull IHookBridge.IMemberHookCallback callback, int priority) {
        checkHookTarget(member);
        Objects.requireNonNull(callback);
        CallbackWrapper wrapper = new CallbackWrapper(callback, member, priority);
        Class<?> declaringClass = member.getDeclaringClass();
        CallbackListHolder holder;
        synchronized (sRegistryWriteLock) {
            ConcurrentHashMap<Member, CallbackListHolder> map = sCallbackRegistry.get(declaringClass);
            if (map == null) {
                map = new ConcurrentHashMap<>(2);
                sCallbackRegistry.put(declaringClass, map);
            }
            holder = map.get(member);
            if (holder == null) {
                holder = new CallbackListHolder();
                map.put(member, holder);
            }
        }
        synchronized (holder.lock) {
            // step 1. check if the method is already hooked
            if (holder.token == null) {
                // underlying ArtMethod is not hooked, we need to hook it before adding callback
                JvmPlantCallbackToken token = new JvmPlantCallbackToken(member);
                // perform hook
                Method backup = JvmPlantNativeBridge.nativeHookMethod(member, sCallbackMethod, token);
                if (backup == null) {
                    throw new UnsupportedOperationException("JvmPlant failed to hook method: " + member);
                }
                backup.setAccessible(true);
                // hook success, set backup method
                token.setBackupMember(backup);
                // add token to holder
                holder.token = token;
            }
            // step 2. add callback to list, descending order by priority
            int newSize = holder.callbacks.length + 1;
            CallbackWrapper[] newCallbacks = new CallbackWrapper[newSize];
            int i = 0;
            for (; i < holder.callbacks.length; i++) {
                if (holder.callbacks[i].priority > priority) {
                    newCallbacks[i] = holder.callbacks[i];
                } else {
                    break;
                }
            }
            newCallbacks[i] = wrapper;
            for (; i < holder.callbacks.length; i++) {
                newCallbacks[i + 1] = holder.callbacks[i];
            }
            holder.callbacks = newCallbacks;
        }
        return wrapper;
    }

    private static void unhookMethodImpl(@NotNull CallbackWrapper callback) {
        Member target = callback.target;
        Class<?> declaringClass = target.getDeclaringClass();
        CallbackListHolder holder;
        // ConcurrentHashMap is thread-safe, so we don't need to synchronize here
        ConcurrentHashMap<Member, CallbackListHolder> map1 = sCallbackRegistry.get(declaringClass);
        if (map1 == null) {
            return;
        }
        holder = map1.get(target);
        if (holder == null) {
            return;
        }
        synchronized (holder.lock) {
            // remove callback from list
            int newSize = holder.callbacks.length - 1;
            if (newSize == 0) {
                holder.callbacks = EMPTY_CALLBACKS;
            } else {
                CallbackWrapper[] newCallbacks = new CallbackWrapper[newSize];
                int j = 0;
                for (int i = 0; i < holder.callbacks.length; i++) {
                    if (holder.callbacks[i] != callback) {
                        newCallbacks[j++] = holder.callbacks[i];
                    }
                }
                holder.callbacks = newCallbacks;
            }
            // if no more callbacks, unhook the method
            if (holder.callbacks.length == 0) {
                JvmPlantNativeBridge.nativeUnhookMethod(target);
                holder.token = null;
            }
        }
    }

    private static Object invokeOriginalMemberImpl(@NotNull Member method, @Nullable Object thisObject, @NotNull Object[] args)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(args, "args");
        checkMemberValid(method);
        boolean hasThis = (method instanceof Constructor) || !Modifier.isStatic(method.getModifiers());
        if (hasThis && thisObject == null) {
            throw new NullPointerException("thisObject is null for method " + method);
        }
        // CHA lookup
        method = ReflectHelper.virtualMethodLookup(method, thisObject);
        // perform a lookup
        Class<?> declaringClass = method.getDeclaringClass();
        declaringClass.cast(thisObject);
        JvmPlantCallbackToken token = null;
        ConcurrentHashMap<Member, CallbackListHolder> map1 = sCallbackRegistry.get(declaringClass);
        if (map1 != null) {
            CallbackListHolder holder = map1.get(method);
            if (holder != null) {
                synchronized (holder.lock) {
                    token = holder.token;
                }
            }
        }
        if (token != null) {
            Method backup = token.getBackupMember();
            return backup.invoke(thisObject, args);
        } else {
            // method is not hooked, invoke the original method/copnstructor directly
            return ReflectHelper.invokeNonVirtualArtMethodNoDeclaringClassCheck(method, declaringClass, thisObject, args);
        }
    }

    @NotNull
    public static CallbackWrapper[] getActiveHookCallbacks(@NotNull Member method) {
        Objects.requireNonNull(method, "method");
        Class<?> declaringClass = method.getDeclaringClass();
        ConcurrentHashMap<Member, CallbackListHolder> map1 = sCallbackRegistry.get(declaringClass);
        if (map1 == null) {
            return EMPTY_CALLBACKS;
        }
        CallbackListHolder holder = map1.get(method);
        if (holder == null) {
            return EMPTY_CALLBACKS;
        }
        synchronized (holder.lock) {
            // perform a copy
            return holder.callbacks.clone();
        }
    }


}
