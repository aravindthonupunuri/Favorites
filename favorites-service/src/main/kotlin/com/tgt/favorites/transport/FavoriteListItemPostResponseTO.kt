package com.tgt.favorites.transport

import com.fasterxml.jackson.annotation.JsonInclude
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import java.util.*
import javax.validation.constraints.NotNull

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FavoriteListItemPostResponseTO(
    @field:NotNull(message = "List item id must not be empty") val listItemId: UUID? = null,
    @field:NotNull(message = "Item type must not be empty") val itemType: ItemType? = null,
    @field:NotNull(message = "channel must not be empty") val channel: LIST_CHANNEL? = null,
    val tcin: String? = null,
    val itemTitle: String? = null,
    val itemNote: String? = null,
    val addedTs: String? = null,
    val lastModifiedTs: String? = null
) {
    constructor(
        listItemResponseTO: ListItemResponseTO
    ) : this(
        listItemId = listItemResponseTO.listItemId,
        itemType = listItemResponseTO.itemType,
        channel = listItemResponseTO.channel,
        tcin = listItemResponseTO.tcin,
        itemTitle = listItemResponseTO.itemTitle,
        itemNote = listItemResponseTO.itemNote,
        addedTs = listItemResponseTO.addedTs,
        lastModifiedTs = listItemResponseTO.lastModifiedTs
    )

    companion object {
        @JvmStatic
        fun toFavoriteListItemResponseTO(baseList: ListItemResponseTO): FavoriteListItemPostResponseTO {
            return FavoriteListItemPostResponseTO(baseList)
        }
    }
}
