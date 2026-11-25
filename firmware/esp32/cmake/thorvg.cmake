cmake_minimum_required(VERSION 3.16)

get_filename_component(PANEL_FIRMWARE_DIR "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)
set(THORVG_ROOT "${PANEL_FIRMWARE_DIR}/third_party/thorvg")

if(NOT EXISTS "${THORVG_ROOT}/inc/thorvg.h")
    message(FATAL_ERROR
        "Upstream ThorVG is missing. From the repository root run:\n"
        "  git clone --depth 1 --branch v1.1.4 https://github.com/thorvg/thorvg.git firmware/esp32/third_party/thorvg")
endif()

set(THORVG_SOURCES
    ${THORVG_ROOT}/src/common/tvgArray.cpp
    ${THORVG_ROOT}/src/common/tvgCompressor.cpp
    ${THORVG_ROOT}/src/common/tvgMath.cpp
    ${THORVG_ROOT}/src/common/tvgStr.cpp
    ${THORVG_ROOT}/src/renderer/tvgAccessor.cpp
    ${THORVG_ROOT}/src/renderer/tvgAnimation.cpp
    ${THORVG_ROOT}/src/renderer/tvgCanvas.cpp
    ${THORVG_ROOT}/src/renderer/tvgFill.cpp
    ${THORVG_ROOT}/src/renderer/tvgInitializer.cpp
    ${THORVG_ROOT}/src/renderer/tvgLoaderMgr.cpp
    ${THORVG_ROOT}/src/renderer/tvgPaint.cpp
    ${THORVG_ROOT}/src/renderer/tvgPicture.cpp
    ${THORVG_ROOT}/src/renderer/tvgRender.cpp
    ${THORVG_ROOT}/src/renderer/tvgSaver.cpp
    ${THORVG_ROOT}/src/renderer/tvgScene.cpp
    ${THORVG_ROOT}/src/renderer/tvgShape.cpp
    ${THORVG_ROOT}/src/renderer/tvgTaskScheduler.cpp
    ${THORVG_ROOT}/src/renderer/tvgText.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwBlendOp.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwFill.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwImage.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwMemPool.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwPostEffect.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwRaster.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwRasterCore.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwRenderer.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwRle.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwShape.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwStroke.cpp
    ${THORVG_ROOT}/src/renderer/cpu_engine/tvgSwUtil.cpp
    ${THORVG_ROOT}/src/modules/sfnt/tvgSfntLoader.cpp
    ${THORVG_ROOT}/src/modules/sfnt/tvgSfntReader.cpp
    ${THORVG_ROOT}/src/modules/sfnt/tvgTtfReader.cpp
    ${THORVG_ROOT}/src/modules/raw/tvgRawLoader.cpp
)

set(THORVG_INCLUDES
    ${PANEL_FIRMWARE_DIR}/thorvg
    ${THORVG_ROOT}/inc
    ${THORVG_ROOT}/src/common
    ${THORVG_ROOT}/src/renderer
    ${THORVG_ROOT}/src/renderer/cpu_engine
    ${THORVG_ROOT}/src/modules/sfnt
    ${THORVG_ROOT}/src/modules/raw
)
