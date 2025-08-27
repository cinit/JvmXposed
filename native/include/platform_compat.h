#pragma once

// Cross-platform compatibility macros for attributes and compiler-specific features

#ifdef _MSC_VER
// Microsoft Visual C++ Compiler
#define JVMPLANT_ATTRIBUTE_NO_STACK_PROTECTOR
#define JVMPLANT_ATTRIBUTE_VISIBILITY_DEFAULT __declspec(dllexport)
#define JVMPLANT_ATTRIBUTE_VISIBILITY_HIDDEN
#define JVMPLANT_ATTRIBUTE_VISIBILITY_PROTECTED __declspec(dllexport)

// MSVC doesn't have __attribute__ syntax, so we define empty macros
#define __attribute__(x)

// MSVC-specific pragmas for equivalent functionality
#define JVMPLANT_PRAGMA_PUSH_WARNINGS __pragma(warning(push))
#define JVMPLANT_PRAGMA_POP_WARNINGS __pragma(warning(pop))
#define JVMPLANT_PRAGMA_DISABLE_WARNING(num) __pragma(warning(disable : num))

#elif defined(__GNUC__) || defined(__clang__)
// GCC or Clang Compiler
#define JVMPLANT_ATTRIBUTE_NO_STACK_PROTECTOR __attribute__((no_stack_protector))
#define JVMPLANT_ATTRIBUTE_VISIBILITY_DEFAULT __attribute__((visibility("default")))
#define JVMPLANT_ATTRIBUTE_VISIBILITY_HIDDEN __attribute__((visibility("hidden")))
#define JVMPLANT_ATTRIBUTE_VISIBILITY_PROTECTED __attribute__((visibility("protected")))

// GCC/Clang pragma support
#define JVMPLANT_PRAGMA_PUSH_WARNINGS _Pragma("GCC diagnostic push")
#define JVMPLANT_PRAGMA_POP_WARNINGS _Pragma("GCC diagnostic pop")
#define JVMPLANT_PRAGMA_DISABLE_WARNING(name) _Pragma("GCC diagnostic ignored \"" #name "\"")

#else
// Unknown compiler - define empty macros
#define JVMPLANT_ATTRIBUTE_NO_STACK_PROTECTOR
#define JVMPLANT_ATTRIBUTE_VISIBILITY_DEFAULT
#define JVMPLANT_ATTRIBUTE_VISIBILITY_HIDDEN
#define JVMPLANT_ATTRIBUTE_VISIBILITY_PROTECTED
#define __attribute__(x)

#define JVMPLANT_PRAGMA_PUSH_WARNINGS
#define JVMPLANT_PRAGMA_POP_WARNINGS
#define JVMPLANT_PRAGMA_DISABLE_WARNING(x)
#endif
