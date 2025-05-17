package dev.tmpfs.jvmplant.test;


import de.robv.android.xposed.XC_MethodHook;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

public class HookTests {

    public static class DirectMethod {

        private int add(int a, int b) {
            return a + b;
        }

        public static class Test2Exception extends RuntimeException {
            public Test2Exception(String message) {
                super(message);
            }
        }

        private int addFail(int a, int b) {
            throw new Test2Exception("Test 2 Exception");
        }

        private int sub(int a, int b) {
            return a - b;
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

        public static String primitiveArgsPassingTest(int a, long b, float c, double d, boolean e, char f, byte g, short h) {
            return String.format("%d %d %.06f %.06f %b %c %d %d", a, b, c, d, e, f, g, h);
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
        assertEquals(3, new DirectMethod().add(1, 2));
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
        assertEquals(3, new DirectMethod().add(1, 2));
    }

    @Test
    public void virtualMethod() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        // a = 1, b = 2
        // hooked result = -3
        Method method = VirtualMethod.class.getDeclaredMethod("add", int.class, int.class);
        assertEquals(3, new VirtualMethod().add(1, 2));
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
        assertEquals(3, new VirtualMethod().add(1, 2));
    }

    @Test
    public void staticMethod() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        // a = 1, b = 2
        // hooked result = -3
        Method method = StaticMethod.class.getDeclaredMethod("add", int.class, int.class);
        assertEquals(3, StaticMethod.add(1, 2));
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
        assertEquals(3, StaticMethod.add(1, 2));
    }

