package com.tgt.favorites.service

import com.tgt.favorites.api.util.ListDataProvider
import com.tgt.favorites.transport.GuestFavoritesResponseTO
import com.tgt.favorites.transport.ItemRelationshipType
import com.tgt.favorites.transport.ListItemDetailsTO
import com.tgt.lists.cart.CartClient
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.lib.api.domain.ContextContainerManager
import com.tgt.lists.lib.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.lib.api.persistence.GuestPreferenceRepository
import com.tgt.lists.lib.api.service.GetAllListService
import com.tgt.lists.lib.api.service.GetListService
import com.tgt.lists.lib.api.transport.ListGetAllResponseTO
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import reactor.core.publisher.Mono
import spock.lang.Specification

class GetTcinsFromFavouritesServiceTest extends Specification {

    GetFavoritesTcinService getFavoritesTcinService
    GetAllListService getAllListService
    GetListService getListService
    GuestPreferenceSortOrderManager guestPreferenceSortOrderManager
    GuestPreferenceRepository guestPreferenceRepository
    CartClient cartClient
    ContextContainerManager contextContainerManager
    ListDataProvider listDataProvider

    def setup() {
        cartClient = Mock(CartClient)
        getAllListService = Mock(GetAllListService)
        getListService = Mock(GetListService)
        guestPreferenceRepository = Mock(GuestPreferenceRepository)
        guestPreferenceSortOrderManager = new GuestPreferenceSortOrderManager(guestPreferenceRepository)
        contextContainerManager = new ContextContainerManager()
        getFavoritesTcinService = new GetFavoritesTcinService(getAllListService, 2)
        listDataProvider = new ListDataProvider()
    }

    def "test getFavoritesTcin() integrity"() {

        given:
        UUID listId1 = UUID.randomUUID()
        UUID listId2 = UUID.randomUUID()
        UUID listItemId1 = UUID.randomUUID()
        UUID listItemId2 = UUID.randomUUID()

        ListItemDetailsTO listItemDetails1TO = new ListItemDetailsTO(listId1, "list1", listItemId1)
        ListItemDetailsTO listItemDetails2TO = new ListItemDetailsTO(listId2, "list2", listItemId1)
        ListItemDetailsTO listItemDetails3TO = new ListItemDetailsTO(listId1, "list1", listItemId2)
        ListItemDetailsTO listItemDetails4TO = new ListItemDetailsTO(listId2, "list2", listItemId2)

        ListItemResponseTO listItemResponse1TO = listDataProvider.getListItem(listItemId1, "1234", LIST_CHANNEL.WEB, "first", null, ItemType.TCIN, ItemRelationshipType.VPC.value)
        ListItemResponseTO listItemResponse2TO = listDataProvider.getListItem(listItemId2, "5678", LIST_CHANNEL.WEB, "first", null, ItemType.TCIN, ItemRelationshipType.VPC.value)
        ListItemResponseTO listItemResponse3TO = listDataProvider.getListItem(listItemId1, "1234", LIST_CHANNEL.WEB, "first", null, ItemType.TCIN, ItemRelationshipType.VPC.value)
        ListItemResponseTO listItemResponse4TO = listDataProvider.getListItem(listItemId2, "5678", LIST_CHANNEL.WEB, "first", null, ItemType.TCIN, ItemRelationshipType.VPC.value)

        ListGetAllResponseTO listGetAllResponse1TO = new ListGetAllResponseTO(listId1, UUID.randomUUID(), LIST_CHANNEL.WEB, "SHOPPING", "list1", false, "dd", "1", null, null, null, 100, 1, 2, 3, [listItemResponse1TO, listItemResponse2TO], null)
        ListGetAllResponseTO listGetAllResponse2TO = new ListGetAllResponseTO(listId2, UUID.randomUUID(), LIST_CHANNEL.WEB, "SHOPPING", "list2", false, "dd", "1", null, null, null, 100, 1, 2, 3, [listItemResponse3TO, listItemResponse4TO], null)

        when:
        List<GuestFavoritesResponseTO> favouritesTcinResponsesTO = getFavoritesTcinService.getFavoritesTcin("1234", "1234,5678").block()

        then:

        1 * getAllListService.getAllListsForUser(_, _) >> Mono.just([listGetAllResponse1TO, listGetAllResponse2TO])

        favouritesTcinResponsesTO[0].tcin == "1234"
        favouritesTcinResponsesTO[0].listItemDetails[0] == listItemDetails1TO
        favouritesTcinResponsesTO[0].listItemDetails[1] == listItemDetails2TO
        favouritesTcinResponsesTO[1].tcin == "5678"
        favouritesTcinResponsesTO[1].listItemDetails[0] == listItemDetails3TO
        favouritesTcinResponsesTO[1].listItemDetails[1] == listItemDetails4TO
    }

