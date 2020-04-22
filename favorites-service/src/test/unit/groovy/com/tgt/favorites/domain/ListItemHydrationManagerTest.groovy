package com.tgt.favorites.domain

import com.tgt.favorites.api.util.ListDataProvider
import com.tgt.favorites.api.util.RedskyDataProvider
import com.tgt.favorites.client.redsky.RedSkyClient
import com.tgt.favorites.client.redsky.RedskyResponseTO
import com.tgt.favorites.client.redsky.getItemHydrationwithvariation.ItemDetailWithVariationVO
import com.tgt.favorites.client.redsky.getitemhydration.ItemDetailVO
import com.tgt.favorites.transport.ItemRelationshipType
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.util.ItemType
import reactor.core.publisher.Mono
import spock.lang.Specification

class ListItemHydrationManagerTest extends Specification {

    ListItemHydrationManager listItemHydrationManager
    RedSkyClient redSkyClient
    ListDataProvider listDataProvider
    RedskyDataProvider redskyDataProvider

    Long storeId = 1375

    def setup() {
        redSkyClient = Mock(RedSkyClient)
        listItemHydrationManager = new ListItemHydrationManager(redSkyClient, 28)
        listDataProvider = new ListDataProvider()
        redskyDataProvider = new RedskyDataProvider()
    }

    def "Test getItemHydration() when list items is null"() {
        when:
        def actual = listItemHydrationManager.getItemHydration(storeId, null).block()

        then:
        actual.isEmpty()
    }

