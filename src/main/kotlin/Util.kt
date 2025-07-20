package bose.ankush

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient
import com.google.cloud.secretmanager.v1.SecretVersionName

internal fun getSecretValue(secretName: String): String {
    val client = SecretManagerServiceClient.create()
    val secretVersionName = SecretVersionName.of(
        System.getenv("GCP_PROJECT_ID"),
        secretName,
        "1"
    )
    val response = client.accessSecretVersion(secretVersionName)
    return response.payload.data.toStringUtf8()
}
