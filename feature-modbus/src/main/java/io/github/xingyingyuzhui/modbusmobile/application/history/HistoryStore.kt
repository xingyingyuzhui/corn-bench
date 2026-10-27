package io.github.xingyingyuzhui.modbusmobile.application.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** 数据库写不下去（磁盘满或 SQLite 返回 FULL）。记录停止，通信继续。 */
class StorageFullException : Exception()

interface HistoryStore {
    suspend fun openSession(role: String, target: String, epoch: Long, startedAtMs: Long): HistorySession

    suspend fun closeSession(id: Long, endedAtMs: Long)

    /** 进程重启后，把本次启动之前仍未关闭的会话收口。 */
    suspend fun closeDangling(endedAtMs: Long, startedBeforeMs: Long)

    suspend fun insertSamples(samples: List<HistorySample>)

    suspend fun sessions(): List<HistorySession>

    suspend fun samples(sessionId: Long): List<HistorySample>

    /** 分批读取，导出不必把全部历史装进内存。 */
    fun samplePages(sessionId: Long): Flow<List<HistorySample>> = flow { emit(samples(sessionId)) }

    /** 已使用的数据库页，不包含可重用的空闲页；不是文件的物理大小。 */
    suspend fun usageBytes(): Long

    /** 保留中的会话即使出现在名单里也不删。 */
    suspend fun deleteSessions(ids: List<Long>)

    suspend fun setRetained(id: Long, retained: Boolean)
}
