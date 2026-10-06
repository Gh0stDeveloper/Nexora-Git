package com.nexora.git.core.editor.lsp

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OptionalLanguageServerRegistry @Inject constructor(
    private val providers: Set<
        @JvmSuppressWildcards LanguageServerClient
    >,
) {
    fun providersFor(languageId: String): List<LanguageServerClient> =
        providers
            .filter { client ->
                client.descriptor.languageIds.any {
                    it.equals(languageId, ignoreCase = true)
                }
            }
            .sortedBy { it.descriptor.displayName.lowercase() }

    fun stateFor(languageId: String): List<LanguageServerState> =
        providersFor(languageId).map { it.state.value }

    val bundledProviderCount: Int
        get() = providers.size
}
