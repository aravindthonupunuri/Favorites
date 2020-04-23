package com.tgt.favorites.transport

import com.fasterxml.jackson.annotation.JsonInclude
import com.tgt.favorites.client.redsky.getItemHydrationwithvariation.*
import com.tgt.lists.cart.transport.Image
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import java.util.*
import javax.validation.constraints.NotNull

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FavoriteListItemResponseTO(
    @field:NotNull(message = "List item id must not be empty") val listItemId: UUID? = null,
    @field:NotNull(message = "Item type must not be empty") val itemType: ItemType? = null,
    @field:NotNull(message = "channel must not be empty") val channel: LIST_CHANNEL? = null,
    val tcin: String? = null,
    val itemTitle: String? = null,
    val itemNote: String? = null,
    val images: Image? = null,
    val averageOverallRating: Double? = 0.0,
    val totalReviewCount: Int? = 0,
    val item: Item? = null,
    val availableToPromise: AvailableToPromise?,
    val price: Price?,
    val variationHierarchy: List<VariationHierarchy>?,
    val addedTs: String? = null,
    val lastModifiedTs: String? = null
) {
    constructor(
        listItemResponseTO: ListItemResponseTO,
        product: Product? = null
    ) : this(
        listItemId = listItemResponseTO.listItemId,
        itemType = listItemResponseTO.itemType,
        channel = listItemResponseTO.channel,
        tcin = listItemResponseTO.tcin,
        itemTitle = listItemResponseTO.itemTitle,
        itemNote = listItemResponseTO.itemNote,
        images = listItemResponseTO.images,
        averageOverallRating = product?.ratingsAndReviews?.statistics?.rating?.average,
        totalReviewCount = product?.ratingsAndReviews?.statistics?.reviewCount,
        price = product?.price,
        variationHierarchy = product?.variationHierarchy,
        item = product?.item,
        availableToPromise = product?.availableToPromise,
        addedTs = listItemResponseTO.addedTs,
        lastModifiedTs = listItemResponseTO.lastModifiedTs
    )
}
