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
        val tcinToItemDetailsMap = mutableMapOf<String, MutableList<ListItemDetailsTO>?>()

        if (tcinsList.size > 28)
            return throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("tcins count exceeded 28", "tcins count is $tcinsList.size")))

        tcinsList.map {
            tcinToItemDetailsMap[it] = mutableListOf()
        }

        return getAllListService.getAllListsForUser(guestId, ListsTransformationPipeline()
            .addStep(PopulateListItemsTransformationStep()))
            .flatMap { process(it, tcinToItemDetailsMap) }
    }

    fun process(
        listResponses: List<ListGetAllResponseTO>,
        tcinToItemDetailsMap: MutableMap<String, MutableList<ListItemDetailsTO>?>
    ): Mono<List<GuestFavoritesResponseTO>> {
        return listResponses.toFlux()
            .flatMap { addItemDetailsToTcin(it, tcinToItemDetailsMap) }
            .then(Mono.just(true))
            .map { formFavoritesTcinResponse(tcinToItemDetailsMap) }
    }

    private fun addItemDetailsToTcin(listGetAllResponseTO: ListGetAllResponseTO, tcinToItemDetailsMap: MutableMap<String, MutableList<ListItemDetailsTO>?>): Mono<Map<String, List<ListItemDetailsTO>?>> {

        listGetAllResponseTO.pendingItems?.map {
            if (tcinToItemDetailsMap.containsKey(it.tcin)) {

                val valueAssignedToTcin: MutableList<ListItemDetailsTO> = tcinToItemDetailsMap[it.tcin]!!
                valueAssignedToTcin.add(ListItemDetailsTO(listGetAllResponseTO.listId, listGetAllResponseTO.listTitle, it.listItemId))
                tcinToItemDetailsMap[it.tcin!!] = valueAssignedToTcin
            }
        }

        return Mono.just(tcinToItemDetailsMap)
    }

    private fun formFavoritesTcinResponse(tcinToItemDetailsMap: Map<String, List<ListItemDetailsTO>?>): List<GuestFavoritesResponseTO> {

        val tcins = tcinToItemDetailsMap.keys.toList()
        val listOfListItemDetail = tcinToItemDetailsMap.values.toList()
        val listOfFavouritesTcinResponseTO = mutableListOf<GuestFavoritesResponseTO>()
        tcins.indices.map {
            listOfFavouritesTcinResponseTO.add(GuestFavoritesResponseTO(tcins[it], listOfListItemDetail[it]))
        }
        return listOfFavouritesTcinResponseTO
    }
}
