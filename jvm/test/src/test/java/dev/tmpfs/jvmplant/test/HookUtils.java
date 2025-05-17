package dev.tmpfs.jvmplant.test;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Member;

public class HookUtils {

    private HookUtils() {
        throw new AssertionError("No instances for you!");
    }

    public interface BeforeHookWrapper {
        void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable;
    }

    public interface AfterHookWrapper {
        void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable;
    }

    @NotNull
    public static XC_MethodHook.Unhook hookBefore(Member m, BeforeHookWrapper wrapper) {
        return XposedBridge.hookMethod(m, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                wrapper.beforeHookedMethod(param);
            }
        });
    }

    @NotNull
    public static XC_MethodHook.Unhook hookAfter(Member m, AfterHookWrapper wrapper) {
        return XposedBridge.hookMethod(m, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                wrapper.afterHookedMethod(param);
            }
        });
    }

    // with priority

    @NotNull
    public static XC_MethodHook.Unhook hookBefore(Member m, int priority, BeforeHookWrapper wrapper) {
        return XposedBridge.hookMethod(m, new XC_MethodHook(priority) {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                wrapper.beforeHookedMethod(param);
            }
        });
    }

    @NotNull
    public static XC_MethodHook.Unhook hookAfter(Member m, int priority, AfterHookWrapper wrapper) {
        return XposedBridge.hookMethod(m, new XC_MethodHook(priority) {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                wrapper.afterHookedMethod(param);
            }
        });
    }

}
