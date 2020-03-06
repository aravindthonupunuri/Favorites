package com.tgt.favorites.service

import com.tgt.lists.cart.CartClient
import com.tgt.lists.cart.transport.CartState
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.cart.types.SearchCartsFieldGroup
import com.tgt.lists.cart.types.SearchCartsFieldGroups
import com.tgt.lists.lib.api.domain.ListV2MigrationManager
import com.tgt.lists.lib.api.service.GetDefaultListService
import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.util.ItemIncludeFields
import com.tgt.lists.lib.api.util.ItemSortFieldGroup
import com.tgt.lists.lib.api.util.ItemSortOrderGroup
import reactor.core.publisher.Mono
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetDefaultShoppingListService(
    val cartClient: CartClient,
    val getDefaultListService: GetDefaultListService,
    @Inject val listV2MigrationManager: ListV2MigrationManager
) {
    fun getDefaultList(
        guestId: String,
        locationId: Long,
        startX: BigDecimal?,
        startY: BigDecimal?,
        startFloor: String?,
        sortFieldBy: ItemSortFieldGroup? = ItemSortFieldGroup.ADDED_DATE,
        sortOrderBy: ItemSortOrderGroup? = ItemSortOrderGroup.DESCENDING,
        allowExpiredItems: Boolean? = false,
        includeItems: ItemIncludeFields?
    ): Mono<ListResponseTO> {
        val fieldGroups = SearchCartsFieldGroups(arrayListOf(SearchCartsFieldGroup.CART))
        return cartClient.searchCarts(guestId = guestId, cartState = CartState.PENDING, cartType = CartType.LIST, fieldGroups = fieldGroups)
            .flatMap {
                if (it.isNullOrEmpty()) {
                    migrateListV2ForUser(guestId, locationId, startX, startY, startFloor,
                        sortFieldBy, sortOrderBy, allowExpiredItems ?: false, includeItems ?: ItemIncludeFields.ALL)
                } else {
                    getDefaultListService.getDefaultList(guestId, locationId, sortFieldBy, sortOrderBy,
                        allowExpiredItems ?: false, includeItems ?: ItemIncludeFields.ALL)
                }
            }
    }

    fun migrateListV2ForUser(
        guestId: String,
        locationId: Long,
        startX: BigDecimal?,
        startY: BigDecimal?,
        startFloor: String?,
        sortFieldBy: ItemSortFieldGroup?,
        sortOrderBy: ItemSortOrderGroup?,
        allowExpiredItems: Boolean,
        includeItems: ItemIncludeFields
    ): Mono<ListResponseTO> {
        return listV2MigrationManager.migrateListV2(guestId)
            .flatMap { result ->
                if (result) { // Lists in lists v2 migrated to lists v4
                    getDefaultList(guestId, locationId, startX, startY, startFloor, sortFieldBy, sortOrderBy, allowExpiredItems, ItemIncludeFields.PENDING)
                } else { // No lists in lists v2 to migrate to lists v4
                    Mono.empty()
                }
            }
    }
}
