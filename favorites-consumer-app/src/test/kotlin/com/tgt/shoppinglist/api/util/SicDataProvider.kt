package com.tgt.shoppinglist.api.util

import com.tgt.lists.sic.client.transport.ItemDataSources
import com.tgt.lists.sic.client.transport.ItemLocation
import com.tgt.lists.sic.client.transport.Location

class SicDataProvider {

    fun getItemLocations(
        tcinList: List<String>
    ): List<ItemLocation> {
        val list = arrayListOf<ItemLocation>()
        for (tcin in tcinList) {
            val locations = arrayListOf<Location>()
            locations.add(Location("1", "A", 1, Math.random().toFloat(), Math.random().toFloat(), ItemDataSources(null, null)))
            list.add(ItemLocation(tcin, "dpci", locations))
        }
        return list
    }
}
