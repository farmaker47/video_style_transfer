package com.george.lite.examples.video_style_transfer.di

import com.george.lite.examples.video_style_transfer.lib.MLExecutionViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

val machineLearningExecutionModule = module {
    viewModel {
        MLExecutionViewModel(get())
    }
}