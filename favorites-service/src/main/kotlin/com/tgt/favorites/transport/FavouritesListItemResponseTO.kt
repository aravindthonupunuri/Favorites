package com.tgt.favorites.transport

import com.fasterxml.jackson.annotation.JsonInclude
import com.tgt.lists.lib.api.util.ItemType
import java.util.*
import javax.validation.constraints.NotNull

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FavouritesListItemResponseTO(
    @field:NotNull(message = "List item id must not be empty") val listItemId: UUID? = null,
    @field:NotNull(message = "Item type must not be empty") val itemType: ItemType? = null,
    val tcin: String? = null,
    val itemTitle: String? = null,
    val itemNote: String? = null,
    val addedTs: String? = null,
    val lastModifiedTs: String? = null
)
