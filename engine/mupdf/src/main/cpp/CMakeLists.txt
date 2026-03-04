cmake_minimum_required(VERSION 3.22.1)
project("mupdf_bridge")

# MuPDF static library placeholder
# In a real environment, you'd build MuPDF and link it here
# add_library(mupdf STATIC IMPORTED)
# set_target_properties(mupdf PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../prebuilt/${ANDROID_ABI}/libmupdf.a)

add_library(mupdf_bridge SHARED mupdf-bridge.cpp)

# Link standard Android libraries
target_link_libraries(mupdf_bridge
    android
    log
)
