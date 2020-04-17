package com.tgt.favorites.api.controller

import com.tgt.favorites.util.FavoriteConstants
import com.tgt.favorites.service.*
import com.tgt.favorites.transport.*
import com.tgt.favorites.transport.FavoriteItemSortFieldGroup
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.lib.api.service.*
import com.tgt.lists.lib.api.transport.*
import com.tgt.lists.lib.api.util.*
import com.tgt.lists.lib.api.util.Constants.CONTEXT_OBJECT
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.*
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import java.util.*
import javax.validation.Valid

@Controller(FavoriteConstants.BASEPATH)
class ListController(
    private val createListService: CreateListService,
    private val updateListService: UpdateListService,
    private val deleteListService: DeleteListService,
    private val getFavoriteTcinService: GetFavoritesTcinService,
    private val getAllFavoriteListService: GetAllFavoriteListService,
    private val createFavoriteListItemService: CreateFavoriteListItemService,
    private val createFavoriteDefaultListItemService: CreateFavoriteDefaultListItemService,
    private val deleteListItemService: DeleteListItemService,
    private val getFavoriteListService: GetFavoriteListService,
    private val getDefaultFavoriteListService: GetDefaultFavoriteListService,
    private val getFavoriteListItemService: GetFavoriteListItemService,
    private val updateFavoriteListItemService: UpdateFavoriteListItemService
) {

    /**
     *
     * Create list.
     *
     * @param guestId: guest id
     * @param listRequestTO the list request body
     * @return list response transfer object
     *
     */
    @Post("/")
    @Status(HttpStatus.CREATED)
    fun createList(@Header(FavoriteConstants.PROFILE_ID) guestId: String, @Valid @Body listRequestTO: FavoriteListRequestTO): Mono<FavouritesListPostResponseTO> {
        return createListService.createList(guestId, listRequestTO.toListRequestTO()).map { validate(it) }
            .map { FavouritesListPostResponseTO(it) }
    }

    /**
     *
     * Get list by list id.
     *
     * @param listId list id
     * @param locationId store id
     * @param includeItems include items
     * @return List response transfer object
     *
     */
    @Get("/{list_id}")
    @Status(HttpStatus.OK)
    @ApiResponse(content = [Content(mediaType = "application/json", schema = Schema(implementation = FavouritesListResponseTO::class))])
    fun getList(
        @Header(FavoriteConstants.PROFILE_ID) guestId: String,
        @PathVariable("list_id") listId: UUID,
        @QueryValue("sort_field") sortFieldBy: FavoriteItemSortFieldGroup? = FavoriteItemSortFieldGroup.ADDED_DATE,
        @QueryValue("sort_order") sortOrderBy: ItemSortOrderGroup? = ItemSortOrderGroup.DESCENDING,
        @QueryValue("page", defaultValue = "0") page: Int?,
        @QueryValue("location_id") locationId: Long?
    ): Mono<MutableHttpResponse<ListResponseTO>> {
        if (locationId == null) {
            throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("location_id is incorrect, can’t be null")))
        }
        return getFavoriteListService.getList(guestId, locationId, listId, sortFieldBy?.toItemSortFieldGroup(), sortOrderBy, page!!, false)
            .zipWith(Mono.subscriberContext())
            .map {
                if (it.t2.get<ContextContainer>(CONTEXT_OBJECT).partialResponse) {
                    HttpResponse.status<ListResponseTO>(HttpStatus.PARTIAL_CONTENT).body(it.t1)
                } else {
                    HttpResponse.ok(it.t1)
                }
            }.subscriberContext {
                it.put(CONTEXT_OBJECT, ContextContainer())
            }
    }

    /**
     *
     * Get default list by guest id.
     *
     * @param guestId guestId
     * @param sortFieldBy sort field by
     * @param sortOrderBy sort order by
     * @return get all response transfer objects
     *
     */
    @Get("/default_list")
    @Status(HttpStatus.OK)
    @ApiResponse(content = [Content(mediaType = "application/json", schema = Schema(implementation = FavouritesListResponseTO::class))])
    fun getDefaultList(
        @Header(FavoriteConstants.PROFILE_ID) guestId: String,
        @QueryValue("sort_field") sortFieldBy: FavoriteItemSortFieldGroup? = FavoriteItemSortFieldGroup.ADDED_DATE,
        @QueryValue("sort_order") sortOrderBy: ItemSortOrderGroup? = ItemSortOrderGroup.DESCENDING,
        @QueryValue("location_id") locationId: Long?,
        @QueryValue("page", defaultValue = "0") page: Int?
    ): Mono<MutableHttpResponse<ListResponseTO>> {
        if (locationId == null) {
            throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("location_id is incorrect, can’t be null")))
        }

        return getDefaultFavoriteListService.getDefaultList(guestId, locationId,
            sortFieldBy?.toItemSortFieldGroup(), sortOrderBy, page!!, false)
            .zipWith(Mono.subscriberContext())
            .map {
                if (it.t2.get<ContextContainer>(CONTEXT_OBJECT).partialResponse) {
                    HttpResponse.status<ListResponseTO>(HttpStatus.PARTIAL_CONTENT).body(it.t1)
                } else {
                    HttpResponse.ok(it.t1)
                }
            }
            .switchIfEmpty { Mono.just(HttpResponse.noContent()) }
            .subscriberContext { it.put(CONTEXT_OBJECT, ContextContainer()) }
    }

    /**
     *
     * Get guest_favourites by TCINs.
     *
     * @param guestId guestId
     * @param tcins tcins
     * @return get list of guest favourites response
     *
     */
    @Get("/guest_favorites")
    @Status(HttpStatus.OK)
    fun getFavoritesOfTcins(
        @Header(FavoriteConstants.PROFILE_ID) guestId: String,
        @QueryValue("tcins") tcins: String
    ): Mono<List<GuestFavoritesResponseTO>> {
        return getFavoriteTcinService.getFavoritesTcin(guestId, tcins)
    }

    /**
     *
     * Delete list by list id.
     *
     * @param guestId: guest id
     * @param listId list id
     * @return list delete response transfer object
     *
     */
    @Delete("/{list_id}")
    @Status(HttpStatus.NO_CONTENT)
    fun deleteList(
        @Header("profile_id") guestId: String,
        @PathVariable("list_id") listId: UUID
    ): Mono<Void> {
        return deleteListService.deleteList(guestId, listId).then()
    }

    /**
     * Update list by list id.
     *
     * @param guestId: guest id
     * @param listId list id
     * @param favoriteListUpdateRequestTO the list request body
     * @return list delete response transfer object
     *
     */
    @Put("/{list_id}")
    @Status(HttpStatus.OK)
    fun updateList(
        @Header(FavoriteConstants.PROFILE_ID) guestId: String,
        @PathVariable("list_id") listId: UUID,
        @Valid @Body favoriteListUpdateRequestTO: FavoriteListUpdateRequestTO
    ): Mono<FavouritesListPostResponseTO> {
        return updateListService.updateList(guestId, listId, favoriteListUpdateRequestTO.toListUpdateRequestTO())
            .map { FavouritesListPostResponseTO(it) }
    }

    /**
     *
     * Get list by guest id.
     *
     * @param guestId guestId
     * @param sortFieldBy sort field by
     * @param sortOrderBy sort order by
     * @return get all response transfer objects
     *
     */
    @Get("/")
    @ApiResponse(content = [Content(mediaType = "application/json", schema = Schema(implementation = FavoriteGetAllListResponseTO::class))])
    fun getListForUser(
        @Header(FavoriteConstants.PROFILE_ID) guestId: String,
        @QueryValue("sort_field") sortFieldBy: ListSortFieldGroup? = ListSortFieldGroup.ADDED_DATE,
        @QueryValue("sort_order") sortOrderBy: ListSortOrderGroup? = ListSortOrderGroup.DESCENDING
    ): Mono<MutableHttpResponse<List<FavoriteGetAllListResponseTO>>> {
        return getAllFavoriteListService.getListForUser(guestId, sortFieldBy, sortOrderBy)
            .zipWith(Mono.subscriberContext())
            .map {
                if (it.t2.get<ContextContainer>(CONTEXT_OBJECT).partialResponse) {
                    HttpResponse.status<List<FavoriteGetAllListResponseTO>>(HttpStatus.PARTIAL_CONTENT).body(it.t1)
                } else {
                    HttpResponse.ok(it.t1)
                }
            }.subscriberContext {
                it.put(CONTEXT_OBJECT, ContextContainer())
            }
    }

    /**
     * Get list item by item id.
     *
     * @param locationId store id
     * @param listId list id
     * @param listItemId list item id
     * @return list item response transfer object
     *
     */
    @Get("/{list_id}/list_items/{list_item_id}")
    @Status(HttpStatus.OK)
    @ApiResponse(content = [Content(mediaType = "application/json", schema = Schema(implementation = FavoriteListItemGetResponseTO::class))])
    fun getListItem(
        @Header(FavoriteConstants.PROFILE_ID) guestId: String,
        @QueryValue("location_id") locationId: Long?,
        @PathVariable("list_id") listId: UUID,
        @PathVariable("list_item_id") listItemId: UUID
    ): Mono<MutableHttpResponse<ListItemResponseTO>> {
        if (locationId == null) {
            throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("location_id is incorrect, can’t be null")))
        }
        return getFavoriteListItemService.getListItem(guestId, locationId, listId, listItemId).zipWith(Mono.subscriberContext())
            .map {
                if (it.t2.get<ContextContainer>(CONTEXT_OBJECT).partialResponse) {
                    HttpResponse.status<ListItemResponseTO>(HttpStatus.PARTIAL_CONTENT).body(it.t1)
                } else {
                    HttpResponse.ok(it.t1)
                }
            }.subscriberContext {
            it.put(CONTEXT_OBJECT, ContextContainer())
        }
    }

    /**
     * Create list item.
     *
     * @param listId list id
     * @return list item response transfer object
     *
     */
    @Post("/{list_id}/list_items")
    @Status(HttpStatus.CREATED)
    fun createListItem(
        @Header(FavoriteConstants.PROFILE_ID) guestId: String,
        @PathVariable("list_id") listId: UUID,
        @Valid @Body favoriteListItemRequestTO: FavoriteListItemRequestTO
    ): Mono<FavoriteListItemResponseTO> {
        return createFavoriteListItemService.createListItem(guestId, listId, FavoriteConstants.LOCATION_ID, favoriteListItemRequestTO)
    }

    /**
     * Create an item to be added to the guest's default list.
     *
     * @return list item response transfer object
     *
     */
    @Post("/list_items")
    @Status(HttpStatus.CREATED)
    fun createFavoriteListItem(
        @Header(FavoriteConstants.PROFILE_ID) guestId: String,
        @Valid @Body favoriteListItemRequestTO: FavoriteListItemRequestTO
    ): Mono<FavoriteListItemResponseTO> {
        return createFavoriteDefaultListItemService.createFavoriteItem(guestId, FavoriteConstants.LOCATION_ID, favoriteListItemRequestTO)
    }

    /**
     * Delete list item by id.
     *
     * @param listId list id
     * @param listItemId list item id
     * @return list item delete response transfer object
     *
     */
    @Delete("/{list_id}/list_items/{list_item_id}")
    @Status(HttpStatus.NO_CONTENT)
    fun deleteListItem(
        @Header(FavoriteConstants.PROFILE_ID) guestId: String,
        @PathVariable("list_id") listId: UUID,
        @PathVariable("list_item_id") listItemId: UUID
    ): Mono<Void> {
        return deleteListItemService.deleteListItem(guestId, listId, listItemId).then()
    }

    /**
     * Update list item by list item id.
     *
     * @param listId list id
     * @param listItemId list item id
     * @return list item updateByListId response transfer object
     *
     */
    @Put("/{list_id}/list_items/{list_item_id}")
    @Status(HttpStatus.OK)
    fun updateListItem(
        @Header(FavoriteConstants.PROFILE_ID) guestId: String,
        @PathVariable("list_id") listId: UUID,
        @PathVariable("list_item_id") listItemId: UUID,
        @Valid @Body favoriteListItemUpdateRequestTO: FavoriteListItemUpdateRequestTO
    ): Mono<FavoriteListItemResponseTO> {
        return updateFavoriteListItemService.updateFavoriteListItem(guestId, FavoriteConstants.LOCATION_ID,
            listId, listItemId, favoriteListItemUpdateRequestTO.toListItemUpdateRequestTO())
    }
}
