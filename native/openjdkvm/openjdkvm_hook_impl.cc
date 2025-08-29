#include "openjdkvm_hook_impl.h"

#include <atomic>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <optional>
#include <span>
#include <string>
#include <string_view>

#include <algorithm>
#include <jni.h>
#include <jvmti.h>
#include <ranges>
#include <unordered_map>

#include "platform_compat.h"

// for dlopen, dlsym, dlclose, LoadLibraryW, GetProcAddress, FreeLibrary
#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

#include <fmt/format.h>

#include "jvmti_error_strings.h"
#include "utils/jni_utils.h"
#include "utils/log_macro.h"

static constexpr auto LOG_TAG = "OpenJdkVmHookImpl";

namespace jvmplant {

static OpenJdkVmHookImpl* volatile sInstance = nullptr;

class JObjectHolder {
public:
    JObjectHolder(JNIEnv* env, jobject obj) : env_(env), obj_(nullptr) {
        if (env_ != nullptr && obj != nullptr) {
            obj_ = env_->NewGlobalRef(obj);
            if (obj_ == nullptr) {
                // failed to create global ref
                env->ExceptionDescribe();
                env->FatalError("Failed to create global reference");
#ifdef _MSC_VER
                __assume(0);
#else
                __builtin_unreachable();
#endif
            }
        }
    }

    ~JObjectHolder() {
        if (env_ != nullptr && obj_ != nullptr) {
            env_->DeleteGlobalRef(obj_);
        }
    }
    [[nodiscard]] jobject Peek() const { return obj_; }

    [[nodiscard]] bool IsValid() const { return env_ != nullptr && obj_ != nullptr; }

    [[nodiscard]] jobject Release() {
        jobject tmp = obj_;
        obj_ = nullptr; // prevent deletion in destructor
        return tmp;
    }

    JObjectHolder(const JObjectHolder&) = delete;

    JObjectHolder& operator=(const JObjectHolder&) = delete;

    JObjectHolder(JObjectHolder&& other) noexcept : env_(other.env_), obj_(other.obj_) {
        other.env_ = nullptr;
        other.obj_ = nullptr;
    }