    @Test
    public void constructor() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        // v = 1
        // hooked result = -1
        Constructor<ConstructorI> method = ConstructorI.class.getDeclaredConstructor(int.class);
        assertEquals(2, new ConstructorI(2).value);
        XC_MethodHook.Unhook unhook = null;
        try {
            unhook = HookUtils.hookBefore(method, param -> {
                int v = (int) param.args[0];
                param.args[0] = -v;
            });
            assertEquals(-1, new ConstructorI(1).value);
        } finally {
            if (unhook != null) {
                unhook.unhook();
            }
        }
        assertEquals(3, new ConstructorI(3).value);
    }

    public static class TestException extends RuntimeException {
        public TestException(String message) {
            super(message);
        }
    }

    @Test
    public void exceptionHandling1() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        Method method = DirectMethod.class.getDeclaredMethod("add", int.class, int.class);
        assertEquals(3, new DirectMethod().add(1, 2));
        XC_MethodHook.Unhook unhook = null;
        try {
            unhook = HookUtils.hookBefore(method, param -> {
                param.setThrowable(new TestException("Test Exception"));
            });
            assertThrows(TestException.class, () -> new DirectMethod().add(1, 2));
        } finally {
            if (unhook != null) {
                unhook.unhook();
            }
        }
        assertEquals(3, new DirectMethod().add(1, 2));
    }


    @Test
    public void exceptionHandling2() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        Method method = DirectMethod.class.getDeclaredMethod("addFail", int.class, int.class);
        assertThrows(DirectMethod.Test2Exception.class, () -> new DirectMethod().addFail(1, 2));
        XC_MethodHook.Unhook unhook = null;
        try {
            unhook = HookUtils.hookBefore(method, param -> {
                // no exception
                param.setResult(233);
            });
            assertEquals(233, new DirectMethod().addFail(1, 2));
        } finally {
            if (unhook != null) {
                unhook.unhook();
            }
        }
        assertThrows(DirectMethod.Test2Exception.class, () -> new DirectMethod().addFail(1, 2));
    }

    @Test
    public void edgeCases() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        Method method = DirectMethod.class.getDeclaredMethod("add", int.class, int.class);
        assertEquals(0, new DirectMethod().add(0, 0));
        XC_MethodHook.Unhook unhook = null;
        try {
            unhook = HookUtils.hookBefore(method, param -> {
                int a = (int) param.args[0];
                int b = (int) param.args[1];
                param.setResult(-a - b);
            });
            assertEquals(0, new DirectMethod().add(0, 0));
        } finally {
            if (unhook != null) {
                unhook.unhook();
            }
        }
        assertEquals(0, new DirectMethod().add(0, 0));
    }

    @Test
    public void multipleHooksOnSameMethod() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        Method method = DirectMethod.class.getDeclaredMethod("add", int.class, int.class);
        assertEquals(3, new DirectMethod().add(1, 2));
        XC_MethodHook.Unhook unhook1 = null;
        XC_MethodHook.Unhook unhook2 = null;
        try {
            unhook1 = HookUtils.hookBefore(method, 53, param -> {
                int a = (int) param.args[0];
                int b = (int) param.args[1];
                param.setResult(a + b + 1);
            });
            unhook2 = HookUtils.hookBefore(method, 51, param -> {
                int result = (int) param.getResult();
                param.setResult(result * 2);
            });
            assertEquals(8, new DirectMethod().add(1, 2));
        } finally {
            if (unhook1 != null) {
                unhook1.unhook();
            }
            if (unhook2 != null) {
                unhook2.unhook();
            }
        }
        assertEquals(3, new DirectMethod().add(1, 2));
    }

    @Test
    public void hookAfter() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        Method method = DirectMethod.class.getDeclaredMethod("add", int.class, int.class);
        assertEquals(3, new DirectMethod().add(1, 2));
        XC_MethodHook.Unhook unhook = null;
        try {
            unhook = HookUtils.hookAfter(method, param -> {
                int result = (int) param.getResult();
                param.setResult(result * 2);
            });
            assertEquals(6, new DirectMethod().add(1, 2));
        } finally {
            if (unhook != null) {
                unhook.unhook();
            }
        }
        assertEquals(3, new DirectMethod().add(1, 2));
    }


    @Test
    public void multipleMethodsInSameClass() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        Method method1 = DirectMethod.class.getDeclaredMethod("add", int.class, int.class);
        Method method2 = DirectMethod.class.getDeclaredMethod("sub", int.class, int.class);
        assertEquals(3, new DirectMethod().add(1, 2));
        assertEquals(-1, new DirectMethod().sub(1, 2));
        XC_MethodHook.Unhook unhook1 = null;
        XC_MethodHook.Unhook unhook2 = null;
        try {
            unhook1 = HookUtils.hookBefore(method1, param -> {
                int a = (int) param.args[0];
                int b = (int) param.args[1];
                param.setResult(a + b + 1);
            });
            unhook2 = HookUtils.hookBefore(method2, param -> {
                int a = (int) param.args[0];
                int b = (int) param.args[1];
                param.setResult(a - b - 1);
            });
            assertEquals(4, new DirectMethod().add(1, 2));
            assertEquals(-2, new DirectMethod().sub(1, 2));
        } finally {
            if (unhook1 != null) {
                unhook1.unhook();
            }
            if (unhook2 != null) {
                unhook2.unhook();
            }
        }
        assertEquals(3, new DirectMethod().add(1, 2));
        assertEquals(-1, new DirectMethod().sub(1, 2));
    }

    @Test
    public void primitiveArgsPassingTest() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        Method method = StaticMethod.class.getDeclaredMethod("primitiveArgsPassingTest", int.class, long.class, float.class, double.class, boolean.class, char.class, byte.class, short.class);
        assertEquals("1 2 3.000000 4.000000 true a 5 6", StaticMethod.primitiveArgsPassingTest(1, 2L, 3f, 4d, true, 'a', (byte) 5, (short) 6));
        XC_MethodHook.Unhook unhook = null;
        try {
            unhook = HookUtils.hookBefore(method, param -> {
                int a = (int) param.args[0];
                long b = (long) param.args[1];
                float c = (float) param.args[2];
                double d = (double) param.args[3];
                boolean e = (boolean) param.args[4];
                char f = (char) param.args[5];
                byte g = (byte) param.args[6];
                short h = (short) param.args[7];
                // modify the arguments
                param.args[0] = (int) (a + 1);
                param.args[1] = (long) (b + 1);
                param.args[2] = (float) (c + 1);
                param.args[3] = (double) (d + 1);
                param.args[4] = (boolean) !e;
                param.args[5] = (char) (f + 1);
                param.args[6] = (byte) (g + 1);
                param.args[7] = (short) (h + 1);
            });
            assertEquals("2 3 4.000000 5.000000 false b 6 7", StaticMethod.primitiveArgsPassingTest(1, 2L, 3f, 4d, true, 'a', (byte) 5, (short) 6));
        } finally {
            if (unhook != null) {
                unhook.unhook();
            }
        }
        assertEquals("1 2 3.000000 4.000000 true a 5 6", StaticMethod.primitiveArgsPassingTest(1, 2L, 3f, 4d, true, 'a', (byte) 5, (short) 6));
    }

    public static class ReturnTypeTests {

        public static int returnInteger(int a) {
            return a;
        }

        public static long returnLong(long a) {
            return a;
        }

        public static float returnFloat(float a) {
            return a;
        }

        public static double returnDouble(double a) {
            return a;
        }

        public static boolean returnBoolean(boolean a) {
            return a;
        }

        public static char returnChar(char a) {
            return a;
        }

        public static byte returnByte(byte a) {
            return a;
        }

        public static short returnShort(short a) {
            return a;
        }

        public static void returnVoid() {
        }

    }

    @Test
    public void returnTypeTests() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        Class<?>[] primitiveNumberTypes = {int.class, long.class, float.class, double.class, byte.class, short.class};
        Class<?>[] wrapperNumberTypes = {Integer.class, Long.class, Float.class, Double.class, Byte.class, Short.class};
        for (int i = 0; i < primitiveNumberTypes.length; i++) {
            Class<?> primitiveType = primitiveNumberTypes[i];
            Class<?> wrapperType = wrapperNumberTypes[i];
            Method method = ReturnTypeTests.class.getDeclaredMethod("return" + wrapperType.getSimpleName(), primitiveType);
            // check by value and type
            Method xxxValueMethod = Number.class.getDeclaredMethod(primitiveType.getSimpleName() + "Value");
            // install hook , add value by 1
            XC_MethodHook.Unhook unhook = HookUtils.hookAfter(method, param -> {
                Object a = param.args[0];
                if (a instanceof Number) {
                    Number number = (Number) a;
                    if (number instanceof Integer) {
                        param.setResult((int) (number.intValue() + 1));
                    } else if (number instanceof Long) {
                        param.setResult((long) (number.longValue() + 1));
                    } else if (number instanceof Float) {
                        param.setResult((float) (number.floatValue() + 1));
                    } else if (number instanceof Double) {
                        param.setResult((double) (number.doubleValue() + 1));
                    } else if (number instanceof Byte) {
                        param.setResult((byte) (number.byteValue() + 1));
                    } else if (number instanceof Short) {
                        param.setResult((short) (number.shortValue() + 1));
                    }
                }
            });
            Object param = xxxValueMethod.invoke(1);
            Object returnValue = method.invoke(null, param);
            // check by type
            assertEquals(wrapperType, returnValue.getClass());
            // check by value
            double doubleValue = ((Number) returnValue).doubleValue();
            if (Math.abs(doubleValue - 2) > 0.0001) {
                throw new AssertionError("Expected value: 2, but got: " + doubleValue);
            }
        }
        // check void method
        Method voidMethod = ReturnTypeTests.class.getDeclaredMethod("returnVoid");
        XC_MethodHook.Unhook unhook = HookUtils.hookAfter(voidMethod, param -> {
            // do nothing
        });
        voidMethod.invoke(null);
    }

    private static boolean sForGetClassStaticInitializerFired = false;

    private static class ForGetClassStaticInitializer {

        static {
            sForGetClassStaticInitializerFired = true;
        }

        public static void empty() {
        }

    }

    @Test
    public void testGetClassStaticInitializer() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        Method getClassStaticInitializer = Class.forName("dev.tmpfs.jvmplant.impl.ReflectHelper").getDeclaredMethod("getClassStaticInitializer", Class.class);
        getClassStaticInitializer.setAccessible(true);
        Constructor<?> clinit = (Constructor<?>) getClassStaticInitializer.invoke(null, ForGetClassStaticInitializer.class);
        assertEquals(ForGetClassStaticInitializer.class, clinit.getDeclaringClass());
        assertTrue(Modifier.isStatic(clinit.getModifiers()));
        assertFalse(sForGetClassStaticInitializerFired);
        // array and primitive types should not have static initializers
        assertNull(getClassStaticInitializer.invoke(null, int[].class));
        assertNull(getClassStaticInitializer.invoke(null, long.class));
    }

    private static int sForHookClassStaticInitializerOriginalFired = 0;
    private static int sForHookClassStaticInitializerHookedInvoked = 0;

    private static class ForHookClassStaticInitializer {

        static {
            sForHookClassStaticInitializerOriginalFired++;
        }

        public static void empty() {
        }

    }

    @Test
    public void testHookClassStaticInitializer() throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        Method getClassStaticInitializer = Class.forName("dev.tmpfs.jvmplant.impl.ReflectHelper").getDeclaredMethod("getClassStaticInitializer", Class.class);
        getClassStaticInitializer.setAccessible(true);
        Constructor<?> clinit = (Constructor<?>) getClassStaticInitializer.invoke(null, ForHookClassStaticInitializer.class);
        HookUtils.hookBefore(clinit, param -> {
            sForHookClassStaticInitializerHookedInvoked++;
            // prevent the original static initializer from running
            param.setResult(null);
        });
        // call the static initializer
        ForHookClassStaticInitializer.empty();
        ForHookClassStaticInitializer.empty();
        // the original static initializer should not be fired
        assertEquals(0, sForHookClassStaticInitializerOriginalFired);
        // the hook should be fired
        assertEquals(1, sForHookClassStaticInitializerHookedInvoked);
    }

}
