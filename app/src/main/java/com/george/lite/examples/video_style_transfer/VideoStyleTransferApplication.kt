package com.george.lite.examples.video_style_transfer

import android.app.Application
import com.george.lite.examples.video_style_transfer.di.styleExecutorModule
import com.george.lite.examples.video_style_transfer.di.machineLearningExecutionModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VideoStyleTransferApplication : Application() {

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            //androidContext(applicationContext)
            androidContext(this@VideoStyleTransferApplication)
            modules(
                //styleExecutorModule,
                machineLearningExecutionModule
            )
        }

        delayedInit()
    }

    private fun delayedInit() {
        applicationScope.launch {
            Thread.sleep(4_000)
        }
    }
}