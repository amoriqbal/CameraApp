package com.example.cam2app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.view.SurfaceView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var cameraManager: CameraManager
    private var cameraIdList: Array<String> = arrayOf()
    private var currentCameraIndex: Int = 0
        set (value) {
            if(cameraIdList.isEmpty()) {
                field = 0
                R.string.flip_button_text
                return
            }
            field = value % cameraIdList.size
            button.text = cameraIdList[field]
        }
    private var currentCameraSession : CameraCaptureSession? = null
    private var currentOpenDevice : CameraDevice? = null
    private lateinit var outputDirectory: File
    private val imageReader: ImageReader = ImageReader.newInstance(1280, 720,ImageFormat.JPEG, 10)

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        println("PERMISSION CHECK $it");ContextCompat.checkSelfPermission(
        baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun initCameraManager(){
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraIdList = cameraManager.cameraIdList
    }

    private fun startCamera(cameraId: String){
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        cameraManager.openCamera(cameraId, object:CameraDevice.StateCallback(){
            override fun onDisconnected(camera: CameraDevice) {
                println("CAMERA DEVICE DISCONNECTED $cameraId")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                println("CAMERA DEVICE ERROR $cameraId $error")
                stopCamera()
                currentCameraIndex = 0
            }

            override fun onOpened(camera: CameraDevice) {
                val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
                val targets = mutableListOf(surfaceView.holder.surface, imageReader.surface)
                currentOpenDevice = camera
                camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback(){
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        stopCamera()
                        currentCameraIndex = 0
                    }

                    override fun onConfigured(session: CameraCaptureSession) {
                        val captureRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        captureRequest.addTarget(targets[0])
                        currentCameraSession = session
                        session.setRepeatingRequest(captureRequest.build(), null, null)
                    }
                }, null)
            }
        }, null)
    }

    private fun stopCamera(){
        currentCameraSession?.close()
        currentCameraSession = null
        currentOpenDevice?.close()
        currentOpenDevice = null
    }

    private fun takePhoto(){
        val session = currentCameraSession ?: return
//        val cameraCharacteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(cameraIdList[currentCameraIndex])
//        println(cameraCharacteristics.availableCaptureResultKeys)

        val captureRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequest.addTarget(imageReader.surface)

//        imageReader.setOnImageAvailableListener(object:ImageReader.OnImageAvailableListener{
//            override fun onImageAvailable(reader: ImageReader?) {
//                val img = reader?.acquireLatestImage()
//                img?:return
//                val photoFile = File(outputDirectory,img.timestamp.toString()+".jpeg")
//                val imgByteArray = ByteArray(img.planes[0].buffer.capacity())
//                img.planes[0].buffer.get(imgByteArray)
//                photoFile.writeBytes(imgByteArray)
//
//                Toast.makeText(this@MainActivity,"created file ${photoFile.absolutePath}", Toast.LENGTH_SHORT).show()
//            }
//                                                                                           }, null)
        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback(){
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                println("CAPTURE COMPLETED")
                val img = imageReader.acquireNextImage()

                if(img == null){
                    Toast.makeText(this@MainActivity,"image is null", Toast.LENGTH_SHORT).show()
                    return
                }
                val photoFile = File(
                    outputDirectory,
                    SimpleDateFormat(FILENAME_FORMAT, Locale.US
                    ).format(System.currentTimeMillis()) + ".jpeg")
                if(!photoFile.exists()){
                    if(!photoFile.createNewFile()){
                        Toast.makeText(this@MainActivity,"file not created", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
                val imgByteArray = ByteArray(img.planes[0].buffer.capacity())
                img.planes[0].buffer.get(imgByteArray)
                photoFile.writeBytes(imgByteArray)
                img.close()
                Toast.makeText(this@MainActivity,"created file ${photoFile.absolutePath}", Toast.LENGTH_SHORT).show()
            }
                                                                                               }, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            initCameraManager()
            currentCameraIndex = 0
            startCamera(cameraIdList[currentCameraIndex])
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            return
        }

        button.setOnClickListener {
            stopCamera()
            currentCameraIndex ++
            startCamera(cameraIdList[currentCameraIndex])
        }

        photo_button.setOnClickListener { takePhoto() }
        outputDirectory = getOutputDirectory()

    }

    override fun onStop() {
        super.onStop()
        stopCamera()
    }
    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}