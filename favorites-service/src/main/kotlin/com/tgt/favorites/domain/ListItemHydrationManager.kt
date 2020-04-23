package com.tgt.favorites.domain

import com.tgt.favorites.client.redsky.RedSkyClient
import com.tgt.favorites.client.redsky.RedskyResponseTO
import com.tgt.favorites.client.redsky.getitemhydration.ItemDetailVO
import com.tgt.favorites.transport.FavoriteListItemGetResponseTO
import com.tgt.favorites.transport.ItemRelationshipType
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import reactor.core.publisher.toFlux
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListItemHydrationManager(
    @Inject private val redSkyClient: RedSkyClient,
    @Value("\${list.redsky-batch-size}") private val batchSize: Int = 28
) {

    private val logger = KotlinLogging.logger { ListItemHydrationManager::class.java.name }

    fun getItemHydration(locationId: Long, listItems: List<ListItemResponseTO>?): Mono<List<FavoriteListItemGetResponseTO>> {
        if (listItems.isNullOrEmpty()) {
            return Mono.just(emptyList())
        }
        return getItemDetail(locationId, listItems)
            .zipWith(getItemDetailWithVariation(locationId, listItems))
            .map { listOf(it.t1, it.t2).flatten() }
    }

    fun getItemDetail(
        locationId: Long,
        listItems: List<ListItemResponseTO>
    ): Mono<List<FavoriteListItemGetResponseTO>> {
        val items = listItems.filter { !isVariationParent(it.relationshipType) }
        if (items.isEmpty()) {
            return Mono.just(emptyList())
        }

        return items
            .chunked(batchSize).toList().toFlux()
            .flatMap { chunkedItems ->
                redSkyClient.getItemHydration(locationId.toString(),
                    chunkedItems.map { it.tcin }.joinToString(",").trim())
                    .switchIfEmpty { Mono.just(RedskyResponseTO()) }
                    .onErrorResume {
                        logger.error("Exception from redsky item detail hydration", it)
                        Mono.just(RedskyResponseTO())
                    }.map { toFavoriteListItemGetResponseTO(it, chunkedItems) }
            }.collectList().map { it.flatten() }
    }

    fun getItemDetailWithVariation(
        locationId: Long,
        listItems: List<ListItemResponseTO>
    ): Mono<List<FavoriteListItemGetResponseTO>> {
        val items = listItems.filter { isVariationParent(it.relationshipType) }
        if (items.isEmpty()) {
            return Mono.just(emptyList())
        }

        return Flux.fromIterable(items)
            .flatMap { listItem ->
                redSkyClient.getItemHydrationWithVariation(locationId.toString(), listItem.tcin!!.trim())
                    .switchIfEmpty { Mono.just(RedskyResponseTO()) }
                    .onErrorResume {
                        logger.error("Exception from redsky item detail with variation hydration", it)
                        Mono.just(RedskyResponseTO())
                    }.map {
                        if (it.errors != null) {
                            logger.error("Exception from redsky item detail with variation hydration", it.errors)
                        }
                        if (it.data == null) {
                            FavoriteListItemGetResponseTO(listItem)
                        } else {
                            FavoriteListItemGetResponseTO(listItem, it.data.product)
                        }
                    }
            }.collectList()
    }

    private fun isVariationParent(relationshipType: String?): Boolean {
        return relationshipType != null && (relationshipType == ItemRelationshipType.VAP.value ||
            relationshipType == ItemRelationshipType.VPC.value)
    }

    private fun toFavoriteListItemGetResponseTO(
        redskyResponseTO: RedskyResponseTO<ItemDetailVO>,
        listItems: List<ListItemResponseTO>
    ): List<FavoriteListItemGetResponseTO> {
        if (redskyResponseTO.errors != null) {
            logger.error("Exception from redsky item detail with hydration", redskyResponseTO.errors)
        }
        if (redskyResponseTO.data == null ||
            redskyResponseTO.data.products.isNullOrEmpty()) {
            return listItems.map { FavoriteListItemGetResponseTO(it) }
        }
        val set = redskyResponseTO.data.products.map { it.tcin }.toSet()
        val failedItems = listItems.filter { !set.contains(it.tcin) }.map { FavoriteListItemGetResponseTO(it) }
        val successItems = redskyResponseTO.data.products
            .map { product -> FavoriteListItemGetResponseTO(listItems.first { it.tcin == product.tcin }, product) }
        return listOf(failedItems, successItems).flatten()
    }
}
