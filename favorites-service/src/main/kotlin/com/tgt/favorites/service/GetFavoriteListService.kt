package com.tgt.favorites.service

import com.tgt.lists.lib.api.service.GetListService
import com.tgt.lists.lib.api.service.transform.list_items.ListItemsTransformationPipeline
import com.tgt.lists.lib.api.service.transform.list_items.PaginateListItemsTransformationStep
import com.tgt.lists.lib.api.service.transform.list_items.SortListItemsTransformationStep
import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.util.ItemIncludeFields
import com.tgt.lists.lib.api.util.ItemSortFieldGroup
import com.tgt.lists.lib.api.util.ItemSortOrderGroup
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetFavoriteListService(
    @Inject val getListService: GetListService
) {

    fun getList(
        guestId: String,
        locationId: Long,
        listId: UUID,
        sortFieldBy: ItemSortFieldGroup? = ItemSortFieldGroup.ADDED_DATE,
        sortOrderBy: ItemSortOrderGroup? = ItemSortOrderGroup.DESCENDING,
        page: Int,
        allowExpiredItems: Boolean
    ): Mono<ListResponseTO> {
        return getListService.getList(guestId, locationId, listId, ListItemsTransformationPipeline()
            .addStep(SortListItemsTransformationStep(sortFieldBy, sortOrderBy))
            .addStep(PaginateListItemsTransformationStep(page)),
            allowExpiredItems, ItemIncludeFields.PENDING)
    }
}
