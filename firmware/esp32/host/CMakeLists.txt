cmake_minimum_required(VERSION 3.16)
project(panel_host LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(${CMAKE_CURRENT_LIST_DIR}/../cmake/thorvg.cmake)

add_library(thorvg STATIC ${THORVG_SOURCES})
target_include_directories(thorvg PUBLIC ${THORVG_INCLUDES})
target_compile_options(thorvg PRIVATE -fexceptions -Wno-unused-parameter -Wno-missing-field-initializers)

set(PANEL_DIR ${CMAKE_CURRENT_LIST_DIR}/../components/panel)
add_library(panel STATIC
    ${PANEL_DIR}/src/document.cpp
    ${PANEL_DIR}/src/paint.cpp
    ${PANEL_DIR}/src/protocol.cpp
)
target_include_directories(panel PUBLIC ${PANEL_DIR}/include)
target_link_libraries(panel PUBLIC thorvg)
target_compile_options(panel PRIVATE -Wall -Wextra -Werror)

add_executable(panel_host test_main.cpp)
target_link_libraries(panel_host PRIVATE panel)
target_compile_definitions(panel_host PRIVATE
    PANEL_RC_PATH="${CMAKE_CURRENT_LIST_DIR}/../testdata/panel.rc"
    PANEL_FONT_PATH="${CMAKE_CURRENT_LIST_DIR}/../fonts/Roboto-Regular.ttf"
    PANEL_PPM_PATH="${CMAKE_CURRENT_BINARY_DIR}/panel.ppm"
)
target_compile_options(panel_host PRIVATE -Wall -Wextra -Werror)
