/*
 * YesNoDialog
 * Implements the YesNoDialog class
 * A YesNoDialog asks the user if he/she wants to do something or not
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
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.Keys
import com.michatec.radio.R


/*
 * YesNoDialog class
 */
class YesNoDialog(private var yesNoDialogListener: YesNoDialogListener) {

    /* Interface used to communicate back to activity */
    interface YesNoDialogListener {
        fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String) {
        }
    }


    /* Main class variables */
    private lateinit var dialog: AlertDialog


    /* Construct and show dialog - variant: message from string  */
    fun show(
        context: Context,
        type: Int,
        title: Int = Keys.EMPTY_STRING_RESOURCE,
        message: Int,
        yesButton: Int = R.string.dialog_yes_no_positive_button_default,
        noButton: Int = R.string.dialog_generic_button_cancel,
        payload: Int = Keys.DIALOG_EMPTY_PAYLOAD_INT,
        payloadString: String = Keys.DIALOG_EMPTY_PAYLOAD_STRING
    ) {
        // extract string from message resource and feed into main show method
        show(
            context,
            type,
            title,
            context.getString(message),
            yesButton,
            noButton,
            payload,
            payloadString
        )
    }


    /* Construct and show dialog */
    fun show(
        context: Context,
        type: Int,
        title: Int = Keys.EMPTY_STRING_RESOURCE,
        messageString: String,
        yesButton: Int = R.string.dialog_yes_no_positive_button_default,
        noButton: Int = R.string.dialog_generic_button_cancel,
        payload: Int = Keys.DIALOG_EMPTY_PAYLOAD_INT,
        payloadString: String = Keys.DIALOG_EMPTY_PAYLOAD_STRING
    ) {

        // prepare dialog builder
        val builder = MaterialAlertDialogBuilder(context)

        // set title and message
        builder.setMessage(messageString)
        if (title != Keys.EMPTY_STRING_RESOURCE) {
            builder.setTitle(context.getString(title))
        }


        // add yes button
        builder.setPositiveButton(yesButton) { _, _ ->
            // listen for click on yes button
            yesNoDialogListener.onYesNoDialog(type, true, payload, payloadString)
        }

        // add no button
        builder.setNegativeButton(noButton) { _, _ ->
            // listen for click on no button
            yesNoDialogListener.onYesNoDialog(type, false, payload, payloadString)
        }

        // handle outside-click as "no"
        builder.setOnCancelListener {
            yesNoDialogListener.onYesNoDialog(type, false, payload, payloadString)
        }

        // display dialog
        dialog = builder.create()
        dialog.show()
    }
}
