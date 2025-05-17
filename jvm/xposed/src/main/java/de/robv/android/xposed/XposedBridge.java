package de.robv.android.xposed;

import dev.tmpfs.jvmplant.api.JvmPlant;
import dev.tmpfs.jvmplant.api.DefaultHookBridge;
import dev.tmpfs.jvmplant.api.IHookBridge;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * This class contains most of Xposed's central logic, such as initialization and callbacks used by
 * the native side. It also includes methods to add new hooks.
 */
@SuppressWarnings("JniMissingFunction")
public final class XposedBridge {
    /**
     * The system class loader which can be used to locate Android framework classes.
     * Application classes cannot be retrieved from it.
     *
     * @see ClassLoader#getSystemClassLoader
     */
    public static final ClassLoader BOOTCLASSLOADER = ClassLoader.getSystemClassLoader();

    /**
     * @hide
     */
    public static final String TAG = "Xposed";

    /**
     * @deprecated Use {@link #getXposedVersion()} instead.
     */
    @Deprecated
    public static int XPOSED_BRIDGE_VERSION = getXposedVersion();

    private static final Object[] EMPTY_ARRAY = new Object[0];

    // built-in handlers
    // JVM Changed: hook callback dispatch is not handled by XposedBridge
    private static final Map<Member, HashMap<XC_MethodHook, XC_MethodHook.Unhook>> sHookedMethodCallbacks = new ConcurrentHashMap<>();
    // JVM Changed: no load package callbacks
    // static final CopyOnWriteSortedSet<XC_LoadPackage> sLoadedPackageCallbacks = new CopyOnWriteSortedSet<>();
    // static final CopyOnWriteSortedSet<XC_InitPackageResources> sInitPackageResourcesCallbacks = new CopyOnWriteSortedSet<>();

    private XposedBridge() {
    }

    /**
     * Returns the currently installed version of the Xposed framework.
     */
    public static int getXposedVersion() {
        IHookBridge hookBridge = DefaultHookBridge.getHookBridge();
        return hookBridge != null ? hookBridge.getApiLevel() : 0;
    }

    /**
     * Writes a message to the Xposed error log.
     *
     * <p class="warning"><b>DON'T FLOOD THE LOG!!!</b> This is only meant for error logging.
     * If you want to write information/debug messages, use logcat.
     *
     * @param text The log message.
     */
    public synchronized static void log(String text) {
        JvmPlant.getLogHandler().log(TAG, java.util.logging.Level.INFO, text, null);
    }

    /**
     * Logs a stack trace to the Xposed error log.
     *
     * <p class="warning"><b>DON'T FLOOD THE LOG!!!</b> This is only meant for error logging.
     * If you want to write information/debug messages, use logcat.
     *
     * @param t The Throwable object for the stack trace.
     */
    public synchronized static void log(Throwable t) {
        JvmPlant.getLogHandler().log(TAG, Level.WARNING, null, t);
    }

    /**
     * Hook any method (or constructor) with the specified callback. See below for some wrappers
     * that make it easier to find a method/constructor in one step.
     *
     * @param hookMethod The method to be hooked.
     * @param callback   The callback to be executed when the hooked method is called.
     * @return An object that can be used to remove the hook.
     * @see XposedHelpers#findAndHookMethod(String, ClassLoader, String, Object...)
     * @see XposedHelpers#findAndHookMethod(Class, String, Object...)
     * @see #hookAllMethods
     * @see XposedHelpers#findAndHookConstructor(String, ClassLoader, Object...)
     * @see XposedHelpers#findAndHookConstructor(Class, Object...)
     * @see #hookAllConstructors
     */
    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        if (!(hookMethod instanceof Method) && !(hookMethod instanceof Constructor<?>)) {
            throw new IllegalArgumentException("Only methods and constructors can be hooked: " + hookMethod.toString());
        } else if (Modifier.isAbstract(hookMethod.getModifiers())) {
            throw new IllegalArgumentException("Cannot hook abstract methods: " + hookMethod);
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        int priority = callback.priority;
        IHookBridge hookBridge = DefaultHookBridge.requireHookBridge();
        HashMap<XC_MethodHook, XC_MethodHook.Unhook> callbacks = sHookedMethodCallbacks.computeIfAbsent(hookMethod, k -> new HashMap<>());
        IHookBridge.IMemberHookCallback wrappedCallback = new WrappedCallbacks.WrappedHookCallback(callback);
        synchronized (callbacks) {
            // check if the method is already hooked by the same callback
            if (callbacks.containsKey(callback)) {
                throw new IllegalStateException("Method is already hooked with the same callback");
            }
            // do the actual hooking
            IHookBridge.MemberUnhookHandle unhookHandle = hookBridge.hookMethod(hookMethod, wrappedCallback, priority);
            XC_MethodHook.Unhook unhook = new XC_MethodHook.Unhook(unhookHandle, callback);
            // we have already checked that the callback is not already in the map
            callbacks.put(callback, unhook);
            return unhook;
        }
    }

