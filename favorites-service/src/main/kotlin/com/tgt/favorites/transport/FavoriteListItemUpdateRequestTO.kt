package com.tgt.favorites.transport

import com.tgt.lists.lib.api.transport.ListItemUpdateRequestTO

class FavoriteListItemUpdateRequestTO(
    val itemNote: String
) {
    fun toListItemUpdateRequestTO(): ListItemUpdateRequestTO {
        return ListItemUpdateRequestTO(itemNote = itemNote)
    }
}
