package com.tgt.favorites.service

import com.tgt.favorites.transport.FavoriteListItemRequestTO
import com.tgt.favorites.transport.FavoriteListItemResponseTO
import com.tgt.lists.lib.api.service.CreateListItemService
import com.tgt.lists.lib.api.util.GuestId
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateFavoriteListItemService(
    @Inject val createListItemService: CreateListItemService
) {
        fun createListItem(
            guestId: GuestId,
            listId: UUID,
            locationId: Long,
            favoriteListItemRequestTO: FavoriteListItemRequestTO
        ): Mono<FavoriteListItemResponseTO> {
            return createListItemService.createListItem(guestId, listId, locationId, favoriteListItemRequestTO.toListItemRequestTO())
                .map { FavoriteListItemResponseTO.toFavoriteListItemResponseTO(it) }
        }
}
