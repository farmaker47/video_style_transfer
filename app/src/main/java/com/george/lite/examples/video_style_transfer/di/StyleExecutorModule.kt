package com.george.lite.examples.video_style_transfer.di

import com.george.lite.examples.video_style_transfer.lib.StyleTransferModelExecutor
import org.koin.dsl.module

val styleExecutorModule = module {

    factory {
        StyleTransferModelExecutor(get(), getKoin().getProperty("koinUseGpu")!!)
    }
}