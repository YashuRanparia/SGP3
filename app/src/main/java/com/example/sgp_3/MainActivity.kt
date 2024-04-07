package com.example.sgp_3

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sgp_3.ImageUtils.convertYUV420ToARGB8888
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale


class MainActivity : AppCompatActivity(),ImageReader.OnImageAvailableListener {

    var sensorOrientation = 0
    lateinit var cameraManager: CameraManager
    lateinit var handler: Handler
    lateinit var fragment: CameraConnectionFragment
    lateinit var btn: Button
    lateinit var tts: TextToSpeech


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        btn = findViewById(R.id.sendbtn)

        tts = TextToSpeech(this,object : TextToSpeech.OnInitListener{
            override fun onInit(p0: Int) {
                if (p0 == TextToSpeech.SUCCESS) {
                    val result = tts!!.setLanguage(Locale.US)

                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS","The Language not supported!")
                    } else {
                        Log.d("TTS", "onInit: Initialized")
                    }
                }
            }

        })

        Log.d("mydb", "onCreate: ")
        get_permission()
    }

    //TODO fragment which show llive footage from camera
    var previewHeight = 0
    var previewWidth = 0

    fun setFragment() {

        Log.d("Function Call", "Set Fragment called")
        var cameraId: String? = null
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        val camera2Fragment = CameraConnectionFragment.newInstance(
            object : CameraConnectionFragment.ConnectionCallback {

                override fun onPreviewSizeChosen(size: Size?, cameraRotation: Int) {
                    previewHeight = size!!.height
                    previewWidth = size.width
                    Log.d(
                        "tryOrientation",
                        "rotation: " + cameraRotation + "   orientation: " + getScreenOrientation() + "  " + previewWidth + "   " + previewHeight
                    )
                    sensorOrientation = cameraRotation - 0
                }
            },
            this,
            R.layout.camera_fragment,
            Size(640, 640)
        )
        camera2Fragment.setCamera(cameraId)
        fragment = camera2Fragment
        fragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    fun getScreenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    fun framCaptured(image: Image){
        btn.setOnClickListener(View.OnClickListener {
            Log.d("btnClicked", "onImageAvailable: ")
            try {
                val buffer = image.planes[0].buffer
                val data = ByteArray (buffer.remaining ())
                buffer.get (data)
                // Save the image data to a file
                val file = File (getExternalFilesDir (null), "yashu.jpg")
                val outputStream = FileOutputStream (file)
                outputStream.write (data)
                outputStream.close ()

                

                val reqFile = RequestBody.create(MediaType.parse("image/*"), file)
                val body = MultipartBody.Part.createFormData("image", file.name, reqFile)
                Log.d("YashuFile", "Name: ${file.name} Path: ${file.absolutePath}")

                try {
//            callAPI(body)

                    val reqbody = UploadRequestBody(file, "image")

                    UploadAPI().postImage(MultipartBody.Part.createFormData(
                        "image",
                        file.name,
                        reqbody
                    ),
                        RequestBody.create(MediaType.parse("multipart/form-data"),"json")
                    ).enqueue(object : Callback<ResponseData>{
                        override fun onResponse(
                            call: Call<ResponseData>,
                            response: Response<ResponseData>
                        ) {
                            Log.d("XYZ", "onResponse: height - >${response.body()!!.detectedObjects.get(0)}")
                        }

                        override fun onFailure(call: Call<ResponseData>, t: Throwable) {
                            Log.d("ABC", "onFailure: $t")
                        }

                    })
                }catch (e:Exception){
                    Log.d("mycall", "Unable to call api")
                }
            }catch (e:Exception){
                Log.d("FileSave", "onImageAvailable: $e")
            }
            Log.d("file saved", "onImageAvailable: ")
            //making the request

            // Close the image and the reader
//                image.close ()
        })
    }

    //TODO getting frames of live camera footage and passing them to model
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var yRowStride = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    private var rgbFrameBitmap: Bitmap? = null


    override fun onImageAvailable(reader: ImageReader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader.acquireLatestImage() ?: return
            // Get the image data as a byte array
//            framCaptured(image)

            if (isProcessingFrame) {
                image.close()
                return
            }
            isProcessingFrame = true
            val planes = image.planes
            fillBytes(planes, yuvBytes)
            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = Runnable {
                convertYUV420ToARGB8888(
                    yuvBytes[0]!!,
                    yuvBytes[1]!!,
                    yuvBytes[2]!!,
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes!!
                )
            }
            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }
            processImage()
        } catch (e: Exception) {
            Log.d("tryError", e.message!!)
            return
        }
    }

    private fun processImage() {

        imageConverter!!.run()
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        rgbFrameBitmap!!.setPixels(rgbBytes!!, 0, previewWidth, 0, 0, previewWidth, previewHeight)

        Log.d("Processed", "processImage: Ok")
        btn.setOnClickListener(View.OnClickListener {
            Log.d("Btn", "Btn clicked")
            writeBitmap(this, rgbFrameBitmap!!)
        })

        Log.d("Funcall", "ObjectDetection  fun called")
//        objectDetection(this,rgbFrameBitmap!!)

        postInferenceCallback!!.run()
    }

    protected fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]!!]
        }
    }

    fun get_permission(){
        Log.d("Function Call", "Get Permission is called")

        if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions( this, arrayOf(android.Manifest.permission.CAMERA),101)
//            ActivityCompat.requestPermissions( this, arrayOf(android.Manifest.permission.RECORD_AUDIO),101)
        }else{
            //Permission is granted do something
            setFragment()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
            //show live camera footage
            setFragment()
        }else {
            //ask for permission if it is denied
            get_permission()
        }
    }

    fun writeBitmap(context: Context, bitmap: Bitmap){
        //create a file to write bitmap data
        var f: File = File(context.cacheDir, "yashu.jpeg");
        f.createNewFile();

        Log.d("BitWrite", "writeBitmap: ")

//Convert bitmap to byte array
        var bos:ByteArrayOutputStream = ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60 /*ignored for PNG*/, bos);
        var bitmapdata: ByteArray = bos.toByteArray();

