package com.nexora.build

import org.gradle.api.GradleException
import org.gradle.api.Project

data class ReleaseSigningSecrets(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

object NexoraBuildSecrets {
    private val requiredSigningVariables = listOf(
        "NEXORA_SIGNING_STORE_FILE",
        "NEXORA_SIGNING_STORE_PASSWORD",
        "NEXORA_SIGNING_KEY_ALIAS",
        "NEXORA_SIGNING_KEY_PASSWORD",
    )

    fun releaseSigning(project: Project): ReleaseSigningSecrets? {
        val values = requiredSigningVariables.associateWith { name ->
            project.providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
        }

        val configured = values.values.count { it != null }
        if (configured == 0) {
            return null
        }
        if (configured != requiredSigningVariables.size) {
            val missing = values.filterValues { it == null }.keys.sorted().joinToString()
            throw GradleException(
                "Partial release-signing environment detected. Missing: $missing. " +
                    "Signing secrets must be supplied together by the VPS vault or CI secret store.",
            )
        }

        return ReleaseSigningSecrets(
            storeFile = requireNotNull(values.getValue("NEXORA_SIGNING_STORE_FILE")),
            storePassword = requireNotNull(values.getValue("NEXORA_SIGNING_STORE_PASSWORD")),
            keyAlias = requireNotNull(values.getValue("NEXORA_SIGNING_KEY_ALIAS")),
            keyPassword = requireNotNull(values.getValue("NEXORA_SIGNING_KEY_PASSWORD")),
        )
    }
}
