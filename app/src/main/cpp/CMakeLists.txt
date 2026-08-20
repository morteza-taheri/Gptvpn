cmake_minimum_required(VERSION 3.22.1)
project("softetherclient")

set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Find required system libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(z-lib z)

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
)

# Source files for main library
set(SOFTETHER_SOURCES
    jni/softether_jni.c
    jni/softether_core.c
)

# Create main shared library
add_library(softether SHARED ${SOFTETHER_SOURCES})

# Link libraries
target_link_libraries(softether
    ${log-lib}
    ${android-lib}
    ${z-lib}
)

# Compiler flags
target_compile_options(softether PRIVATE
    -Wall
    -Wno-unused-parameter
    -Wno-unused-variable
    -fPIC
    -O2
)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
