//
// Created by sulfate on 2025-08-26.
//

#include "debug_utils.h"

#include <cstdint>
#include <cstring>
#include <string>
#include <string_view>
#include <vector>

#ifdef __WIN32
#include <windows.h>
#else
#include <fcntl.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#endif

namespace jvmplant {

bool IsDebuggerAttached() {
#ifdef __WIN32
    return IsDebuggerPresent() != 0;
#else
    // check /proc/self/status TracerPid field
    FILE* statusFile = fopen("/proc/self/status", "r");
    if (statusFile == nullptr) {
        return false;
    }
    char line[256];
    while (fgets(line, sizeof(line), statusFile) != nullptr) {
        if (strncmp(line, "TracerPid:", 10) == 0) {
            // parse the TracerPid value
            int tracerPid = atoi(line + 10);
            fclose(statusFile);
            return tracerPid != 0;
        }
    }
    // unexpected, no TracerPid field found
    fclose(statusFile);
    return false;
#endif
}

bool WaitForDebuggerMillis(uint64_t maxWaitMillis) {
    // wait 20ms each loop, 0 for don't wait
    const uint64_t waitInterval = 20;
    uint64_t waited = 0;
    while (true) {
        if (IsDebuggerAttached()) {
            return true;
        }
        if (maxWaitMillis > 0 && waited >= maxWaitMillis) {
            return false;
        }
#ifdef __WIN32
        Sleep(static_cast<DWORD>(waitInterval));
#else
        usleep(waitInterval * 1000);
#endif
        waited += waitInterval;
    }
}

} // namespace jvmplant
