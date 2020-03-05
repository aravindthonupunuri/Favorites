package com.tgt.favorites.api.util

import com.tgt.lists.msgbus.EventType
import com.tgt.lists.msgbus.event.EventHeaderFactory
import com.tgt.lists.msgbus.event.EventHeaders

class EventHeadersDataProvider {

    companion object {
        val eventHeaderFactory = EventHeaderFactory(eventRetryIntervalSecs = 2, maxDlqEventRetryCount = 3)
    }
    fun newEventHeaders(eventType: EventType): EventHeaders {
        return eventHeaderFactory.newEventHeaders(eventType = eventType)
    }

    fun setErrorHeaders(eventHeaders: EventHeaders): EventHeaders {
        return eventHeaderFactory.nextRetryHeaders(eventHeaders = eventHeaders, errorCode = 500)
    }

    fun nextRetryHeaders(eventHeaders: EventHeaders, message: String = ""): EventHeaders {
        return eventHeaderFactory.nextRetryHeaders(eventHeaders = eventHeaders, errorCode = 500, errorMsg = message)
    }
}
