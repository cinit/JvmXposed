#ifndef JVMXPOSED_LOG_MACRO_H
#define JVMXPOSED_LOG_MACRO_H

#include "log_common.h"

#include <fmt/format.h>

// separate the log macros from the log.h file to avoid messing up the global namespace

#define STRINGIFY_(x) #x
#define TOSTRING_(x) STRINGIFY_(x)

// Cross-platform filename extraction
#ifdef _MSC_VER
    // MSVC doesn't have __FILE_NAME__, so we extract it from __FILE__
    #define __FILENAME__ (strrchr(__FILE__, '\\') ? strrchr(__FILE__, '\\') + 1 : __FILE__)
#elif defined(__GNUC__) || defined(__clang__)
    // GCC and Clang support __FILE_NAME__ in newer versions
    #ifdef __FILE_NAME__
        #define __FILENAME__ __FILE_NAME__
    #else
        // Fallback for older GCC/Clang versions
        #define __FILENAME__ (strrchr(__FILE__, '/') ? strrchr(__FILE__, '/') + 1 : __FILE__)
    #endif
#else
    // Unknown compiler - use __FILE__ as fallback
    #define __FILENAME__ __FILE__
#endif

#define CHECK_1ARGS_(condition) \
    do { \
        if (!(condition)) [[unlikely]] { \
            ::jvmplant::util::Abort("CHECK failed: " #condition " #" __FILENAME__ ":" TOSTRING_(__LINE__)); \
        } \
    } while (false)

#define CHECK_2ARGS_(condition, msg) \
    do { \
        if (!(condition)) [[unlikely]] { \
            ::jvmplant::util::Abort("CHECK failed: " #condition " #" __FILENAME__ ":" TOSTRING_(__LINE__) ": " msg); \
        } \
    } while (false)

#define CHECK_GET_MACRO_(_1, _2, _NAME, ...) _NAME

#define CHECK(...) CHECK_GET_MACRO_(__VA_ARGS__, CHECK_2ARGS_, CHECK_1ARGS_)(__VA_ARGS__)

#ifdef NDEBUG

#define LOGV(...) ((void)0)
#define LOGD(...) ((void)0)
#define LOGI(...) (::jvmplant::util::Log::LogMessage(::jvmplant::util::Log::Level::kInfo, LOG_TAG, ::fmt::format(__VA_ARGS__)))
#define LOGW(...) (::jvmplant::util::Log::LogMessage(::jvmplant::util::Log::Level::kWarn, LOG_TAG, ::fmt::format(__VA_ARGS__)))
#define LOGE(...) (::jvmplant::util::Log::LogMessage(::jvmplant::util::Log::Level::kError, LOG_TAG, ::fmt::format(__VA_ARGS__)))

#define DCHECK(...) ((void)0)

#else

#define LOGV(...) (::jvmplant::util::Log::LogMessage(::jvmplant::util::Log::Level::kVerbose, LOG_TAG, ::fmt::format(__VA_ARGS__)))
#define LOGD(...) (::jvmplant::util::Log::LogMessage(::jvmplant::util::Log::Level::kDebug, LOG_TAG, ::fmt::format(__VA_ARGS__)))
#define LOGI(...) (::jvmplant::util::Log::LogMessage(::jvmplant::util::Log::Level::kInfo, LOG_TAG, ::fmt::format(__VA_ARGS__)))
#define LOGW(...) (::jvmplant::util::Log::LogMessage(::jvmplant::util::Log::Level::kWarn, LOG_TAG, ::fmt::format(__VA_ARGS__)))
#define LOGE(...) (::jvmplant::util::Log::LogMessage(::jvmplant::util::Log::Level::kError, LOG_TAG, ::fmt::format(__VA_ARGS__)))

#define DCHECK(...) CHECK(__VA_ARGS__)

#endif

#define NOT_IMPLEMENTED() CHECK(false, "Not implemented")

#endif //JVMXPOSED_LOG_MACRO_H
