package com.tgt.favorites.service

import com.tgt.favorites.transport.FavoriteListItemResponseTO
import com.tgt.lists.lib.api.service.UpdateListItemService
import com.tgt.lists.lib.api.transport.ListItemUpdateRequestTO
import com.tgt.lists.lib.api.util.GuestId
import com.tgt.lists.lib.api.util.ItemId
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateFavoriteListItemService(
    @Inject val updateListItemService: UpdateListItemService
) {
    fun updateFavoriteListItem(
        guestId: GuestId,
        locationId: Long,
        listId: UUID,
        listItemId: ItemId,
        listItemUpdateRequestTO: ListItemUpdateRequestTO
    ): Mono<FavoriteListItemResponseTO> {
        return updateListItemService.updateListItem(guestId, locationId, listId, listItemId, listItemUpdateRequestTO)
            .map { FavoriteListItemResponseTO.toFavoriteListItemResponseTO(it) }
    }
}
