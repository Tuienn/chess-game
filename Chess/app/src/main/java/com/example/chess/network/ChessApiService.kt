package com.example.chess.network

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

data class CreateRoomResponse(
    val code: String
)

interface ChessApiService {
    @POST("/room")
    suspend fun createRoom(): Response<CreateRoomResponse>
    
    companion object {
        private const val BASE_URL = "http://10.0.2.2:4000/" // Android emulator localhost
        
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
