package com.nexora.git.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WorkspaceEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NexoraDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
}
