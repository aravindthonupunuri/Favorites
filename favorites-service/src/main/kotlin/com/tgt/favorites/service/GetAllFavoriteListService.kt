package com.tgt.favorites.service

import com.tgt.favorites.transport.FavoriteGetAllListResponseTO
import com.tgt.lists.lib.api.service.GetAllListService
import com.tgt.lists.lib.api.service.transform.list.ListsTransformationPipeline
import com.tgt.lists.lib.api.service.transform.list.PopulateListItemsTransformationStep
import com.tgt.lists.lib.api.service.transform.list.SortListsTransformationStep
import com.tgt.lists.lib.api.util.*
import reactor.core.publisher.Mono
import javax.inject.Singleton

@Singleton
class GetAllFavoriteListService(
    val getAllListService: GetAllListService
) {
    fun getListForUser(
        guestId: String,
        sortFieldBy: ListSortFieldGroup? = ListSortFieldGroup.ADDED_DATE,
        sortOrderBy: ListSortOrderGroup? = ListSortOrderGroup.DESCENDING
    ): Mono<List<FavoriteGetAllListResponseTO>> {
        return getAllListService.getAllListsForUser(guestId,
            ListsTransformationPipeline().addStep(PopulateListItemsTransformationStep()).addStep(SortListsTransformationStep(sortFieldBy, sortOrderBy)))
            .map { listResponses ->
                listResponses.map { FavoriteGetAllListResponseTO(it.listId,
                    it.channel, it.listType, it.listTitle, it.defaultList, it.shortDescription, it.addedTs,
                    it.lastModifiedTs, it.maxListsCount, it.totalItemsCount) }
            }
    }
}
