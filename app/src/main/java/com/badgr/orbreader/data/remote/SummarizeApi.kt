package com.badgr.orbreader.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class SummarizeRequest(
    val text: String,
    val max_sentences: Int = 6
)

data class SummarizeResponse(
    val summary: String?,
    val keyPoints: List<String>?,
    val wordCount: Int?,
    val error: String?
)

interface SummarizeApi {

    @POST("summarize")
    suspend fun summarize(
        @Body request: SummarizeRequest
    ): Response<SummarizeResponse>
}
