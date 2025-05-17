//
// Created by sulfate on 2024-10-21.
//

#include "jni_utils.h"

#include <fmt/format.h>

namespace jvmplant::util {

std::optional<std::string> JstringToStringOpt(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) {
        return std::nullopt;
    }
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (chars == nullptr) {
        env->ExceptionClear();
        return std::nullopt;
    }
    std::string str(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return str;
}

std::string JstringToString(JNIEnv* env, jstring jstr) { return JstringToStringOpt(env, jstr).value_or(""); }

void ThrowIfNoPendingException(JNIEnv* env, const char* klass, std::string_view msg) {
    if (env->ExceptionCheck()) {
        return;
    }
    // in case string_view is not null-terminated
    env->ThrowNew(env->FindClass(klass), std::string(msg).c_str());
}

std::string GetThrowableStackTraceString(JNIEnv* env, jthrowable throwable) {
    if (throwable == nullptr) {
        return "<null>";
    }
    jthrowable current = nullptr;
    if (env->ExceptionCheck()) {
        current = env->ExceptionOccurred();
        env->ExceptionClear();
    }
    jclass kThrowable = env->FindClass("java/lang/Throwable");
    jmethodID toString = env->GetMethodID(kThrowable, "toString", "()Ljava/lang/String;");
    auto errorMessage = static_cast<jstring>(env->CallObjectMethod(throwable, toString));
    if (env->ExceptionCheck()) {
        auto ret = "an exception occurred while getting stack trace, no further information available";
        // restore the original exception
        if (current != nullptr) {
            env->Throw(current);
            env->DeleteLocalRef(current);
        }
        return ret;
    } else {
        const char* stackTraceChars = env->GetStringUTFChars(errorMessage, nullptr);
        std::string result(stackTraceChars);
        env->ReleaseStringUTFChars(errorMessage, stackTraceChars);
        env->DeleteLocalRef(errorMessage);
        env->DeleteLocalRef(kThrowable);
        // restore the original exception
        if (current != nullptr) {
            env->Throw(current);
            env->DeleteLocalRef(current);
        }
        return result;
    }
}

std::string GetErrorMessageAndClearCurrentThrowable(JNIEnv* env, std::string_view msg) {
    if (env->ExceptionCheck()) {
        jthrowable throwable = env->ExceptionOccurred();
        env->ExceptionClear();
        std::string throwableMsg = GetThrowableStackTraceString(env, throwable);
        env->DeleteLocalRef(throwable);
        return fmt::format("{}: {}", msg, throwableMsg);
    } else {
        return fmt::format("{}: no throwable available", msg);
    }
}


jobject WrapPrimitiveValue(JNIEnv* env, char type, const jvalue& jvalue) {
    switch (type) {
        case 'Z': {
            jclass kBoolean = env->FindClass("java/lang/Boolean");
            jmethodID cid = env->GetStaticMethodID(kBoolean, "valueOf", "(Z)Ljava/lang/Boolean;");
            return env->CallStaticObjectMethod(kBoolean, cid, jvalue.z);
        }
        case 'B': {
            jclass kByte = env->FindClass("java/lang/Byte");
            jmethodID cid = env->GetStaticMethodID(kByte, "valueOf", "(B)Ljava/lang/Byte;");
            return env->CallStaticObjectMethod(kByte, cid, jvalue.b);
        }
        case 'C': {
            jclass kCharacter = env->FindClass("java/lang/Character");
            jmethodID cid = env->GetStaticMethodID(kCharacter, "valueOf", "(C)Ljava/lang/Character;");
            return env->CallStaticObjectMethod(kCharacter, cid, jvalue.c);
        }
        case 'S': {
            jclass kShort = env->FindClass("java/lang/Short");
            jmethodID cid = env->GetStaticMethodID(kShort, "valueOf", "(S)Ljava/lang/Short;");
            return env->CallStaticObjectMethod(kShort, cid, jvalue.s);
        }
        case 'I': {
            jclass kInteger = env->FindClass("java/lang/Integer");
            jmethodID cid = env->GetStaticMethodID(kInteger, "valueOf", "(I)Ljava/lang/Integer;");
            return env->CallStaticObjectMethod(kInteger, cid, jvalue.i);
        }
        case 'J': {
            jclass kLong = env->FindClass("java/lang/Long");
            jmethodID cid = env->GetStaticMethodID(kLong, "valueOf", "(J)Ljava/lang/Long;");
            return env->CallStaticObjectMethod(kLong, cid, jvalue.j);
        }
        case 'F': {
            jclass kFloat = env->FindClass("java/lang/Float");
            jmethodID cid = env->GetStaticMethodID(kFloat, "valueOf", "(F)Ljava/lang/Float;");
            return env->CallStaticObjectMethod(kFloat, cid, jvalue.f);
        }
        case 'D': {
            jclass kDouble = env->FindClass("java/lang/Double");
            jmethodID cid = env->GetStaticMethodID(kDouble, "valueOf", "(D)Ljava/lang/Double;");
            return env->CallStaticObjectMethod(kDouble, cid, jvalue.d);
        }
        case 'V': {
            return nullptr;
        }
        case 'L': {
            return jvalue.l;
        }
        default: {
            env->ThrowNew(env->FindClass("java/lang/AssertionError"),
                          (std::string("unsupported primitive type: ") + std::to_string(type)).c_str());
            return nullptr;
        }
    }
}

void ExtractWrappedValue(JNIEnv* env, jvalue& out, char type, jobject value) {
    switch (type) {
        case 'Z': {
            jclass kBoolean = env->FindClass("java/lang/Boolean");
            jmethodID cid = env->GetMethodID(kBoolean, "booleanValue", "()Z");
            out.z = env->CallBooleanMethod(value, cid);
            break;
        }
        case 'B': {
            jclass kByte = env->FindClass("java/lang/Byte");
            jmethodID cid = env->GetMethodID(kByte, "byteValue", "()B");
            out.b = env->CallByteMethod(value, cid);
            break;
        }
        case 'C': {
            jclass kCharacter = env->FindClass("java/lang/Character");
            jmethodID cid = env->GetMethodID(kCharacter, "charValue", "()C");
            out.c = env->CallCharMethod(value, cid);
            break;
        }
        case 'S': {
            jclass kShort = env->FindClass("java/lang/Short");
            jmethodID cid = env->GetMethodID(kShort, "shortValue", "()S");
            out.s = env->CallShortMethod(value, cid);
            break;
        }
        case 'I': {
            jclass kInteger = env->FindClass("java/lang/Integer");
            jmethodID cid = env->GetMethodID(kInteger, "intValue", "()I");
            out.i = env->CallIntMethod(value, cid);
            break;
        }
        case 'J': {
            jclass kLong = env->FindClass("java/lang/Long");
            jmethodID cid = env->GetMethodID(kLong, "longValue", "()J");
            out.j = env->CallLongMethod(value, cid);
            break;
        }
        case 'F': {
            jclass kFloat = env->FindClass("java/lang/Float");
            jmethodID cid = env->GetMethodID(kFloat, "floatValue", "()F");
            out.f = env->CallFloatMethod(value, cid);
            break;
        }
        case 'D': {
            jclass kDouble = env->FindClass("java/lang/Double");
            jmethodID cid = env->GetMethodID(kDouble, "doubleValue", "()D");
            out.d = env->CallDoubleMethod(value, cid);
            break;
        }
        case 'V': {
            out.l = nullptr;
            break;
        }
        case 'L': {
            out.l = value;
            break;
        }
        default: {
            env->ThrowNew(env->FindClass("java/lang/AssertionError"),
                          (std::string("unsupported primitive type: ") + std::to_string(type)).c_str());
            break;
        }
    }
}

jstring GetClassNameJ(JNIEnv* env, jclass klass) {
    static jmethodID getName = nullptr;
    if (getName == nullptr) {
        jclass kClass = env->FindClass("java/lang/Class");
        getName = env->GetMethodID(kClass, "getName", "()Ljava/lang/String;");
        env->DeleteLocalRef(kClass);
    }
    if (getName == nullptr) {
        return nullptr;
    }
    auto name = static_cast<jstring>(env->CallObjectMethod(klass, getName));
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    return name;
}

std::string GetClassName(JNIEnv* env, jclass klass) {
    auto jname = GetClassNameJ(env, klass);
    if (jname == nullptr || env->ExceptionCheck()) {
        return {};
    }
    auto name = JstringToString(env, jname);
    env->DeleteLocalRef(jname);
    return name;
}


} // namespace jvmplant::util
