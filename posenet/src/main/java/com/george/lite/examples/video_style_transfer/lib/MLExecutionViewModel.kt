package com.george.lite.examples.video_style_transfer.lib

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MLExecutionViewModel(application: Application) : AndroidViewModel(application) {

    private val _styledBitmap = MutableLiveData<ModelExecutionResult>()

    val styledBitmap: LiveData<ModelExecutionResult>
        get() = _styledBitmap

    private val viewModelJob = Job()
    private val viewModelScope = CoroutineScope(viewModelJob)

    fun onApplyStyle(
        context: Context,
        contentBitmap: Bitmap,
        styleFilePath: String,
        styleTransferModelExecutor: StyleTransferModelExecutor,
        inferenceThread: ExecutorCoroutineDispatcher
    ) {
        viewModelScope.launch(inferenceThread) {
            val result =
                styleTransferModelExecutor.execute(contentBitmap, styleFilePath, context)

            Log.e("TIME", result.styleTransferTime.toString())
            Log.e("BITMAP", result.styledImage.width.toString())
            _styledBitmap.postValue(result)
        }
    }
}