    def "test getFavoritesTcin() if ticn is not present in lists"() {

        given:
        UUID listId1 = UUID.randomUUID()
        UUID listId2 = UUID.randomUUID()
        UUID listItemId1 = UUID.randomUUID()
        UUID listItemId2 = UUID.randomUUID()

        ListItemDetailsTO listItemDetails1TO = new ListItemDetailsTO(listId1, "list1", listItemId1)
        ListItemDetailsTO listItemDetails2TO = new ListItemDetailsTO(listId2, "list2", listItemId1)
        ListItemDetailsTO listItemDetails3TO = new ListItemDetailsTO(listId1, "list1", listItemId2)
        ListItemDetailsTO listItemDetails4TO = new ListItemDetailsTO(listId2, "list2", listItemId2)

        ListItemResponseTO listItemResponse1TO = listDataProvider.getListItem(listItemId1, "1234", LIST_CHANNEL.WEB, "first", null, ItemType.TCIN, ItemRelationshipType.VPC.value)
        ListItemResponseTO listItemResponse2TO = listDataProvider.getListItem(listItemId2, "5678", LIST_CHANNEL.WEB, "first", null, ItemType.TCIN, ItemRelationshipType.VPC.value)
        ListItemResponseTO listItemResponse3TO = listDataProvider.getListItem(listItemId1, "1234", LIST_CHANNEL.WEB, "first", null, ItemType.TCIN, ItemRelationshipType.VPC.value)
        ListItemResponseTO listItemResponse4TO = listDataProvider.getListItem(listItemId2, "5678", LIST_CHANNEL.WEB, "first", null, ItemType.TCIN, ItemRelationshipType.VPC.value)

        ListGetAllResponseTO listGetAllResponse1TO = new ListGetAllResponseTO(listId1, UUID.randomUUID(), LIST_CHANNEL.WEB, "SHOPPING", "list1", false, "dd", "1", null, null, null, 100, 1, 2, 3, [listItemResponse1TO, listItemResponse2TO], null)
        ListGetAllResponseTO listGetAllResponse2TO = new ListGetAllResponseTO(listId2, UUID.randomUUID(), LIST_CHANNEL.WEB, "SHOPPING", "list2", false, "dd", "1", null, null, null, 100, 1, 2, 3, [listItemResponse3TO, listItemResponse4TO], null)

        when:
        List<GuestFavoritesResponseTO> favouritesTcinResponsesTO = getFavoritesTcinService.getFavoritesTcin("1234", "123,5678").block()

        then:
        1 * getAllListService.getAllListsForUser(_, _) >> Mono.just([listGetAllResponse1TO, listGetAllResponse2TO])

        favouritesTcinResponsesTO[0].tcin == "5678"
        favouritesTcinResponsesTO[0].listItemDetails[1] == listItemDetails4TO
        favouritesTcinResponsesTO[1] == null
    }

    def "test getFavoritesTcin() if there are no pending items in a list"() {

        given:
        UUID listId1 = UUID.randomUUID()
        ListGetAllResponseTO listGetAllResponse1TO = new ListGetAllResponseTO(listId1, UUID.randomUUID(), LIST_CHANNEL.WEB, "SHOPPING", "list1", false, "dd", "1", null, null, null, 100, 1, 2, 3, null, null)
        when:
        List<GuestFavoritesResponseTO> favouritesTcinResponsesTO = getFavoritesTcinService.getFavoritesTcin("1234", "1234,5678").block()

        then:
        1 * getAllListService.getAllListsForUser(_, _) >> Mono.just([listGetAllResponse1TO])

        favouritesTcinResponsesTO == []
    }

    def "test getFavoritesTcin() when tcin count exceeds max count specified"() {

        String tcinString = "1234,5678,9876"

        when:
        getFavoritesTcinService.getFavoritesTcin("1234", tcinString).block()

        then:
        thrown BadRequestException
    }
}
