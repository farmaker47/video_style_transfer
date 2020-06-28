package com.george.lite.examples.video_style_transfer.lib

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
//import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

class MLExecutionViewModel(application: Application) : AndroidViewModel(application) {

    private val _styledBitmap = MutableLiveData<ModelExecutionResult>()
    val styledBitmap: LiveData<ModelExecutionResult>
        get() = _styledBitmap

    private val _inferenceDone = MutableLiveData<Boolean>()
    val inferenceDone: LiveData<Boolean>
        get() = _inferenceDone

    private var _currentList: ArrayList<String> = ArrayList()
    val currentList: ArrayList<String>
        get() = _currentList


    private val viewModelJob = Job()
    private val viewModelScope = CoroutineScope(viewModelJob)

    init {
        _inferenceDone.value = true

        // Create list of styles
        //val styles = ArrayList<String>()
        _currentList.addAll(application.assets.list("thumbnails")!!)
    }

    fun onApplyStyle(
        context: Context,
        contentBitmap: Bitmap,
        styleFilePath: String,
        styleTransferModelExecutor: StyleTransferModelExecutor,
        inferenceThread: ExecutorCoroutineDispatcher
    ) {
        viewModelScope.launch(inferenceThread) {
            _inferenceDone.postValue(false)
            val result =
                styleTransferModelExecutor.execute(contentBitmap, styleFilePath, context)
            _styledBitmap.postValue(result)
            _inferenceDone.postValue(true)
        }

        // Initialize Interpreter
        /*viewModelScope.launch {
            initializeInterpreter(app)
        }*/
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

}