    def "Test getItemHydration when there is no variation parent and redsky returns no data"() {
        given:
        def tcin1 = "1"; def tcin2 = "2"; def tcin3 = "3"
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), tcin1, "first", null, ItemType.TCIN, ItemRelationshipType.SA.value)
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), tcin2, "second", null, ItemType.TCIN, ItemRelationshipType.SA.value)
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), tcin3, "third", null, ItemType.TCIN, ItemRelationshipType.SA.value)

        when:
        def actual = listItemHydrationManager.getItemHydration(storeId, [item1, item2, item3]).block()

        then:
        1 * redSkyClient.getItemHydration(storeId.toString(), "$tcin1,$tcin2,$tcin3") >> Mono.just(new RedskyResponseTO())
        0 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), _)

        actual.size() == 3
        actual[0].tcin == tcin1
        actual[1].tcin == tcin2
        actual[2].tcin == tcin3
    }

    def "Test getItemHydration when there is no variation parent and redsky returns 404"() {
        given:
        def tcin1 = "1"; def tcin2 = "2"; def tcin3 = "3"
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), tcin1, "first", null, ItemType.TCIN, ItemRelationshipType.SA.value)
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), tcin2, "second", null, ItemType.TCIN, ItemRelationshipType.SA.value)
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), tcin3, "third", null, ItemType.TCIN, ItemRelationshipType.SA.value)

        when:
        def actual = listItemHydrationManager.getItemHydration(storeId, [item1, item2, item3]).block()

        then:
        1 * redSkyClient.getItemHydration(storeId.toString(), "$tcin1,$tcin2,$tcin3") >> Mono.empty()
        0 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), _)

        actual.size() == 3
        actual[0].tcin == tcin1
        actual[1].tcin == tcin2
        actual[2].tcin == tcin3
    }

    def "Test getItemHydration when there is no variation parent and redsky throws error"() {
        given:
        def tcin1 = "1"; def tcin2 = "2"; def tcin3 = "3"
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), tcin1, "first", null, ItemType.TCIN, ItemRelationshipType.SA.value)
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), tcin2, "second", null, ItemType.TCIN, ItemRelationshipType.SA.value)
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), tcin3, "third", null, ItemType.TCIN, ItemRelationshipType.SA.value)

        when:
        def actual = listItemHydrationManager.getItemHydration(storeId, [item1, item2, item3]).block()

        then:
        1 * redSkyClient.getItemHydration(storeId.toString(), "$tcin1,$tcin2,$tcin3") >> Mono.error(new RuntimeException("some exception"))
        0 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), _)

        actual.size() == 3
        actual[0].tcin == tcin1
        actual[1].tcin == tcin2
        actual[2].tcin == tcin3
    }

    def "Test getItemHydration when there is no variation parent"() {
        given:
        def tcin1 = "1"; def tcin2 = "2"; def tcin3 = "3"
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), tcin1, "first", null, ItemType.TCIN, ItemRelationshipType.SA.value)
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), tcin2, "second", null, ItemType.TCIN, ItemRelationshipType.SA.value)
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), tcin3, "third", null, ItemType.TCIN, ItemRelationshipType.SA.value)
        ItemDetailVO itemDetailVO = redskyDataProvider.getItemDetailVO([tcin1, tcin2, tcin3])

        when:
        def actual = listItemHydrationManager.getItemHydration(storeId, [item1, item2, item3]).block()

        then:
        1 * redSkyClient.getItemHydration(storeId.toString(), "$tcin1,$tcin2,$tcin3") >> Mono.just(new RedskyResponseTO(null, itemDetailVO))
        0 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), _)

        actual.size() == 3
        actual[0].tcin == tcin1
        actual[0].item == itemDetailVO.products[0].item
        actual[0].price == itemDetailVO.products[0].price
        actual[0].averageOverallRating == itemDetailVO.products[0].ratingsAndReviews.statistics.rating.average
        actual[0].totalReviewCount == itemDetailVO.products[0].ratingsAndReviews.statistics.reviewCount
        actual[1].tcin == tcin2
        actual[1].item == itemDetailVO.products[1].item
        actual[1].price == itemDetailVO.products[1].price
        actual[1].averageOverallRating == itemDetailVO.products[1].ratingsAndReviews.statistics.rating.average
        actual[1].totalReviewCount == itemDetailVO.products[1].ratingsAndReviews.statistics.reviewCount
        actual[2].tcin == tcin3
        actual[2].item == itemDetailVO.products[2].item
        actual[2].price == itemDetailVO.products[2].price
        actual[2].averageOverallRating == itemDetailVO.products[2].ratingsAndReviews.statistics.rating.average
        actual[2].totalReviewCount == itemDetailVO.products[2].ratingsAndReviews.statistics.reviewCount
    }

    def "Test getItemHydration when there is no variation child and redsky returns no data"() {
        given:
        def tcin1 = "1"; def tcin2 = "2"; def tcin3 = "3"
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), tcin1, "first", null, ItemType.TCIN, ItemRelationshipType.VAP.value)
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), tcin2, "second", null, ItemType.TCIN, ItemRelationshipType.VAP.value)
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), tcin3, "third", null, ItemType.TCIN, ItemRelationshipType.VAP.value)

        when:
        def actual = listItemHydrationManager.getItemHydration(storeId, [item1, item2, item3]).block()

        then:
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin1") >> Mono.just(new RedskyResponseTO())
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin2") >> Mono.just(new RedskyResponseTO())
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin3") >> Mono.just(new RedskyResponseTO())
        0 * redSkyClient.getItemHydration(storeId.toString(), _)

        actual.size() == 3
        actual[0].tcin == tcin1
        actual[1].tcin == tcin2
        actual[2].tcin == tcin3
    }

    def "Test getItemHydration when there is no variation child and redsky returns 404"() {
        given:
        def tcin1 = "1"; def tcin2 = "2"; def tcin3 = "3"
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), tcin1, "first", null, ItemType.TCIN, ItemRelationshipType.VAP.value)
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), tcin2, "second", null, ItemType.TCIN, ItemRelationshipType.VAP.value)
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), tcin3, "third", null, ItemType.TCIN, ItemRelationshipType.VAP.value)

        when:
        def actual = listItemHydrationManager.getItemHydration(storeId, [item1, item2, item3]).block()

        then:
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin1") >> Mono.empty()
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin2") >> Mono.empty()
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin3") >> Mono.empty()
        0 * redSkyClient.getItemHydration(storeId.toString(), _)

        actual.size() == 3
        actual[0].tcin == tcin1
        actual[1].tcin == tcin2
        actual[2].tcin == tcin3
    }

    def "Test getItemHydration when there is no variation child and redsky throws error"() {
        given:
        def tcin1 = "1"; def tcin2 = "2"; def tcin3 = "3"
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), tcin1, "first", null, ItemType.TCIN, ItemRelationshipType.VAP.value)
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), tcin2, "second", null, ItemType.TCIN, ItemRelationshipType.VAP.value)
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), tcin3, "third", null, ItemType.TCIN, ItemRelationshipType.VAP.value)

        when:
        def actual = listItemHydrationManager.getItemHydration(storeId, [item1, item2, item3]).block()

        then:
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin1") >> Mono.error(new RuntimeException("some exception"))
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin2") >> Mono.error(new RuntimeException("some exception"))
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin3") >> Mono.error(new RuntimeException("some exception"))
        0 * redSkyClient.getItemHydration(storeId.toString(), _)

        actual.size() == 3
        actual[0].tcin == tcin1
        actual[1].tcin == tcin2
        actual[2].tcin == tcin3
    }

    def "Test getItemHydration when there is no variation child"() {
        given:
        def tcin1 = "1"; def tcin2 = "2"; def tcin3 = "3"
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), tcin1, "first", null, ItemType.TCIN, ItemRelationshipType.VAP.value)
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), tcin2, "second", null, ItemType.TCIN, ItemRelationshipType.VAP.value)
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), tcin3, "third", null, ItemType.TCIN, ItemRelationshipType.VAP.value)

        ItemDetailWithVariationVO itemDetailWithVariationVO1 = redskyDataProvider.getItemDetailWithVariationVO(tcin1)
        ItemDetailWithVariationVO itemDetailWithVariationVO2 = redskyDataProvider.getItemDetailWithVariationVO(tcin2)
        ItemDetailWithVariationVO itemDetailWithVariationVO3 = redskyDataProvider.getItemDetailWithVariationVO(tcin3)

        when:
        def actual = listItemHydrationManager.getItemHydration(storeId, [item1, item2, item3]).block()

        then:
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin1") >> Mono.just(new RedskyResponseTO(null, itemDetailWithVariationVO1))
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin2") >> Mono.just(new RedskyResponseTO(null, itemDetailWithVariationVO2))
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin3") >> Mono.just(new RedskyResponseTO(null, itemDetailWithVariationVO3))
        0 * redSkyClient.getItemHydration(storeId.toString(), _)

        actual.size() == 3
        actual[0].tcin == tcin1
        actual[0].item == itemDetailWithVariationVO1.product.item
        actual[0].price == itemDetailWithVariationVO1.product.price
        actual[0].averageOverallRating == itemDetailWithVariationVO1.product.ratingsAndReviews.statistics.rating.average
        actual[0].totalReviewCount == itemDetailWithVariationVO1.product.ratingsAndReviews.statistics.reviewCount
        actual[1].tcin == tcin2
        actual[1].item == itemDetailWithVariationVO2.product.item
        actual[1].price == itemDetailWithVariationVO2.product.price
        actual[1].averageOverallRating == itemDetailWithVariationVO2.product.ratingsAndReviews.statistics.rating.average
        actual[1].totalReviewCount == itemDetailWithVariationVO2.product.ratingsAndReviews.statistics.reviewCount
        actual[2].tcin == tcin3
        actual[2].item == itemDetailWithVariationVO3.product.item
        actual[2].price == itemDetailWithVariationVO3.product.price
        actual[2].averageOverallRating == itemDetailWithVariationVO3.product.ratingsAndReviews.statistics.rating.average
        actual[2].totalReviewCount == itemDetailWithVariationVO3.product.ratingsAndReviews.statistics.reviewCount
    }

    def "Test getItemHydration when there is variation parent and child"() {
        given:
        def tcin1 = "1"; def tcin2 = "2"; def tcin3 = "3"
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), tcin1, "first", null, ItemType.TCIN, ItemRelationshipType.VPC.value)
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), tcin2, "second", null, ItemType.TCIN, ItemRelationshipType.SA.value)
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), tcin3, "third", null, ItemType.TCIN, ItemRelationshipType.SA.value)
        ItemDetailVO itemDetailVO = redskyDataProvider.getItemDetailVO([tcin2, tcin3])
        ItemDetailWithVariationVO itemDetailWithVariationVO1 = redskyDataProvider.getItemDetailWithVariationVO(tcin1)

        when:
        def actual = listItemHydrationManager.getItemHydration(storeId, [item1, item2, item3]).block()

        then:
        1 * redSkyClient.getItemHydration(storeId.toString(), "$tcin2,$tcin3") >> Mono.just(new RedskyResponseTO(null, itemDetailVO))
        1 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin1") >> Mono.just(new RedskyResponseTO(null, itemDetailWithVariationVO1))
        0 * redSkyClient.getItemHydrationWithVariation(storeId.toString(), _)

        actual.size() == 3
        actual[0].tcin == tcin2
        actual[0].item == itemDetailVO.products[0].item
        actual[0].price == itemDetailVO.products[0].price
        actual[0].averageOverallRating == itemDetailVO.products[0].ratingsAndReviews.statistics.rating.average
        actual[0].totalReviewCount == itemDetailVO.products[0].ratingsAndReviews.statistics.reviewCount
        actual[1].tcin == tcin3
        actual[1].item == itemDetailVO.products[1].item
        actual[1].price == itemDetailVO.products[1].price
        actual[1].averageOverallRating == itemDetailVO.products[1].ratingsAndReviews.statistics.rating.average
        actual[1].totalReviewCount == itemDetailVO.products[1].ratingsAndReviews.statistics.reviewCount
        actual[2].tcin == tcin1
        actual[2].item == itemDetailWithVariationVO1.product.item
        actual[2].price == itemDetailWithVariationVO1.product.price
        actual[2].averageOverallRating == itemDetailWithVariationVO1.product.ratingsAndReviews.statistics.rating.average
        actual[2].totalReviewCount == itemDetailWithVariationVO1.product.ratingsAndReviews.statistics.reviewCount

    }
}
