package com.tgt.favorites.api.util

import com.tgt.lists.cartwheel.client.transport.CartWheelItemOffer
import com.tgt.lists.cartwheel.client.transport.CartWheelOffer
import com.tgt.lists.cartwheel.client.transport.Meta
import com.tgt.lists.lib.api.domain.OfferCountManager

@Suppress("UNCHECKED_CAST")
class CartWheelDataProvider {

    fun getOfferCount(itemIdentifier: String?, count: Int?): OfferCountManager.OfferCount {
        return OfferCountManager.OfferCount(itemIdentifier = itemIdentifier, count = count)
    }

    fun getCartWheelOffers(count: Int): List<CartWheelOffer> {
        val cartWheelOfferList = ArrayList<CartWheelOffer>()
        cartWheelOfferList.add(CartWheelOffer(meta = Meta(count = count)))

        return cartWheelOfferList
    }

    fun getCartWheelItemOffers(count: Int): List<CartWheelItemOffer> {
        val cartWheelItemOfferList = ArrayList<CartWheelItemOffer>()
        for (i in 1..count) {
            cartWheelItemOfferList.add(CartWheelItemOffer(id = i, channel = "CWL", value = "$i% off", title = "title$i", inStore = true, online = false))
        }

        return cartWheelItemOfferList
    }
}
