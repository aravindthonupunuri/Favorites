package com.tgt.shoppinglist.persistence

import com.tgt.lists.lib.api.domain.model.GuestPreference
import com.tgt.lists.lib.api.persistence.GuestPreferenceRepository
import com.tgt.shoppinglist.util.BasePersistenceFunctionalTest
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Stepwise

import javax.inject.Inject

@MicronautTest
@Stepwise
class GuestPreferenceRepositoryFunctionalTest extends BasePersistenceFunctionalTest  {

    @Inject
    GuestPreferenceRepository guestListRepository
    String guestId = "100"
    @Shared
    String sortOrder = ""
    @Shared
    String newOrder = ""

    def setupSpec() {
        sortOrder = UUID.randomUUID().toString() + "," + UUID.randomUUID().toString()
        newOrder = sortOrder + "," + UUID.randomUUID().toString()
        truncate()
    }

    def "test save"() {
        given:
        def listSortOrder = new GuestPreference(guestId, sortOrder, null, null)

        when:
        def result = guestListRepository.save(listSortOrder).block()

        then:
        result != null
        result.guestId == guestId
        result.listSortOrder == sortOrder
        result.dateCreated != null
        result.dateUpdated != null
    }

    def "test findByGuestId"() {
        when:
        def result = guestListRepository.find(guestId).block()

        then:
        result != null
        result.guestId == guestId
        result.listSortOrder == sortOrder
    }

    def "test update"() {
        when:
        def result = guestListRepository.update(guestId, newOrder).block()

        then:
        result == 1
    }

    def "test findByGuestId after update"() {
        when:
        def result = guestListRepository.find(guestId).block()

        then:
        result != null
        result.guestId == guestId
        result.listSortOrder == newOrder
    }
}
