package tech.shaffersoftworks.remotescreens.server.service

import tech.shaffersoftworks.remotescreens.shared.GridSize
import kotlinx.serialization.Serializable

@Serializable
data class CreateScreenRequest(
    val name: String,
    val grid: GridSize? = null,
    val widthDp: Int? = null,
    val heightDp: Int? = null,
)
