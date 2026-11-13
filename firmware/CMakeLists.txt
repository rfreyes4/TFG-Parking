cmake_minimum_required(VERSION 3.16)
project(monitor_ld2450_c LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Se descarga y compila raylib de forma local en build/ sin instalarlo
# globalmente ni modificar otras partes del proyecto.
include(FetchContent)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_GAMES OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    raylib
    URL https://github.com/raysan5/raylib/archive/refs/tags/5.5.tar.gz
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE
)
FetchContent_MakeAvailable(raylib)

add_executable(monitor_ld2450_c monitor_ld2450.c)
target_compile_options(monitor_ld2450_c PRIVATE -Wall -Wextra -O2)
target_link_libraries(monitor_ld2450_c PRIVATE raylib m)