//write the bytes in file
        var fos: FileOutputStream? = null;
        try {
            fos = FileOutputStream(f);
        } catch (e: FileNotFoundException) {
            e.printStackTrace();
        }
        try {
            fos!!.write(bitmapdata);
            fos.flush();
            fos.close();
        } catch (e: IOException) {
            Log.d("FileWrite", "Unable to write file : $e")
            e.printStackTrace();
        }

        val reqFile = RequestBody.create(MediaType.parse("image/*"), f)
        val body = MultipartBody.Part.createFormData("image", f.name, reqFile)
        Log.d("YashuFile", "Name: ${f.name} Path: ${f.absolutePath}")

        try {
//            callAPI(body)

            val reqbody = UploadRequestBody(f, "image")

            UploadAPI().postImage(MultipartBody.Part.createFormData(
                "image",
                f.name,
                reqbody
            ),
                RequestBody.create(MediaType.parse("multipart/form-data"),"json")
                ).enqueue(object : Callback<ResponseData>{
                override fun onResponse(
                    call: Call<ResponseData>,
                    response: Response<ResponseData>
                ) {
                    tts.speak(response.body()!!.detectedObjects.get(0),TextToSpeech.QUEUE_FLUSH,null)
                    Log.d("XYZ", "onResponse: height - >${response.body()!!.detectedObjects.get(0)}")
                }

                override fun onFailure(call: Call<ResponseData>, t: Throwable) {
                    Log.d("ABC", "onFailure: $t")
                }

            })
        }catch (e:Exception){
            Log.d("mycall", "Unable to call api")
        }
    }

//    fun callAPI(body: MultipartBody.Part){
//        try {
//            val service: UploadAPI =
//                Retrofit.Builder().baseUrl("http://192.168.131.94:5000").build().create(UploadAPI::class.java)
//            val req: Call<ResponseData> = service.postImage(body)
//
//            req.enqueue(object : Callback<ResponseData?> {
//                override fun onResponse(call: Call<ResponseData?>?, response: Response<ResponseData?>?) {
//                    // Do Something with response
//                    Log.d("MyResponse", "Success")
//                }
//
//                override fun onFailure(call: Call<ResponseData?>?, t: Throwable) {
//                    //failure message
//                    Log.d("MyResponse", "Failure")
//                    t.printStackTrace()
//                }
//            })
//        }catch (e:Exception){
//            Log.d("OnAPICALL", "callAPI: $e")
//        }
//
//    }

}