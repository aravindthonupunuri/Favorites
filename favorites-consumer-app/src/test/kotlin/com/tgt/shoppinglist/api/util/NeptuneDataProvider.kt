package com.tgt.shoppinglist.api.util

import com.tgt.lists.neptune.transport.ItemInformation
import com.tgt.lists.neptune.transport.StorePathingResponse

class NeptuneDataProvider {
    fun getStoreResponse(tcinList: List<String>): StorePathingResponse {
        return StorePathingResponse(storePath = tcinList.map { ItemInformation(tcin = it) }.toTypedArray())
    }
}
