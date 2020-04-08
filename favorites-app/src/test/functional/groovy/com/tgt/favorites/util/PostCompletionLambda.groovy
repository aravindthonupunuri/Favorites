package com.tgt.favorites.util

import com.tgt.lists.msgbus.event.EventHeaders
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

interface PostCompletionLambda {
    void onPostCompletionConsumerEvent(String topic, boolean success, @NotNull EventHeaders eventHeaders, @Nullable Object result, boolean isPoisonEvent, @Nullable Throwable error)
}
