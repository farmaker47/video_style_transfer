/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.george.lite.examples.video_style_transfer.ui

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.george.lite.examples.video_style_transfer.*
import com.george.lite.examples.video_style_transfer.adapters.SearchFragmentNavigationAdapter
import com.george.lite.examples.video_style_transfer.databinding.TfePnActivityStyleTransferBinding
import com.george.lite.examples.video_style_transfer.lib.MLExecutionViewModel
import com.george.lite.examples.video_style_transfer.lib.StyleTransferModelExecutor
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import org.koin.android.ext.android.get
import org.koin.android.ext.android.getKoin
import org.koin.android.viewmodel.ext.android.viewModel
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import kotlin.math.abs
import kotlin.math.roundToInt

class StyleTransferFragment :
    Fragment(),
    ActivityCompat.OnRequestPermissionsResultCallback,
    SearchFragmentNavigationAdapter.SearchClickItemListener {

    private val viewModel: MLExecutionViewModel by viewModel()
    private val inferenceThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val mainScope = MainScope()
    private lateinit var styleTransferModelExecutor: StyleTransferModelExecutor
    private var doneInference = true
    private var isExecutorInitialized = false
    private lateinit var mSearchFragmentNavigationAdapter: SearchFragmentNavigationAdapter
    private var styleNumber: Float = 0F
    private lateinit var binding: TfePnActivityStyleTransferBinding
    private lateinit var scaledBitmap: Bitmap

    /** A shape for extracting frame data.   */
    private val PREVIEW_WIDTH = 640
    private val PREVIEW_HEIGHT = 480

    /** ID of the current [CameraDevice].   */
    private var cameraId: String? = null

    /** A [SurfaceView] for camera preview.   */
    private var surfaceView: SurfaceView? = null

    /** A [CameraCaptureSession] for camera preview.   */
    private var captureSession: CameraCaptureSession? = null

    /** A reference to the opened [CameraDevice].    */
    private var cameraDevice: CameraDevice? = null

    /** The [android.util.Size] of camera preview.  */
    private var previewSize: Size? = null

    /** The [android.util.Size.getWidth] of camera preview. */
    private var previewWidth = 0

    /** The [android.util.Size.getHeight] of camera preview.  */
    private var previewHeight = 0

    /** An IntArray to save image data in ARGB8888 format  */
    private lateinit var rgbBytes: IntArray

    /** A ByteArray to save image data in YUV format  */
    private var yuvBytes = arrayOfNulls<ByteArray>(3)

    /** An additional thread for running tasks that shouldn't block the UI.   */
    private var backgroundThread: HandlerThread? = null

    /** A [Handler] for running tasks in the background.    */
    private var backgroundHandler: Handler? = null

    /** An [ImageReader] that handles preview frame capture.   */
    private var imageReader: ImageReader? = null

    /** [CaptureRequest.Builder] for the camera preview   */
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    /** [CaptureRequest] generated by [.previewRequestBuilder   */
    private var previewRequest: CaptureRequest? = null

    /** A [Semaphore] to prevent the app from exiting before closing the camera.    */
    private val cameraOpenCloseLock = Semaphore(1)

    /** Whether the current camera device supports Flash or not.    */
    private var flashSupported = false

    /** Orientation of the camera sensor.   */
    private var sensorOrientation: Int? = null

    /** Abstract interface to someone holding a display surface.    */
    private var surfaceHolder: SurfaceHolder? = null

    /** [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.   */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@StyleTransferFragment.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@StyleTransferFragment.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            this@StyleTransferFragment.activity?.finish()
        }
    }

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture.
     */
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
        }
    }

    /**
     * Shows a [Toast] on the UI thread.
     *
     * @param text The message to show
     */
    private fun showToast(text: String) {
        val activity = activity
        activity?.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = TfePnActivityStyleTransferBinding.inflate(inflater)
        binding.lifecycleOwner = this

        // RecyclerView setup
        binding.recyclerViewStyles.setHasFixedSize(true)
        binding.recyclerViewStyles.layoutManager =
            LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        mSearchFragmentNavigationAdapter =
            SearchFragmentNavigationAdapter(
                activity!!,
                viewModel.currentList,
                this
            )
        binding.recyclerViewStyles.adapter = mSearchFragmentNavigationAdapter

        // Bind xml viewmodel
        binding.viewmodelXml = viewModel

        // First use of style executor class
        mainScope.async(inferenceThread) {
            // At start show progress bar
            binding.progressBar.visibility = View.VISIBLE
            getKoin().setProperty(getString(R.string.koinUseGpu), viewModel.cpuGpu != "false")

            // Initialize Executor class with Koin
            styleTransferModelExecutor = get()
            //styleTransferModelExecutor = StyleTransferModelExecutor(activity!!, viewModel.cpuGpu == "false")

            styleTransferModelExecutor.firstSelectStyle(
                viewModel.stylename,
                viewModel.seekBarProgress * 0.2F,
                activity!!
            )
            getKoin().setProperty(getString(R.string.koinStyle), viewModel.stylename)

            isExecutorInitialized = true

            // and then set switch
            // this is done inside for rotation problem initialization

            // GPU switch
            // GPUs are designed to have high throughput for massively parallelizable workloads.
            // Thus, they are well-suited for deep neural nets, which consist of a huge number of operators,
            // each working on some input tensor(s) that can be easily divided into smaller workloads and carried out in parallel,
            // typically resulting in lower latency. In the best scenario,
            // inference on the GPU may now run fast enough for previously not available real-time applications.
            binding.switchUseGpu.setOnCheckedChangeListener { _, isChecked ->
                //useGPU = isChecked
                //Log.i("SWITCH_CHECKED", binding.switchUseGpu.isChecked.toString())

                // Disable UI buttons
                enableControls(false)
                viewModel.setTypeCpuGpu(binding.switchUseGpu.isChecked.toString())
                binding.progressBar.visibility = View.VISIBLE

                // Reinitialize TF Lite models with new GPU setting
                mainScope.async(inferenceThread) {
                    styleTransferModelExecutor.close()
                    isExecutorInitialized = false
                    getKoin().setProperty(getString(R.string.koinUseGpu), isChecked)

                    // Because we used factory at koin module here we get a new instance of object
                    styleTransferModelExecutor = get()

                    //styleTransferModelExecutor = StyleTransferModelExecutor(activity!!, useGPU)
                    styleTransferModelExecutor.selectStyle(
                        getKoin().getProperty(getString(R.string.koinStyle))!!,
                        viewModel.seekBarProgress * 0.2F,
                        scaledBitmap,
                        activity!!
                    )
                    binding.progressBar.visibility = View.INVISIBLE
                    isExecutorInitialized = true
                    // Re-enable control buttons
                    activity!!.runOnUiThread { enableControls(true) }
                }
            }

            // Send styletransfermodelexecutor to close it when destroy of viewmodel
            viewModel.setStyleExecutorModule(styleTransferModelExecutor)

            // Make progress bar invisible
            binding.progressBar.visibility = View.INVISIBLE

            // After rotation we select style and seekbar progress
            styleTransferModelExecutor.selectStyle(
                getKoin().getProperty(getString(R.string.koinStyle))!!,
                viewModel.seekBarProgress * 0.2F,
                viewModel.scaledBitmapObject,
                activity!!
            )
        }

        // Observe viewmodel object
        viewModel.styledBitmap.observe(
            activity!!,
            Observer { resultImage ->
                if (resultImage != null) {
                    /*Glide.with(activity!!)
                        .load(resultImage.styledImage)
                        .fitCenter()
                        .into(binding.imageViewStyled)*/
                    binding.imageViewStyled.setImageBitmap(resultImage.styledImage)
                }
            }
        )

        // Observe when inference is done so to use only images for inference at that free time
        // otherwise queue of images that wait for inference gets toooo long
        viewModel.inferenceDone.observe(
            activity!!,
            Observer { inferenceIsDone ->
                doneInference = inferenceIsDone
            }
        )

        return binding.root
    }

    private fun getBitmapFromAsset(context: Context, path: String): Bitmap =
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }

    private fun enableControls(enable: Boolean) {
        binding.switchUseGpu.isEnabled = enable
        binding.seekBar.isEnabled = enable
        if(enable){
            binding.recyclerViewStyles.visibility = View.VISIBLE
        }else{
            binding.recyclerViewStyles.visibility = View.INVISIBLE
        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        surfaceView = view.findViewById(R.id.surfaceView)
        surfaceHolder = surfaceView!!.holder
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        setUpSeekBar()
    }

    private fun setUpSeekBar() {
        // Setting up Seekbar for style inheritance

        binding.seekBar.progress = viewModel.seekBarProgress.toInt();
        Log.i("SeekBarProgress", binding.seekBar.progress.toString())

        binding.seekBar.incrementProgressBy(0);
        binding.seekBar.max = 5;
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {

                // In case someone touches seekbar during initialization
                if (isExecutorInitialized) {
                    styleNumber = .2f * progress
                    Log.i("SeekBar", styleNumber.toString())

                    styleTransferModelExecutor.selectStyle(
                        getKoin().getProperty(getString(R.string.koinStyle))!!,
                        styleNumber,
                        scaledBitmap,
                        activity!!
                    )

                    viewModel.setTheSeekBarProgress(styleNumber / 0.2F)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Toast.makeText(
                    activity!!,
                    "Style blending is: " + ((1f - styleNumber) * 100).roundToInt()
                        .toString() + "%",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })


    }

    override fun onStart() {
        super.onStart()
        openCamera()
    }

    override fun onPause() {
        super.onPause()
        stopBackgroundThread()
    }

    override fun onStop() {
        super.onStop()
        closeCamera()
    }

    override fun onDestroy() {
        super.onDestroy()

        // This is transfered inside viewmodel to get closed only when application closes eg when viewmodel is cleared
        // not on rotation
        /*if (isExecutorInitialized) {
            styleTransferModelExecutor.closeDestroy()
        }*/
    }

    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            ConfirmationDialog()
                .show(
                    childFragmentManager,
                    FRAGMENT_DIALOG
                )

        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allPermissionsGranted(grantResults)) {

                afterPermissionsGranted()

            } else {
                ErrorDialog.newInstance(
                    getString(R.string.tfe_pn_request_permission)
                )
                    .show(
                        childFragmentManager,
                        FRAGMENT_DIALOG
                    )
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun afterPermissionsGranted() {
        setUpCameraOutputs()
        // Camera manager
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (ActivityCompat.checkSelfPermission(
                    context!!,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            manager.openCamera(cameraId!!, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    private fun allPermissionsGranted(grantResults: IntArray) = grantResults.all {
        it == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sets up member variables related to camera.
     */
    private fun setUpCameraOutputs() {
        val activity = activity
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a back facing camera in this sample.
                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraDirection != null &&
                    cameraDirection == CameraCharacteristics.LENS_FACING_BACK
                ) {
                    continue
                }

                previewSize = Size(PREVIEW_WIDTH, PREVIEW_HEIGHT)

                imageReader = ImageReader.newInstance(
                    PREVIEW_WIDTH, PREVIEW_HEIGHT,
                    ImageFormat.YUV_420_888, /*maxImages*/ 2
                )

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

                previewHeight = previewSize!!.height
                previewWidth = previewSize!!.width

                // Initialize the storage bitmaps once when the resolution is known.
                rgbBytes = IntArray(previewWidth * previewHeight)

                // Check if the flash is supported.
                flashSupported =
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

                this.cameraId = cameraId

                // We've found a viable camera and finished setting up member variables,
                // so we don't need to iterate through other available cameras.
                return
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(
                getString(R.string.tfe_pn_camera_error)
            )
                .show(
                    childFragmentManager,
                    FRAGMENT_DIALOG
                )
        }
    }

    /**
     * Opens the camera specified by [StyleTransferFragment.cameraId].
     */
    private fun openCamera() {
        val permissionCamera = context!!.checkPermission(
            Manifest.permission.CAMERA, Process.myPid(), Process.myUid()
        )
        if (permissionCamera != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
        } else {
            afterPermissionsGranted()
        }

    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        if (captureSession == null) {
            return
        }

        try {
            cameraOpenCloseLock.acquire()
            captureSession!!.close()
            captureSession = null
            cameraDevice!!.close()
            cameraDevice = null
            imageReader!!.close()
            imageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("imageAvailableListener").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }
    }

    /** Fill the yuvBytes with data from image planes.   */
    private fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
        // Row stride is the total number of bytes occupied in memory by a row of an image.
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(yuvBytes[i]!!)
        }
    }

    /** A [OnImageAvailableListener] to receive frames as they are available.  */
    private var imageAvailableListener = object : OnImageAvailableListener {
        override fun onImageAvailable(imageReader: ImageReader) {
            // We need wait until we have some size from onPreviewSizeChosen
            if (previewWidth == 0 || previewHeight == 0) {
                return
            }

            val image = imageReader.acquireLatestImage() ?: return
            fillBytes(image.planes, yuvBytes)

            ImageUtils.convertYUV420ToARGB8888(
                yuvBytes[0]!!,
                yuvBytes[1]!!,
                yuvBytes[2]!!,
                previewWidth,
                previewHeight,
                /*yRowStride=*/ image.planes[0].rowStride,
                /*uvRowStride=*/ image.planes[1].rowStride,
                /*uvPixelStride=*/ image.planes[1].pixelStride,
                rgbBytes
            )

            // Create bitmap from int array
            val imageBitmap = Bitmap.createBitmap(
                rgbBytes, previewWidth, previewHeight,
                Bitmap.Config.ARGB_8888
            )

            // Create rotated version for portrait display
            val rotateMatrix = Matrix()
            rotateMatrix.postRotate(270.0f)

            val rotatedBitmap = Bitmap.createBitmap(
                imageBitmap, 0, 0, previewWidth, previewHeight,
                rotateMatrix, true
            )
            image.close()

            processImage(rotatedBitmap)
        }
    }

    /** Crop Bitmap to maintain aspect ratio of model input.   */
    private fun cropBitmap(bitmap: Bitmap): Bitmap {
        val bitmapRatio = bitmap.height.toFloat() / bitmap.width
        val modelInputRatio = MODEL_HEIGHT.toFloat() / MODEL_WIDTH
        var croppedBitmap = bitmap

        // Acceptable difference between the modelInputRatio and bitmapRatio to skip cropping.
        val maxDifference = 1e-5

        // Checks if the bitmap has similar aspect ratio as the required model input.
        when {
            abs(modelInputRatio - bitmapRatio) < maxDifference -> return croppedBitmap

            modelInputRatio < bitmapRatio -> {
                // New image is taller so we are height constrained.
                val cropHeight = bitmap.height - (bitmap.width.toFloat() / modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    (cropHeight / 2).toInt(),
                    bitmap.width,
                    (bitmap.height - cropHeight).toInt()
                )
            }
            else -> {
                val cropWidth = bitmap.width - (bitmap.height.toFloat() * modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    (cropWidth / 2).toInt(),
                    0,
                    (bitmap.width - cropWidth).toInt(),
                    bitmap.height
                )
            }
        }
        return croppedBitmap
    }

    /** Process image using StyleTransfer library.   */
    private fun processImage(bitmap: Bitmap) {
        // Crop bitmap.
        val croppedBitmap = cropBitmap(bitmap)

        // Created scaled version of bitmap for model input.
        scaledBitmap = Bitmap.createScaledBitmap(
            croppedBitmap,
            MODEL_WIDTH,
            MODEL_HEIGHT, true
        )

        // Pass scaledBitmap to viewmodel
        viewModel.setScaledBitmap(scaledBitmap)

        if (doneInference && isExecutorInitialized) {
            viewModel.onApplyStyle(
                activity!!, scaledBitmap, "zkate.jpg", styleTransferModelExecutor,
                inferenceThread
            )
        }

    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            // We capture images from preview in YUV format.
            imageReader = ImageReader.newInstance(
                previewSize!!.width, previewSize!!.height, ImageFormat.YUV_420_888, 2
            )
            imageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)

            // This is the surface we need to record images for processing.
            val recordingSurface = imageReader!!.surface

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )
            previewRequestBuilder!!.addTarget(recordingSurface)

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice!!.createCaptureSession(
                listOf(recordingSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (cameraDevice == null) return

                        // When the session is ready, we start displaying the preview.
                        captureSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview.
                            previewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            // Flash is automatically enabled when necessary.
                            setAutoFlash(previewRequestBuilder!!)

                            // Finally, we start displaying the camera preview.
                            previewRequest = previewRequestBuilder!!.build()
                            captureSession!!.setRepeatingRequest(
                                previewRequest!!,
                                captureCallback, backgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, e.toString())
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        showToast("Failed")
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
        if (flashSupported) {
            requestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )
        }
    }

    /**
     * Shows an error message dialog.
     */
    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity)
                .setMessage(arguments!!.getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ -> activity!!.finish() }
                .create()

        companion object {

            @JvmStatic
            private val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(message: String): ErrorDialog = ErrorDialog()
                .apply {
                    arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
                }
        }
    }

    companion object {
        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()
        private val FRAGMENT_DIALOG = "dialog"

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        /**
         * Tag for the [Log].
         */
        private const val TAG = "StyleTransferFragment"
    }

    override fun onListItemClick(itemIndex: Int, sharedImage: ImageView?, type: String) {
        styleTransferModelExecutor.selectStyle(
            type,
            viewModel.seekBarProgress * 0.2F,
            scaledBitmap,
            activity!!
        )
        getKoin().setProperty(getString(R.string.koinStyle), type)
        viewModel.setStyleName(type)
    }
}
