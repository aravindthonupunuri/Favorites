package com.tgt.favorites.api.controller

import com.tgt.favorites.service.*
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.*
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import java.math.BigDecimal
import java.util.*
import javax.validation.Valid
import com.tgt.lists.lib.api.exception.BadRequestException
import com.tgt.lists.lib.api.service.*
import com.tgt.lists.lib.api.transport.*
import com.tgt.lists.lib.api.util.*
import com.tgt.lists.lib.api.util.Constants.CONTEXT_OBJECT
import com.tgt.lists.lib.api.util.Constants.PROFILE_ID

@Controller(Constants.LISTS_BASEPATH)
class ListController(
    private val createListService: CreateListService,
    private val updateListService: UpdateListService,
    private val deleteListService: DeleteListService,
    private val replaceListItemService: ReplaceListItemService,
    private val getListsService: GetAllListService,
    private val createListItemService: CreateListItemService,
    private val deleteListItemService: DeleteListItemService,
    private val getShoppingListService: GetShoppingListService,
    private val getDefaultShoppingListService: GetDefaultShoppingListService,
    private val getShoppingListItemService: GetShoppingListItemService,
    private val updateListItemService: UpdateListItemService,
    private val deleteMultipleListItemService: DeleteMultipleListItemService,
    private val editListSortOrderService: EditListSortOrderService,
    private val editItemSortOrderService: EditItemSortOrderService,
    private val addMultipleListItemService: AddMultipleListItemService
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
    fun createList(@Header(PROFILE_ID) guestId: String, @Valid @Body listRequestTO: ListRequestTO): Mono<ListResponseTO> {
        return createListService.createList(guestId, listRequestTO).map { validate(it) }
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
        @QueryValue("start_x") startX: BigDecimal?,
        @QueryValue("start_y") startY: BigDecimal?,
        @QueryValue("start_floor") startFloor: String?,
        @QueryValue("allow_expired_items") allowExpiredItems: Boolean? = false,
        @QueryValue("include_items") includeItems: ItemIncludeFields?
    ): Mono<MutableHttpResponse<ListResponseTO>> {
        if (locationId == null) {
            return throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("location_id is incorrect $locationId")))
        }
        return getShoppingListService.getList(guestId, locationId!!, listId, startX, startY, startFloor, sortFieldBy, sortOrderBy,
            allowExpiredItems ?: false, includeItems ?: ItemIncludeFields.ALL)
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
        @QueryValue("start_x") startX: BigDecimal?,
        @QueryValue("start_y") startY: BigDecimal?,
        @QueryValue("start_floor") startFloor: String?,
        @QueryValue("allow_expired_items") allowExpiredItems: Boolean? = false,
        @QueryValue("include_items") includeItems: ItemIncludeFields?
    ): Mono<MutableHttpResponse<ListResponseTO>> {
        return getDefaultShoppingListService.getDefaultList(guestId, locationId, startX, startY, startFloor, sortFieldBy, sortOrderBy,
            allowExpiredItems ?: false, includeItems ?: ItemIncludeFields.ALL)
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
            sortFieldBy, sortOrderBy)
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
        @QueryValue("location_id") locationId: Long?,
        @PathVariable("list_id") listId: UUID,
        @PathVariable("list_item_id") listItemId: UUID
    ): Mono<MutableHttpResponse<ListItemResponseTO>> {
        if (locationId == null) {
            return throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("location_id is incorrect $locationId")))
        }
        return getShoppingListItemService.getListItem(locationId, listId, listItemId).zipWith(Mono.subscriberContext())
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
        @PathVariable("list_id") listId: UUID,
        @QueryValue("location_id") locationId: Long?,
        @Valid @Body listItemRequestTO: ListItemRequestTO
    ): Mono<ListItemResponseTO> {
        if (locationId == null) {
            return throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("location_id is incorrect $locationId")))
        }
        return createListItemService.createListItem(listId, locationId, listItemRequestTO)
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

    /**
     * Multiple delete of list items.
     *
     * @param listId list id
     * @QueryValue("include_items") includeItems: ITEM_INCLUDE_FIELDS? = null
     * @param deleteFields delete fields
     *
     */
    @Delete("/{list_id}/list_items")
    @Status(HttpStatus.OK)
    fun multipleDeleteListItem(
        @Header(PROFILE_ID) guestId: GuestId,
        @PathVariable("list_id") listId: UUID,
        @QueryValue("itemIds") itemIds: String? = null,
        @QueryValue("include_items") deleteFields: ItemIncludeFields? = null
    ): Mono<ListItemMultiDeleteResponseTO> {
        return deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, itemIds, deleteFields)
    }

    /**
     * Multiple addition of list items
     *
     *@param listId list id
     *@param location_id location id
     *@return list multi items response transfer object
     */
    @Post("/{list_id}/multiple_list_items")
    @Status(HttpStatus.CREATED)
    fun multipleAddListItem(
        @Header(PROFILE_ID) guestId: GuestId,
        @PathVariable("list_id") listId: UUID,
        @QueryValue("location_id") locationId: Long,
        @Valid @Body listItemMultiAddRequestTO: ListItemMultiAddRequestTO
    ): Mono<ListItemMultiAddResponseTO> {
        return addMultipleListItemService.addMultipleListItem(guestId, listId, locationId, listItemMultiAddRequestTO.items)
    }

    /**
     * Edit list sort order for the guest.
     *
     * @param guestId guest id
     * @param editListSortOrderRequestTO body for editing list sort order
     *
     */
    @Put("/guest_preferences/list_sort_order")
    @Status(HttpStatus.OK)
    fun editListSortOrder(
        @Header("profile_id") guestId: String,
        @Valid @Body editListSortOrderRequestTO: EditListSortOrderRequestTO
    ): Mono<Boolean> {
        return editListSortOrderService.editListPosition(guestId, editListSortOrderRequestTO)
    }

    /**
     * Edit Item Sort order for given list.
     *
     * @param editItemSortOrderRequestTO body for edit item sort order
     */
    @Put("/guest_preferences/item_sort_order")
    @Status(HttpStatus.OK)
    fun editItemsSortOrder(
        @Valid @Body editItemSortOrderRequestTO: EditItemSortOrderRequestTO
    ): Mono<Boolean> {
        return editItemSortOrderService.editItemPosition(editItemSortOrderRequestTO)
    }

    /**
     * Replace Item for given list.
     *
     * @param listItemTO body for replace item
     * @param sourceitemid source item id
     */
    @Put("/{list_id}/replace_list_item/{source_item_id}")
    @Status(HttpStatus.OK)
    fun replaceListItem(
        @Header(PROFILE_ID) guestId: String,
        @QueryValue("location_id") locationId: Long?,
        @PathVariable("list_id") listId: UUID,
        @PathVariable("source_item_id") sourceItemId: UUID,
        @Valid @Body listItemRequestTO: ListItemRequestTO
    ): Mono<ListItemResponseTO> {
        if (locationId == null) {
            return throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("location_id is incorrect $locationId")))
        }
        return replaceListItemService.replaceListItem(guestId, listId, sourceItemId, locationId, listItemRequestTO)
    }
}
