package com.example.sgp_3

import android.os.Looper
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream

class UploadRequestBody(
    private val file: File,
    private val contentType: String,
): RequestBody() {
    override fun contentType(): MediaType? {
        return MediaType.parse("$contentType/*")
    }

    override fun contentLength(): Long {
        return super.contentLength()
    }

    override fun writeTo(sink: BufferedSink) {

        val length = file.length()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val fileInputStream = FileInputStream(file)
        var uploaded = 0L;
        fileInputStream.use {
            inputStram ->
            var read: Int
            val handler = android.os.Handler(Looper.getMainLooper())

            while(inputStram.read(buffer).also {
                read = it
            }!= -1){
                uploaded += read;
                sink.write(buffer,0,read)
            }
        }
    }

    interface UploadCallBack{

    }

    companion object{
        private var DEFAULT_BUFFER_SIZE = 2048
    }

}