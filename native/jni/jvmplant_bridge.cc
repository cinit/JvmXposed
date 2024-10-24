//
// Created by sulfate on 2024-10-21.
//

#include "jvmplant_bridge.h"

#include <cstdint>
#include <vector>
#include <cstring>
#include <string>
#include <array>
#include <string_view>

#include <jni.h>

#include "utils/jni_utils.h"
#include "shared/jvmplant_api.h"
#include "openjdkvm/openjdkvm_hook_impl.h"

static jvmplant::JvmPlantInterface* sJvmPlant = nullptr;

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

/*
 * Class:     dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge
 * Method:    nativeInitializeJvmPlant
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeInitializeJvmPlant
        (JNIEnv* env, jclass) {
    using namespace jvmplant;
    using namespace jvmplant::util;
    std::string errMsg;
    auto it = OpenJdkVmHookImpl::GetOrCreateInstance(env, errMsg);
    if (env->ExceptionCheck()) {
        // return with exception
        return;
    }
    if (it == nullptr) {
        ThrowIfNoPendingException(env, ExceptionNames::kRuntimeException, errMsg);
        return;
    }
    sJvmPlant = it;
}

/*
 * Class:     dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge
 * Method:    nativeHookMethod
 * Signature: (Ljava/lang/reflect/Member;Ljava/lang/reflect/Member;Ljava/lang/Object;)Ljava/lang/reflect/Method;
 */
JNIEXPORT jobject JNICALL
Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeHookMethod
        (JNIEnv* env, jclass, jobject target, jobject callback, jobject context) {
    using namespace jvmplant::util;
    auto it = sJvmPlant;
    if (it == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalStateException,
                                  "JvmPlant is not initialized");
        return nullptr;
    }
    if (target == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kNullPointerException,
                                  "target method is null");
        return nullptr;
    }
    if (callback == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kNullPointerException,
                                  "callback method is null");
        return nullptr;
    }
    if (context == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kNullPointerException,
                                  "context is null");
        return nullptr;
    }
    // target should be a Method/Constructor object
    jclass kMethod = env->FindClass("java/lang/reflect/Method");
    jclass kConstructor = env->FindClass("java/lang/reflect/Constructor");
    if (env->IsInstanceOf(target, kMethod) == JNI_FALSE && env->IsInstanceOf(target, kConstructor) == JNI_FALSE) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalArgumentException,
                                  "target is not a Method/Constructor object");
        return nullptr;
    }
    // callback should be a Method object
    if (env->IsInstanceOf(callback, kMethod) == JNI_FALSE) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalArgumentException,
                                  "callback is not a Method object");
        return nullptr;
    }
    jmethodID getDeclaringClass = env->GetMethodID(kMethod, "getDeclaringClass", "()Ljava/lang/Class;");
    auto callbackClass = static_cast<jclass>(env->CallObjectMethod(callback, getDeclaringClass));
    if (!env->IsInstanceOf(context, callbackClass)) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalArgumentException,
                                  "callback context is not an instance of class declaring callback method");
        return nullptr;
    }
    env->DeleteLocalRef(kMethod);
    env->DeleteLocalRef(kConstructor);
    env->DeleteLocalRef(callbackClass);
    if (it->IsMethodHooked(env, target)) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalArgumentException,
                                  "target method is already hooked");
        return nullptr;
    }
    // do hook
    return it->HookMethod(env, target, context, callback);
}

/*
 * Class:     dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge
 * Method:    nativeIsMethodHooked
 * Signature: (Ljava/lang/reflect/Member;)Z
 */
JNIEXPORT jboolean JNICALL
Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeIsMethodHooked
        (JNIEnv* env, jclass, jobject method) {
    using namespace jvmplant::util;
    auto it = sJvmPlant;
    if (it == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalStateException,
                                  "JvmPlant is not initialized");
        return JNI_FALSE;
    }
    if (method == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kNullPointerException, "method is null");
        return JNI_FALSE;
    }
    // check if argument is a Method/Constructor object
    jclass kMethod = env->FindClass("java/lang/reflect/Method");
    jclass kConstructor = env->FindClass("java/lang/reflect/Constructor");
    if (env->IsInstanceOf(method, kMethod) == JNI_FALSE && env->IsInstanceOf(method, kConstructor) == JNI_FALSE) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalArgumentException,
                                  "method is not a Method/Constructor object");
        return JNI_FALSE;
    }
    env->DeleteLocalRef(kMethod);
    env->DeleteLocalRef(kConstructor);
    return it->IsMethodHooked(env, method) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge
 * Method:    nativeUnhookMethod
 * Signature: (Ljava/lang/reflect/Member;)Z
 */
