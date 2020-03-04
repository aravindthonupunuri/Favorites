package com.tgt.shoppinglist.persistence

import com.tgt.lists.lib.api.persistence.GuestPreferenceRepository
import com.tgt.lists.micronaut.persistence.instrumentation.DatabaseExecTestListener
import com.tgt.lists.micronaut.persistence.instrumentation.RepositoryInstrumenter
import com.tgt.shoppinglist.util.BasePersistenceFunctionalTest
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.http.HttpRequest
import io.micronaut.test.annotation.MicronautTest
import org.jetbrains.annotations.NotNull
import spock.lang.Shared
import spock.lang.Stepwise

import javax.inject.Inject

@MicronautTest
@Stepwise
class DatabaseTimeoutMetricsFunctionalTest extends BasePersistenceFunctionalTest {

    @Inject
    GuestPreferenceRepository guestListRepository

    @Inject
    RepositoryInstrumenter repositoryInstrumenter

    String guestId = "100"

    @Shared
    boolean executeTimeout = false

    def setup() {
        repositoryInstrumenter.attachTestListener(new DatabaseExecTestListener() {
            @Override
            boolean shouldOverrideWithTimeoutQuery(@NotNull String repoName, @NotNull String methodName) {
                return executeTimeout
            }
        })
    }

    @Override
    Map<String, String> getAdditionalProperties() {
        return ["jdbc-stmt-timeout.serverStatementTimeoutMillis": "50"]
    }

    def "test timeout handling"() {
        given:
        executeTimeout = true

        when:
        guestListRepository.find(guestId).block()

        then:
        def ex = thrown(DataAccessException.class)
        ex != null
        ex.message.contains("canceling statement due to statement timeout")

        when: 'db timeout failure metrics is produced'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains("db_timeout_seconds_count{method=\"find\",repo=\"GuestPreferenceCrudRepository\",} 1.0")
    }

    def "test second timeout handling"() {
        given:
        executeTimeout = true

        when:
        guestListRepository.find(guestId).block()

        then:
        def ex = thrown(DataAccessException.class)
        ex != null
        ex.message.contains("canceling statement due to statement timeout")

        when: 'db timeout failure metrics is produced'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains("db_timeout_seconds_count{method=\"find\",repo=\"GuestPreferenceCrudRepository\",} 2.0")
    }
}
