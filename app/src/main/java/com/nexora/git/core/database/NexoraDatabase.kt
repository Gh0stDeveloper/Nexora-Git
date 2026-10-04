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
    version = 3,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN sourceDisplayName TEXT",
                )
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN sourceAuthority TEXT",
                )
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN sourceWritable INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN strategy TEXT NOT NULL DEFAULT 'MANAGED'",
                )
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN syncState TEXT NOT NULL DEFAULT 'READY'",
                )
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN lastSyncedAtEpochMillis INTEGER",
                )
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN lastScanAtEpochMillis INTEGER",
                )
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN fileCount INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN totalBytes INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN secretWarningCount INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN largeFileWarningCount INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN syncConflictCount INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
