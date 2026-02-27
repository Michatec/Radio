/*
 * ErrorDialog.kt
 * Implements the ErrorDialog class
 * An ErrorDialog shows an error dialog with details
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.michatec.radio.dialogs

import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.R


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

        // get views
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val view: View = inflater.inflate(R.layout.dialog_generic_with_details, null)
        val errorMessageView: TextView = view.findViewById(R.id.dialog_message)
        val errorDetailsLinkView: TextView = view.findViewById(R.id.dialog_details_link)
        val errorDetailsView: TextView = view.findViewById(R.id.dialog_details)

        // set dialog view
        builder.setView(view)

        // set detail view
        val detailsNotEmpty = errorDetails.isNotEmpty()
        // show/hide details link depending on whether details are empty or not
        errorDetailsLinkView.isVisible = detailsNotEmpty

        if (detailsNotEmpty) {
            // allow scrolling on details view
            errorDetailsView.movementMethod = ScrollingMovementMethod()

            // show and hide details on click
            errorDetailsLinkView.setOnClickListener {
                when (errorDetailsView.visibility) {
                    View.GONE -> errorDetailsView.isVisible = true
                    View.VISIBLE -> errorDetailsView.isGone = true
                    View.INVISIBLE -> {
                        return@setOnClickListener
                    }
                }
            }
            // set details text view
            errorDetailsView.text = errorDetails
        }

        // set text views
        errorMessageView.text = context.getString(errorMessage)

        // add okay button
        builder.setPositiveButton(R.string.dialog_generic_button_okay) { _, _ ->
            Toast.makeText(context, R.string.dialog_generic_button_okay, Toast.LENGTH_SHORT).show()
        }

        // display error dialog
        builder.show()
    }
}