JNIEXPORT jboolean JNICALL
Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeUnhookMethod
        (JNIEnv* env, jclass, jobject method) {
    using namespace jvmplant::util;
    auto it = sJvmPlant;
    if (it == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalStateException,
                                  "JvmPlant is not initialized");
        return JNI_FALSE;
    }
    if (method == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kNullPointerException, "method is null");
        return JNI_FALSE;
    }
    // check if argument is a Method/Constructor object
    jclass kMethod = env->FindClass("java/lang/reflect/Method");
    jclass kConstructor = env->FindClass("java/lang/reflect/Constructor");
    if (env->IsInstanceOf(method, kMethod) == JNI_FALSE && env->IsInstanceOf(method, kConstructor) == JNI_FALSE) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalArgumentException,
                                  "method is not a Method/Constructor object");
        return JNI_FALSE;
    }
    env->DeleteLocalRef(kMethod);
    env->DeleteLocalRef(kConstructor);
    return it->UnHookMethod(env, method) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge
 * Method:    nativeDeoptimizeMethod
 * Signature: (Ljava/lang/reflect/Member;)Z
 */
JNIEXPORT jboolean JNICALL
Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeDeoptimizeMethod
        (JNIEnv* env, jclass, jobject method) {
    using namespace jvmplant::util;
    auto it = sJvmPlant;
    if (it == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalStateException,
                                  "JvmPlant is not initialized");
        return JNI_FALSE;
    }
    if (method == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kNullPointerException, "method is null");
        return JNI_FALSE;
    }
    // check if argument is a Method/Constructor object
    jclass kMethod = env->FindClass("java/lang/reflect/Method");
    jclass kConstructor = env->FindClass("java/lang/reflect/Constructor");
    if (env->IsInstanceOf(method, kMethod) == JNI_FALSE && env->IsInstanceOf(method, kConstructor) == JNI_FALSE) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalArgumentException,
                                  "method is not a Method/Constructor object");
        return JNI_FALSE;
    }
    env->DeleteLocalRef(kMethod);
    env->DeleteLocalRef(kConstructor);
    return it->DeoptimizeMethod(env, method) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge
 * Method:    nativeGetClassInitializer
 * Signature: (Ljava/lang/Class;)Ljava/lang/reflect/Executable;
 */
JNIEXPORT jobject JNICALL
Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeGetClassInitializer
        (JNIEnv* env, jclass, jclass klass) {
    using namespace jvmplant::util;
    auto it = sJvmPlant;
    if (it == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalStateException,
                                  "JvmPlant is not initialized");
        return JNI_FALSE;
    }
    if (klass == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kNullPointerException, "class is null");
        return nullptr;
    }
    return it->GetClassInitializer(env, klass);
}

