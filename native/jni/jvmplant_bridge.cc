//
// Created by sulfate on 2024-10-21.
//

#include "jvmplant_bridge.h"

#include <array>
#include <cstdint>
#include <cstring>
#include <string>
#include <string_view>
#include <vector>

#include <jni.h>

#include "openjdkvm/openjdkvm_hook_impl.h"
#include "shared/jvmplant_api.h"
#include "utils/jni_utils.h"

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
JNIEXPORT void JNICALL Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeInitializeJvmPlant(JNIEnv* env, jclass) {
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
 * Method:    nativeGetClassInitializer
 * Signature: (Ljava/lang/Class;)Ljava/lang/reflect/Executable;
 */
JNIEXPORT jobject JNICALL Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeGetClassInitializer(JNIEnv* env,
                                                                                                      jclass,
                                                                                                      jclass klass) {
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
    std::string errMsg;
    jobject rc = it->GetClassInitializer(env, klass, errMsg);
    if (env->ExceptionCheck()) {
        // return with exception
        return nullptr;
    }
    if (rc == nullptr && !errMsg.empty()) {
        // if the class has no <clinit> method, just return null
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalStateException, errMsg);
        return nullptr;
    }
    // return the Constructor object, or null if the class has no <clinit> method
    return rc;
}

/*
 * Class:     dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge
 * Method:    nativeAllocateInstance
 * Signature: (Ljava/lang/Class;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeAllocateInstance(JNIEnv* env, jclass,
                                                                                                   jclass target) {
    using namespace jvmplant::util;
    if (target == nullptr) {
        ThrowIfNoPendingException(env, ExceptionNames::kNullPointerException, "target class is null");
        return nullptr;
    }
    return env->AllocObject(target);
}

static jobject TransformArgumentsAndInvokeNonVirtual(JNIEnv* env, jmethodID method, jclass clazz,
                                                     const std::vector<char>& parameterShorts, char returnTypeShort,
                                                     bool isStatic, jobject obj, jobjectArray args) {
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
 * Signature:
 * (Ljava/lang/reflect/Member;Ljava/lang/String;Ljava/lang/Class;ZLjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_invokeNonVirtualArtMethodImpl(
        JNIEnv* env, jclass clazz, jobject member, jstring signature, jclass klass, jboolean is_static, jobject obj,
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
    return TransformArgumentsAndInvokeNonVirtual(env, methodId, klass, paramShorts, returnTypeShort, is_static, obj,
                                                 args);
}

/*
 * Class:     dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge
 * Method:    nativeGetClassFile
 * Signature: (Ljava/lang/Class;)[B
 */
JNIEXPORT jbyteArray JNICALL Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeGetClassFile(JNIEnv* env, jclass,
                                                                                                  jclass klass) {
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
    std::string errMsg;
    std::vector<uint8_t> bytecode = it->GetClassBytecode(env, klass, errMsg);
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    if (bytecode.empty()) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kRuntimeException, errMsg);
        return nullptr;
    }
    jbyteArray bytecodeArray = env->NewByteArray(static_cast<jsize>(bytecode.size()));
    if (bytecodeArray == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kOutOfMemoryError, "out of memory");
        return nullptr;
    }
    env->SetByteArrayRegion(bytecodeArray, 0, static_cast<jsize>(bytecode.size()),
                            reinterpret_cast<const jbyte*>(bytecode.data()));
    return bytecodeArray;
}

/*
 * Class:     dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge
 * Method:    nativeRedefineClassV2
 * Signature: (Ljava/lang/Class;[BZ)V
 */
JNIEXPORT void JNICALL Java_dev_tmpfs_jvmplant_impl_JvmPlantNativeBridge_nativeRedefineClassV2(
        JNIEnv* env, jclass, jclass klass, jbyteArray bytecode, jboolean skipVerification) {
    using namespace jvmplant::util;
    auto it = sJvmPlant;
    if (it == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalStateException,
                                  "JvmPlant is not initialized");
        return;
    }
    if (klass == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kNullPointerException, "class is null");
        return;
    }
    if (bytecode == nullptr) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kNullPointerException, "bytecode is null");
        return;
    }
    jsize bytecodeLength = env->GetArrayLength(bytecode);
    if (bytecodeLength <= 0) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kIllegalArgumentException,
                                  "bytecode length is invalid");
        return;
    }
    std::vector<uint8_t> bytecodeVector(static_cast<size_t>(bytecodeLength));
    env->GetByteArrayRegion(bytecode, 0, bytecodeLength, reinterpret_cast<jbyte*>(bytecodeVector.data()));
    if (env->ExceptionCheck()) {
        return;
    }
    std::string errMsg;
    if (!it->RedefineClassV2(env, klass, bytecodeVector, static_cast<bool>(skipVerification), errMsg)) {
        ThrowIfNoPendingException(env, jvmplant::util::ExceptionNames::kRuntimeException, errMsg);
        return;
    }
}
