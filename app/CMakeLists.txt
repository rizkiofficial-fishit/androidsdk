cmake_minimum_required(VERSION 3.18.1)
project("ryznode")

add_library(native-lib SHARED src/main/cpp/native-lib.cpp)

add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libnode/bin/${ANDROID_ABI}/libnode.so)

target_link_libraries(native-lib node log)
target_link_options(native-lib PRIVATE -Wl,-z,max-page-size=16384)
