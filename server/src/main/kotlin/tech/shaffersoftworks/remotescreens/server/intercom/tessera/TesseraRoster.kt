package tech.shaffersoftworks.remotescreens.server.intercom.tessera

import tech.shaffersoftworks.remotescreens.shared.SignalingClient

/** Which Remote Screens stations Tessera may call, and which Tessera screens join the intercom roster. */
internal object TesseraRoster {
    fun peers(clients: List<SignalingClient>): List<TesseraPeer> = clients
        .filter { client -> TesseraProtocol.nodeOf(client.clientId) == null }
        .map { client ->
            TesseraPeer(
                node = client.clientId,
                name = displayName(client),
                online = true,
                hears = true,
                speaks = true,
            )
        }
        .sortedBy { peer -> peer.node }

    /**
     * A screen joins once Tessera has said it can be called, and it stays while it is online.
     * [callable] remembers those screens across lists, because a busy screen no longer says it can be called.
     */
    fun stations(
        rows: List<TesseraContact>,
        callable: MutableSet<String>,
    ): List<TesseraStation> {
        val listed = mutableListOf<TesseraStation>()

        for (row in rows) {
            if (row.canSound) callable += row.id

            val stays = row.availability != TesseraProtocol.AVAILABILITY_OFFLINE && row.id in callable

            if (stays) listed += TesseraStation(node = row.id, name = row.name.ifEmpty { row.id })
        }

        return listed
    }

    private fun displayName(client: SignalingClient): String {
        val name = client.displayName?.take(TesseraProtocol.NAME_LIMIT).orEmpty()

        return name.ifEmpty { client.clientId.take(TesseraProtocol.NAME_LIMIT) }
    }
}
