package com.rancho.gestion.data.network

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SupabaseClient {
    const val PROJECT_URL = "https://zvjeirzmxbkkdurhedxg.supabase.co/"
    private const val REST_BASE_URL = "${PROJECT_URL}rest/v1/"
    private const val STORAGE_BASE_URL = "${PROJECT_URL}storage/v1/"

    const val BUCKET_ANIMALES = "animales"

    private const val API_KEY = "sb_publishable_UZS4T-v2mrfUkmkGuutvrg_Te0KnNEF"

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .build()
        chain.proceed(request)
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    val retrofit: Retrofit by lazy {
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()

        Retrofit.Builder()
            .baseUrl(REST_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val storageRetrofit: Retrofit by lazy {
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()

        Retrofit.Builder()
            .baseUrl(STORAGE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    fun getPublicImageUrl(cloudKey: String): String {
        return "${PROJECT_URL}storage/v1/object/public/$BUCKET_ANIMALES/$cloudKey"
    }
}
