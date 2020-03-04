package com.tgt.shoppinglist.api.util

import com.target.platform.connector.config.FileSource
import com.tgt.lists.lib.api.util.TAPEnvironmentLoader

class TestTAPEnvironmentLoader : TAPEnvironmentLoader() {

    var blockRegion = "" // If set to non-empty region, then block this region and return null file contents for testing

    override fun getConfigSourceFile(filename: String): FileSource? {

        var encodedData = ""

        if (blockRegion.isNotEmpty()) {
            if (filename.contains(blockRegion)) {
                return null
            }
        }
        if (filename.startsWith("client-truststore")) {
            encodedData = "YXBpOgogIG9hdXRoOgogICAgdXJsOiBodHRwczovL29hdXRoLmlhbS5wZXJmLnRhcmdldC5jb20KICAgIGNsaWVudC1pZDogImxpc3RzX3Y0X3JvcGMiCiAgICBjbGllbnQtc2VjcmV0OiAic2VjcmV0MSIK"
        } else if (filename.startsWith("lists-bus-keystore")) {
            encodedData = "Zmx5d2F5OgogIHNjaGVtYXM6IGxpc3R2NF9zdGcKICBkYXRhc291cmNlczoKICAgIGRlZmF1bHQ6CiAgICAgIGxvY2F0aW9uczogY2xhc3NwYXRoOmRiLm1pZ3JhdGlvbgo="
        } else if (filename.startsWith("secret-env-")) {
            encodedData = "a2Fma2FlbnY6CiAgc2VydmVyczoga2Fma2EtdHRjLWFwcC5kZXYudGFyZ2V0LmNvbTo5MDkzCiAga2V5c3RvcmU6CiAgICBwYXNzd29yZDoga2V5c3RvcmVwYXNzMQogIHRydXN0c3RvcmU6CiAgICBwYXNzd29yZDogdHJ1ZXN0c3RvcmVwYXNzMQo="
        } else if (filename.startsWith("application-env-")) {
            encodedData = "ZGF0YXNvdXJjZXM6CiAgICB1c2VybmFtZTogImRidXNlciIKICAgIHBhc3N3b3JkOiAiZGJwd2QiCiAgICBkaWFsZWN0OiBQT1NUR1JFUwo="
        }

        if (!encodedData.isEmpty()) {
            return FileSource.fromBase64Encoded(encodedData)
        }
        return null
    }
}
