package service

import com.tgt.favorites.service.CreateFavoriteListItemService
import com.tgt.favorites.service.GetDefaultFavoriteListService
import com.tgt.lists.cart.CartClient
import com.tgt.lists.lib.api.domain.ContextContainerManager
import com.tgt.lists.lib.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.lib.api.persistence.GuestPreferenceRepository
import com.tgt.lists.lib.api.service.CreateListItemService
import com.tgt.lists.lib.api.service.CreateListService
import com.tgt.lists.lib.api.transport.ListItemRequestTO
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.UnitOfMeasure
import reactor.core.publisher.Mono
import spock.lang.Specification

class CreateFavoritesListItemServiceTest extends Specification {

    CreateFavoriteListItemService createFavoriteListItemService
    GetDefaultFavoriteListService getDefaultFavoriteListService
    CreateListService createListService
    CreateListItemService createListItemService
    GuestPreferenceSortOrderManager guestPreferenceSortOrderManager
    GuestPreferenceRepository guestPreferenceRepository
    CartClient cartClient
    ContextContainerManager contextContainerManager

    def setup() {
        cartClient = Mock(CartClient)
        getDefaultFavoriteListService = Mock(GetDefaultFavoriteListService)
        createListService = Mock(CreateListService)
        createListItemService = Mock(CreateListItemService)
        guestPreferenceRepository = Mock(GuestPreferenceRepository)
        guestPreferenceSortOrderManager = new GuestPreferenceSortOrderManager(guestPreferenceRepository)
        contextContainerManager = new ContextContainerManager()
        createFavoriteListItemService = new CreateFavoriteListItemService(getDefaultFavoriteListService, createListService, createListItemService)
    }

    def "test createFavoriteItem() integrity"() {

        given:
        String guestId = "1234"
        UUID listItemId = UUID.randomUUID()
        UUID listId = UUID.randomUUID()

        ListItemRequestTO listItemRequestTO = new ListItemRequestTO(ItemType.TCIN, "35446", null, null, null, UnitOfMeasure.EACHES, null, null)

        ListItemResponseTO listItemResponseTO = new ListItemResponseTO(listItemId, null, "1234", "item", null, null, UnitOfMeasure.EACHES, null, null, null, null, 0, null, null, null, null, null, null)

        ListResponseTO listResponseTO = new ListResponseTO(listId, LIST_CHANNEL.WEB, null, "list-title", null, null, null, null, null, null, null, null, null, null, null, null)

        when:
        ListItemResponseTO favouriteItemResponsesTO = createFavoriteListItemService.createFavoriteItem(guestId, 1357L, listItemRequestTO).block()

        then:

        1 * getDefaultFavoriteListService.getDefaultList(_, _, _, _, _) >> Mono.just(listResponseTO)
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

        ListItemRequestTO listItemRequestTO = new ListItemRequestTO(ItemType.TCIN, "35446", null, null, null, UnitOfMeasure.EACHES, null, null)

        ListItemResponseTO listItemResponseTO = new ListItemResponseTO(listItemId, null, "1234", "item", null, null, UnitOfMeasure.EACHES, null, null, null, null, 0, null, null, null, null, null, null)

        ListResponseTO listResponseTO = new ListResponseTO(listId, LIST_CHANNEL.WEB, null, "list-title", null, null, null, null, null, null, null, null, null, null, null, null)

        when:
        ListItemResponseTO favouriteItemResponsesTO = createFavoriteListItemService.createFavoriteItem(guestId, 1357L, listItemRequestTO).block()

        then:

        1 * getDefaultFavoriteListService.getDefaultList(_, _, _, _, _) >> Mono.empty()
        1 * createListService.createList(_, _) >> Mono.just(listResponseTO)
        1 * createListItemService.createListItem(_, _, _, _) >> Mono.just(listItemResponseTO)

        favouriteItemResponsesTO.listItemId == listItemResponseTO.listItemId
        favouriteItemResponsesTO.itemTitle == listItemResponseTO.itemTitle
        favouriteItemResponsesTO.itemNote == listItemResponseTO.itemNote
    }
}
