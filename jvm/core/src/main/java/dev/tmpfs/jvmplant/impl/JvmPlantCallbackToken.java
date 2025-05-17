package dev.tmpfs.jvmplant.impl;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Objects;

public class JvmPlantCallbackToken {

    /*package*/ JvmPlantCallbackToken(@NotNull Member target) {
        mMember = target;
    }

    private Member mMember;

    public Member getTargetMember() {
        return mMember;
    }

    // called from native
    public Object callback(Object[] idThisArgs) throws Throwable {
        Member targetMethod = mMember;
        if (targetMethod == null) {
            throw new AssertionError("targetMethod is null");
        }
        return JvmPlantCallbackDispatcher.handleCallback(this, targetMethod, /*backupMethod,*/ idThisArgs);
    }

}
