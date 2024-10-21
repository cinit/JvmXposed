package dev.tmpfs.jvmplant.test;


import de.robv.android.xposed.XC_MethodHook;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HookTests {

    public static class DirectMethod {

        private int add(int a, int b) {
            return a + b;
        }

    }

    public static class VirtualMethod {

        public int add(int a, int b) {
            return a + b;
        }

    }

    public static class StaticMethod {

        public static int add(int a, int b) {
            return a + b;
        }

    }

    public static class ConstructorI {
        private int value;

        public ConstructorI(int v) {
            this.value = v;
        }

    }

    @Test
    public void directMethod() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        // a = 1, b = 2
        // hooked result = -3
        Method method = DirectMethod.class.getDeclaredMethod("add", int.class, int.class);
        XC_MethodHook.Unhook unhook = null;
        try {
            unhook = HookUtils.hookBefore(method, param -> {
                int a = (int) param.args[0];
                int b = (int) param.args[1];
                param.setResult(-a - b);
            });
            assertEquals(-3, new DirectMethod().add(1, 2));
        } finally {
            if (unhook != null) {
                unhook.unhook();
            }
        }
    }

    @Test
    public void virtualMethod() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        // a = 1, b = 2
        // hooked result = -3
        Method method = VirtualMethod.class.getDeclaredMethod("add", int.class, int.class);
        XC_MethodHook.Unhook unhook = null;
        try {
            unhook = HookUtils.hookBefore(method, param -> {
                int a = (int) param.args[0];
                int b = (int) param.args[1];
                param.setResult(-a - b);
            });
            assertEquals(-3, new VirtualMethod().add(1, 2));
        } finally {
            if (unhook != null) {
                unhook.unhook();
            }
        }
    }

    @Test
    public void staticMethod() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        // a = 1, b = 2
        // hooked result = -3
        Method method = StaticMethod.class.getDeclaredMethod("add", int.class, int.class);
        XC_MethodHook.Unhook unhook = null;
        try {
            unhook = HookUtils.hookBefore(method, param -> {
                int a = (int) param.args[0];
                int b = (int) param.args[1];
                param.setResult(-a - b);
            });
            assertEquals(-3, StaticMethod.add(1, 2));
        } finally {
            if (unhook != null) {
                unhook.unhook();
            }
        }
    }

    @Test
    public void constructor() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        // v = 1
        // hooked result = -1
        Constructor<ConstructorI> method = ConstructorI.class.getDeclaredConstructor(int.class);
        XC_MethodHook.Unhook unhook = null;
        try {
            unhook = HookUtils.hookBefore(method, param -> {
                int v = (int) param.args[0];
                param.setResult(-v);
            });
            assertEquals(-1, new ConstructorI(1).value);
        } finally {
            if (unhook != null) {
                unhook.unhook();
            }
        }
    }


}
