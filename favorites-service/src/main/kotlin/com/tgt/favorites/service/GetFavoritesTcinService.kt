package com.tgt.favorites.service

import com.tgt.favorites.transport.GuestFavoritesResponseTO
import com.tgt.favorites.transport.ListItemDetailsTO
import com.tgt.lists.lib.api.exception.BadRequestException
import com.tgt.lists.lib.api.service.GetAllListService
import com.tgt.lists.lib.api.service.transform.list.ListsTransformationPipeline
import com.tgt.lists.lib.api.service.transform.list.PopulateListItemsTransformationStep
import com.tgt.lists.lib.api.transport.ListGetAllResponseTO
import com.tgt.lists.lib.api.util.AppErrorCodes
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetFavoritesTcinService(
    @Inject val getAllListService: GetAllListService
) {

    fun getFavoritesTcin(guestId: String, tcins: String): Mono<List<GuestFavoritesResponseTO>> {

        val tcinsList = tcins.trim().split(",")
        val tcinAndListItemDetailsMap = mutableMapOf<String, MutableList<ListItemDetailsTO>?>()

        if (tcinsList.size > 28)
            return throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("tcins count exceeded 28", "tcins count is $tcinsList.size")))

        for (element in tcinsList)
        tcinAndListItemDetailsMap[element] = mutableListOf()

        return getAllListService.getAllListsForUser(guestId, ListsTransformationPipeline().addStep(PopulateListItemsTransformationStep()))
            .flatMap { process(it, tcinAndListItemDetailsMap) }
    }

    fun process(
        listResponses: List<ListGetAllResponseTO>,
        tcinAndListItemDetailsMap: MutableMap<String, MutableList<ListItemDetailsTO>?>
    ): Mono<List<GuestFavoritesResponseTO>> {
        return listResponses.toFlux().flatMap { addItemDetailsToTcin(it, tcinAndListItemDetailsMap) }
            .then(Mono.just(true))
            .map { formFavoritesTcinResponse(tcinAndListItemDetailsMap) }
    }

    private fun addItemDetailsToTcin(listGetAllResponseTO: ListGetAllResponseTO, tcinAndListItemDetailsMap: MutableMap<String, MutableList<ListItemDetailsTO>?>): Mono<Map<String, List<ListItemDetailsTO>?>> {

        for (i in listGetAllResponseTO.pendingItems!!.indices) {
            if (tcinAndListItemDetailsMap.containsKey(listGetAllResponseTO.pendingItems!![i].tcin)) {

                val valueAssignedToTcin: MutableList<ListItemDetailsTO> = tcinAndListItemDetailsMap[listGetAllResponseTO.pendingItems!![i].tcin]!!
                valueAssignedToTcin.add(ListItemDetailsTO(listGetAllResponseTO.listId, listGetAllResponseTO.pendingItems!![i].itemTitle, listGetAllResponseTO.pendingItems!![i].listItemId))
                tcinAndListItemDetailsMap[listGetAllResponseTO.pendingItems!![i].tcin!!] = valueAssignedToTcin
            }
        }

        return Mono.just(tcinAndListItemDetailsMap)
    }

    private fun formFavoritesTcinResponse(tcinAndListItemDetailsMap: Map<String, List<ListItemDetailsTO>?>): List<GuestFavoritesResponseTO> {

        val tcins = tcinAndListItemDetailsMap.keys.toList()
        val listOfListItemDetail = tcinAndListItemDetailsMap.values.toList()
        val listOfFavouritesTcinResponseTO = mutableListOf<GuestFavoritesResponseTO>()
        for (i in tcins.indices)
            listOfFavouritesTcinResponseTO.add(GuestFavoritesResponseTO(tcins[i], listOfListItemDetail[i]))
        return listOfFavouritesTcinResponseTO
    }
}
