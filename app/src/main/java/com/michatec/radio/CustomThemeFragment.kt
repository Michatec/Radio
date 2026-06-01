package com.michatec.radio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.michatec.radio.helpers.PreferencesHelper
import com.michatec.radio.helpers.ThemeHelper

class CustomThemeFragment : Fragment() {

    private lateinit var colorPreview: View
    private lateinit var hexCode: TextInputEditText
    private lateinit var seekRed: SeekBar
    private lateinit var seekGreen: SeekBar
    private lateinit var seekBlue: SeekBar
    private lateinit var recyclerView: RecyclerView

    private var currentColor: Int = Color.BLACK
    private var isUpdatingFromHex = false

    private fun applyColor(
        color: Int
    ) {
        updateSeekBars(color)
        updatePreview(color)
    }

    private val isAndroidTV: Boolean by lazy {
        requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_custom_theme, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.pref_custom_theme_title)

        colorPreview = view.findViewById(R.id.color_preview)
        hexCode = view.findViewById(R.id.hex_code)
        seekRed = view.findViewById(R.id.seek_red)
        seekGreen = view.findViewById(R.id.seek_green)
        seekBlue = view.findViewById(R.id.seek_blue)
        recyclerView = view.findViewById(R.id.color_recycler_view)

        currentColor = PreferencesHelper.loadCustomThemeColor(requireContext())

        applyColor(currentColor)

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val r = seekRed.progress
                    val g = seekGreen.progress
                    val b = seekBlue.progress
                    currentColor = Color.rgb(r, g, b)
                    updatePreview(currentColor)
                    PreferencesHelper.saveCustomTheme(currentColor, -1)
                    (recyclerView.adapter as? ColorAdapter)?.resetSelection()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekRed.setOnSeekBarChangeListener(seekBarListener)
        seekGreen.setOnSeekBarChangeListener(seekBarListener)
        seekBlue.setOnSeekBarChangeListener(seekBarListener)

        // Clipboard logic (Non-TV)
        if (!isAndroidTV) {
            hexCode.setOnClickListener {
                copyToClipboard(hexCode.text.toString())
            }
            hexCode.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!isUpdatingFromHex) {
                        try {
                            val color = s.toString().toColorInt()
                            currentColor = color
                            isUpdatingFromHex = true
                            applyColor(color)
                            PreferencesHelper.saveCustomTheme(currentColor, -1)
                            (recyclerView.adapter as? ColorAdapter)?.resetSelection()
                            isUpdatingFromHex = false
                        } catch (_: Exception) {}
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        } else {
            hexCode.isFocusable = false
            hexCode.isFocusableInTouchMode = false
        }

        setupRecyclerView()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.hex_code), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.toastmessage_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun updateSeekBars(color: Int) {
        seekRed.progress = Color.red(color)
        seekGreen.progress = Color.green(color)
        seekBlue.progress = Color.blue(color)
    }

    private fun updatePreview(color: Int) {
        colorPreview.setBackgroundColor(color)
        if (!isUpdatingFromHex) {
            isUpdatingFromHex = true
            hexCode.setText(String.format("#%08X", 0xFFFFFF and color))
            isUpdatingFromHex = false
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 5)
        val colors = ThemeHelper.getPredefinedColors(requireContext())
        val adapter = ColorAdapter(colors) { color, index ->
            currentColor = color
            applyColor(color)
            PreferencesHelper.saveCustomTheme(currentColor, index)
        }
        recyclerView.adapter = adapter
    }

    private inner class ColorAdapter(
        private val colors: List<Int>,
        private val onColorSelected: (Int, Int) -> Unit
    ) : RecyclerView.Adapter<ColorAdapter.ViewHolder>() {

        private var selectedPosition: Int = -1

        init {
            selectedPosition = PreferencesHelper.loadCustomThemeIndex()
        }

        fun resetSelection() {
            val oldPos = selectedPosition
            selectedPosition = -1
            if (oldPos != -1) notifyItemChanged(oldPos)
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val circle: View = view.findViewById(R.id.color_circle)
            init {
                view.isFocusable = true
                view.isFocusableInTouchMode = isAndroidTV
                view.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        val oldPos = selectedPosition
                        selectedPosition = pos
                        if (oldPos != -1) notifyItemChanged(oldPos)
                        notifyItemChanged(selectedPosition)
                        onColorSelected(colors[pos], pos)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.element_color_circle, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val color = colors[position]
            val drawable = holder.circle.background as GradientDrawable
            drawable.setColor(color)
            
            // Set selection state
            holder.itemView.isSelected = (position == selectedPosition)
        }

        override fun getItemCount() = colors.size
    }
}
