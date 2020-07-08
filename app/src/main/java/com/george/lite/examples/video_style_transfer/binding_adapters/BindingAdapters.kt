package com.george.lite.examples.video_style_transfer.binding_adapters

import android.util.Log
import android.widget.TextView
import androidx.databinding.BindingAdapter
import java.text.DecimalFormat

@BindingAdapter("longValueToString")
fun bindTextViewLOngToString(textView: TextView, longValue: Long) {
    if (longValue != 0L) {
        val f = DecimalFormat("#0.00")
        textView.text = "FPS= " + f.format(1000 / longValue.toFloat()).toString()
    }
}