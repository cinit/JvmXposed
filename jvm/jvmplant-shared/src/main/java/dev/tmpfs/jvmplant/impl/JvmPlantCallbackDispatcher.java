package dev.tmpfs.jvmplant.impl;

import dev.tmpfs.jvmplant.api.IHookBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class JvmPlantCallbackDispatcher {

    private JvmPlantCallbackDispatcher() {
        throw new AssertionError("No instance for you!");
    }

    public static class JvmPlantHookParam implements IHookBridge.IMemberHookParam {

        private Member member;
        private Object[] args;
        private Object thisObject;
        // create on demand
        private Object[] extra = null;
        private int currentIndex = -1;
        private int callbackCount = -1;

        private Object result = null;
        private Throwable throwable = null;
        private boolean skipOrigin = false;

        public JvmPlantHookParam(@NotNull Member member, @NotNull Object[] args, @Nullable Object thisObject) {
            this.member = member;
            this.args = args;
            this.thisObject = thisObject;
        }

        @NotNull
        @Override
        public Member getMember() {
            checkState();
            return member;
        }

        @Nullable
        @Override
        public Object getThisObject() {
            checkState();
            return thisObject;
        }

        @NotNull
        @Override
        public Object[] getArgs() {
            checkState();
            return args;
        }

        @Nullable
        @Override
        public Object getResult() {
            checkState();
            return result;
        }

        @Override
        public void setResult(@Nullable Object result) {
            checkState();
            this.result = result;
            this.throwable = null;
            this.skipOrigin = true;
        }

        @Nullable
        @Override
        public Throwable getThrowable() {
            checkState();
            return throwable;
        }

        @Override
        public void setThrowable(@NotNull Throwable throwable) {
            checkState();
            this.throwable = throwable;
            this.result = null;
            this.skipOrigin = true;
        }

        @Nullable
        @Override
        public Object getExtra() {
            checkState();
            if (extra == null) {
                return null;
            }
            return extra[currentIndex];
        }

        @Override
        public void setExtra(@Nullable Object extra) {
            checkState();
            // create on demand
            if (this.extra == null) {
                this.extra = new Object[callbackCount];
            }
            this.extra[currentIndex] = extra;
        }

        private void checkState() {
            if (member == null) {
                throw new IllegalStateException("attempt to use a param that has been destroyed");
            }
        }
    }

    static Object handleCallback(
            @NotNull JvmPlantCallbackToken token,
            @NotNull Member targetMethod,
            @NotNull Method backupMethod,
            @NotNull Object[] rawArgs) throws Throwable {
        JvmPlantHookImpl.CallbackWrapper[] callbacks = JvmPlantHookImpl.getActiveHookCallbacks(targetMethod);
        boolean hasThis = (targetMethod instanceof Constructor) || ((targetMethod.getModifiers() & Modifier.STATIC) == 0);
        Object thisObject = hasThis ? rawArgs[0] : null;
        Object[] args;
        if (hasThis) {
            args = new Object[rawArgs.length - 1];
            System.arraycopy(rawArgs, 1, args, 0, args.length);
        } else {
            args = rawArgs;
        }
        JvmPlantHookParam param = new JvmPlantHookParam(targetMethod, args, thisObject);
        // call before callbacks
        for (int i = 0; i < callbacks.length; i++) {
            JvmPlantHookImpl.CallbackWrapper callback = callbacks[i];
            param.currentIndex = i;
            param.callbackCount = callbacks.length;
            try {
                callback.callback.beforeHookedMember(param);
            } catch (Throwable t) {
                ReflectHelper.logError(t);
            }
        }
        param.currentIndex = -1;
        if (!param.skipOrigin) {
            try {
                param.result = backupMethod.invoke(thisObject, args);
            } catch (Throwable t) {
                param.throwable = ReflectHelper.getIteCauseOrSelf(t);
            }
        }
        // call after callbacks in reverse order
        for (int i = callbacks.length - 1; i >= 0; i--) {
            JvmPlantHookImpl.CallbackWrapper callback = callbacks[i];
            param.currentIndex = i;
            param.callbackCount = callbacks.length;
            try {
                callback.callback.afterHookedMember(param);
            } catch (Throwable t) {
                ReflectHelper.logError(t);
            }
        }
        Object result = param.getResult();
        Throwable throwable = param.getThrowable();
        // cleanup param
        param.currentIndex = -1;
        param.member = null;
        param.args = null;
        param.thisObject = null;
        param.extra = null;
        param.result = null;
        param.throwable = null;
        param = null;
        // perform throw
        if (throwable != null) {
            throw throwable;
        }
        // check return type before return
        if (targetMethod instanceof Constructor) {
            return null;
        }
        Class<?> returnType = ((Method) targetMethod).getReturnType();
        if (returnType == void.class) {
            return null;
        }
        if (!returnType.isPrimitive()) {
            // check cast
            return returnType.cast(result);
        }
        // for primitive type, we need to check the type
        if (result == null) {
            throw new ClassCastException("null cannot be cast to primitive type " + returnType.getName());
        }
        return castPrimitiveType(returnType, result);
    }

    private static Object castPrimitiveType(@NotNull Class<?> returnType, @NotNull Object result) {
        if (returnType == boolean.class) {
            return (Boolean) result;
        } else if (returnType == byte.class) {
            return (Byte) result;
        } else if (returnType == char.class) {
            return (Character) result;
        } else if (returnType == short.class) {
            return (Short) result;
        } else if (returnType == int.class) {
            return (Integer) result;
        } else if (returnType == long.class) {
            return (Long) result;
        } else if (returnType == float.class) {
            return (Float) result;
        } else if (returnType == double.class) {
            return (Double) result;
        } else {
            throw new AssertionError("unknown primitive type " + returnType.getName());
        }
    }

}
