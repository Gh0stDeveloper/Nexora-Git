package com.nexora.git.core.editor

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EditorIntelligenceModule {

    @Binds
    @Singleton
    abstract fun bindEditorSyntaxEngine(
        implementation: TreeSitterSyntaxEngine,
    ): EditorSyntaxEngine
}
