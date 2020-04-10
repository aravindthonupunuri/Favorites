package com.tgt.favorites.transport

import com.tgt.lists.lib.api.transport.ListItemRequestTO
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import javax.validation.constraints.NotNull

class FavoriteListItemRequestTO(
    @field:NotNull(message = "Item type must not be empty") val itemType: ItemType,
    @field:NotNull(message = "channel must not be empty") val channel: LIST_CHANNEL,
    val tcin: String,
    val itemNote: String? = null
) {
    fun toListItemRequestTO(): ListItemRequestTO {
        return ListItemRequestTO(itemType = this.itemType, channel = this.channel, itemRefId = this.tcin, tcin = this.tcin, itemNote = this.itemNote, itemTitle = null)
    }
}
