package com.tgt.favorites.service

import com.tgt.lists.lib.api.service.GetListItemService
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetShoppingListItemService(
    @Inject val getListItemService: GetListItemService
) {
    fun getListItem(
        guestId: String,
        locationId: Long,
        listId: UUID,
        listItemId: UUID
    ): Mono<ListItemResponseTO> {
        return getListItemService.getListItemService(guestId, locationId, listId, listItemId)
    }
}
