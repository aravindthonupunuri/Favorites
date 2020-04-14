package com.tgt.favorites.transport

import com.tgt.lists.lib.api.transport.ListUpdateRequestTO

class FavoriteListUpdateRequestTO(
    val listTitle: String? = null,
    val shortDescription: String? = null
) {
    fun toListUpdateRequestTO(): ListUpdateRequestTO {
        return ListUpdateRequestTO(listTitle, shortDescription)
    }
}
