package com.tgt.shoppinglist.persistence


import com.tgt.lists.lib.api.domain.model.List
import com.tgt.lists.lib.api.persistence.ListRepository
import com.tgt.shoppinglist.util.BasePersistenceFunctionalTest
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Stepwise

import javax.inject.Inject

@MicronautTest
@Stepwise
class ListRepositoryFunctionalTest extends BasePersistenceFunctionalTest {

    @Inject
    ListRepository listRepository
    String guestId = "100"
    @Shared
    UUID listId
    @Shared
    String sortOrder

    def setupSpec() {
        listId = UUID.randomUUID()
        sortOrder = UUID.randomUUID().toString() + "," + UUID.randomUUID().toString()
        truncate()
    }

    def "test save"() {
        given:
        def listItemSortOrder = new List(listId, sortOrder, null, null)

        when:
        def result = listRepository.save(listItemSortOrder).block()

        then:
        result != null
        result.listId == listId
        result.listItemSortOrder == sortOrder
    }

    def "test findBySortOrderId"() {

        when:
        def result = listRepository.find(listId).block()

        then:
        result != null
        result.listId == listId
        result.listItemSortOrder == sortOrder
    }
}
