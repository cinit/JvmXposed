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
    // JvmPlant backup is always a method, regardless of the target type being method or constructor
    private Method mBackup;

    /*package*/ void setBackupMember(@NotNull Method backup) {
        Objects.requireNonNull(backup);
        if (mBackup != null) {
            throw new IllegalStateException("Backup member already set");
        }
        mBackup = backup;
    }

    public Method getBackupMember() {
        return mBackup;
    }

    public Member getTargetMember() {
        return mMember;
    }

    // called from native
    public Object callback(Object[] args) throws Throwable {
        Member targetMethod = mMember;
        Method backupMethod = mBackup;
        if (targetMethod == null) {
            throw new AssertionError("targetMethod is null");
        }
        if (backupMethod == null) {
            throw new AssertionError("backupMethod is null");
        }
        return JvmPlantCallbackDispatcher.handleCallback(this, targetMethod, backupMethod, args);
    }

}
