package com.tgt.favorites.transport

import com.tgt.lists.lib.api.util.LIST_CHANNEL
import java.util.*

data class FavoriteGetAllListResponseTO(
    val listId: UUID?,
    val channel: LIST_CHANNEL? = null,
    val listType: String?,
    val listTitle: String?,
    val defaultList: Boolean = false,
    val shortDescription: String?,
    val addedTs: String? = null,
    val lastModifiedTs: String? = null,
    val maxListsCount: Int = -1,
    val totalItemsCount: Int = -1
)
