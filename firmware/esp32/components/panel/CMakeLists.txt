include(${CMAKE_CURRENT_LIST_DIR}/../../cmake/thorvg.cmake)

idf_component_register(
        SRCS
        "src/document.cpp"
        "src/paint.cpp"
        "src/protocol.cpp"
        ${THORVG_SOURCES}
    INCLUDE_DIRS
        "include"
        ${THORVG_INCLUDES}
    EMBED_FILES
        "${CMAKE_CURRENT_LIST_DIR}/../../fonts/Roboto-Regular.ttf"
)

target_compile_options(${COMPONENT_LIB} PRIVATE -fexceptions -Wno-unused-parameter -Wno-missing-field-initializers)
set_source_files_properties(
        ${THORVG_SOURCES}
        PROPERTIES COMPILE_OPTIONS "-Wno-error=format;-Wno-format;-Wno-error=unknown-pragmas;-Wno-unknown-pragmas;-Wno-error=type-limits"
)
