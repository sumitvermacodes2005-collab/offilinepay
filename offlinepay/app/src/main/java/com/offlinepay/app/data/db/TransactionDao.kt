package com.offlinepay.app.data.db

import androidx.room.*
import com.offlinepay.app.data.model.TransactionEntity
import com.offlinepay.app.data.model.TxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status IN (:statuses)")
    suspend fun byStatus(statuses: List<TxStatus>): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE txId = :txId LIMIT 1")
    suspend fun byId(txId: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(tx: TransactionEntity): Long

    @Update
    suspend fun update(tx: TransactionEntity)
}
