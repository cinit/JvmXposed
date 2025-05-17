//
// Created by sulfate on 2024-10-21.
//

#ifndef JVMXPOSED_OPENJDKVM_HOOK_IMPL_H
#define JVMXPOSED_OPENJDKVM_HOOK_IMPL_H

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#include <jni.h>

#include "shared/jvmplant_api.h"

namespace jvmplant {

class OpenJdkVmHookImpl : public JvmPlantInterface {
public:
    ~OpenJdkVmHookImpl() override = default;

    OpenJdkVmHookImpl(const OpenJdkVmHookImpl&) = delete;

    OpenJdkVmHookImpl& operator=(const OpenJdkVmHookImpl&) = delete;

    OpenJdkVmHookImpl(OpenJdkVmHookImpl&&) = delete;

    OpenJdkVmHookImpl& operator=(OpenJdkVmHookImpl&&) = delete;

    std::vector<uint8_t> GetClassBytecode(JNIEnv* env, jclass klass, std::string& errorMsg) override;

    bool RedefineClass(JNIEnv* env, jclass klass, const std::vector<uint8_t>& bytecode, std::string& errorMsg) override;

    void* GetNativeMethodFunction(JNIEnv* env, jobject method) override;

    jobject GetClassInitializer(JNIEnv* env, jclass klass, std::string& errorMsg) override;

    static OpenJdkVmHookImpl* GetOrCreateInstance(JNIEnv* env, std::string& errorMsg);

private:
    OpenJdkVmHookImpl() = default;

    static OpenJdkVmHookImpl* CreateAndSetInstanceInternal(JNIEnv* env, std::string& errorMsg);
};

} // namespace jvmplant

#endif // JVMXPOSED_OPENJDKVM_HOOK_IMPL_H
