package com.george.lite.examples.video_style_transfer.lib

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

class MLExecutionViewModel(
    application: Application
) : AndroidViewModel(application) {

    lateinit var styleTransferModelExecutorObject: StyleTransferModelExecutor
    lateinit var scaledBitmapObject: Bitmap

    private val _styledBitmap = MutableLiveData<ModelExecutionResult>()
    val styledBitmap: LiveData<ModelExecutionResult>
        get() = _styledBitmap

    private val _inferenceDone = MutableLiveData<Boolean>()
    val inferenceDone: LiveData<Boolean>
        get() = _inferenceDone

    private var _currentList: ArrayList<String> = ArrayList()
    val currentList: ArrayList<String>
        get() = _currentList

    private val _totalTimeInference = MutableLiveData<Int>()

    val totalTimeInference: LiveData<Int>
        get() = _totalTimeInference

    var stylename = String()
    var cpuGpu = String()
    var seekBarProgress: Float = 0F
    var seekBarQuality: Int = 0

    private val viewModelJob = Job()
    private val viewModelScopeJob = CoroutineScope(Dispatchers.IO + viewModelJob)

    init {
        _inferenceDone.value = true
        stylename = "mona.JPG"
        cpuGpu = "false"
        // Create list of styles
        _currentList.addAll(application.assets.list("thumbnails")!!)
    }

    fun setTheSeekBarProgress(progress: Float) {
        seekBarProgress = progress
    }

    fun setTheSeekBarQuality(quality: Int) {
        seekBarQuality = quality
    }

    fun setStyleName(string: String) {
        stylename = string
    }

    fun setTypeCpuGpu(string: String) {
        cpuGpu = string
    }

    // this should change with DI
    fun setStyleExecutorModule(styleTransferModelExecutor: StyleTransferModelExecutor) {
        styleTransferModelExecutorObject = styleTransferModelExecutor
    }

    fun setScaledBitmap(bitmap: Bitmap) {
        scaledBitmapObject = bitmap
    }

    fun onApplyStyle(
        context: Context,
        contentBitmap: Bitmap,
        styleFilePath: String,
        styleTransferModelExecutor: StyleTransferModelExecutor
    ) {

        //inferenceExecute(styleTransferModelExecutor, contentBitmap, styleFilePath, context)
        viewModelScopeJob.launch{
            inferenceExecute(styleTransferModelExecutor, contentBitmap, styleFilePath, context)
        }
    }


    private fun inferenceExecute(
        styleTransferModelExecutor: StyleTransferModelExecutor,
        contentBitmap: Bitmap,
        styleFilePath: String,
        context: Context
    ) {
        _inferenceDone.postValue(false)
        val result = styleTransferModelExecutor.execute(contentBitmap, styleFilePath, context)
        _totalTimeInference.postValue(result.totalExecutionTime.toInt())
        _styledBitmap.postValue(result)
        _inferenceDone.postValue(true)
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
        styleTransferModelExecutorObject.closeEverything()
    }

}