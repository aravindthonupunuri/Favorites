package service

import com.tgt.favorites.service.GetFavoritesTcinService
import com.tgt.favorites.transport.GuestFavoritesResponseTO
import com.tgt.favorites.transport.ListItemDetailsTO
import com.tgt.lists.cart.CartClient
import com.tgt.lists.lib.api.domain.ContextContainerManager
import com.tgt.lists.lib.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.lib.api.exception.BadRequestException
import com.tgt.lists.lib.api.persistence.GuestPreferenceRepository
import com.tgt.lists.lib.api.service.GetAllListService
import com.tgt.lists.lib.api.service.GetListService
import com.tgt.lists.lib.api.transport.ListGetAllResponseTO
import com.tgt.lists.lib.api.transport.ListItemResponseTO
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

    def setup() {
        cartClient = Mock(CartClient)
        getAllListService = Mock(GetAllListService)
        getListService = Mock(GetListService)
        guestPreferenceRepository = Mock(GuestPreferenceRepository)
        guestPreferenceSortOrderManager = new GuestPreferenceSortOrderManager(guestPreferenceRepository)
        contextContainerManager = new ContextContainerManager()
        getFavoritesTcinService = new GetFavoritesTcinService(getAllListService)
    }

    def "test getTcinsFromFavouritesService integrity"() {

        given:
        UUID listId1 = UUID.randomUUID()
        UUID listId2 = UUID.randomUUID()
        UUID listItemId1 = UUID.randomUUID()
        UUID listItemId2 = UUID.randomUUID()

        ListItemDetailsTO listItemDetails1TO = new ListItemDetailsTO(listId1, "list1", listItemId1)
        ListItemDetailsTO listItemDetails2TO = new ListItemDetailsTO(listId2, "list2", listItemId1)
        ListItemDetailsTO listItemDetails3TO = new ListItemDetailsTO(listId1, "list1", listItemId2)
        ListItemDetailsTO listItemDetails4TO = new ListItemDetailsTO(listId2, "list2", listItemId2)



        ListItemResponseTO listItemResponse1TO = new ListItemResponseTO(listItemId1, null, "abcd", "item1", null, null, null, null, null, null, null, 0, null, null, null, null, null, null)
        ListItemResponseTO listItemResponse2TO = new ListItemResponseTO(listItemId2, null, "abcde", "item2", null, null, null, null, null, null, null, 0, null, null, null, null, null, null)
        ListItemResponseTO listItemResponse3TO = new ListItemResponseTO(listItemId1, null, "abcd", "item3", null, null, null, null, null, null, null, 0, null, null, null, null, null, null)
        ListItemResponseTO listItemResponse4TO = new ListItemResponseTO(listItemId2, null, "abcde", "item4", null, null, null, null, null, null, null, 0, null, null, null, null, null, null)

        ListGetAllResponseTO listGetAllResponse1TO = new ListGetAllResponseTO(listId1, UUID.randomUUID(), LIST_CHANNEL.WEB, "SHOPPING", "list1", false, "dd", "1", null, null, null, 100, 1, 2, 3, [listItemResponse1TO, listItemResponse2TO], null)
        ListGetAllResponseTO listGetAllResponse2TO = new ListGetAllResponseTO(listId2, UUID.randomUUID(), LIST_CHANNEL.WEB, "SHOPPING", "list2", false, "dd", "1", null, null, null, 100, 1, 2, 3, [listItemResponse3TO, listItemResponse4TO], null)

        when:
        List<GuestFavoritesResponseTO> favouritesTcinResponsesTO = getFavoritesTcinService.getFavoritesTcin("1234", "abcd,abcde").block()

        then:

        1 * getAllListService.getAllListsForUser(_, _) >> Mono.just([listGetAllResponse1TO, listGetAllResponse2TO])

        favouritesTcinResponsesTO[0].tcin == "abcd"
        favouritesTcinResponsesTO[0].listItemDetails[0] == listItemDetails1TO
        favouritesTcinResponsesTO[0].listItemDetails[1] == listItemDetails2TO
        favouritesTcinResponsesTO[1].tcin == "abcde"
        favouritesTcinResponsesTO[1].listItemDetails[0] == listItemDetails3TO
        favouritesTcinResponsesTO[1].listItemDetails[1] == listItemDetails4TO

    }

    def "test getTcinsFromFavouritesService if ticn is not present in lists"() {

        given:
        UUID listId1 = UUID.randomUUID()
        UUID listId2 = UUID.randomUUID()
        UUID listItemId1 = UUID.randomUUID()
        UUID listItemId2 = UUID.randomUUID()

        ListItemDetailsTO listItemDetails1TO = new ListItemDetailsTO(listId1, "list1", listItemId1)
        ListItemDetailsTO listItemDetails2TO = new ListItemDetailsTO(listId2, "list2", listItemId1)
        ListItemDetailsTO listItemDetails3TO = new ListItemDetailsTO(listId1, "list1", listItemId2)
        ListItemDetailsTO listItemDetails4TO = new ListItemDetailsTO(listId2, "list2", listItemId2)



        ListItemResponseTO listItemResponse1TO = new ListItemResponseTO(listItemId1, null, "abcd", "item1", null, null, null, null, null, null, null, 0, null, null, null, null, null, null)
        ListItemResponseTO listItemResponse2TO = new ListItemResponseTO(listItemId2, null, "abcde", "item2", null, null, null, null, null, null, null, 0, null, null, null, null, null, null)
        ListItemResponseTO listItemResponse3TO = new ListItemResponseTO(listItemId1, null, "abcd", "item3", null, null, null, null, null, null, null, 0, null, null, null, null, null, null)
        ListItemResponseTO listItemResponse4TO = new ListItemResponseTO(listItemId2, null, "abcde", "item4", null, null, null, null, null, null, null, 0, null, null, null, null, null, null)

        ListGetAllResponseTO listGetAllResponse1TO = new ListGetAllResponseTO(listId1, UUID.randomUUID(), LIST_CHANNEL.WEB, "SHOPPING", "list1", false, "dd", "1", null, null, null, 100, 1, 2, 3, [listItemResponse1TO, listItemResponse2TO], null)
        ListGetAllResponseTO listGetAllResponse2TO = new ListGetAllResponseTO(listId2, UUID.randomUUID(), LIST_CHANNEL.WEB, "SHOPPING", "list2", false, "dd", "1", null, null, null, 100, 1, 2, 3, [listItemResponse3TO, listItemResponse4TO], null)

        when:
        List<GuestFavoritesResponseTO> favouritesTcinResponsesTO = getFavoritesTcinService.getFavoritesTcin("1234", "abcdf,abcde").block()

        then:
        1 * getAllListService.getAllListsForUser(_, _) >> Mono.just([listGetAllResponse1TO, listGetAllResponse2TO])

        favouritesTcinResponsesTO[0].tcin == "abcdf"
        favouritesTcinResponsesTO[0].listItemDetails == []
        favouritesTcinResponsesTO[1].tcin == "abcde"
        favouritesTcinResponsesTO[1].listItemDetails[1] == listItemDetails4TO

    }

    def "test getTcinsFromFavouritesService if there are no pending items in a list"() {

        given:
        UUID listId1 = UUID.randomUUID()
        ListGetAllResponseTO listGetAllResponse1TO = new ListGetAllResponseTO(listId1, UUID.randomUUID(), LIST_CHANNEL.WEB, "SHOPPING", "list1", false, "dd", "1", null, null, null, 100, 1, 2, 3, null, null)
        when:
        List<GuestFavoritesResponseTO> favouritesTcinResponsesTO = getFavoritesTcinService.getFavoritesTcin("1234", "abcdf,abcde").block()

        then:
        1 * getAllListService.getAllListsForUser(_, _) >> Mono.just([listGetAllResponse1TO])

        favouritesTcinResponsesTO[0].tcin == "abcdf"
        favouritesTcinResponsesTO[0].listItemDetails == []
    }

    def "if tcin count exceeds 28 "() {

        String str = "abc,"
        String tcinString = ""
        for (int i in 0 .. 30)
            { tcinString = tcinString.plus(str) }

        when:
        getFavoritesTcinService.getFavoritesTcin("1234", tcinString).block()

        then:
        thrown BadRequestException
    }


}
