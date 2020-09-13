package com.george.lite.examples.video_style_transfer.lib

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ModelExecutionResult(
    val styledImage: Bitmap,
    val preProcessTime: Long = 0L,
    val stylePredictTime: Long = 0L,
    val styleTransferTime: Long = 0L,
    val postProcessTime: Long = 0L,
    val totalExecutionTime: Long = 0L,
    val executionLog: String = "",
    val errorMessage: String = ""
)

class StyleTransferModelExecutor(
    context: Context,
    private var useGPU: Boolean
) {
    private lateinit var gpuDelegate: GpuDelegate
    private var numberThreads = 4

    private lateinit var interpreterPredict: Interpreter
    private lateinit var interpreterTransform: Interpreter

    private var fullExecutionTime = 0L
    private var preProcessTime = 0L
    private var stylePredictTime = 0L
    private var styleTransferTime = 0L
    private var postProcessTime = 0L

    private val mContext = context

    // Variables that required to run only once at the beginning
    private lateinit var inputsStyleForPredict: Array<Any>
    private lateinit var inputsContentForPredict: Array<Any>
    private lateinit var outputsForPredictStyle: HashMap<Int, Any>
    private lateinit var outputsForPredictContent: HashMap<Int, Any>
    private var contentBottleneck =
        Array(1) { Array(1) { Array(1) { FloatArray(BOTTLENECK_SIZE) } } }
    private var styleBottleneck =
        Array(1) { Array(1) { Array(1) { FloatArray(BOTTLENECK_SIZE) } } }
    private var styleBottleneckBlended =
        Array(1) { Array(1) { Array(1) { FloatArray(BOTTLENECK_SIZE) } } }

    private var CONTENT_IMAGE_SIZE: Int

    init {
        CONTENT_IMAGE_SIZE = 384
        if (useGPU) {
            interpreterPredict = getInterpreter(context, STYLE_PREDICT_FLOAT16_MODEL, true)
            interpreterTransform = getInterpreter(context, STYLE_TRANSFER_FLOAT16_MODEL, true)
            Log.i("GPU_TRUE", "TRUE")
            Log.i("GPU_TRUE", CONTENT_IMAGE_SIZE.toString())
        } /*else {
            interpreterPredict = getInterpreter(context, STYLE_PREDICT_INT_MODEL, false)

            interpreterTransform = getInterpreter(context, STYLE_TRANSFER_INT_MODEL, false)
            val index = interpreterTransform.getInputIndex("content_image")
            interpreterTransform.resizeInput(
                index,
                intArrayOf(1, CONTENT_IMAGE_SIZE, CONTENT_IMAGE_SIZE, 3)
            )
            Log.e("GPU_FALSE", "FALSE")
            Log.e("GPU_FALSE", CONTENT_IMAGE_SIZE.toString())
        }*/
    }

    companion object {
        private const val TAG = "StyleTransferMExec"
        private const val STYLE_IMAGE_SIZE = 256
        private const val BOTTLENECK_SIZE = 100
        private const val STYLE_PREDICT_INT_MODEL = "style_predict_hybrid_last.tflite"
        private const val STYLE_TRANSFER_INT_MODEL = "style_transfer_hybrid_last.tflite"
        private const val STYLE_PREDICT_FLOAT16_MODEL = "style_predict_f16_shayak.tflite"
        private const val STYLE_TRANSFER_FLOAT16_MODEL = "style_transfer_f16_shayak.tflite"
    }

    // Select style at the beginning
    fun firstSelectStyle(
        styleImageName: String,
        styleInheritance: Float,
        context: Context
    ) {

        stylePredictTime = SystemClock.uptimeMillis()
        val styleBitmap =
            ImageUtils.loadBitmapFromResources(context, "thumbnails/$styleImageName")
        val inputStyle =
            ImageUtils.bitmapToByteBuffer(styleBitmap, STYLE_IMAGE_SIZE, STYLE_IMAGE_SIZE)

        inputsStyleForPredict = arrayOf(inputStyle)
        outputsForPredictStyle = HashMap()

        // Stylebottleneckblended is calculated once and used inside style transfer
        outputsForPredictStyle[0] = styleBottleneckBlended
        preProcessTime = SystemClock.uptimeMillis() - preProcessTime

        interpreterPredict.runForMultipleInputsOutputs(
            inputsStyleForPredict,
            outputsForPredictStyle
        )

        stylePredictTime = SystemClock.uptimeMillis() - stylePredictTime
        Log.i("PREDICT", "Style Predict Time to run: $stylePredictTime")

    }

    fun selectStyle(
        styleImageName: String,
        styleInheritance: Float,
        contentBitmap: Bitmap,
        context: Context
    ) {

        stylePredictTime = SystemClock.uptimeMillis()
        val styleBitmap =
            ImageUtils.loadBitmapFromResources(context, "thumbnails/$styleImageName")
        val inputStyle =
            ImageUtils.bitmapToByteBuffer(styleBitmap, STYLE_IMAGE_SIZE, STYLE_IMAGE_SIZE)
        val inputContent =
            ImageUtils.bitmapToByteBuffer(contentBitmap, STYLE_IMAGE_SIZE, STYLE_IMAGE_SIZE)

        inputsStyleForPredict = arrayOf(inputStyle)
        inputsContentForPredict = arrayOf(inputContent)
        outputsForPredictStyle = HashMap()
        outputsForPredictContent = HashMap()
        outputsForPredictStyle[0] = styleBottleneck
        outputsForPredictContent[0] = contentBottleneck
        preProcessTime = SystemClock.uptimeMillis() - preProcessTime
        // Run for style
        interpreterPredict.runForMultipleInputsOutputs(
            inputsStyleForPredict,
            outputsForPredictStyle
        )
        // Run for blending
        interpreterPredict.runForMultipleInputsOutputs(
            inputsContentForPredict,
            outputsForPredictContent
        )

        val contentBlendingRatio = styleInheritance

        // Calculation of style blending
        // # Define content blending ratio between [0..1].
        //# 0.0: 0% style extracts from content image.
        //# 1.0: 100% style extracted from content image.
        //content_blending_ratio = 0.5
        //
        //# Blend the style bottleneck of style image and content image
        //style_bottleneck_blended = content_blending_ratio * style_bottleneck_content \
        //
        //                           + (1 - content_blending_ratio) * style_bottleneck

        // Apply style inheritance by changing values with seekbar integers
        for (i in 0 until contentBottleneck[0][0][0].size) {
            contentBottleneck[0][0][0][i] =
                contentBottleneck[0][0][0][i] * contentBlendingRatio
        }

        for (i in styleBottleneck[0][0][0].indices) {
            styleBottleneck[0][0][0][i] =
                styleBottleneck[0][0][0][i] * (1 - contentBlendingRatio)
        }

        for (i in styleBottleneckBlended[0][0][0].indices) {
            styleBottleneckBlended[0][0][0][i] =
                contentBottleneck[0][0][0][i] + styleBottleneck[0][0][0][i]
        }

        stylePredictTime = SystemClock.uptimeMillis() - stylePredictTime
        Log.i("PREDICT", "Style Predict Time to run: $stylePredictTime")

    }

    fun selectVideoQuality(value: Int) {
        when (value) {
            0 -> initializeIntrpreterForVideoQuality(200)
            1 -> initializeIntrpreterForVideoQuality(240)
            2 -> initializeIntrpreterForVideoQuality(260)
            3 -> initializeIntrpreterForVideoQuality(300)
            4 -> initializeIntrpreterForVideoQuality(340)
            else -> initializeIntrpreterForVideoQuality(380)
        }
    }

    private fun initializeIntrpreterForVideoQuality(value: Int) {
        CONTENT_IMAGE_SIZE = value
        Log.i("CONTENT_SIZE", CONTENT_IMAGE_SIZE.toString())

        interpreterPredict = getInterpreter(mContext, STYLE_PREDICT_INT_MODEL, false)
        interpreterTransform = getInterpreter(mContext, STYLE_TRANSFER_INT_MODEL, false)
        val index = interpreterTransform.getInputIndex("content_image")
        interpreterTransform.resizeInput(
            index,
            intArrayOf(1, CONTENT_IMAGE_SIZE, CONTENT_IMAGE_SIZE, 3)
        )
    }

    suspend fun execute(
        contentImageBitmap: Bitmap,
        styleImageName: String,
        context: Context
    ): ModelExecutionResult {
        try {
            Log.i(TAG, "running models")

            fullExecutionTime = SystemClock.uptimeMillis()
            preProcessTime = SystemClock.uptimeMillis()

            //val contentImage = ImageUtils.decodeBitmap(File(contentImagePath))
            val contentArray =
                ImageUtils.bitmapToByteBuffer(
                    contentImageBitmap,
                    CONTENT_IMAGE_SIZE,
                    CONTENT_IMAGE_SIZE
                )

            /*val styleBitmap =
                ImageUtils.loadBitmapFromResources(context, "thumbnails/$styleImageName")
            val input =
                ImageUtils.bitmapToByteBuffer(styleBitmap, STYLE_IMAGE_SIZE, STYLE_IMAGE_SIZE)

            val inputsForPredict = arrayOf<Any>(input)
            val outputsForPredict = HashMap<Int, Any>()
            val styleBottleneck = Array(1) { Array(1) { Array(1) { FloatArray(BOTTLENECK_SIZE) } } }
            outputsForPredict[0] = styleBottleneck
            preProcessTime = SystemClock.uptimeMillis() - preProcessTime

            stylePredictTime = SystemClock.uptimeMillis()
            // The results of this inference could be reused given the style does not change
            // That would be a good practice in case this was applied to a video stream.
            interpreterPredict.runForMultipleInputsOutputs(inputsForPredict, outputsForPredict)
            stylePredictTime = SystemClock.uptimeMillis() - stylePredictTime*/

            val inputsForStyleTransfer = if (useGPU) {
                arrayOf(contentArray, styleBottleneckBlended)
            } else {
                arrayOf(styleBottleneckBlended, contentArray)
            }

            val outputsForStyleTransfer = HashMap<Int, Any>()
            val outputImage =
                Array(1) { Array(CONTENT_IMAGE_SIZE) { Array(CONTENT_IMAGE_SIZE) { FloatArray(3) } } }
            outputsForStyleTransfer[0] = outputImage

            styleTransferTime = SystemClock.uptimeMillis()
            interpreterTransform.runForMultipleInputsOutputs(
                inputsForStyleTransfer,
                outputsForStyleTransfer
            )
            styleTransferTime = SystemClock.uptimeMillis() - styleTransferTime
            Log.i(TAG, "Style apply Time to run: $styleTransferTime")

            postProcessTime = SystemClock.uptimeMillis()
            val styledImage =
                ImageUtils.convertArrayToBitmap(outputImage, CONTENT_IMAGE_SIZE, CONTENT_IMAGE_SIZE)
            postProcessTime = SystemClock.uptimeMillis() - postProcessTime
            Log.i(TAG, "Post process time: $postProcessTime")

            fullExecutionTime = SystemClock.uptimeMillis() - fullExecutionTime
            Log.i("STYLE_SOLOUPIS", "Time to run everything: $fullExecutionTime")

            return ModelExecutionResult(
                styledImage,
                preProcessTime,
                stylePredictTime,
                styleTransferTime,
                postProcessTime,
                fullExecutionTime,
                formatExecutionLog()
            )
        } catch (e: Exception) {
            val exceptionLog = "something went wrong: ${e.message}"
            Log.d(TAG, exceptionLog)

            val emptyBitmap =
                ImageUtils.createEmptyBitmap(
                    CONTENT_IMAGE_SIZE,
                    CONTENT_IMAGE_SIZE
                )
            return ModelExecutionResult(
                emptyBitmap, errorMessage = e.message!!
            )
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(context: Context, modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        fileDescriptor.close()
        return retFile
    }

    @Throws(IOException::class)
    private fun getInterpreter(
        context: Context,
        modelName: String,
        useGpu: Boolean
    ): Interpreter {
        val tfliteOptions = Interpreter.Options()
        if (useGpu) {
            gpuDelegate = GpuDelegate()
            tfliteOptions.addDelegate(gpuDelegate)

            // Create the Delegate instance.
            /*try {
                gpuDelegate = HexagonDelegate(context)
                tfliteOptions.addDelegate(gpuDelegate)
            } catch (e: Exception) {
                // Hexagon delegate is not supported on this device.
                Log.e("HEXAGON", e.toString())
            }*/

            //val delegate =
            //GpuDelegate(GpuDelegate.Options().setQuantizedModelsAllowed(true))
        }

        tfliteOptions.setNumThreads(numberThreads)
        //tfliteOptions.setUseXNNPACK(true)
        return Interpreter(loadModelFile(context, modelName), tfliteOptions)
    }

    private fun formatExecutionLog(): String {
        val sb = StringBuilder()
        sb.append("Input Image Size: $CONTENT_IMAGE_SIZE x $CONTENT_IMAGE_SIZE\n")
        sb.append("GPU enabled: $useGPU\n")
        sb.append("Number of threads: $numberThreads\n")
        sb.append("Pre-process execution time: $preProcessTime ms\n")
        sb.append("Predicting style execution time: $stylePredictTime ms\n")
        sb.append("Transferring style execution time: $styleTransferTime ms\n")
        sb.append("Post-process execution time: $postProcessTime ms\n")
        sb.append("Full execution time: $fullExecutionTime ms\n")
        return sb.toString()
    }

    fun close() {
        interpreterPredict.close()
        //interpreterTransform.close()
    }

    fun closeEverything() {
        interpreterPredict.close()
        interpreterTransform.close()
        gpuDelegate.close()
    }
}