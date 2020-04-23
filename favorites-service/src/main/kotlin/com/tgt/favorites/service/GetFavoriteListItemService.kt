package com.tgt.favorites.service

import com.tgt.favorites.domain.ListItemHydrationManager
import com.tgt.favorites.transport.FavoriteListItemResponseTO
import com.tgt.lists.lib.api.service.GetListItemService
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetFavoriteListItemService(
    @Inject val getListItemService: GetListItemService,
    @Inject val listItemHydrationManager: ListItemHydrationManager
) {
    fun getListItem(
        guestId: String,
        locationId: Long,
        listId: UUID,
        listItemId: UUID
    ): Mono<FavoriteListItemResponseTO> {
        return getListItemService.getListItemService(guestId, locationId, listId, listItemId)
            .flatMap { listItemHydrationManager.getItemDetail(locationId, listOf(it)).map { it.first() } }
    }
}
