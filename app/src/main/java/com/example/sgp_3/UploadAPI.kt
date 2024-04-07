package com.example.sgp_3

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadAPI {
    @Multipart
    @POST("/post/images")
    fun postImage(
        @Part image: MultipartBody.Part,
        @Part("desc") desc: RequestBody
    ): Call<ResponseData>

    companion object{
        operator fun invoke(): UploadAPI{
            return Retrofit.Builder()
                .baseUrl("http://192.168.131.94:5000")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(UploadAPI::class.java)
        }
    }
}