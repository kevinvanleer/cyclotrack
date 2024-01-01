package com.kvl.cyclotrack.data

import javax.inject.Inject

class ExportRepository @Inject constructor(private val exportDao: ExportDao) {
    suspend fun save(new: Export) = exportDao.save(new)
    suspend fun update(updated: Export) = exportDao.update(updated)
    suspend fun load() = exportDao.load()
    suspend fun getUris() = exportDao.getUris()
    suspend fun delete(exports: Array<Export>) = exportDao.delete(exports)
}
