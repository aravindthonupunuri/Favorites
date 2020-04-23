package com.tgt.favorites.transport

import com.fasterxml.jackson.annotation.JsonInclude
import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import java.util.*
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FavouritesListResponseTO(
    @field:NotNull(message = "List id must not be empty") val listId: UUID?,
    @field:NotNull(message = "Channel must not be empty") val channel: LIST_CHANNEL?,
    @field:NotNull(message = "List type must not be empty") val listType: String?,
    @field:NotEmpty(message = "List title must not be empty") val listTitle: String?,
    val shortDescription: String?,
    val defaultList: Boolean? = false,
    val listItems: List<FavoriteListItemResponseTO>? = null,
    val addedTs: String?,
    val lastModifiedTs: String?
) {
    constructor(listResponseTO: ListResponseTO, favoriteListItems: List<FavoriteListItemResponseTO>? = null) : this(
        listId = listResponseTO.listId, channel = listResponseTO.channel, listType = listResponseTO.listType,
        listTitle = listResponseTO.listTitle, shortDescription = listResponseTO.shortDescription,
        defaultList = listResponseTO.defaultList, listItems = favoriteListItems,
        addedTs = listResponseTO.addedTs,
        lastModifiedTs = listResponseTO.lastModifiedTs)
}
