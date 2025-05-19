# JvmXposed

JvmXposed is a library for runtime Java method hooking for OpenJDK HotSpot JVM, inspired by the Xposed framework.
It allows you to intercept method calls and modify their behavior at runtime, after the class has been loaded.

## Usage

JvmXposed provides Xposed-like APIs to hook methods. You can use the `XposedBridge.hookMethod` method to hook a specific method/constructor.

If you are not familiar with the Xposed framework, you can visit
[the official documentation for Xposed API](https://api.xposed.info/reference/de/robv/android/xposed/XposedBridge.html)
for more information.

Here's an example [Example1.java](jvm/test/src/test/java/dev/tmpfs/jvmplant/example/Example1.java) of how to use JvmXposed to hook a method:

```java
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import dev.tmpfs.jvmplant.api.JvmPlant;

public static void main(String[] args) {
    // initialize JvmPlant, which is required to use JvmXposed
    JvmPlant.initialize();
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
```

The `JvmPlant.initialize()` method optionally takes a `NativeLibraryLoader` as an argument, which is used to load the native library. If you don't provide a
`NativeLibraryLoader`, JvmXposed will use the default one, which loads the library from the classpath.

JvmXposed imposes no requirements on the JVM startup arguments, but it does uses `System.load` or `System.loadLibrary` to load the native library.
If you do not override the default `NativeLibraryLoader`, please make sure that the native library is in the library path.

## Notices

- JvmXposed uses JVMTI RedefineClasses to redefine classes, which is not supported in all JVMs. It is known to work with OpenJDK HotSpot JVM.
- JvmXposed requires reflection+unsafe+native library to work correctly, while using unsafe or loading a native library is not desirable in some environments.
- JvmXposed is not a Xposed framework, and only provides a subset of the Xposed API.
  It only provides the method hooking API, and does not provide the module loading or other features of the Xposed framework.
- This library is not intended to be used in production environments, and is only for research and educational purposes
  (or for fun). It is not recommended to use this library in production code.
- This library has beed tested on OpenJDK VM 8, 9, 11, 17 and 21. It is not guaranteed to work on other versions of Java, or on other JVMs.

## Compiling

For the Java part, run:

```bash
./gradlew :jvm:core:assemble :jvm:xposed:assemble
```

For the native part, run:

```bash
cd native && mkdir -p build && cd build && cmake .. && make jvmplant -j 4
```

Run the unit tests:

```bash
export JVMPLANT_TEST_NATIVE_DIR=/path/to/JvmXposed/native/build
./gradlew :jvm:test:test --tests "dev.tmpfs.jvmplant.test.HookTests"
```

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
