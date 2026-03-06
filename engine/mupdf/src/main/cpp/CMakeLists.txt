cmake_minimum_required(VERSION 3.22.1)
project("mupdf_bridge")

set(MUPDF_PREBUILT_ROOT "${CMAKE_SOURCE_DIR}/../prebuilt")
set(MUPDF_INCLUDE_DIR "${MUPDF_PREBUILT_ROOT}/include")
set(MUPDF_LIB_PATH "${MUPDF_PREBUILT_ROOT}/${ANDROID_ABI}/libmupdf.a")

if(EXISTS "${MUPDF_INCLUDE_DIR}/mupdf/fitz.h" AND EXISTS "${MUPDF_LIB_PATH}")
  message(STATUS "MuPDF prebuilts found: linking real MuPDF")
  add_library(mupdf STATIC IMPORTED GLOBAL)
  set_target_properties(mupdf PROPERTIES
    IMPORTED_LOCATION "${MUPDF_LIB_PATH}"
  )
  target_include_directories(mupdf INTERFACE "${MUPDF_INCLUDE_DIR}")
  set(USE_MUPDF 1)
else()
  message(STATUS "MuPDF prebuilts not found: using stub implementation")
endif()

add_library(mupdf_bridge SHARED mupdf-bridge.cpp)
if(USE_MUPDF)
  target_compile_definitions(mupdf_bridge PRIVATE USE_MUPDF)
  target_include_directories(mupdf_bridge PRIVATE "${MUPDF_INCLUDE_DIR}")
  target_link_libraries(mupdf_bridge PRIVATE mupdf z m)
endif()

# 16 KB page alignment for Android 15+ / Google Play (Nov 2025+)
target_link_options(mupdf_bridge PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(mupdf_bridge PRIVATE android log)
