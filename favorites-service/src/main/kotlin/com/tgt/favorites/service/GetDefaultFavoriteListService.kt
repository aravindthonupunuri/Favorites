package com.tgt.favorites.service

import com.tgt.lists.lib.api.service.GetDefaultListService
import com.tgt.lists.lib.api.service.transform.list_items.ListItemsTransformationPipeline
import com.tgt.lists.lib.api.service.transform.list_items.SortListItemsTransformationStep
import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.util.ItemIncludeFields
import com.tgt.lists.lib.api.util.ItemSortFieldGroup
import com.tgt.lists.lib.api.util.ItemSortOrderGroup
import reactor.core.publisher.Mono
import javax.inject.Singleton

@Singleton
class GetDefaultFavoriteListService(
    val getDefaultListService: GetDefaultListService
) {
    fun getDefaultList(
        guestId: String,
        locationId: Long,
        sortFieldBy: ItemSortFieldGroup? = ItemSortFieldGroup.ADDED_DATE,
        sortOrderBy: ItemSortOrderGroup? = ItemSortOrderGroup.DESCENDING,
        allowExpiredItems: Boolean? = false
    ): Mono<ListResponseTO> {
        return getDefaultListService.getDefaultList(guestId, locationId, ListItemsTransformationPipeline()
            .addStep(SortListItemsTransformationStep(sortFieldBy, sortOrderBy)),
            allowExpiredItems ?: false, ItemIncludeFields.PENDING)
    }
}
