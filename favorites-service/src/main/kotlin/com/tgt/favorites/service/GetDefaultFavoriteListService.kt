package com.tgt.favorites.service

import com.tgt.favorites.domain.ListItemHydrationManager
import com.tgt.favorites.transport.FavouritesListResponseTO
import com.tgt.lists.lib.api.service.GetDefaultListService
import com.tgt.lists.lib.api.service.transform.list_items.ListItemsTransformationPipeline
import com.tgt.lists.lib.api.service.transform.list_items.PaginateListItemsTransformationStep
import com.tgt.lists.lib.api.service.transform.list_items.SortListItemsTransformationStep
import com.tgt.lists.lib.api.util.ItemIncludeFields
import com.tgt.lists.lib.api.util.ItemSortFieldGroup
import com.tgt.lists.lib.api.util.ItemSortOrderGroup
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetDefaultFavoriteListService(
    @Inject val getDefaultListService: GetDefaultListService,
    @Inject val listItemHydrationManager: ListItemHydrationManager
) {
    fun getDefaultList(
        guestId: String,
        locationId: Long,
        sortFieldBy: ItemSortFieldGroup? = ItemSortFieldGroup.ADDED_DATE,
        sortOrderBy: ItemSortOrderGroup? = ItemSortOrderGroup.DESCENDING,
        page: Int = 0,
        allowExpiredItems: Boolean? = false
    ): Mono<FavouritesListResponseTO> {
        return getDefaultListService.getDefaultList(guestId, locationId, ListItemsTransformationPipeline()
            .addStep(SortListItemsTransformationStep(sortFieldBy, sortOrderBy))
            .addStep(PaginateListItemsTransformationStep(page)),
            allowExpiredItems ?: false, ItemIncludeFields.PENDING)
            .flatMap { listResponse ->
                listItemHydrationManager.getItemHydration(locationId, listResponse.pendingListItems)
                    .map { FavouritesListResponseTO(listResponse, it) }
            }
    }
}
