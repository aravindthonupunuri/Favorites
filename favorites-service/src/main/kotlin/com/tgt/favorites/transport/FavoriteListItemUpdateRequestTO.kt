package com.tgt.favorites.transport

import com.tgt.lists.lib.api.transport.ListItemUpdateRequestTO

class FavoriteListItemUpdateRequestTO(
    val itemTitle: String,
    val itemNote: String
) {
    fun toListItemUpdateRequestTO(): ListItemUpdateRequestTO {
        return ListItemUpdateRequestTO(itemTitle = itemTitle, itemNote = itemNote)
    }
}
