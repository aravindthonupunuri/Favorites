package com.tgt.favorites.service

import com.tgt.lists.lib.api.service.GetDefaultListService
import com.tgt.lists.lib.api.service.transform.ListItemsTransformationPipeline
import com.tgt.lists.lib.api.service.transform.SortListItemsTransformationStep
import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.util.ItemIncludeFields
import com.tgt.lists.lib.api.util.ItemSortFieldGroup
import com.tgt.lists.lib.api.util.ItemSortOrderGroup
import reactor.core.publisher.Mono
import java.math.BigDecimal
import javax.inject.Singleton

@Singleton
class GetDefaultFavoriteListService(
    val getDefaultListService: GetDefaultListService
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
        return getDefaultListService.getDefaultList(guestId, locationId, ListItemsTransformationPipeline()
            .addStep(SortListItemsTransformationStep(sortFieldBy, sortOrderBy)),
            allowExpiredItems ?: false, includeItems ?: ItemIncludeFields.ALL)
    }
}
