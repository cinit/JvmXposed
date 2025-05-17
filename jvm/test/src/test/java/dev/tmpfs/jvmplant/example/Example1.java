package dev.tmpfs.jvmplant.example;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import dev.tmpfs.jvmplant.test.TestEnvironments;

import java.util.Random;

public class Example1 {

    public static void main(String[] args) throws ReflectiveOperationException {
        TestEnvironments.ensureInitialized();
        Random random = new Random();
        System.out.println("Random number #1: 0x" + Integer.toHexString(random.nextInt()));
        // hook the random number generator
        XC_MethodHook.Unhook unhook = XposedBridge.hookMethod(Random.class.getMethod("nextInt"),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int value = (int) param.getResult();
                        System.out.println("inside hook: original result = 0x" + Integer.toHexString(value));
                        param.setResult(0x114514);
                    }
                });
        // call the random number generator and print the result
        System.out.println("Random number #2: 0x" + Integer.toHexString(random.nextInt()));
        // hook is persistent until it is unhooked
        System.out.println("Random number #3: 0x" + Integer.toHexString(random.nextInt()));
        // remove the hook
        unhook.unhook();
        // call the random number generator and print the result
        System.out.println("Random number #4: 0x" + Integer.toHexString(random.nextInt()));
    }

}
