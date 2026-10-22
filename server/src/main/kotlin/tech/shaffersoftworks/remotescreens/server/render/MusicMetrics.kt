package tech.shaffersoftworks.remotescreens.server.render

/** Layout metrics for the Music Assistant full page (RPI MusicAssistantScreen proportions). */
internal object MusicMetrics {
    const val BASE_W = 900f
    const val BASE_H = 600f
    const val WIDE_BREAKPOINT_DP = 720f
    const val PAD_DP = 16f
    const val CARD_PAD_DP = 14f
    const val CARD_CORNER_DP = 12f
    const val COLUMN_GAP_DP = 16f
    const val SECTION_GAP_DP = 14f
    const val ROW_GAP_DP = 6f
    const val CHIP_GAP_DP = 6f
    /** Album art on the now-playing card (RPI `NowPlayingSection`). */
    const val ART_DP = 120f
    /** Now-playing card: art beside title, album, times, and transport icons. */
    const val NOW_CARD_H_DP = 200f
    /** Cap so Browse still gets the lower left column on a short page. */
    const val NOW_CARD_MAX_SHARE = 0.45f
    const val TRANSPORT_DP = 40f
    const val TRANSPORT_ICON_DP = 24f
    const val ART_CORNER_DP = 8f
    const val THUMB_DP = 40f
    const val THUMB_CORNER_DP = 8f
    const val KEY_DP = 44f
    const val CHIP_H_DP = 36f
    const val ROW_H_DP = 52f
    const val SEGMENT_H_DP = 40f
    const val PROGRESS_H_DP = 4f
    const val PROGRESS_STEPS = 40
    const val VOLUME_STEPS = 20
    const val VOLUME_PERCENT = 100
    const val VOLUME_W_DP = 100f
    const val TIME_GAP_DP = 8f
    const val MAX_BROWSE_ROWS = 40
    const val MAX_QUEUE_ROWS = 30
    const val CARD_FILL_ALPHA = 0.18f
    const val NOW_PLAYING_FILL_ALPHA = 0.22f
    const val ROW_CURRENT_ALPHA = 0.24f
    const val ROW_OTHER_ALPHA = 0.14f
    const val SECONDS_PER_MINUTE = 60
    /** Minimum now-playing progress track when the meta column is tight. */
    const val PROGRESS_BAR_MIN_W_DP = 120f
    const val PROGRESS_TIME_GAP_DP = 4f
    const val BROWSE_TITLE_GAP_DP = 10f
    const val LISTEN_SEGMENT_GAP_DP = 10f
    /** Floor for the native search overlay; the slot also gets an explicit size so it can be measured. */
    const val SEARCH_MIN_H_DP = 160f
    const val KEY_ICON_SHARE = 0.5f
    const val ROW_CORNER_DP = 10f
    const val ROW_PAD_H_DP = 8f
    const val ROW_PAD_V_DP = 6f
    const val ROW_THUMB_GAP_DP = 10f
    const val ALPHA_BYTE_MAX = 255f
    const val RGB_MASK = 0x00FFFFFF.toInt()
    const val ALPHA_SHIFT = 24
}
