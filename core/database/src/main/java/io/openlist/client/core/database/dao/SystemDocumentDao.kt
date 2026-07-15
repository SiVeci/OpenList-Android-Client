package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.openlist.client.core.database.entity.SystemDocumentEntity

@Dao
interface SystemDocumentDao {
    @Query("SELECT * FROM system_documents WHERE documentId = :documentId")
    suspend fun getById(documentId: String): SystemDocumentEntity?

    @Query("SELECT * FROM system_documents WHERE instanceId = :instanceId AND currentPath = :path AND lifecycle = 'ACTIVE' LIMIT 1")
    suspend fun getActiveByPath(instanceId: String, path: String): SystemDocumentEntity?

    @Query("SELECT * FROM system_documents WHERE instanceId = :instanceId AND parentDocumentId = :parentDocumentId AND lifecycle = 'ACTIVE' ORDER BY isDirectory DESC, displayName COLLATE NOCASE")
    suspend fun getActiveChildren(instanceId: String, parentDocumentId: String): List<SystemDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SystemDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SystemDocumentEntity>)

    @Query("UPDATE system_documents SET currentPath = NULL, lifecycle = 'TOMBSTONED', updatedAt = :updatedAt WHERE instanceId = :instanceId AND currentPath = :path AND lifecycle = 'ACTIVE'")
    suspend fun tombstonePath(instanceId: String, path: String, updatedAt: Long): Int

    @Query("UPDATE system_documents SET currentPath = NULL, lifecycle = 'TOMBSTONED', updatedAt = :updatedAt WHERE instanceId = :instanceId AND (currentPath = :pathPrefix OR currentPath LIKE :pathPrefix || '/%') AND lifecycle = 'ACTIVE'")
    suspend fun tombstonePathPrefix(instanceId: String, pathPrefix: String, updatedAt: Long): Int

    @Query("UPDATE system_documents SET parentDocumentId = :newParentDocumentId, currentPath = :newPath, lastKnownPath = :newPath, displayName = :newName, updatedAt = :updatedAt WHERE documentId = :documentId AND lifecycle = 'ACTIVE'")
    suspend fun updateDocumentLocation(documentId: String, newParentDocumentId: String?, newPath: String, newName: String, updatedAt: Long): Int

    @Query("UPDATE system_documents SET currentPath = :newPrefix || SUBSTR(currentPath, LENGTH(:oldPrefix) + 1), lastKnownPath = :newPrefix || SUBSTR(lastKnownPath, LENGTH(:oldPrefix) + 1), updatedAt = :updatedAt WHERE instanceId = :instanceId AND currentPath LIKE :oldPrefix || '/%' AND lifecycle = 'ACTIVE'")
    suspend fun updateDescendantPathPrefix(instanceId: String, oldPrefix: String, newPrefix: String, updatedAt: Long): Int

    @Query("DELETE FROM system_documents WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)

    @Transaction
    suspend fun moveMappedDocument(
        documentId: String,
        instanceId: String,
        newParentDocumentId: String?,
        oldPath: String,
        newPath: String,
        newName: String,
        updatedAt: Long,
    ) {
        updateDocumentLocation(documentId, newParentDocumentId, newPath, newName, updatedAt)
        updateDescendantPathPrefix(instanceId, oldPath, newPath, updatedAt)
    }
}
