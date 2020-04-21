package com.tgt.favorites.service

import com.tgt.favorites.transport.FavoriteListItemRequestTO
import com.tgt.favorites.transport.FavoriteListItemResponseTO
import com.tgt.favorites.transport.FavouritesListResponseTO
import com.tgt.lists.lib.api.service.CreateListItemService
import com.tgt.lists.lib.api.service.CreateListService
import com.tgt.lists.lib.api.transport.ListRequestTO
import io.micronaut.context.annotation.Value
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateFavoriteDefaultListItemService(
    @Inject private val getDefaultFavoriteListService: GetDefaultFavoriteListService,
    @Inject private val createListService: CreateListService,
    @Inject private val createListItemService: CreateListItemService,
    @Value("\${list.default-list-title}") val defaultListTitle: String = "My Favorites"
) {
    fun createFavoriteItem(
        guestId: String,
        locationId: Long,
        favoriteListItemRequestTO: FavoriteListItemRequestTO
    ): Mono<FavoriteListItemResponseTO> {
        return getDefaultFavoriteListService.getDefaultList(guestId, locationId)
            .switchIfEmpty {
                createListService.createList(guestId, ListRequestTO(favoriteListItemRequestTO.channel,
                    defaultListTitle, locationId, defaultList = true)).map { FavouritesListResponseTO(it) }
            }.flatMap { createListItemService.createListItem(guestId, it.listId!!, locationId, favoriteListItemRequestTO.toListItemRequestTO()) }
            .map { FavoriteListItemResponseTO.toFavoriteListItemResponseTO(it) }
    }
}
