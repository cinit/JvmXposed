cmake_minimum_required(VERSION 3.20)

project(JvmXposed)

enable_language(CXX C)

macro(SET_OPTION option value)
    set(${option} ${value} CACHE INTERNAL "" FORCE)
endmacro()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_C_STANDARD 11)

# keep this synchronized with the ones in build.gradle.kts
set(CMAKE_CXX_STANDARD 20)
# C standard should be 18 by default in build.gradle.kts, but we cmake 3.22.1 doesn't support it
set(CMAKE_C_STANDARD 11)

#SET_OPTION(DOBBY_GENERATE_SHARED OFF)
#SET_OPTION(Plugin.SymbolResolver ON)
#add_subdirectory(../libs/Dobby dobby)

# =============================================================================
# Cross-Platform Compiler Configuration
# =============================================================================

# Detect compiler and set appropriate flags
if (MSVC)
    # Microsoft Visual C++ Compiler
    message(STATUS "Configuring for MSVC compiler")

    # Warning configuration for MSVC
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /W4")  # High warning level
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /we4716")  # Error on missing return (equivalent to -Werror=return-type)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /wd4996")  # Disable deprecated warnings (similar to -Wno-invalid-offsetof)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /wd4068")  # Disable unknown pragma warnings

    # Frame pointer configuration
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /Oy-")  # Keep frame pointers (equivalent to -fno-omit-frame-pointer)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} /Oy-")

    # Runtime library configuration
    if (CMAKE_BUILD_TYPE MATCHES Debug)
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /MTd")  # Static debug runtime
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} /MTd")
    else ()
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /MT")   # Static release runtime
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} /MT")
    endif ()

    # Optimization flags for release builds
    if (NOT CMAKE_BUILD_TYPE MATCHES Debug)
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /O2")   # Optimize for speed
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /GL")   # Whole program optimization
        set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} /LTCG")  # Link-time code generation
    endif ()

    # MSVC-specific definitions
    add_definitions(-D_CRT_SECURE_NO_WARNINGS)  # Disable CRT security warnings
    add_definitions(-DNOMINMAX)  # Prevent Windows.h from defining min/max macros
    add_definitions(-DWIN32_LEAN_AND_MEAN)  # Reduce Windows header bloat

    # Symbol visibility (MSVC uses __declspec instead of -fvisibility)
    # We'll handle exports manually to avoid auto-generation issues
    set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS OFF)

elseif ("${CMAKE_CXX_COMPILER_ID}" MATCHES "Clang")
    # Clang Compiler
    message(STATUS "Configuring for Clang compiler")

    # Clang-specific warning flags
    set(CLANG_CXX_EXTRA_OPT "-Werror=unknown-warning-option -Werror=format-invalid-specifier -Werror=call-to-pure-virtual-from-ctor-dtor")
    set(CLANG_C_EXTRA_OPT "-Werror=format-invalid-specifier")

    # Base warning and error flags
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Werror=return-type -Wno-invalid-offsetof")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${CLANG_CXX_EXTRA_OPT}")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${CLANG_C_EXTRA_OPT}")

    # Position Independent Code (not needed on Windows)
    if (NOT WIN32)
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC")
    endif ()

    # Frame pointer configuration
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-omit-frame-pointer")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fno-omit-frame-pointer")

    # Virtual destructor warnings
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Werror=non-virtual-dtor -Werror=delete-non-virtual-dtor")

    # Symbol visibility
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=protected")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=protected")
    if (NOT CMAKE_BUILD_TYPE MATCHES Debug)
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility-inlines-hidden")
    endif ()

    # Static linking of standard libraries (Unix/Linux only)
    if (UNIX AND NOT APPLE)
        set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-libstdc++ -static-libgcc")
    endif ()

elseif ("${CMAKE_CXX_COMPILER_ID}" MATCHES "GNU")
    # GCC Compiler
    message(STATUS "Configuring for GCC compiler")

    # Base warning and error flags
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Werror=return-type -Wno-invalid-offsetof")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Werror=return-type")

    # Position Independent Code (not needed on Windows)
    if (NOT WIN32)
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC")
    endif ()

    # Frame pointer configuration
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-omit-frame-pointer")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fno-omit-frame-pointer")

    # Virtual destructor warnings
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Werror=non-virtual-dtor -Werror=delete-non-virtual-dtor")

    # Symbol visibility
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=protected")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=protected")
    if (NOT CMAKE_BUILD_TYPE MATCHES Debug)
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility-inlines-hidden")
    endif ()

    # Static linking of standard libraries (Unix/Linux only)
    if (UNIX AND NOT APPLE)
        set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-libstdc++ -static-libgcc")
    endif ()

