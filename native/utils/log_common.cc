#include "log_common.h"

#include <cstring>
#include <cstdlib>

#include <fmt/format.h>

#if defined(_WIN32)
// Windows

#include <windows.h>

#else
// Linux
#include <unistd.h>

#if defined(__ANDROID__)

// Android specific
#include <android/log.h>
#include <android/set_abort_message.h>

#endif

#endif


namespace jvmplant::util {

#if defined(__ANDROID__)

static void DefaultLogToAndroidLogHandler(Log::Level level, std::string_view tag, std::string_view msg) noexcept;

constinit volatile Log::LogHandler Log::sLogHandler = &DefaultLogToAndroidLogHandler;

static void DefaultLogToAndroidLogHandler(Log::Level level, std::string_view tag, std::string_view msg) noexcept {
    int prio;
    switch (level) {
        using Level = Log::Level;
        case Level::kVerbose:
            prio = ANDROID_LOG_VERBOSE;
            break;
        case Level::kDebug:
            prio = ANDROID_LOG_DEBUG;
            break;
        case Level::kInfo:
            prio = ANDROID_LOG_INFO;
            break;
        case Level::kWarn:
            prio = ANDROID_LOG_WARN;
            break;
        case Level::kError:
            prio = ANDROID_LOG_ERROR;
            break;
        default:
            prio = ANDROID_LOG_UNKNOWN;
            break;
    }
    __android_log_write(prio, tag.data(), msg.data());
}

#else

// Linux print to stderr

static void DefaultLogToStderrHandler(Log::Level level, std::string_view tag, std::string_view msg) noexcept;

constinit volatile Log::LogHandler Log::sLogHandler = &DefaultLogToStderrHandler;

static void DefaultLogToStderrHandler(Log::Level level, std::string_view tag, std::string_view msg) noexcept {
    const char* levelStr;
    switch (level) {
        using Level = Log::Level;
        case Level::kVerbose:
            levelStr = "VERBOSE";
            break;
        case Level::kDebug:
            levelStr = "DEBUG";
            break;
        case Level::kInfo:
            levelStr = "INFO";
            break;
        case Level::kWarn:
            levelStr = "WARN";
            break;
        case Level::kError:
            levelStr = "ERROR";
            break;
        default:
            levelStr = "UNKNOWN";
            break;
    }
    fmt::print(stderr, "[{}] [{}] {}\n", levelStr, tag, msg);
}

#endif


[[noreturn]] void Abort(std::string_view msg) noexcept {
    if (!msg.empty()) {
        const char oomWhenAborting[] = "Out of memory when trying to allocate memory for abort message.";
        auto* buf = reinterpret_cast<char*>(malloc(msg.size() + 1));
        size_t len;
        if (buf == nullptr) {
            len = sizeof(oomWhenAborting);
            buf = const_cast<char*>(oomWhenAborting);
        } else {
            len = msg.size();
            std::memcpy(buf, msg.data(), len);
            buf[len] = '\0';
        }
#if defined(__ANDROID__)
        __android_log_write(ANDROID_LOG_FATAL, "DEBUG", buf);
        android_set_abort_message(buf);
#else
        fmt::print(stderr, "FATAL: {}\n", buf);
#endif
    }
    ::abort();
}

} // namespace jvmplant::utils
