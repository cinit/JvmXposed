//
// Created by sulfate on 2024-10-21.
//

#ifndef JVMXPOSED_JVMPLANT_API_H
#define JVMXPOSED_JVMPLANT_API_H

#include <jni.h>

namespace jvmplant {

class JvmPlantInterface {
public:
    JvmPlantInterface() = default;

    virtual ~JvmPlantInterface() = default;

    // no copy, no move
    JvmPlantInterface(const JvmPlantInterface&) = delete;

    JvmPlantInterface& operator=(const JvmPlantInterface&) = delete;

    JvmPlantInterface(JvmPlantInterface&&) = delete;

    JvmPlantInterface& operator=(JvmPlantInterface&&) = delete;

    /**
     * Hook a method.
     * @param env The JNI environment.
     * @param target_method The method to be hooked.
     * @param hooker_object The context object.
     * @param callback_method The hook method.
     * @return The original backup method which can be called with Method.invoke().
     */
    virtual jobject HookMethod(JNIEnv* env, jobject target_method, jobject hooker_object, jobject callback_method) = 0;

    /**
     * Restore a hooked method.
     * @param env The JNI environment.
     * @param target_method The method to be restored.
     * @return True if the method is successfully restored.
     */
    virtual bool UnHookMethod(JNIEnv* env, jobject target_method) = 0;

    /**
     * Check if a method is hooked.
     * @param env The JNI environment.
     * @param method The method to be checked.
     * @return True if the method is hooked.
     */
    virtual bool IsMethodHooked(JNIEnv* env, jobject method) = 0;

    /**
     * Deoptimize a method.
     * @param env The JNI environment.
     * @param method The method to be deoptimized.
     * @return True if the method is successfully deoptimized.
     */
    virtual bool DeoptimizeMethod(JNIEnv* env, jobject method) = 0;

    /**
     * Get the native function pointer of a native method. This only works for native methods.
     * @param env The JNI environment.
     * @param method The method to get the native function pointer.
     * @return The native function pointer.
     */
    virtual void* GetNativeMethodFunction(JNIEnv* env, jobject method) = 0;

    /**
     * Get the class initializer, the "<clinit>()V", of a class WITHOUT initializing it.
     * The returned Constructor object can be used to hook the class initializer.
     * @param env The JNI environment.
     * @param klass The class to get the class initializer
     * @return The class initializer, or null if the class has no initializer.
     */
    virtual jobject GetClassInitializer(JNIEnv* env, jclass klass) = 0;

};

}

#endif //JVMXPOSED_JVMPLANT_API_H