/*
 * Class:     dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge
 * Method:    nativeAllocateInstance
 * Signature: (Ljava/lang/Class;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeAllocateInstance
        (JNIEnv* env, jclass, jclass target) {
    using namespace jvmplant::util;
    if (target == nullptr) {
        ThrowIfNoPendingException(env, ExceptionNames::kNullPointerException, "target class is null");
        return nullptr;
    }
    return env->AllocObject(target);
}

static jobject TransformArgumentsAndInvokeNonVirtual(JNIEnv* env, jmethodID method, jclass clazz,
                                                     const std::vector<char>& parameterShorts,
                                                     char returnTypeShort, bool isStatic,
                                                     jobject obj, jobjectArray args) {
    using namespace jvmplant::util;
    int argc = int(parameterShorts.size());
    auto* jargs = new jvalue[argc];
    memset(jargs, 0, sizeof(jvalue) * argc);
    for (int i = 0; i < argc; i++) {
        ExtractWrappedValue(env, jargs[i], parameterShorts[i], env->GetObjectArrayElement(args, i));
        if (env->ExceptionCheck()) {
            delete[] jargs;
            return nullptr;
        }
    }
    jvalue ret;
    memset(&ret, 0, sizeof(jvalue));
    switch (returnTypeShort) {
        case 'L': {
            ret.l = isStatic ? env->CallStaticObjectMethodA(clazz, method, jargs)
                             : env->CallNonvirtualObjectMethodA(obj, clazz, method, jargs);
            break;
        }
        case 'Z': {
            ret.z = isStatic ? env->CallStaticBooleanMethodA(clazz, method, jargs)
                             : env->CallNonvirtualBooleanMethodA(obj, clazz, method, jargs);
            break;
        }
        case 'B': {
            ret.b = isStatic ? env->CallStaticByteMethodA(clazz, method, jargs)
                             : env->CallNonvirtualByteMethodA(obj, clazz, method, jargs);
            break;
        }
        case 'C': {
            ret.c = isStatic ? env->CallStaticCharMethodA(clazz, method, jargs)
                             : env->CallNonvirtualCharMethodA(obj, clazz, method, jargs);
            break;
        }
        case 'S': {
            ret.s = isStatic ? env->CallStaticShortMethodA(clazz, method, jargs)
                             : env->CallNonvirtualShortMethodA(obj, clazz, method, jargs);
            break;
        }
        case 'I': {
            ret.i = isStatic ? env->CallStaticIntMethodA(clazz, method, jargs)
                             : env->CallNonvirtualIntMethodA(obj, clazz, method, jargs);
            break;
        }
        case 'J': {
            ret.j = isStatic ? env->CallStaticLongMethodA(clazz, method, jargs)
                             : env->CallNonvirtualLongMethodA(obj, clazz, method, jargs);
            break;
        }
        case 'F': {
            ret.f = isStatic ? env->CallStaticFloatMethodA(clazz, method, jargs)
                             : env->CallNonvirtualFloatMethodA(obj, clazz, method, jargs);
            break;
        }
        case 'D': {
            ret.d = isStatic ? env->CallStaticDoubleMethodA(clazz, method, jargs)
                             : env->CallNonvirtualDoubleMethodA(obj, clazz, method, jargs);
            break;
        }
        case 'V': {
            if (isStatic) {
                env->CallStaticVoidMethodA(clazz, method, jargs);
            } else {
                env->CallNonvirtualVoidMethodA(obj, clazz, method, jargs);
            }
            ret.l = nullptr;
            break;
        }
        default: {
            env->ThrowNew(env->FindClass("java/lang/AssertionError"),
                          (std::string("unsupported primitive type: ") + std::to_string(returnTypeShort)).c_str());
            delete[] jargs;
            return nullptr;
        }
    }
    delete[] jargs;
    // check for exceptions
    if (env->ExceptionCheck()) {
        // wrap exception with InvocationTargetException
        jthrowable exception = env->ExceptionOccurred();
        env->ExceptionClear();
        jclass exceptionClass = env->FindClass("java/lang/reflect/InvocationTargetException");
        jmethodID exceptionConstructor = env->GetMethodID(exceptionClass, "<init>", "(Ljava/lang/Throwable;)V");
        jobject exceptionObject = env->NewObject(exceptionClass, exceptionConstructor, exception);
        env->Throw((jthrowable) exceptionObject);
        return nullptr;
    }
    // wrap return value
    return WrapPrimitiveValue(env, returnTypeShort, ret);
}

/*
 * Class:     dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge
 * Method:    invokeNonVirtualArtMethodImpl
 * Signature: (Ljava/lang/reflect/Member;Ljava/lang/String;Ljava/lang/Class;ZLjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_invokeNonVirtualArtMethodImpl
        (JNIEnv* env,
         jclass clazz,
         jobject member,
         jstring signature,
         jclass klass,
         jboolean is_static,
         jobject obj,
         jobjectArray args) {
    using namespace jvmplant::util;
    // basic checks have already been done in Java
    jmethodID methodId = env->FromReflectedMethod(member);
    std::string methodSignature = JstringToString(env, signature);
    if (methodSignature.empty()) {
        ThrowIfNoPendingException(env, ExceptionNames::kIllegalArgumentException, "method signature is empty");
        return nullptr;
    }
    int argc = args == nullptr ? 0 : env->GetArrayLength(args);
    // parse method signature
    std::vector<char> paramShorts;
    paramShorts.reserve(argc);
    // skip first '('
    for (int i = 1; i < methodSignature.length(); i++) {
        if (methodSignature[i] == ')') {
            break;
        }
        if (methodSignature[i] == 'L') {
            paramShorts.push_back('L');
            while (methodSignature[i] != ';') {
                i++;
            }
        } else if (methodSignature[i] == '[') {
            paramShorts.push_back('L');
            // it's an array, so we just skip the '['
            while (methodSignature[i] == '[') {
                i++;
            }
            // check if it's a primitive array
            if (methodSignature[i] == 'L') {
                while (methodSignature[i] != ';') {
                    i++;
                }
            }
        } else {
            paramShorts.push_back(methodSignature[i]);
        }
    }
    // find return type, start from last ')'
    char returnTypeShort = 0;
    for (auto i = methodSignature.length() - 1; i > 0; i--) {
        if (methodSignature[i] == ')') {
            returnTypeShort = methodSignature[i + 1];
            break;
        }
    }
    if (returnTypeShort == '[') {
        returnTypeShort = 'L';
    }
    if (paramShorts.size() != argc) {
        ThrowIfNoPendingException(env, ExceptionNames::kIllegalArgumentException, "argument count mismatch");
        return nullptr;
    }
    // invoke
    return TransformArgumentsAndInvokeNonVirtual(env, methodId, klass, paramShorts,
                                                 returnTypeShort, is_static, obj, args);
}
