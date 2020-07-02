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
    private var gpuDelegate: GpuDelegate = GpuDelegate()
    private var numberThreads = 4

    private val interpreterPredict: Interpreter
    private val interpreterTransform: Interpreter

    private var fullExecutionTime = 0L
    private var preProcessTime = 0L
    private var stylePredictTime = 0L
    private var styleTransferTime = 0L
    private var postProcessTime = 0L

    // Variables that required to run only once at the beggining
    private lateinit var inputsForPredict: Array<Any>
    private lateinit var outputsForPredict: HashMap<Int, Any>
    private var styleBottleneck = Array(1) { Array(1) { Array(1) { FloatArray(BOTTLENECK_SIZE) } } }

    init {
        if (useGPU) {
            interpreterPredict = getInterpreter(context, STYLE_PREDICT_FLOAT16_MODEL, true)
            interpreterTransform = getInterpreter(context, STYLE_TRANSFER_FLOAT16_MODEL, true)
            Log.i("GPU_TRUE", "TRUE")
        } else {
            interpreterPredict = getInterpreter(context, STYLE_PREDICT_INT8_MODEL, false)
            interpreterTransform = getInterpreter(context, STYLE_TRANSFER_INT8_MODEL, false)
            Log.i("GPU_FALSE", "FALSE")
        }


    }

    companion object {
        private const val TAG = "StyleTransferMExec"
        private const val STYLE_IMAGE_SIZE = 256
        private const val CONTENT_IMAGE_SIZE = 384
        private const val BOTTLENECK_SIZE = 100
        private const val STYLE_PREDICT_INT8_MODEL = "style_predict_quantized_256.tflite"
        private const val STYLE_TRANSFER_INT8_MODEL = "style_transfer_quantized_384.tflite"
        private const val STYLE_PREDICT_FLOAT16_MODEL = "style_predict_f16_256.tflite"
        private const val STYLE_TRANSFER_FLOAT16_MODEL = "style_transfer_f16_384.tflite"
    }

    fun selectStyle(
        styleImageName: String,
        styleInheritance: Int,
        context: Context
    ) {

        val styleBitmap =
            ImageUtils.loadBitmapFromResources(context, "thumbnails/$styleImageName")
        val input =
            ImageUtils.bitmapToByteBuffer(styleBitmap, STYLE_IMAGE_SIZE, STYLE_IMAGE_SIZE)

        inputsForPredict = arrayOf(input)
        outputsForPredict = HashMap()
        //val styleBottleneck = Array(1) { Array(1) { Array(1) { FloatArray(BOTTLENECK_SIZE) } } }
        outputsForPredict[0] = styleBottleneck
        /*for(i in 0..styleBottleneck[0][0][0].size){
            Log.e("Style_ouputs_first", styleBottleneck[0][0][0][i].toString())
        }*/
        preProcessTime = SystemClock.uptimeMillis() - preProcessTime

        stylePredictTime = SystemClock.uptimeMillis()
        // The results of this inference could be reused given the style does not change
        // That would be a good practice in case this was applied to a video stream.
        interpreterPredict.runForMultipleInputsOutputs(inputsForPredict, outputsForPredict)

        for (i in 0 until styleBottleneck[0][0][0].size) {
            //Log.e("Style_ouputs_later", styleBottleneck[0][0][0][i].toString())
            Log.e("Style_number", styleBottleneck[0][0][0].size.toString())

            styleBottleneck[0][0][0][i] = styleBottleneck[0][0][0][i] / styleInheritance.toFloat()
        }

        stylePredictTime = SystemClock.uptimeMillis() - stylePredictTime
        Log.i("PREDICT", "Style Predict Time to run: $stylePredictTime")

    }

    fun execute(
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
            //Log.e(TAG, "Style Predict Time to run: $stylePredictTime")

            val inputsForStyleTransfer = arrayOf(contentArray, styleBottleneck)
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
        //tfliteOptions.setNumThreads(numberThreads)

        //gpuDelegate = null
        if (useGpu) {
            gpuDelegate = GpuDelegate()
            tfliteOptions.addDelegate(gpuDelegate)

            //val delegate =
            //GpuDelegate(GpuDelegate.Options().setQuantizedModelsAllowed(true))

        }

        tfliteOptions.setNumThreads(numberThreads)
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
        interpreterTransform.close()
        if (gpuDelegate != null) {
            gpuDelegate!!.close()
        }
    }
}