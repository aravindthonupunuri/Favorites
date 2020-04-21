package com.tgt.favorites.service

import com.tgt.favorites.domain.ListItemHydrationManager
import com.tgt.favorites.transport.FavouritesListResponseTO
import com.tgt.lists.lib.api.service.GetListService
import com.tgt.lists.lib.api.service.transform.list_items.ListItemsTransformationPipeline
import com.tgt.lists.lib.api.service.transform.list_items.PaginateListItemsTransformationStep
import com.tgt.lists.lib.api.service.transform.list_items.SortListItemsTransformationStep
import com.tgt.lists.lib.api.util.ItemIncludeFields
import com.tgt.lists.lib.api.util.ItemSortFieldGroup
import com.tgt.lists.lib.api.util.ItemSortOrderGroup
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetFavoriteListService(
    @Inject val getListService: GetListService,
    @Inject val listItemHydrationManager: ListItemHydrationManager
) {

    fun getList(
        guestId: String,
        locationId: Long,
        listId: UUID,
        sortFieldBy: ItemSortFieldGroup? = ItemSortFieldGroup.ADDED_DATE,
        sortOrderBy: ItemSortOrderGroup? = ItemSortOrderGroup.DESCENDING,
        page: Int,
        allowExpiredItems: Boolean
    ): Mono<FavouritesListResponseTO> {
        return getListService.getList(guestId, locationId, listId, ListItemsTransformationPipeline()
            .addStep(SortListItemsTransformationStep(sortFieldBy, sortOrderBy))
            .addStep(PaginateListItemsTransformationStep(page)), ItemIncludeFields.PENDING)
            .flatMap { listResponse ->
                listItemHydrationManager.getItemHydration(locationId, listResponse.pendingListItems)
                    .map { FavouritesListResponseTO(listResponse, it) }
            }
    }
}
