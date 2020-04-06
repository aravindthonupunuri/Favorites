package com.tgt.favorites.service

import com.tgt.lists.lib.api.service.CreateListItemService
import com.tgt.lists.lib.api.service.CreateListService
import com.tgt.lists.lib.api.transport.ListItemRequestTO
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.transport.ListRequestTO
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateFavoriteListItemService(
    @Inject private val getDefaultFavoriteListService: GetDefaultFavoriteListService,
    @Inject private val createListService: CreateListService,
    @Inject private val createListItemService: CreateListItemService
) {
    fun createFavoriteItem(
        guestId: String,
        locationId: Long,
        listItemRequestTO: ListItemRequestTO
    ): Mono<ListItemResponseTO> {
        return getDefaultFavoriteListService.getDefaultList(guestId, locationId)
            .switchIfEmpty {
                createListService.createList(guestId, ListRequestTO(LIST_CHANNEL.WEB, "list-title", locationId, defaultList = true))
    }
            .flatMap { createListItemService.createListItem(guestId, it.listId!!, locationId, listItemRequestTO) }
    }
}
