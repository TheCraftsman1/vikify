cmake_minimum_required(VERSION 3.22.1)
project(vikify_audio LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# DSD playback (DSF/DFF). The decimator turns 1-bit DSD into float PCM for
# DsdExtractor; DST is the lossless compression SACD rips use inside DFF.
# DST is LGPL (see dsd/dst/NOTICE.md), so it is a library of its own that can
# be rebuilt or swapped without touching the rest.
add_library(vikify_dsd SHARED dsd/dsd_decimator.cpp)
target_compile_options(vikify_dsd PRIVATE -O3 -fno-fast-math -ffp-contract=off -fvisibility=hidden)
target_link_options(vikify_dsd PRIVATE "-Wl,-z,max-page-size=16384")

add_library(vikify_dst SHARED dsd/dst/decoder.c dsd/dst/jni.cpp)
target_compile_options(vikify_dst PRIVATE -O3 -fno-strict-aliasing -fvisibility=hidden)
target_link_options(vikify_dst PRIVATE "-Wl,-z,max-page-size=16384")
