package com.example.albud

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider

import androidx.core.content.PermissionChecker

import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import android.provider.MediaStore

import android.content.ContentValues
import android.media.Image
import android.os.Build
import android.provider.Telephony
import com.example.albud.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import java.io.IOException
import java.lang.IllegalStateException


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null




    private lateinit var cameraExecutor: ExecutorService

    //function to stop camera



    //MLKIT recogonizer




    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()

            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }


        cameraExecutor = Executors.newSingleThreadExecutor()
    }



    private fun takePhoto() {

        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    class TextReaderAnalyzer(private val textFoundListener : (String)-> Unit) : ImageAnalysis.Analyzer
    {
        @ExperimentalGetImage
        override fun analyze(image: ImageProxy) {
            image.image?.let {process(it,image)}
        }

        private fun process(it: Image, image: ImageProxy) {
            try {
                readTextFromImage(InputImage.fromMediaImage(it,90),image)
            }
            catch (e : IOException)
            {
                Log.d(TAG,"Failed to load the image")
                e.printStackTrace()
            }
        }

        private fun readTextFromImage(image1: InputImage, image: ImageProxy) {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image1)
                .addOnSuccessListener { visionText ->
                    processTextFromImagr(visionText,image)
                    image.close()
                }
                .addOnFailureListener { error ->
                    Log.d(TAG,"Failed to process the image")
                    error.printStackTrace()
                    image.close()
                }

        }

        private fun processTextFromImagr(visionText: Text, image: ImageProxy) {
            
                for (block in visionText.textBlocks) {
                    // You can access whole block of text using block.text
                    for (line in block.lines) {
                        // You can access whole line of text using line.text
                        for (element in line.elements) {
                            textFoundListener(element.text)
                        }
                    }
                }

        }

    }
    private val imageAnalyzer by lazy {
        ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
            .also {
                it.setAnalyzer(
                    cameraExecutor,
                    TextReaderAnalyzer(::onTextFound)
                )
            }
    }
    private  fun onTextFound(foundText :String)
    {
        Log.d(TAG,"We found some Text : $foundText")
    }








    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }
            cameraProvider.bind(preview,imageAnalyzer)
        }, ContextCompat.getMainExecutor(this))
    }
    private fun ProcessCameraProvider.bind (preview: Preview,imageAnalzer:ImageAnalysis)=
        try{
            unbindAll()
            bindToLifecycle(this@MainActivity,
            CameraSelector.DEFAULT_BACK_CAMERA,preview,imageAnalzer)
        }
        catch (ise : IllegalStateException)
        {
            Log.e(TAG,"Binding failed",ise)
        }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }



    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}