#ifndef JVMXPOSED_LOG_COMMON_H
#define JVMXPOSED_LOG_COMMON_H

#include <string>
#include <string_view>

namespace jvmplant::util {

#ifdef NDEBUG
static constexpr bool kIsDebugBuild = false;
#else
static constexpr bool kIsDebugBuild = true;
#endif

class Log {
public:
    enum class Level : int {
        kUnknown = 0,
        kVerbose = 1,
        kDebug = 2,
        kInfo = 3,
        kWarn = 5,
        kError = 6
    };
    using LogHandler = void (*)(Level level, std::string_view tag, std::string_view msg) noexcept;

private:
    static volatile LogHandler sLogHandler;
public:

    static inline void LogMessage(Level level, std::string_view tag, std::string_view msg) noexcept {
        LogHandler h = sLogHandler;
        if (h == nullptr) {
            return;
        }
        h(level, tag, msg);
    }

    static inline LogHandler GetLogHandler() noexcept {
        return sLogHandler;
    }

    static inline void SetLogHandler(LogHandler h) noexcept {
        sLogHandler = h;
    }
};

[[noreturn]] void Abort(std::string_view msg) noexcept;

} // namespace jvmplant::utils

#endif //JVMXPOSED_LOG_COMMON_H
