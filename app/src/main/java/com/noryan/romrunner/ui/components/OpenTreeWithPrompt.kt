package com.noryan.romrunner.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The system folder picker, with its main button relabelled to [prompt] (e.g. "Set up PrimeHack") instead of
 * "Use this folder". RomRunner opens the picker already inside the right app's folder, so that button is the one
 * thing the user taps; naming what it does makes that obvious without any explanation.
 */
class OpenTreeWithPrompt(private val prompt: String) : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_PROMPT, prompt)
}
