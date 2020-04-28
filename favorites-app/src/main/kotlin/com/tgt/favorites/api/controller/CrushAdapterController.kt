package com.tgt.favorites.api.controller

import com.tgt.favorites.transport.FavoriteListItemRequestTO
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.lib.api.domain.CartManager
import com.tgt.lists.lib.api.service.CreateListItemService
import com.tgt.lists.lib.api.service.CreateListService
import com.tgt.lists.lib.api.service.DeleteListItemService
import com.tgt.lists.lib.api.service.GetDefaultListService
import com.tgt.lists.lib.api.service.transform.list_items.ListItemsTransformationPipeline
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.transport.ListRequestTO
import com.tgt.lists.lib.api.util.ItemIncludeFields
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.getListMetaDataFromCart
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import reactor.core.publisher.Mono
import java.util.*
import javax.validation.Valid

@Controller("/guest_loves/v2/favorites")
class CrushAdapterController(
    private val deleteListItemService: DeleteListItemService,
    private val getDefaultListService: GetDefaultListService,
    private val createListService: CreateListService,
    private val createListItemService: CreateListItemService,
    private val cartManager: CartManager,
    @Value("\${list.list-type}") private val listType: String,
    @Value("\${list.default-list-title}") val defaultListTitle: String
) {

    private val storeId = 1375L

    /**
     *
     * Get all crush items from default list by guest id.
     *
     * @param guestId guestId
     * @return get list of crush item transfer object
     *
     */
    @Get
    @Status(HttpStatus.OK)
    fun getDefaultList(
        @QueryValue("guest_id") guestId: String
    ): Mono<List<CrushItemTO>> {
        return getDefaultListService.getDefaultList(guestId,
            storeId, ListItemsTransformationPipeline(), false, ItemIncludeFields.PENDING)
            .map { it.pendingListItems?.map { CrushItemTO(guestId, it) } }
    }

    /**
     *
     * Create a crush item to be added to the guest's default list.
     *
     * @param guestId guestId
     * @param crushItemRequestTO crush item post request
     * @return crush item transfer object
     *
     */
    @Post
    @Status(HttpStatus.CREATED)
    fun createFavoriteListItem(
        @QueryValue("guest_id") guestId: String,
        @Valid @Body crushItemRequestTO: CrushItemRequestTO
    ): Mono<CrushItemTO> {
        val request = crushItemRequestTO.toFavoriteListItemRequestTO().toListItemRequestTO()
        return getDefaultCartId(guestId)
            .flatMap {
                it?.let { Mono.just(it.cartId) }
                    ?: createListService.createList(guestId, ListRequestTO(request.channel!!,
                        defaultListTitle, storeId, defaultList = true)).map { it.listId }
            }.flatMap { createListItemService.createListItem(guestId, it!!, storeId, request) }
            .map { CrushItemTO(guestId, it) }
    }

    /**
     *
     * Delete crush item by id in default list.
     *
     * @param favoriteId favorite id
     * @return empty response
     *
     */
    @Delete("{favorite_id}")
    @Status(HttpStatus.NO_CONTENT)
    fun deleteListItem(
        @QueryValue("guest_id") guestId: String,
        @PathVariable("favorite_id") favoriteId: UUID
    ): Mono<Void> {
        return getDefaultCartId(guestId)
            .flatMap {
                it?.let {
                    deleteListItemService.deleteListItem(guestId, it.cartId!!, favoriteId)
                }
            }.then()
    }

    private fun getDefaultCartId(guestId: String): Mono<CartResponse?> {
        return cartManager.getAllCarts(guestId = guestId)
            .map {
                it.firstOrNull { cartResponse ->
                    val metadata = getListMetaDataFromCart(cartResponse.metadata)
                    metadata.defaultList && listType == cartResponse.cartSubchannel
                }
            }
    }
}

data class CrushItemRequestTO(
    val guestId: String,
    val channelName: String,
    val identifier: String,
    val identifier_type: String,
    val price: String
) {
    fun toFavoriteListItemRequestTO(): FavoriteListItemRequestTO {
        return FavoriteListItemRequestTO(
            itemType = ItemType.TCIN,
            channel = LIST_CHANNEL.WEB,
            tcin = identifier
        )
    }
}

data class CrushItemTO(
    val guestId: String,
    val channelName: String,
    val identifier: String,
    val identifier_type: String,
    val price: String,
    val createTimeStamp: String,
    val favoriteId: String
) {
    constructor(guestId: String, listItemResponseTO: ListItemResponseTO) : this(
        guestId = guestId,
        channelName = listItemResponseTO.channel?.name ?: "",
        identifier = listItemResponseTO.tcin!!,
        identifier_type = ItemType.TCIN.value,
        price = listItemResponseTO.price?.toString() ?: "",
        createTimeStamp = listItemResponseTO.addedTs!!,
        favoriteId = listItemResponseTO.listItemId.toString()
    )
}
