#include <string>
#include <string_view>

#include <jvmti.h>

#include "../include/jvmti.h"

#ifndef JVMTI_ERROR_STRINGS_H
#define JVMTI_ERROR_STRINGS_H

namespace jvmplant {

std::string JvmtiErrorToSting(jvmtiError err);

}

#endif // JVMTI_ERROR_STRINGS_H
