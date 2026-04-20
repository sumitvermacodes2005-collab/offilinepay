package com.offlinepay.app.data.repo

import com.offlinepay.app.data.crypto.TokenSigner
import com.offlinepay.app.data.db.TransactionDao
import com.offlinepay.app.data.model.*
import com.offlinepay.app.data.remote.SettleRequest
import com.offlinepay.app.data.remote.SettlementApi
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val dao: TransactionDao,
    private val signer: TokenSigner,
    private val api: SettlementApi,
    moshi: Moshi
) {
    val tokenAdapter: JsonAdapter<PaymentToken> =
        moshi.adapter(PaymentToken::class.java)

    companion object {
        const val TOKEN_TTL_MS = 5 * 60 * 1000L  // 5 minutes
    }

    fun observeAll(): Flow<List<TransactionEntity>> = dao.observeAll()

    suspend fun createOutgoing(
        senderId: String,
        receiverId: String,
        amountMinor: Long,
        currency: String = "INR"
    ): PaymentToken {
        val now = System.currentTimeMillis()
        val payload = TokenPayload(
            txId = UUID.randomUUID().toString(),
            senderId = senderId,
            receiverId = receiverId,
            amountMinor = amountMinor,
            currency = currency,
            issuedAtMillis = now,
            expiresAtMillis = now + TOKEN_TTL_MS,
            nonce = UUID.randomUUID().toString()
        )
        val token = signer.sign(payload)
        val json = tokenAdapter.toJson(token)
        dao.insertIfAbsent(
            TransactionEntity(
                txId = payload.txId,
                senderId = payload.senderId,
                receiverId = payload.receiverId,
                amountMinor = payload.amountMinor,
                currency = payload.currency,
                issuedAtMillis = payload.issuedAtMillis,
                expiresAtMillis = payload.expiresAtMillis,
                status = TxStatus.PENDING_SETTLEMENT,
                direction = TxDirection.OUTGOING,
                tokenJson = json
            )
        )
        return token
    }

    sealed interface IncomingResult {
        data class Ok(val token: PaymentToken, val payload: TokenPayload) : IncomingResult
        data object BadSignature : IncomingResult
        data object Expired : IncomingResult
        data object Duplicate : IncomingResult
        data class Malformed(val msg: String) : IncomingResult
    }

    suspend fun parseAndVerify(rawJson: String): IncomingResult {
        return try {
            val token = tokenAdapter.fromJson(rawJson)
                ?: return IncomingResult.Malformed("null")
            val payload = signer.verify(token) ?: return IncomingResult.BadSignature
            if (System.currentTimeMillis() > payload.expiresAtMillis)
                return IncomingResult.Expired
            if (dao.byId(payload.txId) != null) return IncomingResult.Duplicate
            IncomingResult.Ok(token, payload)
        } catch (t: Throwable) {
            IncomingResult.Malformed(t.message ?: "parse error")
        }
    }

    suspend fun acceptIncoming(token: PaymentToken, payload: TokenPayload): Boolean {
        if (dao.byId(payload.txId) != null) return false
        dao.insertIfAbsent(
            TransactionEntity(
                txId = payload.txId,
                senderId = payload.senderId,
                receiverId = payload.receiverId,
                amountMinor = payload.amountMinor,
                currency = payload.currency,
                issuedAtMillis = payload.issuedAtMillis,
                expiresAtMillis = payload.expiresAtMillis,
                status = TxStatus.PENDING_SETTLEMENT,
                direction = TxDirection.INCOMING,
                tokenJson = tokenAdapter.toJson(token)
            )
        )
        return true
    }

    suspend fun rejectIncoming(token: PaymentToken, payload: TokenPayload) {
        dao.insertIfAbsent(
            TransactionEntity(
                txId = payload.txId,
                senderId = payload.senderId,
                receiverId = payload.receiverId,
                amountMinor = payload.amountMinor,
                currency = payload.currency,
                issuedAtMillis = payload.issuedAtMillis,
                expiresAtMillis = payload.expiresAtMillis,
                status = TxStatus.REJECTED,
                direction = TxDirection.INCOMING,
                tokenJson = tokenAdapter.toJson(token)
            )
        )
    }

    suspend fun pendingForSync(): List<TransactionEntity> =
        dao.byStatus(listOf(TxStatus.PENDING_SETTLEMENT, TxStatus.SYNCING))

    suspend fun settleOne(tx: TransactionEntity): Boolean {
        dao.update(tx.copy(status = TxStatus.SYNCING, updatedAt = System.currentTimeMillis()))
        return try {
            val resp = api.settle(SettleRequest(tx.tokenJson))
            val newStatus = when (resp.status) {
                "COMPLETED", "DUPLICATE" -> TxStatus.COMPLETED
                "EXPIRED", "INVALID"     -> TxStatus.FAILED
                else                     -> TxStatus.FAILED
            }
            dao.update(
                tx.copy(
                    status = newStatus,
                    updatedAt = System.currentTimeMillis(),
                    attempts = tx.attempts + 1,
                    lastError = if (newStatus == TxStatus.FAILED) resp.message else null
                )
            )
            newStatus == TxStatus.COMPLETED
        } catch (t: Throwable) {
            dao.update(
                tx.copy(
                    status = TxStatus.PENDING_SETTLEMENT,
                    updatedAt = System.currentTimeMillis(),
                    attempts = tx.attempts + 1,
                    lastError = t.message
                )
            )
            false
        }
    }
}
