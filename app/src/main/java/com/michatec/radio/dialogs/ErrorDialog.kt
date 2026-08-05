package com.michatec.radio.dialogs

import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.R
import com.michatec.radio.databinding.DialogGenericWithDetailsBinding


/*
 * ErrorDialog class
 */
class ErrorDialog {

    /* Construct and show dialog */
    fun show(
        context: Context,
        errorTitle: Int,
        errorMessage: Int,
        errorDetails: String = String()
    ) {
        // prepare dialog builder
        val builder = MaterialAlertDialogBuilder(context)

        // set title
        builder.setTitle(context.getString(errorTitle))

        // get binding
        val binding = DialogGenericWithDetailsBinding.inflate(LayoutInflater.from(context))

        // set dialog view
        builder.setView(binding.root)

        // set detail view
        val detailsNotEmpty = errorDetails.isNotEmpty()
        // show/hide details link depending on whether details are empty or not
        binding.dialogDetailsLink.isVisible = detailsNotEmpty

        if (detailsNotEmpty) {
            // allow scrolling on details view
            binding.dialogDetails.movementMethod = ScrollingMovementMethod()

            // show and hide details on click
            binding.dialogDetailsLink.setOnClickListener {
                when (binding.dialogDetails.visibility) {
                    View.GONE -> binding.dialogDetails.isVisible = true
                    View.VISIBLE -> binding.dialogDetails.isGone = true
                    View.INVISIBLE -> {
                        return@setOnClickListener
                    }
                }
            }
            // set details text view
            binding.dialogDetails.text = errorDetails
        }

        // set text views
        binding.dialogMessage.text = context.getString(errorMessage)

        // add okay button
        builder.setPositiveButton(R.string.dialog_generic_button_okay) { _, _ ->
            Toast.makeText(context, R.string.dialog_generic_button_okay, Toast.LENGTH_SHORT).show()
        }

        // display error dialog
        builder.show()
    }
}
