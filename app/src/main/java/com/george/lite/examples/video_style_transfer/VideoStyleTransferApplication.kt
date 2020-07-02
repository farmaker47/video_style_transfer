package com.george.lite.examples.video_style_transfer

import android.app.Application
import com.george.lite.examples.video_style_transfer.di.styleExecutorModule
import com.george.lite.examples.video_style_transfer.di.machineLearningExecutionModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VideoStyleTransferApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            //androidContext(applicationContext)
            androidContext(this@VideoStyleTransferApplication)
            modules(
                machineLearningExecutionModule,
                styleExecutorModule
            )
        }
    }
}