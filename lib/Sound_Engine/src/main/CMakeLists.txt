cmake_minimum_required(VERSION 3.10.2)

# DSPark is header-only, so we just need to include its directory
# You may need to adjust this path based on how you integrate DSPark
set(DSPARK_DIR "${CMAKE_CURRENT_SOURCE_DIR}/dspark")

# Create a shared library
add_library( # Sets the name of the library.
            soundengine

            # Sets the library as a shared library.
            SHARED

            # Provides a relative path to your source file(s).
            native-lib.cpp )

# Include directories for headers
target_include_directories(soundengine
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${DSPARK_DIR}
        )

# Find and link the log library
find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Links the target library to the log library
# included in the NDK.
target_link_libraries( # Specifies the target library.
                       soundengine

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )