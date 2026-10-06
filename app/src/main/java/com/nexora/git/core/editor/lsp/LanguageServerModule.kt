package com.nexora.git.core.editor.lsp

import dagger.Module
import dagger.hilt.InstallIn
import dagger.multibindings.Multibinds
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LanguageServerModule {

    @Multibinds
    abstract fun languageServerClients():
        Set<LanguageServerClient>
}
