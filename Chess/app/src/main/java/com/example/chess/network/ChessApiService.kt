package com.example.chess.network

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST
import retrofit2.http.Body
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

data class CreateRoomRequest(
    val timeControlMs: Long?
)

data class CreateRoomResponse(
    val code: String
)

interface ChessApiService {
    @POST("/room")
    suspend fun createRoom(@Body request: CreateRoomRequest): Response<CreateRoomResponse>
    
    companion object {
        fun create(): ChessApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ChessApiService::class.java)
        }
    }
}
