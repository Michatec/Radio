package com.michatec.radio

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
import com.michatec.radio.databinding.ElementColorCircleBinding
import com.michatec.radio.databinding.FragmentCustomThemeBinding
import com.michatec.radio.helpers.PreferencesHelper
import com.michatec.radio.helpers.ThemeHelper

class CustomThemeFragment : Fragment() {

    private var _binding: FragmentCustomThemeBinding? = null
    private val binding get() = _binding!!

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
    ): View {
        _binding = FragmentCustomThemeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.pref_custom_theme_title)

        currentColor = PreferencesHelper.loadCustomThemeColor(requireContext())

        applyColor(currentColor)

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val r = binding.seekRed.progress
                    val g = binding.seekGreen.progress
                    val b = binding.seekBlue.progress
                    currentColor = Color.rgb(r, g, b)
                    updatePreview(currentColor)
                    PreferencesHelper.saveCustomTheme(currentColor, -1)
                    (binding.colorRecyclerView.adapter as? ColorAdapter)?.resetSelection()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        binding.seekRed.setOnSeekBarChangeListener(seekBarListener)
        binding.seekGreen.setOnSeekBarChangeListener(seekBarListener)
        binding.seekBlue.setOnSeekBarChangeListener(seekBarListener)

        // Clipboard logic (Non-TV)
        if (!isAndroidTV) {
            binding.hexCode.setOnClickListener {
                copyToClipboard(binding.hexCode.text.toString())
            }
            binding.hexCode.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!isUpdatingFromHex) {
                        try {
                            val color = s.toString().toColorInt()
                            currentColor = color
                            isUpdatingFromHex = true
                            applyColor(color)
                            PreferencesHelper.saveCustomTheme(currentColor, -1)
                            (binding.colorRecyclerView.adapter as? ColorAdapter)?.resetSelection()
                            isUpdatingFromHex = false
                        } catch (_: Exception) {}
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        } else {
            binding.hexCode.isFocusable = false
            binding.hexCode.isFocusableInTouchMode = false
        }

        setupRecyclerView()
    }

    private fun updateSeekBars(color: Int) {
        binding.seekRed.progress = Color.red(color)
        binding.seekGreen.progress = Color.green(color)
        binding.seekBlue.progress = Color.blue(color)
    }

    private fun updatePreview(color: Int) {
        binding.colorPreview.setBackgroundColor(color)
        if (!isUpdatingFromHex) {
            isUpdatingFromHex = true
            binding.hexCode.setText(String.format("#%08X", 0xFFFFFF and color))
            isUpdatingFromHex = false
        }
    }

    private fun setupRecyclerView() {
        binding.colorRecyclerView.layoutManager = GridLayoutManager(requireContext(), 5)
        val colors = ThemeHelper.getPredefinedColors(requireContext())
        val adapter = ColorAdapter(colors) { color, index ->
            currentColor = color
            applyColor(color)
            PreferencesHelper.saveCustomTheme(currentColor, index)
        }
        binding.colorRecyclerView.adapter = adapter
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText(getString(R.string.hex_code), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.toastmessage_copied_to_clipboard, Toast.LENGTH_SHORT).show()
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

        inner class ViewHolder(val binding: ElementColorCircleBinding) : RecyclerView.ViewHolder(binding.root) {
            init {
                binding.root.isFocusable = true
                binding.root.isFocusableInTouchMode = isAndroidTV
                binding.root.setOnClickListener {
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
            return ViewHolder(
                ElementColorCircleBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val color = colors[position]
            val drawable = holder.binding.colorCircle.background as GradientDrawable
            drawable.setColor(color)
            
            // Set selection state
            holder.itemView.isSelected = (position == selectedPosition)
        }

        override fun getItemCount() = colors.size
    }
}
