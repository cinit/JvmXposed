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

    virtual std::vector<uint8_t> GetClassBytecode(JNIEnv* env, jclass klass, std::string& errorMsg) = 0;

    virtual bool RedefineClassV2(JNIEnv* env, jclass klass, const std::vector<uint8_t>& bytecode, bool skipVerification,
                                 std::string& errorMsg) = 0;

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
    virtual jobject GetClassInitializer(JNIEnv* env, jclass klass, std::string& errorMsg) = 0;
};
} // namespace jvmplant

#endif // JVMXPOSED_JVMPLANT_API_H
