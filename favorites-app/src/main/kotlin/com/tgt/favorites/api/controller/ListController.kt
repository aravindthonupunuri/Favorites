package com.tgt.favorites.api.controller

import com.tgt.favorites.api.util.FavoriteConstants
import com.tgt.favorites.service.GetDefaultFavoriteListService
import com.tgt.favorites.service.GetFavoriteListItemService
import com.tgt.favorites.service.GetFavoriteListService
import com.tgt.favorites.transport.*
import com.tgt.lists.lib.api.exception.BadRequestException
import com.tgt.lists.lib.api.service.*
import com.tgt.lists.lib.api.service.transform.list.ListsTransformationPipeline
import com.tgt.lists.lib.api.service.transform.list.PopulateListItemsTransformationStep
import com.tgt.lists.lib.api.service.transform.list.SortListsTransformationStep
import com.tgt.lists.lib.api.transport.*
import com.tgt.lists.lib.api.util.*
import com.tgt.lists.lib.api.util.Constants.CONTEXT_OBJECT
import com.tgt.lists.lib.api.util.Constants.PROFILE_ID
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.*
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import java.util.*
import javax.validation.Valid

@Controller(FavoriteConstants.BASEPATH)
class ListController(
    private val createListService: CreateListService,
    private val updateListService: UpdateListService,
    private val deleteListService: DeleteListService,
    private val getListsService: GetAllListService,
    private val createListItemService: CreateListItemService,
    private val deleteListItemService: DeleteListItemService,
    private val getFavoriteListService: GetFavoriteListService,
    private val getDefaultFavoriteListService: GetDefaultFavoriteListService,
    private val getFavoriteListItemService: GetFavoriteListItemService,
    private val updateListItemService: UpdateListItemService
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
    fun createList(@Header(PROFILE_ID) guestId: String, @Valid @Body listRequestTO: ListRequestTO): Mono<FavouritesListResponseTO> {
        return createListService.createList(guestId, listRequestTO).map { validate(it) }
            .map { toFavouritesListResponse(it) }
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
    fun getList(
        @Header(PROFILE_ID) guestId: String,
        @PathVariable("list_id") listId: UUID,
        @QueryValue("sort_field") sortFieldBy: ItemSortFieldGroup? = ItemSortFieldGroup.ADDED_DATE,
        @QueryValue("sort_order") sortOrderBy: ItemSortOrderGroup? = ItemSortOrderGroup.DESCENDING,
        @QueryValue("location_id") locationId: Long?,
        @QueryValue("allow_expired_items") allowExpiredItems: Boolean? = false
    ): Mono<MutableHttpResponse<ListResponseTO>> {
        if (locationId == null) {
            return throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("location_id is incorrect $locationId")))
        }
        return getFavoriteListService.getList(guestId, locationId!!, listId, sortFieldBy, sortOrderBy,
            allowExpiredItems ?: false)
            .zipWith(Mono.subscriberContext())
            .map {
                if (it.t2.get<ContextContainer>(CONTEXT_OBJECT).partialResponse) {
                    HttpResponse.status<ListResponseTO>(HttpStatus.PARTIAL_CONTENT).body(it.t1)
                } else {
                    HttpResponse.ok(it.t1)
                }
            }
            .subscriberContext {
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
    fun getDefaultList(
        @Header(PROFILE_ID) guestId: String,
        @QueryValue("sort_field") sortFieldBy: ItemSortFieldGroup? = ItemSortFieldGroup.ADDED_DATE,
        @QueryValue("sort_order") sortOrderBy: ItemSortOrderGroup? = ItemSortOrderGroup.DESCENDING,
        @QueryValue("location_id") locationId: Long,
        @QueryValue("allow_expired_items") allowExpiredItems: Boolean? = false
    ): Mono<MutableHttpResponse<ListResponseTO>> {
        return getDefaultFavoriteListService.getDefaultList(guestId, locationId, sortFieldBy, sortOrderBy,
            allowExpiredItems ?: false)
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
    ): Mono<ListDeleteResponseTO> {
        return deleteListService.deleteList(guestId, listId)
    }

    /**
     * Update list by list id.
     *
     * @param guestId: guest id
     * @param listId list id
     * @param listUpdateRequestTO the list request body
     * @return list delete response transfer object
     *
     */
    @Put("/{list_id}")
    @Status(HttpStatus.OK)
    fun updateList(
        @Header(PROFILE_ID) guestId: String,
        @PathVariable("list_id") listId: UUID,
        @Valid @Body listUpdateRequestTO: ListUpdateRequestTO
    ): Mono<ListResponseTO> {
        return updateListService.updateList(guestId, listId, listUpdateRequestTO)
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
    fun getListForUser(
        @Header(PROFILE_ID) guestId: String,
        @QueryValue("sort_field") sortFieldBy: ListSortFieldGroup? = ListSortFieldGroup.ADDED_DATE,
        @QueryValue("sort_order") sortOrderBy: ListSortOrderGroup? = ListSortOrderGroup.DESCENDING
    ): Mono<MutableHttpResponse<List<ListGetAllResponseTO>>> {
        return getListsService.getAllListsForUser(guestId,
            ListsTransformationPipeline().addStep(PopulateListItemsTransformationStep()).addStep(SortListsTransformationStep(sortFieldBy, sortOrderBy)))
            .zipWith(Mono.subscriberContext())
            .map {
                if (it.t2.get<ContextContainer>(CONTEXT_OBJECT).partialResponse) {
                    HttpResponse.status<List<ListGetAllResponseTO>>(HttpStatus.PARTIAL_CONTENT).body(it.t1)
                } else {
                    HttpResponse.ok(it.t1)
                }
            }
            .subscriberContext {
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
    fun getListItem(
        @Header(PROFILE_ID) guestId: String,
        @QueryValue("location_id") locationId: Long?,
        @PathVariable("list_id") listId: UUID,
        @PathVariable("list_item_id") listItemId: UUID
    ): Mono<MutableHttpResponse<ListItemResponseTO>> {
        if (locationId == null) {
            return throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("location_id is incorrect $locationId")))
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
        @Header(PROFILE_ID) guestId: String,
        @PathVariable("list_id") listId: UUID,
        @QueryValue("location_id") locationId: Long?,
        @Valid @Body listItemRequestTO: ListItemRequestTO
    ): Mono<FavouritesListItemResponseTO> {
        if (locationId == null) {
            return throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("location_id is incorrect $locationId")))
        }
        return createListItemService.createListItem(guestId, listId, locationId, listItemRequestTO)
            .map { toFavouritesListItemResponse(it) }
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
        @Header(PROFILE_ID) guestId: String,
        @PathVariable("list_id") listId: UUID,
        @PathVariable("list_item_id") listItemId: UUID
    ): Mono<ListItemDeleteResponseTO> {
        return deleteListItemService.deleteListItem(guestId, listId, listItemId)
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
        @Header(PROFILE_ID) guestId: String,
        @PathVariable("list_id") listId: UUID,
        @QueryValue("location_id") locationId: Long?,
        @PathVariable("list_item_id") listItemId: UUID,
        @Valid @Body listItemUpdateRequestTO: ListItemUpdateRequestTO
    ): Mono<ListItemResponseTO> {
        if (locationId == null) {
            return throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("location_id is incorrect $locationId")))
        }
        return updateListItemService.updateListItem(guestId, locationId, listId, listItemId, listItemUpdateRequestTO)
    }
}
