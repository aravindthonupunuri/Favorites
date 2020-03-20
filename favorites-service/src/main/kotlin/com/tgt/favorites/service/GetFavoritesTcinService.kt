package com.tgt.favorites.service
import com.tgt.favorites.transport.GuestFavoritesResponseTO
import com.tgt.favorites.transport.ListItemDetailsTO
import com.tgt.lists.lib.api.exception.BadRequestException
import com.tgt.lists.lib.api.service.GetAllListService
import com.tgt.lists.lib.api.service.transform.list.ListsTransformationPipeline
import com.tgt.lists.lib.api.service.transform.list.PopulateListItemsTransformationStep
import com.tgt.lists.lib.api.transport.ListGetAllResponseTO
import com.tgt.lists.lib.api.util.AppErrorCodes
import io.micronaut.context.annotation.Value
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class GetFavoritesTcinService(
    @Inject val getAllListService: GetAllListService,
    @Value("\${list.max-tcin-count}") val maxTcinCount: Int = 28 // default value of 28 tcins
) {
    fun getFavoritesTcin(guestId: String, tcins: String): Mono<List<GuestFavoritesResponseTO>> {
        val tcinsList = tcins.trim().split(",")
        val tcinToItemDetailsMap = mutableMapOf<String, MutableList<ListItemDetailsTO>?>()
        if (tcinsList.size > 28)
            throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("tcins count exceeded 28", "tcins count is $tcinsList.size")))
        tcinsList.map {
            tcinToItemDetailsMap[it] = mutableListOf()
        }
        return getAllListService.getAllListsForUser(guestId, ListsTransformationPipeline()
            .addStep(PopulateListItemsTransformationStep()))
            .map { process(it, tcinToItemDetailsMap) }
            .map { formFavoritesTcinResponse(it) }
    }

    fun process(
        listResponses: List<ListGetAllResponseTO>,
        tcinToItemDetailsMap: MutableMap<String, MutableList<ListItemDetailsTO>?>
    ): MutableMap<String, MutableList<ListItemDetailsTO>?> {
        listResponses.map { addItemDetailsToTcin(it, tcinToItemDetailsMap) }
        return tcinToItemDetailsMap
    }
    private fun addItemDetailsToTcin(listGetAllResponseTO: ListGetAllResponseTO, tcinToItemDetailsMap: MutableMap<String, MutableList<ListItemDetailsTO>?>): Map<String, List<ListItemDetailsTO>?> {
        listGetAllResponseTO.pendingItems?.map {
            if (tcinToItemDetailsMap.containsKey(it.tcin)) {
                val valueAssignedToTcin: MutableList<ListItemDetailsTO> = tcinToItemDetailsMap[it.tcin]!!
                valueAssignedToTcin.add(ListItemDetailsTO(listGetAllResponseTO.listId, listGetAllResponseTO.listTitle, it.listItemId))
                tcinToItemDetailsMap[it.tcin!!] = valueAssignedToTcin
            }
        }
        return tcinToItemDetailsMap
    }
    private fun formFavoritesTcinResponse(tcinToItemDetailsMap: MutableMap<String, MutableList<ListItemDetailsTO>?>): List<GuestFavoritesResponseTO> {
        val tcins = tcinToItemDetailsMap.keys.toList()
        for (tcin in tcins) if (tcinToItemDetailsMap[tcin] == emptyList<ListItemDetailsTO>()) { tcinToItemDetailsMap.remove(tcin) }
        val newTcins = tcinToItemDetailsMap.keys.toList()
        val listOfListItemDetail = tcinToItemDetailsMap.values.toList()
        val listOfFavouritesTcinResponseTO = mutableListOf<GuestFavoritesResponseTO>()
        newTcins.indices.map {
            listOfFavouritesTcinResponseTO.add(GuestFavoritesResponseTO(newTcins[it], listOfListItemDetail[it]))
        }
        return listOfFavouritesTcinResponseTO
    }
}
