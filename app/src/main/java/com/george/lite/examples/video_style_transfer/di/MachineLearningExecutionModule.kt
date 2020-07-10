package com.george.lite.examples.video_style_transfer.di

import com.george.lite.examples.video_style_transfer.lib.MLExecutionViewModel
import com.george.lite.examples.video_style_transfer.lib.StyleTransferModelExecutor
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

val machineLearningExecutionModule = module {

    factory { StyleTransferModelExecutor(get(), getKoin().getProperty("koinUseGpu")!!) }

    viewModel {
        MLExecutionViewModel(get())
    }
}