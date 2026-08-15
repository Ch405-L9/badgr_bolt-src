package com.badgr.orbreader.audio.cwalts

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

data class CwaltsNarrateRequest(
    val text: String,
    val metadata: Map<String, String>? = null
)

data class CwaltsJobResponse(
    @SerializedName("job_id") val jobId: String,
    val status: String
)

data class CwaltsHealthResponse(
    val status: String,
    val provider: String,
    val voice: String,
    val compute: String,
    @SerializedName("queue_depth") val queueDepth: Int,
    val busy: Boolean
)

interface CwaltsNarrationApi {
    @GET("health")
    suspend fun health(): Response<CwaltsHealthResponse>

    @POST("narrate")
    suspend fun narrate(@Body request: CwaltsNarrateRequest): Response<CwaltsJobResponse>

    @GET("jobs/{jobId}")
    suspend fun job(@Path("jobId") jobId: String): Response<CwaltsJobResponse>

    @Streaming
    @GET("audio/{jobId}")
    suspend fun audio(@Path("jobId") jobId: String): Response<ResponseBody>
}
