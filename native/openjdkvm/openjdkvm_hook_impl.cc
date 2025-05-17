#include "openjdkvm_hook_impl.h"

#include <atomic>
#include <cstdint>
#include <mutex>
#include <span>
#include <string>
#include <string_view>

#include <jni.h>
#include <jvmti.h>

#include <dobby.h>
#include <unordered_map>

#include "jvmti_error_strings.h"
#include "utils/jni_utils.h"
#include "utils/log_macro.h"

static constexpr auto LOG_TAG = "OpenJdkVmHookImpl";

namespace jvmplant {

static OpenJdkVmHookImpl* volatile sInstance = nullptr;


namespace openjdkvm {

struct OpenJdkVmHookInfo {
    // all values assigned in InitOpenJdkVmHookInfoLocked
    bool initialized = false;
    jvmtiEnv* ti = nullptr;
    std::mutex dumpClassBytecodeTransformMutex;
    std::mutex dumpClassBytecodeMapMutex;
    std::unordered_map<std::string, std::vector<uint8_t>> classFileBytes;
};

} // namespace openjdkvm

static openjdkvm::OpenJdkVmHookInfo sHookInfo;

void JNICALL OnJvmtiEventClassFileLoadHook(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jclass class_being_redefined,
                                           jobject loader, const char* name, jobject protection_domain,
                                           jint class_data_len, const unsigned char* class_data,
                                           jint* new_class_data_len, unsigned char** new_class_data) {
    // LOGI("OnJvmtiEventClassFileLoadHook: class name: {}", name);
    std::scoped_lock lock(sHookInfo.dumpClassBytecodeMapMutex);

    // add or update class data to map
    if (sHookInfo.classFileBytes.find(name) == sHookInfo.classFileBytes.end()) {
        sHookInfo.classFileBytes[name] = std::vector(class_data, class_data + class_data_len);
    } else {
        sHookInfo.classFileBytes[name].assign(class_data, class_data + class_data_len);
    }
}

static bool InitOpenJdkVmHookInfoLocked(JNIEnv* env, std::string& errorMsg) {
    if (sHookInfo.initialized) {
        return true;
    }
    using namespace openjdkvm;
    // get jvmtiEnv
    JavaVM* vm = nullptr;
    if (env->GetJavaVM(&vm) != JNI_OK) {
        errorMsg = "Failed to get JavaVM";
        return false;
    }
    if (vm->GetEnv(reinterpret_cast<void**>(&sHookInfo.ti), JVMTI_VERSION_1_2) != JNI_OK) {
        errorMsg = "Failed to get jvmtiEnv";
        return false;
    }
    auto ti = sHookInfo.ti;
    // add capabilities
    jvmtiCapabilities capabilities = {0};

    capabilities.can_get_bytecodes = 1;
    capabilities.can_get_constant_pool = 1;
    capabilities.can_redefine_classes = 1;
    capabilities.can_retransform_classes = 1;
    capabilities.can_redefine_any_class = 1;
    capabilities.can_retransform_any_class = 1;

    auto rc = ti->AddCapabilities(&capabilities);
    if (rc != JVMTI_ERROR_NONE) {
        errorMsg = "Failed to add capabilities: " + std::to_string(rc);
        return false;
    }

    // register ClassFileLoadHook event callbacks
    jvmtiEventCallbacks callbacks = {};
    callbacks.ClassFileLoadHook = OnJvmtiEventClassFileLoadHook;
    rc = ti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (rc != JVMTI_ERROR_NONE) {
        errorMsg = "Failed to set event callbacks: " + std::to_string(rc);
        return false;
    }

    sHookInfo.initialized = true;
    return true;
}

OpenJdkVmHookImpl* OpenJdkVmHookImpl::CreateAndSetInstanceInternal(JNIEnv* env, std::string& errorMsg) {
    static std::mutex sInitMutex;
    std::scoped_lock<std::mutex> lock_(sInitMutex);
    if (sInstance != nullptr) {
        return sInstance;
    }
    if (!InitOpenJdkVmHookInfoLocked(env, errorMsg)) {
        return nullptr;
    }
    auto* instance = new OpenJdkVmHookImpl();
    sInstance = instance;
    return instance;
}

OpenJdkVmHookImpl* OpenJdkVmHookImpl::GetOrCreateInstance(JNIEnv* env, std::string& errorMsg) {
    OpenJdkVmHookImpl* instance = sInstance;
    if (instance != nullptr) {
        return instance;
    }
    return CreateAndSetInstanceInternal(env, errorMsg);
}

std::vector<uint8_t> OpenJdkVmHookImpl::GetClassBytecode(JNIEnv* env, jclass klass, std::string& errorMsg) {
    using namespace jvmplant::util;
    if (!sHookInfo.initialized) {
        errorMsg = "OpenJdkVmHookImpl is not initialized";
        return {};
    }
    if (klass == nullptr) {
        errorMsg = "class is null";
        return {};
    }
    auto klassName = GetClassName(env, klass);
    // replace '.' to '/' in class name
    std::ranges::replace(klassName, '.', '/');
    jvmtiEnv* ti = sHookInfo.ti;
    {
        std::scoped_lock lock1(sHookInfo.dumpClassBytecodeTransformMutex);
        // clear old class data, if any
        {
            std::scoped_lock lock2(sHookInfo.dumpClassBytecodeMapMutex);
            sHookInfo.classFileBytes.clear();
        }
        {
            // enable ClassFileLoadHook event
            jvmtiError rc = ti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
            if (rc != JVMTI_ERROR_NONE) {
                errorMsg = "Failed to enable ClassFileLoadHook event: " + std::to_string(rc);
                return {};
            }
        }
        auto fnDisable = [](jvmtiEnv* ti) {
            jvmtiError rc = ti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
            if (rc != JVMTI_ERROR_NONE) {
                LOGE("Failed to disable ClassFileLoadHook event: {}", std::to_string(rc));
            }
        };
        // request transform
        jvmtiError err = ti->RetransformClasses(1, &klass);
        if (err != JVMTI_ERROR_NONE) {
            errorMsg = "Failed to request RetransformClasses: " + std::to_string(err);
            fnDisable(ti);
            return {};
        }
        // wait for transform to complete
        std::vector<uint8_t> bytecode;
        {
            std::scoped_lock lock3(sHookInfo.dumpClassBytecodeMapMutex);
            for (auto& [name, data]: sHookInfo.classFileBytes) {
                if (name == klassName) {
                    bytecode = data;
                    break;
                }
            }
        }
        fnDisable(ti);
        if (bytecode.empty()) {
            errorMsg = "Failed to get class bytecode, no data found";
            return {};
        }
        // remove class data from map
        {
            std::scoped_lock lock4(sHookInfo.dumpClassBytecodeMapMutex);
            sHookInfo.classFileBytes.erase(klassName);
        }
        // return bytecode
        return bytecode;
    }
}

bool OpenJdkVmHookImpl::RedefineClass(JNIEnv* env, jclass klass, const std::vector<uint8_t>& bytecode,
                                      std::string& errorMsg) {
    using namespace jvmplant::util;
    if (!sHookInfo.initialized) {
        errorMsg = "OpenJdkVmHookImpl is not initialized";
        return {};
    }
    if (klass == nullptr) {
        errorMsg = "class is null";
        return {};
    }
    auto klassName = GetClassName(env, klass);
    // replace '.' to '/' in class name
    std::ranges::replace(klassName, '.', '/');
    jvmtiEnv* ti = sHookInfo.ti;
    jvmtiClassDefinition classDefinition = {};
    classDefinition.klass = klass;
    classDefinition.class_byte_count = static_cast<jint>(bytecode.size());
    classDefinition.class_bytes = bytecode.data();
    jvmtiError err = ti->RedefineClasses(1, &classDefinition);
    if (err != JVMTI_ERROR_NONE) {
        errorMsg = "Failed to request RedefineClasses: " + JvmtiErrorToSting(err);
        return false;
    }
    return true;
}

void* OpenJdkVmHookImpl::GetNativeMethodFunction(JNIEnv* env, jobject method) { return nullptr; }

jobject OpenJdkVmHookImpl::GetClassInitializer(JNIEnv* env, jclass klass, std::string& errorMsg) {
    using namespace jvmplant::util;
    if (!sHookInfo.initialized) {
        errorMsg = "OpenJdkVmHookImpl is not initialized";
        return {};
    }
    if (klass == nullptr) {
        errorMsg = "class is null";
        return {};
    }
    // iterate class methods
    auto ti = sHookInfo.ti;
    jint count = 0;
    jmethodID* methods = nullptr;
    auto rc = ti->GetClassMethods(klass, &count, &methods);
    if (rc != JVMTI_ERROR_NONE) {
        errorMsg = "Failed to get class methods: " + JvmtiErrorToSting(rc);
        return nullptr;
    }
    jmethodID clinit = nullptr;
    // find <clinit> method
    for (auto i = 0; i < count; i++) {
        jmethodID method = methods[i];
        char* name = nullptr;
        char* signature = nullptr;
        // we don't need generic
        if (ti->GetMethodName(method, &name, &signature, nullptr) != JVMTI_ERROR_NONE) {
            continue;
        }
        if (name == nullptr || signature == nullptr) {
            continue;
        }
        if (std::string_view("<clinit>") == name && std::string_view("()V") == signature) {
            // found <clinit> method
            clinit = method;
        }
        // free name and signature
        ti->Deallocate(reinterpret_cast<unsigned char*>(name));
        ti->Deallocate(reinterpret_cast<unsigned char*>(signature));
        if (clinit != nullptr) {
            break;
        }
    }
    // free methods
    if (methods != nullptr) {
        ti->Deallocate(reinterpret_cast<unsigned char*>(methods));
    }
    if (clinit == nullptr) {
        // the class has no <clinit> method, just return null
        return nullptr;
    }
    // ToReflectedMethod, it will return a Constructor object, or the exception
    return env->ToReflectedMethod(klass, clinit, JNI_TRUE);
}

} // namespace jvmplant