    static void removeUnhookForCallback(Member hookMethod, XC_MethodHook callback) {
        HashMap<XC_MethodHook, XC_MethodHook.Unhook> callbacks = sHookedMethodCallbacks.get(hookMethod);
        if (callbacks != null) {
            synchronized (callbacks) {
                callbacks.remove(callback);
                // don't remove the map entry if it's empty, we don't want to lock the map
            }
        }
    }

    /**
     * Removes the callback for a hooked method/constructor.
     *
     * @param hookMethod The method for which the callback should be removed.
     * @param callback   The reference to the callback as specified in {@link #hookMethod}.
     * @deprecated Use {@link XC_MethodHook.Unhook#unhook} instead. An instance of the {@code Unhook}
     * class is returned when you hook the method.
     */
    @Deprecated
    public static void unhookMethod(Member hookMethod, XC_MethodHook callback) {
        // find the Unhook instance for the callback
        XC_MethodHook.Unhook unhook = null;
        HashMap<XC_MethodHook, XC_MethodHook.Unhook> callbacks = sHookedMethodCallbacks.get(hookMethod);
        if (callbacks != null) {
            synchronized (callbacks) {
                unhook = callbacks.remove(callback);
                // don't remove the map entry if it's empty, we don't want to lock the map
            }
        }
        if (unhook != null) {
            unhook.unhook();
        }
    }

    /**
     * Hooks all methods with a certain name that were declared in the specified class. Inherited
     * methods and constructors are not considered. For constructors, use
     * {@link #hookAllConstructors} instead.
     *
     * @param hookClass  The class to check for declared methods.
     * @param methodName The name of the method(s) to hook.
     * @param callback   The callback to be executed when the hooked methods are called.
     * @return A set containing one object for each found method which can be used to unhook it.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        for (Member method : hookClass.getDeclaredMethods())
            if (method.getName().equals(methodName))
                unhooks.add(hookMethod(method, callback));
        return unhooks;
    }

    /**
     * Hook all constructors of the specified class.
     *
     * @param hookClass The class to check for constructors.
     * @param callback  The callback to be executed when the hooked constructors are called.
     * @return A set containing one object for each found constructor which can be used to unhook it.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        for (Member constructor : hookClass.getDeclaredConstructors())
            unhooks.add(hookMethod(constructor, callback));
        return unhooks;
    }

	/*
	 * Adds a callback to be executed when an app ("Android package") is loaded.
	 *
	 * <p class="note">You probably don't need to call this. Simply implement {@link IXposedHookLoadPackage}
	 * in your module class and Xposed will take care of registering it as a callback.
	 *
	 * @param callback The callback to be executed.
	 * @hide
	 // JVM Changed: no load package callbacks
	public static void hookLoadPackage(XC_LoadPackage callback) {
		synchronized (sLoadedPackageCallbacks) {
			sLoadedPackageCallbacks.add(callback);
		}
	}
	* */

