package com.tgt.shoppinglist.util

import com.tgt.lists.msgbus.event.EventHeaders
import org.jetbrains.annotations.NotNull

interface PreDispatchLambda {
    boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent)
}
