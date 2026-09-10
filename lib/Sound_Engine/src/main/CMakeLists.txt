cmake_minimum_required(VERSION 3.22.1)

project(SoundEngine LANGUAGES CXX)

# Set C++ standard to 20 as required by DSPark
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Include DSPark as a sub-directory.
# It's header-only but provides a nice interface target.
add_subdirectory(cpp/dspark)

# Create the soundengine shared library
add_library(soundengine SHARED
            cpp/native-lib.cpp)

# Find NDK log library
find_library(log-lib log)

# Link dependencies
# We link dspark::dspark to get the C++20 requirement and include paths
target_link_libraries(soundengine
                      PRIVATE
                      dspark::dspark
                      ${log-lib})

# Also include the 'cpp' directory so that #include "dspark/DSPark.h"
# in native-lib.cpp continues to work as expected.
target_include_directories(soundengine
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/cpp
        )
