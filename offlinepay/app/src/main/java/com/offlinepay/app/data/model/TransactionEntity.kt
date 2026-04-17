package com.offlinepay.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TxStatus {
    PENDING_TRANSFER,    // created by sender, not yet transferred
    PENDING_SETTLEMENT,  // accepted by receiver / sent by sender, awaits internet
    SYNCING,             // currently being settled by WorkManager
    COMPLETED,
    FAILED,
    REJECTED
}

enum class TxDirection { OUTGOING, INCOMING }

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val txId: String,
    val senderId: String,
    val receiverId: String,
    val amountMinor: Long,
    val currency: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val status: TxStatus,
    val direction: TxDirection,
    /** Full signed token JSON (for retry / re-verify). */
    val tokenJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastError: String? = null,
    val attempts: Int = 0
)
