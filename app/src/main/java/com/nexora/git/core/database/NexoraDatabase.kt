package com.nexora.git.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkspaceEntity::class,
        AuthAccountEntity::class,
        GitHubRepositoryEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class NexoraDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun authAccountDao(): AuthAccountDao
    abstract fun githubRepositoryDao(): GitHubRepositoryDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS github_repositories (
                        accountId INTEGER NOT NULL,
                        repositoryId INTEGER NOT NULL,
                        nodeId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        fullName TEXT NOT NULL,
                        ownerLogin TEXT NOT NULL,
                        ownerAvatarUrl TEXT,
                        description TEXT,
                        privateRepository INTEGER NOT NULL,
                        fork INTEGER NOT NULL,
                        archived INTEGER NOT NULL,
                        visibility TEXT NOT NULL,
                        language TEXT,
                        defaultBranch TEXT NOT NULL,
                        cloneUrl TEXT NOT NULL,
                        htmlUrl TEXT NOT NULL,
                        stars INTEGER NOT NULL,
                        forks INTEGER NOT NULL,
                        openIssues INTEGER NOT NULL,
                        sizeKb INTEGER NOT NULL,
                        updatedAt TEXT,
                        pushedAt TEXT,
                        permissionAdmin INTEGER NOT NULL,
                        permissionMaintain INTEGER NOT NULL,
                        permissionPush INTEGER NOT NULL,
                        permissionTriage INTEGER NOT NULL,
                        permissionPull INTEGER NOT NULL,
                        cachedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(accountId, repositoryId)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_github_repositories_accountId_fullName
                    ON github_repositories(accountId, fullName)
                    """.trimIndent(),
                )
            }
        }
    }
}