else ()
    # Unknown compiler - use minimal configuration
    message(WARNING "Unknown compiler: ${CMAKE_CXX_COMPILER_ID}. Using minimal configuration.")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-omit-frame-pointer")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fno-omit-frame-pointer")
endif ()

# =============================================================================
# Platform-Specific Linker Configuration
# =============================================================================

if (UNIX AND NOT APPLE)
    # Linux-specific linker flags
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--no-allow-shlib-undefined,--no-undefined")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,defs,-z,now,-z,relro")

    # Garbage collection for release builds
    if (NOT CMAKE_BUILD_TYPE MATCHES Debug)
        set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")
    endif ()

elseif (APPLE)
    # macOS-specific linker flags
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-undefined,error")

    # Garbage collection for release builds
    if (NOT CMAKE_BUILD_TYPE MATCHES Debug)
        set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-dead_strip")
    endif ()

elseif (WIN32)
    # Windows-specific linker flags
    if (NOT CMAKE_BUILD_TYPE MATCHES Debug)
        set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} /OPT:REF /OPT:ICF")
    endif ()
endif ()

# =============================================================================
# External Dependencies
# =============================================================================

add_subdirectory(../libs/fmt fmt)

target_compile_definitions(fmt-header-only INTERFACE FMT_STATIC_THOUSANDS_SEPARATOR=1)

# set -fomit-frame-pointer for Dobby if ANDROID_ABI is armeabi-v7a
#if ("${ANDROID_ABI}" STREQUAL "armeabi-v7a")
#    target_compile_options(dobby PRIVATE -fomit-frame-pointer)
#endif ()

# =============================================================================
# Target Definition
# =============================================================================

add_library(
        jvmplant
        SHARED
        utils/jni_utils.cc
        utils/log_common.cc
        jni/jvmplant_bridge.cc
        openjdkvm/openjdkvm_hook_impl.cc
        openjdkvm/jvmti_error_strings.cc
        platform/debug_utils.cc
)

target_include_directories(
        jvmplant
        PUBLIC include
        PRIVATE .
)

target_link_libraries(
        jvmplant
        PRIVATE
        fmt-header-only
        # dobby
)

# =============================================================================
# Platform-Specific Library Dependencies
# =============================================================================

if (WIN32)
    # Windows-specific libraries
    target_link_libraries(jvmplant PRIVATE
            kernel32
            user32
            advapi32
            shell32
    )
elseif (UNIX)
    # Unix-specific libraries
    target_link_libraries(jvmplant PRIVATE
            ${CMAKE_DL_LIBS}  # Dynamic loading library (libdl)
            pthread           # POSIX threads
    )
endif ()

# =============================================================================
# Compiler-Specific Target Properties
# =============================================================================

if (MSVC)
    # MSVC-specific target properties
    set_target_properties(jvmplant PROPERTIES
            COMPILE_PDB_NAME "jvmplant"
            COMPILE_PDB_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}"
            # Disable automatic DLL export generation that's causing issues
            WINDOWS_EXPORT_ALL_SYMBOLS OFF
    )

    # Generate debug information for release builds
    if (NOT CMAKE_BUILD_TYPE MATCHES Debug)
        set_target_properties(jvmplant PROPERTIES
                LINK_FLAGS "/DEBUG /PDBALTPATH:%_PDB%"
        )
    endif ()
endif ()

# =============================================================================
# Build Information
# =============================================================================

message(STATUS "Build configuration:")
message(STATUS "  Compiler: ${CMAKE_CXX_COMPILER_ID}")
message(STATUS "  Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "  C++ Standard: ${CMAKE_CXX_STANDARD}")
message(STATUS "  C Standard: ${CMAKE_C_STANDARD}")
message(STATUS "  Platform: ${CMAKE_SYSTEM_NAME}")
message(STATUS "  Architecture: ${CMAKE_SYSTEM_PROCESSOR}")
