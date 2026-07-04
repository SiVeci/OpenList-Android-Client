package io.openlist.client.data.repository

import io.openlist.client.core.database.entity.RemoteTaskEntity
import io.openlist.client.core.model.RemoteTask
import io.openlist.client.core.model.TaskStateMapper
import io.openlist.client.core.model.UnifiedTaskStatus
import io.openlist.client.core.network.dto.TaskInfoDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

/** Shared TaskInfoDto/RemoteTaskEntity/RemoteTask mapping used by both
 * TaskRepositoryImpl (polled tasks) and OfflineDownloadRepositoryImpl (the
 * tasks returned inline by a submit response). */
internal fun TaskInfoDto.toRemoteTaskEntity(instanceId: String, taskType: String, cachedAt: Long, json: Json) = RemoteTaskEntity(
    id = id,
    instanceId = instanceId,
    taskType = taskType,
    title = name,
    stateRaw = state,
    status = TaskStateMapper.map(state).name,
    progress = progress.toInt().coerceIn(0, 100),
    targetPath = null,
    errorMessage = error.ifBlank { null },
    totalBytes = totalBytes,
    rawJson = json.encodeToString(this),
    startTime = startTime?.let { parseIsoTimestamp(it) },
    endTime = endTime?.let { parseIsoTimestamp(it) },
    cachedAt = cachedAt,
)

internal fun RemoteTaskEntity.toRemoteTask() = RemoteTask(
    id = id,
    instanceId = instanceId,
    taskType = taskType,
    title = title,
    status = runCatching { UnifiedTaskStatus.valueOf(status) }.getOrDefault(UnifiedTaskStatus.UNKNOWN),
    progress = progress,
    targetPath = targetPath,
    errorMessage = errorMessage,
    totalBytes = totalBytes,
    startTime = startTime,
    endTime = endTime,
)

internal fun parseIsoTimestamp(raw: String): Long? =
    runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
