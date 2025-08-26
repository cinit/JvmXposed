//
// Created by sulfate on 2025-08-26.
//

#ifndef JVMXPOSED_DEBUG_UTILS_H
#define JVMXPOSED_DEBUG_UTILS_H

#include <cstdint>

namespace jvmplant {

bool IsDebuggerAttached();

bool WaitForDebuggerMillis(uint64_t maxWaitMillis);

} // namespace jvmplant

#endif // JVMXPOSED_DEBUG_UTILS_H
