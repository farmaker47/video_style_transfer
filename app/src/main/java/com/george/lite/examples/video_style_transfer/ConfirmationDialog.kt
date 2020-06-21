/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.george.lite.examples.video_style_transfer

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment

/**
 * Shows OK/Cancel confirmation dialog about camera permission.
 */
class ConfirmationDialog : DialogFragment() {

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
    AlertDialog.Builder(activity)
      .setMessage(R.string.tfe_pn_request_permission)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        parentFragment!!.requestPermissions(
          arrayOf(Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION
        )
      }
      .setNegativeButton(android.R.string.cancel) { _, _ ->
        parentFragment!!.activity?.finish()
      }
      .create()
}
