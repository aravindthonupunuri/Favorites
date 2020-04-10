package com.tgt.favorites.transport

import com.tgt.lists.lib.api.transport.ListResponseTO

fun toFavouritesListResponse(listResponseTO: ListResponseTO): FavouritesListResponseTO {
    return FavouritesListResponseTO(listId = listResponseTO.listId, channel = listResponseTO.channel, listType = listResponseTO.listType, listTitle = listResponseTO.listTitle, shortDescription = listResponseTO.shortDescription, defaultList = listResponseTO.defaultList, addedTs = listResponseTO.addedTs, lastModifiedTs = listResponseTO.lastModifiedTs)
}
