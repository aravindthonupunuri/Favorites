package com.tgt.favorites.service

import com.tgt.favorites.api.util.ListDataProvider
import com.tgt.favorites.transport.FavoriteListItemRequestTO
import com.tgt.favorites.transport.FavoriteListItemPostResponseTO
import com.tgt.favorites.transport.FavouritesListResponseTO
import com.tgt.favorites.transport.ItemRelationshipType
import com.tgt.lists.cart.CartClient
import com.tgt.lists.lib.api.domain.ContextContainerManager
import com.tgt.lists.lib.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.lib.api.persistence.GuestPreferenceRepository
import com.tgt.lists.lib.api.service.CreateListItemService
import com.tgt.lists.lib.api.service.CreateListService
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import reactor.core.publisher.Mono
import spock.lang.Specification

class CreateFavoritesListItemServiceTest extends Specification {

    CreateFavoriteDefaultListItemService createFavoriteListItemService
    GetDefaultFavoriteListService getDefaultFavoriteListService
    CreateListService createListService
    CreateListItemService createListItemService
    GuestPreferenceSortOrderManager guestPreferenceSortOrderManager
    GuestPreferenceRepository guestPreferenceRepository
    CartClient cartClient
    ContextContainerManager contextContainerManager
    ListDataProvider listDataProvider

    def setup() {
        cartClient = Mock(CartClient)
        getDefaultFavoriteListService = Mock(GetDefaultFavoriteListService)
        createListService = Mock(CreateListService)
        createListItemService = Mock(CreateListItemService)
        guestPreferenceRepository = Mock(GuestPreferenceRepository)
        guestPreferenceSortOrderManager = new GuestPreferenceSortOrderManager(guestPreferenceRepository)
        contextContainerManager = new ContextContainerManager()
        createFavoriteListItemService = new CreateFavoriteDefaultListItemService(getDefaultFavoriteListService, createListService, createListItemService, "My Favorites")
        listDataProvider = new ListDataProvider()
    }

    def "test createFavoriteItem() integrity"() {

        given:
        String guestId = "1234"
        UUID listItemId = UUID.randomUUID()
        UUID listId = UUID.randomUUID()

        FavoriteListItemRequestTO listItemRequestTO = new FavoriteListItemRequestTO(ItemType.TCIN, LIST_CHANNEL.WEB, "35446", "item-note")

        ListItemResponseTO listItemResponseTO = listDataProvider.getListItem(listItemId, "1", LIST_CHANNEL.WEB, "first", null, ItemType.TCIN, ItemRelationshipType.VPC.value)

        ListResponseTO listResponseTO = listDataProvider.getListResponseTO(listId, "PENDING", "list-title")

        when:
        FavoriteListItemPostResponseTO favouriteItemResponsesTO = createFavoriteListItemService.createFavoriteItem(guestId, 1357L, listItemRequestTO).block()

        then:

        1 * getDefaultFavoriteListService.getDefaultList(*_) >> Mono.just(new FavouritesListResponseTO(listResponseTO, null))
        1 * createListItemService.createListItem(_, _, _, _) >> Mono.just(listItemResponseTO)

        favouriteItemResponsesTO.listItemId == listItemResponseTO.listItemId
        favouriteItemResponsesTO.itemTitle == listItemResponseTO.itemTitle
        favouriteItemResponsesTO.itemNote == listItemResponseTO.itemNote
    }

    def "test createFavoriteItem() if user dont have default list"() {

        given:
        String guestId = "1234"
        UUID listItemId = UUID.randomUUID()
        UUID listId = UUID.randomUUID()

        FavoriteListItemRequestTO listItemRequestTO = new FavoriteListItemRequestTO(ItemType.TCIN, LIST_CHANNEL.WEB, "35446", "item-note")

        ListItemResponseTO listItemResponseTO = listDataProvider.getListItem(listItemId, "1", LIST_CHANNEL.WEB, "first", null, ItemType.TCIN, ItemRelationshipType.VPC.value)

        ListResponseTO listResponseTO = listDataProvider.getListResponseTO(listId, "PENDING", "list-title")

        when:
        FavoriteListItemPostResponseTO favouriteItemResponsesTO = createFavoriteListItemService.createFavoriteItem(guestId, 1357L, listItemRequestTO).block()

        then:

        1 * getDefaultFavoriteListService.getDefaultList(*_) >> Mono.empty()
        1 * createListService.createList(_, _) >> Mono.just(listResponseTO)
        1 * createListItemService.createListItem(_, _, _, _) >> Mono.just(listItemResponseTO)

        favouriteItemResponsesTO.listItemId == listItemResponseTO.listItemId
        favouriteItemResponsesTO.itemTitle == listItemResponseTO.itemTitle
        favouriteItemResponsesTO.itemNote == listItemResponseTO.itemNote
    }
}
