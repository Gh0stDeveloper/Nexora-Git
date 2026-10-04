package com.nexora.git.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkspaceEntity::class,
        AuthAccountEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class NexoraDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun authAccountDao(): AuthAccountDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS auth_accounts (
                        accountId INTEGER NOT NULL,
                        login TEXT NOT NULL,
                        name TEXT,
                        avatarUrl TEXT,
                        tokenExpiresAtEpochMillis INTEGER,
                        refreshTokenExpiresAtEpochMillis INTEGER,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(accountId)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
