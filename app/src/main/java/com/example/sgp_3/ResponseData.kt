package com.example.sgp_3

import com.google.gson.annotations.SerializedName
import java.lang.reflect.Array


data class ResponseData(
//    @SerializedName("width")
//    val width: Int, // 100
//    @SerializedName("height")
//    val height: Int // 200
    val error: String?,
    @SerializedName("Results")
    val detectedObjects: ArrayList<String>
)
