package com.michatec.radio.helpers

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.michatec.radio.R
import androidx.core.view.isEmpty

class ExtrasHelper {
    companion object {
        private const val TAG = "ExtrasHelper"
        init {
            try {
                System.loadLibrary("extra")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load extra library", e)
            }
        }

        @JvmStatic
        private external fun visualize(surface: Surface, data: FloatArray)

        private val renderLock = Any()

        fun render(surface: Surface?, data: FloatArray) {
            if (surface == null) return
            synchronized(renderLock) {
                if (!surface.isValid) return
                try {
                    visualize(surface, data)
                } catch (e: Exception) {
                    Log.e(TAG, "Native visualize failed", e)
                }
            }
        }
    }

    class VisualizerPreference(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
        private var visualizerView: VisualizerView? = null

        init {
            // We can use a standard layout and inject our view
            layoutResource = R.layout.preference_visualizer
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            
            // Try to find the container in the inflated layout
            var container = holder.findViewById(R.id.visualizer_container) as? ViewGroup

            // Fallback: If not found by ID, maybe the root is the container?
            if (container == null && holder.itemView is ViewGroup) {
                container = holder.itemView as ViewGroup
            }

            if (container != null) {
                if (visualizerView == null) {
                    visualizerView = VisualizerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }

                val currentParent = visualizerView?.parent as? ViewGroup
                if (currentParent != container) {
                    currentParent?.removeView(visualizerView)
                    // If we injected into a standard preference, don't clear everything, just add
                    if (container is FrameLayout || container.isEmpty()) {
                        container.removeAllViews()
                    }
                    container.addView(visualizerView)
                }
            } else {
                Log.e("VisualizerPreference", "Could not find any container to attach VisualizerView!")
            }
        }

        fun update(data: FloatArray) {
            visualizerView?.update(data)
        }
    }

    class VisualizerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
    ) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

        private var surface: Surface? = null

        init {
            Log.d("VisualizerView", "VisualizerView initialized")
            holder.addCallback(this)
        }

        fun update(data: FloatArray) {
            render(surface, data)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            synchronized(renderLock) {
                surface = holder.surface
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            synchronized(renderLock) {
                surface = holder.surface
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            synchronized(renderLock) {
                surface = null
            }
        }
    }
}
