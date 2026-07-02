package io.openlist.client.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "instances")
data class InstanceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long,
    val isCurrent: Boolean,
    val note: String?,
)