    JObjectHolder& operator=(JObjectHolder&& other) noexcept {
        if (this != &other) {
            if (env_ != nullptr && obj_ != nullptr) {
                env_->DeleteGlobalRef(obj_);
            }
            env_ = other.env_;
            obj_ = other.obj_;
            other.env_ = nullptr;
            other.obj_ = nullptr;
        }
        return *this;
    }

private:
    JNIEnv* env_ = nullptr;
    jobject obj_ = nullptr; // global reference to the object
};

namespace openjdkvm {

struct OpenJdkVmHookInfo {
    // all values assigned in InitOpenJdkVmHookInfoLocked
    bool initialized = false;
    jvmtiEnv* ti = nullptr;
    std::mutex dumpClassBytecodeTransformMutex;
    std::mutex dumpClassBytecodeMapMutex;
    std::mutex redefineClassMutex;
    std::vector<std::tuple<JObjectHolder, std::vector<uint8_t>>> classFileBytes;
    uint8_t* pBytecodeVerificationLocal = nullptr;
    uint8_t* pBytecodeVerificationRemote = nullptr;
};

} // namespace openjdkvm

static openjdkvm::OpenJdkVmHookInfo sHookInfo;

void JNICALL OnJvmtiEventClassFileLoadHook(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jclass class_being_redefined,
                                           jobject loader, const char* name, jobject protection_domain,
                                           jint class_data_len, const unsigned char* class_data,
                                           jint* new_class_data_len, unsigned char** new_class_data) {
    // LOGI("OnJvmtiEventClassFileLoadHook: class name: {}", name);
    if (class_being_redefined == nullptr) {
        // ignore initial load, only care about retransform
        return;
    }
    std::scoped_lock lock(sHookInfo.dumpClassBytecodeMapMutex);
    auto&& classFile = std::vector(class_data, class_data + class_data_len);
    auto&& obj = JObjectHolder(jni_env, class_being_redefined);
    // add to map
    sHookInfo.classFileBytes.emplace_back(std::move(obj), std::move(classFile));
}

static bool InitOpenJdkVmHookInfoLocked(JNIEnv* env, std::string& errorMsg) {
    if (sHookInfo.initialized) {
        return true;
    }
    constexpr uint64_t kInvalidOffset = static_cast<uint64_t>(-1);
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

    // find address of bytecode verification flags
    void* libjvmHandle = nullptr;
#ifdef _WIN32
    libjvmHandle = GetModuleHandleW(L"jvm.dll");
    if (libjvmHandle == nullptr) {
        errorMsg = fmt::format("Failed to get handle of jvm.dll: {}", GetLastError());
        return false;
    }
#else
    libjvmHandle = dlopen("libjvm.so", RTLD_NOW | RTLD_NOLOAD);
    if (libjvmHandle == nullptr) {
        errorMsg = fmt::format("Failed to get handle of libjvm.so: {}", dlerror());
        return false;
    }
#endif
    std::function<void*(std::string_view)> fnGetSymbolAddress;
    fnGetSymbolAddress = [libjvmHandle](std::string_view name) -> void* {
        void* addr;
#ifdef _WIN32
        addr = GetProcAddress(static_cast<HMODULE>(libjvmHandle), std::string(name).c_str());
#else
        addr = dlsym(libjvmHandle, std::string(name).c_str());
#endif
        return addr;
    };
    std::unique_ptr<void, std::function<void(void*)>> libjvmCloser(libjvmHandle, [](void* handle) {
#ifdef _WIN32
    /* GetModuleHandleW does not need to be freed */
#else
        dlclose(handle);
#endif
    });
    uint64_t gHotSpotVMStructEntryTypeNameOffset = 0;
    uint64_t gHotSpotVMStructEntryFieldNameOffset = 0;
    uint64_t gHotSpotVMStructEntryTypeStringOffset = 0;
    uint64_t gHotSpotVMStructEntryIsStaticOffset = 0;
    uint64_t gHotSpotVMStructEntryOffsetOffset = 0;
    uint64_t gHotSpotVMStructEntryAddressOffset = 0;
    uint64_t gHotSpotVMStructEntryArrayStride = 0;
    uint64_t gHotSpotVMTypeEntryTypeNameOffset = 0;
    uint64_t gHotSpotVMTypeEntrySuperclassNameOffset = 0;
    uint64_t gHotSpotVMTypeEntryIsOopTypeOffset = 0;
    uint64_t gHotSpotVMTypeEntryIsIntegerTypeOffset = 0;
    uint64_t gHotSpotVMTypeEntryIsUnsignedOffset = 0;
    uint64_t gHotSpotVMTypeEntrySizeOffset = 0;
    uint64_t gHotSpotVMTypeEntryArrayStride = 0;
    // load values
    {
        struct U64Field {
            std::string_view name;
            uint64_t* ptr;
        };
#define U64_FIELD(name_) {#name_, &name_}
        std::vector<U64Field> fields = {
                U64_FIELD(gHotSpotVMStructEntryTypeNameOffset),     U64_FIELD(gHotSpotVMStructEntryFieldNameOffset),
                U64_FIELD(gHotSpotVMStructEntryTypeStringOffset),   U64_FIELD(gHotSpotVMStructEntryIsStaticOffset),
                U64_FIELD(gHotSpotVMStructEntryOffsetOffset),       U64_FIELD(gHotSpotVMStructEntryAddressOffset),
                U64_FIELD(gHotSpotVMStructEntryArrayStride),        U64_FIELD(gHotSpotVMTypeEntryTypeNameOffset),
                U64_FIELD(gHotSpotVMTypeEntrySuperclassNameOffset), U64_FIELD(gHotSpotVMTypeEntryIsOopTypeOffset),
                U64_FIELD(gHotSpotVMTypeEntryIsIntegerTypeOffset),  U64_FIELD(gHotSpotVMTypeEntryIsUnsignedOffset),
                U64_FIELD(gHotSpotVMTypeEntrySizeOffset),           U64_FIELD(gHotSpotVMTypeEntryArrayStride),
        };
#undef U64_FIELD
        for (auto& field: fields) {
            void* addr = fnGetSymbolAddress(field.name);
            if (addr == nullptr) {
                errorMsg = fmt::format("Failed to get symbol address of {}.", field.name);
                return false;
            }
            *field.ptr = *static_cast<uint64_t*>(addr);
        }
    }
    void** gHotSpotVMStructs = nullptr;
    void** gHotSpotVMTypes = nullptr;
    {
        gHotSpotVMStructs = static_cast<void**>(fnGetSymbolAddress("gHotSpotVMStructs"));
        if (gHotSpotVMStructs == nullptr) {
            errorMsg = "Failed to get symbol address of gHotSpotVMStructs.";
            return false;
        }
        gHotSpotVMTypes = static_cast<void**>(fnGetSymbolAddress("gHotSpotVMTypes"));
        if (gHotSpotVMTypes == nullptr) {
            errorMsg = "Failed to get symbol address of gHotSpotVMTypes.";
            return false;
        }
    }
    uint64_t jvmFlag_name_offset = kInvalidOffset;
    uint64_t jvmFlag_addr_offset = kInvalidOffset;
    void* jvmFlag_flags = nullptr;
    size_t jvmFlag_numFlags = 0;
    // find JVMFlag struct entry in gHotSpotVMStructs
    {
        auto* entry = static_cast<uint8_t*>(*gHotSpotVMStructs);
        while (true) {
            auto* typeNamePtr = *reinterpret_cast<const char**>(entry + gHotSpotVMStructEntryTypeNameOffset);
            auto* fieldNamePtr = *reinterpret_cast<const char**>(entry + gHotSpotVMStructEntryFieldNameOffset);
            if (typeNamePtr == nullptr && fieldNamePtr == nullptr) {
                // end of list
                break;
            }
            auto isStatic = *reinterpret_cast<const int*>(entry + gHotSpotVMStructEntryIsStaticOffset);
            uint64_t offset = *reinterpret_cast<const uint64_t*>(entry + gHotSpotVMStructEntryOffsetOffset);
            void* address = *reinterpret_cast<void**>(entry + gHotSpotVMStructEntryAddressOffset);
            std::string typeName = typeNamePtr != nullptr ? std::string(typeNamePtr) : "";
            std::string fieldName = fieldNamePtr != nullptr ? std::string(fieldNamePtr) : "";
            if (typeName == "JVMFlag") {
                // JVMFlag.{_name,_addr,_flags} are non-static fields
                if (fieldName == "_name" && isStatic == 0) {
                    jvmFlag_name_offset = static_cast<uint8_t>(offset);
                } else if (fieldName == "_addr" && isStatic == 0) {
                    jvmFlag_addr_offset = static_cast<uint8_t>(offset);
                }
                // JVMFlag::{flags,numFlags} are static fields
                else if (fieldName == "flags" && isStatic == 1) {
                    jvmFlag_flags = *reinterpret_cast<void**>(address);
                } else if (fieldName == "numFlags" && isStatic == 1) {
                    jvmFlag_numFlags = *reinterpret_cast<size_t*>(address);
                }
            }
            entry += gHotSpotVMStructEntryArrayStride;
        }
    }
    // check found
    if (jvmFlag_name_offset == kInvalidOffset || jvmFlag_addr_offset == kInvalidOffset || jvmFlag_flags == nullptr ||
        jvmFlag_numFlags == 0) {
        errorMsg = fmt::format("Failed to find JVMFlag struct entry or its fields, got name_offset={}, addr_offset={}, "
                               "flags={}, numFlags={}",
                               jvmFlag_name_offset, jvmFlag_addr_offset, jvmFlag_flags, jvmFlag_numFlags);
        return false;
    }

    // find JVMType size
    size_t jvmFlag_structSize = 0;
    {
        auto* entry = static_cast<uint8_t*>(*gHotSpotVMTypes);
        while (true) {
            auto* typeNamePtr = *reinterpret_cast<const char**>(entry + gHotSpotVMTypeEntryTypeNameOffset);
            auto* superclassNamePtr = *reinterpret_cast<const char**>(entry + gHotSpotVMTypeEntrySuperclassNameOffset);
            if (typeNamePtr == nullptr && superclassNamePtr == nullptr) {
                // end of list
                break;
            }
            std::string typeName = typeNamePtr != nullptr ? std::string(typeNamePtr) : "";
            if (typeName == "JVMFlag") {
                jvmFlag_structSize = *reinterpret_cast<size_t*>(entry + gHotSpotVMTypeEntrySizeOffset);
                break;
            }
            entry += gHotSpotVMTypeEntryArrayStride;
        }
    }
    if (jvmFlag_structSize == 0) {
        errorMsg = "Failed to find JVMFlag type entry or its size.";
        return false;
    }

    // iterate JVMFlag::flags to find bytecode verification flags
    {
        auto* entry = static_cast<uint8_t*>(jvmFlag_flags);
        for (size_t i = 0; i < jvmFlag_numFlags; i++) {
            auto* namePtr = *reinterpret_cast<const char**>(entry + jvmFlag_name_offset);
            auto* addrPtr = *reinterpret_cast<void**>(entry + jvmFlag_addr_offset);
            if (namePtr != nullptr && addrPtr != nullptr) {
                std::string name(namePtr);
                if (name == "BytecodeVerificationLocal") {
                    sHookInfo.pBytecodeVerificationLocal = static_cast<uint8_t*>(addrPtr);
                } else if (name == "BytecodeVerificationRemote") {
                    sHookInfo.pBytecodeVerificationRemote = static_cast<uint8_t*>(addrPtr);
                }
            }
            entry += jvmFlag_structSize;
        }
    }

    if (sHookInfo.pBytecodeVerificationLocal == nullptr || sHookInfo.pBytecodeVerificationRemote == nullptr) {
        errorMsg = fmt::format("Failed to find bytecode verification flags, got local={}, remote={}",
                               fmt::ptr(sHookInfo.pBytecodeVerificationLocal),
                               fmt::ptr(sHookInfo.pBytecodeVerificationRemote));
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
    auto klassName = GetJavaClassName(env, klass);
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
                if (name.IsValid() && env->IsSameObject(name.Peek(), klass) == JNI_TRUE) {
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
            // just clear all, the whole function is locked by dumpClassBytecodeTransformMutex
            sHookInfo.classFileBytes.clear();
        }
        // return bytecode
        return bytecode;
    }
}

bool OpenJdkVmHookImpl::RedefineClassV2(JNIEnv* env, jclass klass, const std::vector<uint8_t>& bytecode,
                                        bool skipVerification, std::string& errorMsg) {
    using namespace jvmplant::util;
    if (!sHookInfo.initialized) {
        errorMsg = "OpenJdkVmHookImpl is not initialized";
        return {};
    }
    if (klass == nullptr) {
        errorMsg = "class is null";
        return {};
    }
    auto klassName = GetJavaClassName(env, klass);
    // replace '.' to '/' in class name
    std::ranges::replace(klassName, '.', '/');
    jvmtiEnv* ti = sHookInfo.ti;
    jvmtiClassDefinition classDefinition = {};
    classDefinition.klass = klass;
    classDefinition.class_byte_count = static_cast<jint>(bytecode.size());
    classDefinition.class_bytes = bytecode.data();
    auto* pVerifyLocal = sHookInfo.pBytecodeVerificationLocal;
    auto* pVerifyRemote = sHookInfo.pBytecodeVerificationRemote;
    std::scoped_lock lock(sHookInfo.redefineClassMutex);
    std::optional<uint8_t> originalBytecodeVerificationLocal;
    std::optional<uint8_t> originalBytecodeVerificationRemote;
    if (skipVerification && pVerifyLocal != nullptr && pVerifyRemote != nullptr) {
        // patch bytecode verification flags
        originalBytecodeVerificationLocal = *pVerifyLocal;
        originalBytecodeVerificationRemote = *pVerifyRemote;
        *pVerifyLocal = 0;
        *pVerifyRemote = 0;
    }
    jvmtiError err = ti->RedefineClasses(1, &classDefinition);
    // restore bytecode verification flags
    if (pVerifyLocal != nullptr && originalBytecodeVerificationLocal.has_value()) {
        *pVerifyLocal = originalBytecodeVerificationLocal.value();
    }
    if (pVerifyRemote != nullptr && originalBytecodeVerificationRemote.has_value()) {
        *pVerifyRemote = originalBytecodeVerificationRemote.value();
    }
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
