cmake_minimum_required(VERSION 3.22.1)

project(sound_engine)

include(FetchContent)

FetchContent_Declare(
        dspark
        URL https://github.com/CristianMoresi/DSPark/archive/refs/tags/v1.7.0.tar.gz
)
FetchContent_MakeAvailable(dspark)

add_library(sound_engine SHARED native-lib.cpp)

target_compile_features(sound_engine PRIVATE cxx_std_20)
target_compile_definitions(sound_engine PRIVATE DSPARK_NO_FILE_IO)
target_link_libraries(sound_engine PRIVATE dspark::dspark log)

set_target_properties(sound_engine PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
)