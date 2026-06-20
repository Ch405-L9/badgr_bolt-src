package com.badgr.orbreader.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class QuizRequest(
    val text: String,
    val num_questions: Int = 3
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val answerIndex: Int
)

data class QuizResponse(
    val questions: List<QuizQuestion>?
)

interface QuizApi {

    @POST("quiz")
    suspend fun fetchQuiz(
        @Body request: QuizRequest
    ): Response<QuizResponse>
}
