package com.tgt.shoppinglist.kafka.api.app

import com.tgt.lists.lib.api.service.CreateListService
import com.tgt.lists.lib.api.service.EditListSortOrderService
import com.tgt.lists.lib.api.transport.EditListSortOrderRequestTO
import com.tgt.lists.lib.api.transport.ListRequestTO
import com.tgt.lists.lib.api.transport.ListResponseTO
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import reactor.core.publisher.Mono

@Controller("/testserver")
class TestController {

    private final CreateListService createListService
    private final EditListSortOrderService editListSortOrderService

    TestController(CreateListService createListService,  EditListSortOrderService editListSortOrderService) {
        this.createListService = createListService
        this.editListSortOrderService = editListSortOrderService
    }

    @Post("/list_create")
    @Status(HttpStatus.CREATED)
    Mono<ListResponseTO> createList(@Header("profile_id") String guestId, @Body ListRequestTO listRequestTO) {
        return createListService.createList(guestId, listRequestTO)
    }

    @Post("/list_sort_order")
    @Status(HttpStatus.CREATED)
    Mono<Boolean> editListSortOrder(@Header("profile_id") String guestId, @Body EditListSortOrderRequestTO editListSortOrderRequestTO) {
        return editListSortOrderService.editListPosition(guestId, editListSortOrderRequestTO)
    }
}
