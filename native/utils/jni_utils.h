//
// Created by sulfate on 2024-10-21.
//

#ifndef JVMXPOSED_JNI_UTILS_H
#define JVMXPOSED_JNI_UTILS_H

#include <cstdint>
#include <string>
#include <string_view>
#include <optional>
#include <jni.h>

namespace jvmplant::util {

class ScopedLocalFrame {
public:
    explicit inline ScopedLocalFrame(JNIEnv* env, int capacity = 16) : env_(env) {
        env_->PushLocalFrame(capacity);
    }

    inline ~ScopedLocalFrame() {
        env_->PopLocalFrame(nullptr);
    }

    // non-copyable
    ScopedLocalFrame(const ScopedLocalFrame&) = delete;

    ScopedLocalFrame& operator=(const ScopedLocalFrame&) = delete;

    ScopedLocalFrame(ScopedLocalFrame&&) = delete;

    ScopedLocalFrame& operator=(ScopedLocalFrame&&) = delete;

private:
    JNIEnv* env_;
};

namespace ExceptionNames {

constexpr auto kRuntimeException = "java/lang/RuntimeException";
constexpr auto kIllegalArgumentException = "java/lang/IllegalArgumentException";
constexpr auto kIllegalStateException = "java/lang/IllegalStateException";
constexpr auto kNullPointerException = "java/lang/NullPointerException";
constexpr auto kOutOfMemoryError = "java/lang/OutOfMemoryError";

}

std::optional<std::string> JstringToStringOpt(JNIEnv* env, jstring jstr);

std::string JstringToString(JNIEnv* env, jstring jstr);

void ThrowIfNoPendingException(JNIEnv* env, const char* klass, std::string_view msg);

std::string GetThrowableStackTraceString(JNIEnv* env, jthrowable throwable);

std::string GetErrorMessageAndClearCurrentThrowable(JNIEnv* env, std::string_view msg);

jobject WrapPrimitiveValue(JNIEnv* env, char type, const jvalue& jvalue);

void ExtractWrappedValue(JNIEnv* env, jvalue& out, char type, jobject value);

jstring GetJavaClassNameJ(JNIEnv* env, jclass klass);

std::string GetJavaClassName(JNIEnv* env, jclass klass);

}

#endif //JVMXPOSED_JNI_UTILS_H
