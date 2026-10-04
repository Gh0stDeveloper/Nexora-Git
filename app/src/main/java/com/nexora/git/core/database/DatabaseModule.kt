package com.nexora.git.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): NexoraDatabase = Room.databaseBuilder(
        context,
        NexoraDatabase::class.java,
        "nexora-git.db",
    )
        .addMigrations(NexoraDatabase.MIGRATION_1_2)
        .build()

    @Provides
    fun provideWorkspaceDao(
        database: NexoraDatabase,
    ): WorkspaceDao = database.workspaceDao()

    @Provides
    fun provideAuthAccountDao(
        database: NexoraDatabase,
    ): AuthAccountDao = database.authAccountDao()
}
