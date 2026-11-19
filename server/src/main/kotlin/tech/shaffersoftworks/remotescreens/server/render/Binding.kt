package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.VarValue

class Binding(
    val entityId: String?,
    val compute: (Map<String, EntityState>) -> List<VarValue>,
)
