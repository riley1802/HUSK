cmake_minimum_required(VERSION 3.22)

project(whisper_jni)

set(CMAKE_CXX_STANDARD 17)
set(WHISPER_DIR ${CMAKE_SOURCE_DIR}/whisper)

# Get whisper.cpp version
file(READ "${WHISPER_DIR}/CMakeLists.txt" MAIN_CMAKE_CONTENT)
string(REGEX MATCH "project\\(\"whisper\\.cpp\" VERSION ([0-9]+\\.[0-9]+\\.[0-9]+)\\)" VERSION_MATCH "${MAIN_CMAKE_CONTENT}")
if(CMAKE_MATCH_1)
	set(WHISPER_VERSION ${CMAKE_MATCH_1})
else()
	set(WHISPER_VERSION "unknown")
endif()

message(STATUS "Whisper version: ${WHISPER_VERSION}")

# Source files
set(SOURCE_FILES
	${WHISPER_DIR}/src/whisper.cpp
	${CMAKE_SOURCE_DIR}/whisper_jni.cpp
)

find_library(LOG_LIB log)

include(FetchContent)

# Build the shared library
add_library(whisper_jni SHARED ${SOURCE_FILES})

FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

target_link_libraries(whisper_jni ${LOG_LIB} android ggml)
target_compile_definitions(whisper_jni PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper_jni PRIVATE WHISPER_VERSION="${WHISPER_VERSION}")

# Optimize for arm64-v8a with fp16 support
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
	target_compile_options(whisper_jni PRIVATE -march=armv8.2-a+fp16)
endif()

# Release optimizations
if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
	target_compile_options(whisper_jni PRIVATE -O3)
	target_compile_options(whisper_jni PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
	target_compile_options(whisper_jni PRIVATE -ffunction-sections -fdata-sections)
endif()

# Include paths
include_directories(${WHISPER_DIR})
include_directories(${WHISPER_DIR}/src)
include_directories(${WHISPER_DIR}/include)
include_directories(${WHISPER_DIR}/ggml/include)
include_directories(${WHISPER_DIR}/ggml/src)
include_directories(${WHISPER_DIR}/ggml/src/ggml-cpu)
