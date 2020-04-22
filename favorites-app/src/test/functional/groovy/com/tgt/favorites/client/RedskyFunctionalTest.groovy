package com.tgt.favorites.client

import com.tgt.favorites.api.util.RedskyDataProvider
import com.tgt.favorites.client.redsky.RedSkyClient
import com.tgt.favorites.client.redsky.RedskyResponseTO
import com.tgt.favorites.client.redsky.getItemHydrationwithvariation.ItemDetailWithVariationVO
import com.tgt.favorites.client.redsky.getitemhydration.ItemDetailVO
import com.tgt.favorites.util.BaseFunctionalTest
import io.micronaut.test.annotation.MicronautTest

import javax.inject.Inject

@MicronautTest
class RedskyFunctionalTest extends BaseFunctionalTest {

    @Inject
    RedSkyClient redSkyClient

    String storeId = 1375
    RedskyDataProvider redskyDataProvider = new RedskyDataProvider()

    def "Test getItemHydration() integrity"() {
        given:
        def redskyUri = "/redsky_aggregations/v1/lists/favorites_list_item_hydration_v1"
        def tcin1 = "1"; def tcin2 = "2"; def tcin3 = "3"
        ItemDetailVO itemDetailVO = redskyDataProvider.getItemDetailVO([tcin1, tcin2, tcin3])
        RedskyResponseTO expected = new RedskyResponseTO(null, itemDetailVO)

        when:
        def actual = redSkyClient.getItemHydration(storeId.toString(), "$tcin1,$tcin2,$tcin3").block()

        then:
        1 * mockServer.get({ path -> path.contains(redskyUri)}, _) >> [status: 200, body: expected]
        actual == expected
    }

    def "Test getItemHydrationWithVariation() integrity"() {
        given:
        def redskyUri = "/redsky_aggregations/v1/lists/favorites_list_item_hydration_with_variation_v1"
        def tcin1 = "1"
        ItemDetailWithVariationVO itemDetailWithVariationVO1 = redskyDataProvider.getItemDetailWithVariationVO(tcin1)
        RedskyResponseTO expected = new RedskyResponseTO(null, itemDetailWithVariationVO1)

        when:
        def actual = redSkyClient.getItemHydrationWithVariation(storeId.toString(), "$tcin1").block()

        then:
        1 * mockServer.get({ path -> path.contains(redskyUri)}, _) >> [status: 200, body: expected]
        actual == expected
    }

}