	/*
	 * Adds a callback to be executed when the resources for an app are initialized.
	 *
	 * <p class="note">You probably don't need to call this. Simply implement {@link IXposedHookInitPackageResources}
	 * in your module class and Xposed will take care of registering it as a callback.
	 *
	 * @param callback The callback to be executed.
	 * @hide
	 // JVM Changed: no load package callbacks
	public static void hookInitPackageResources(XC_InitPackageResources callback) {
		synchronized (sInitPackageResourcesCallbacks) {
			sInitPackageResourcesCallbacks.add(callback);
		}
	}
	*/

    /**
     * Basically the same as {@link Method#invoke}, but calls the original method
     * as it was before the interception by Xposed. Also, access permissions are not checked.
     *
     * <p class="caution">There are very few cases where this method is needed. A common mistake is
     * to replace a method and then invoke the original one based on dynamic conditions. This
     * creates overhead and skips further hooks by other modules. Instead, just hook (don't replace)
     * the method and call {@code param.setResult(null)} in {@link XC_MethodHook#beforeHookedMethod}
     * if the original method should be skipped.
     *
     * @param method     The method to be called.
     * @param thisObject For non-static calls, the "this" pointer, otherwise {@code null}.
     * @param args       Arguments for the method call as Object[] array.
     * @return The result returned from the invoked method.
     * @throws NullPointerException      if {@code receiver == null} for a non-static method
     * @throws IllegalAccessException    if this method is not accessible (see {@link AccessibleObject})
     * @throws IllegalArgumentException  if the number of arguments doesn't match the number of parameters, the receiver
     *                                   is incompatible with the declaring class, or an argument could not be unboxed
     *                                   or converted by a widening conversion to the corresponding parameter type
     * @throws InvocationTargetException if an exception was thrown by the invoked method
     */
    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args)
            throws NullPointerException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (args == null) {
            args = EMPTY_ARRAY;
        }

        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        IHookBridge hookBridge = DefaultHookBridge.requireHookBridge();
        if (method instanceof Method) {
            if (!Modifier.isStatic(method.getModifiers()) && thisObject == null) {
                throw new IllegalArgumentException("receiver == null for a non-static method");
            }
            return hookBridge.invokeOriginalMethod((Method) method, thisObject, args);
        } else if (method instanceof Constructor) {
            if (thisObject == null) {
                throw new NullPointerException("receiver == null for a constructor");
            }
            hookBridge.invokeOriginalConstructor((Constructor) method, thisObject, args);
            return null;
        } else {
            throw new IllegalArgumentException("method must be a method or constructor");
        }
    }

    /**
     * @hide
     */
    public static final class CopyOnWriteSortedSet<E> {
        private transient volatile Object[] elements = EMPTY_ARRAY;

        @SuppressWarnings("UnusedReturnValue")
        public synchronized boolean add(E e) {
            int index = indexOf(e);
            if (index >= 0)
                return false;

            Object[] newElements = new Object[elements.length + 1];
            System.arraycopy(elements, 0, newElements, 0, elements.length);
            newElements[elements.length] = e;
            Arrays.sort(newElements);
            elements = newElements;
            return true;
        }

        @SuppressWarnings("UnusedReturnValue")
        public synchronized boolean remove(E e) {
            int index = indexOf(e);
            if (index == -1)
                return false;

            Object[] newElements = new Object[elements.length - 1];
            System.arraycopy(elements, 0, newElements, 0, index);
            System.arraycopy(elements, index + 1, newElements, index, elements.length - index - 1);
            elements = newElements;
            return true;
        }

        private int indexOf(Object o) {
            for (int i = 0; i < elements.length; i++) {
                if (o.equals(elements[i]))
                    return i;
            }
            return -1;
        }

        public Object[] getSnapshot() {
            return elements;
        }
    }

    private static class AdditionalHookInfo {
        final CopyOnWriteSortedSet<XC_MethodHook> callbacks;
        final Class<?>[] parameterTypes;
        final Class<?> returnType;

        private AdditionalHookInfo(CopyOnWriteSortedSet<XC_MethodHook> callbacks, Class<?>[] parameterTypes, Class<?> returnType) {
            this.callbacks = callbacks;
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
        }
    }
}
