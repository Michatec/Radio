package com.michatec.radio.fragments

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.michatec.radio.R
import com.michatec.radio.integrations.Shizuku
import com.michatec.radio.ui.theme.RadioTheme
import rikka.shizuku.Shizuku as ShizukuApi

class ShizukuFragment : Fragment() {

    private val shizuku = Shizuku()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )

            setContent {
                RadioTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ShizukuSettingsSection(shizuku)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shizuku.unregisterListeners()
    }

    @Composable
    private fun StatusCard(
        icon: ImageVector,
        title: String,
        content: @Composable () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }

    @Composable
    private fun ShizukuSettingsSection(shizuku: Shizuku) {
        val context = LocalContext.current
        val scrollState = rememberScrollState()

        val isAndroidTV = remember {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }

        val msgStartFirst = stringResource(R.string.shizuku_start_first_toast)
        val msgAppNotFound = stringResource(R.string.shizuku_app_not_found)
        val msgOptimizationsEnabled = stringResource(R.string.shizuku_optimizations_enabled_toast)
        val msgOptimizationsDisabled = stringResource(R.string.shizuku_optimizations_disabled_toast)
        val msgCommandFailed = stringResource(R.string.shizuku_command_failed)
        val msgCommandErrorFormat = stringResource(R.string.shizuku_command_error)

        var binderReady by remember {
            mutableStateOf(shizuku.isBinderAlive())
        }
        var hasPermission by remember {
            mutableStateOf(shizuku.hasPermission())
        }
        var isBatteryOptimized by remember {
            mutableStateOf(shizuku.isIgnoringBatteryOptimizations(context))
        }

        fun refreshStatus(ctx: Context) {
            binderReady = shizuku.isBinderAlive()
            hasPermission = shizuku.hasPermission()
            isBatteryOptimized = shizuku.isIgnoringBatteryOptimizations(ctx)
        }

        DisposableEffect(Unit) {
            val binderListener = ShizukuApi.OnBinderReceivedListener {
                refreshStatus(context)
            }
            val binderDeadListener = ShizukuApi.OnBinderDeadListener {
                refreshStatus(context)
            }
            val permissionListener = ShizukuApi.OnRequestPermissionResultListener { _, _ ->
                refreshStatus(context)
            }

            ShizukuApi.addBinderReceivedListenerSticky(binderListener)
            ShizukuApi.addBinderDeadListener(binderDeadListener)
            ShizukuApi.addRequestPermissionResultListener(permissionListener)

            onDispose {
                ShizukuApi.removeBinderReceivedListener(binderListener)
                ShizukuApi.removeBinderDeadListener(binderDeadListener)
                ShizukuApi.removeRequestPermissionResultListener(permissionListener)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = if (isAndroidTV) 32.dp else 0.dp, vertical = 12.dp)
        ) {
            StatusCard(
                icon = Icons.Default.Security,
                title = stringResource(R.string.shizuku_status_title)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            !binderReady -> stringResource(R.string.shizuku_not_connected)
                            hasPermission -> stringResource(R.string.shizuku_connected_permission_granted)
                            else -> stringResource(R.string.shizuku_connected_permission_required)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (!shizuku.isBinderAlive()) {
                            Toast.makeText(context, msgStartFirst, Toast.LENGTH_LONG).show()
                        } else if (!hasPermission) {
                            shizuku.requestPermission()
                        }
                    },
                    enabled = binderReady && !hasPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (hasPermission) stringResource(R.string.shizuku_permission_granted_btn)
                        else stringResource(R.string.shizuku_grant_permission_btn)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        if (shizuku.isInstalled(context)) {
                            val intent = context.packageManager
                                .getLaunchIntentForPackage(Shizuku.SHIZUKU_PACKAGE)
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, msgAppNotFound, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, msgAppNotFound, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.shizuku_open_app_btn))
                }
            }

            StatusCard(
                icon = Icons.Default.FlashOn,
                title = stringResource(R.string.shizuku_optimizations_title)
            ) {
                Text(
                    text = stringResource(R.string.shizuku_optimizations_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = if (isBatteryOptimized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shizuku_unrestricted_playback_title),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (isBatteryOptimized) stringResource(R.string.shizuku_status_whitelisted)
                            else stringResource(R.string.shizuku_status_standard_killed),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isBatteryOptimized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isBatteryOptimized) {
                    Button(
                        onClick = {
                            val res = shizuku.optimizeBackgroundPlayback(context)
                            refreshStatus(context)
                            if (res.success) {
                                Toast.makeText(context, msgOptimizationsEnabled, Toast.LENGTH_SHORT).show()
                            } else {
                                val err = (res.stderr.ifEmpty { msgCommandFailed }).take(60)
                                Toast.makeText(context, String.format(msgCommandErrorFormat, err), Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = hasPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.shizuku_enable_optimizations_btn))
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val res = shizuku.removeBackgroundPlaybackOptimization(context)
                            refreshStatus(context)
                            if (res.success) {
                                Toast.makeText(context, msgOptimizationsDisabled, Toast.LENGTH_SHORT).show()
                            } else {
                                val err = (res.stderr.ifEmpty { msgCommandFailed }).take(60)
                                Toast.makeText(context, String.format(msgCommandErrorFormat, err), Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = hasPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.shizuku_disable_optimizations_btn))
                    }
                }
            }
        }
    }
}