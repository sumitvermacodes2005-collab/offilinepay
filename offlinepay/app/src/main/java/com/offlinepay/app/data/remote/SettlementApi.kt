package com.offlinepay.app.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class SettleRequest(val tokenJson: String)

@JsonClass(generateAdapter = true)
data class SettleResponse(
    val ok: Boolean,
    val txId: String,
    val status: String,           // COMPLETED | DUPLICATE | EXPIRED | INVALID
    val message: String? = null
)

interface SettlementApi {
    @POST("settle")
    suspend fun settle(@Body req: SettleRequest): SettleResponse
}
