//
// Created by sulfate on 2024-10-21.
//

#ifndef JVMXPOSED_OPENJDKVM_HOOK_IMPL_H
#define JVMXPOSED_OPENJDKVM_HOOK_IMPL_H

#include <cstdint>
#include <vector>
#include <string_view>
#include <string>

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

    jobject HookMethod(JNIEnv* env, jobject target_method, jobject hooker_object, jobject callback_method) override;

    bool UnHookMethod(JNIEnv* env, jobject target_method) override;

    bool IsMethodHooked(JNIEnv* env, jobject method) override;

    bool DeoptimizeMethod(JNIEnv* env, jobject method) override;

    void* GetNativeMethodFunction(JNIEnv* env, jobject method) override;

    jobject GetClassInitializer(JNIEnv* env, jclass klass) override;

    static OpenJdkVmHookImpl* GetOrCreateInstance(JNIEnv* env, std::string& errorMsg);

private:
    OpenJdkVmHookImpl() = default;

    static OpenJdkVmHookImpl* CreateAndSetInstanceInternal(JNIEnv* env, std::string& errorMsg);

};

}

#endif //JVMXPOSED_OPENJDKVM_HOOK_IMPL_H
