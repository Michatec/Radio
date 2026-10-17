package com.michatec.radio.fragments

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.michatec.radio.R
import com.michatec.radio.plugins.PluginManager
import com.michatec.radio.plugins.PluginManifest
import com.michatec.radio.plugins.RdsContainer
import com.michatec.radio.ui.theme.RadioTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files

class PluginsFragment : Fragment() {
    private companion object {
        const val MAX_PLUGIN_ARCHIVE_BYTES = 50L * 1024 * 1024
    }

    private lateinit var pluginManager: PluginManager
    private val manifests = mutableStateOf<List<PluginManifest>>(emptyList())

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            handleSelectedPluginUri(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                RadioTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PluginsScreen(
                            manifests = manifests.value,
                            pluginManager = pluginManager,
                            onLoadClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            onRefresh = { loadPlugins() },
                            onUninstall = { manifest -> confirmUninstall(manifest) }
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.show()
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as AppCompatActivity).supportActionBar?.title = getString(R.string.pref_plugins_title)

        pluginManager = PluginManager.getInstance(requireContext())
        loadPlugins()
    }

    private fun loadPlugins() {
        manifests.value = pluginManager.getInstalledPluginManifests()
    }

    private fun confirmUninstall(manifest: PluginManifest) {
        AlertDialog.Builder(requireContext())
            .setTitle("Uninstall Plugin")
            .setMessage("Are you sure you want to uninstall '${manifest.name}'?")
            .setPositiveButton("Uninstall") { _, _ ->
                try {
                    pluginManager.uninstallPlugin(manifest.id)
                    loadPlugins()
                    Snackbar.make(requireView(), "Plugin uninstalled successfully", Snackbar.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Snackbar.make(
                        requireView(),
                        e.localizedMessage ?: "Could not uninstall plugin",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(R.string.dialog_generic_button_cancel, null)
            .show()
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor.use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result.substring(cut + 1)
            }
        }
        return result ?: ""
    }

    private fun handleSelectedPluginUri(uri: Uri) {
        val fileName = getFileName(uri)
        if (!fileName.endsWith(".rds", ignoreCase = true)) {
            AlertDialog.Builder(requireContext())
                .setTitle("Invalid File Type")
                .setMessage("Only .rds plugin files are allowed.")
                .setPositiveButton(R.string.dialog_generic_button_okay, null)
                .show()
            return
        }

        val cacheFile = try {
            File.createTempFile("plugin_import_", ".rds", requireContext().cacheDir)
        } catch (_: IOException) {
            showImportError("Unable to create temporary plugin file")
            return
        }

        try {
            val input = requireNotNull(requireContext().contentResolver.openInputStream(uri)) {
                "Unable to open selected plugin file"
            }
            input.use {
                FileOutputStream(cacheFile).use { output ->
                    val buffer = ByteArray(8_192)
                    var copiedBytes = 0L
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        copiedBytes += count
                        require(copiedBytes <= MAX_PLUGIN_ARCHIVE_BYTES) {
                            "Plugin archive exceeds the size limit"
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }

            val tempDir = Files.createTempDirectory(requireContext().cacheDir.toPath(), "plugin_validate_").toFile()
            val manifest = try {
                RdsContainer.unpack(cacheFile, tempDir)
            } finally {
                tempDir.deleteRecursively()
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.plugins_install_title)
                .setMessage(getString(R.string.plugins_install_confirm, manifest.name, manifest.version, manifest.author.ifEmpty { "Unknown" }))
                .setPositiveButton(R.string.dialog_yes_no_positive_button_default) { _, _ ->
                    try {
                        pluginManager.installPlugin(cacheFile)
                        Snackbar.make(requireView(), R.string.plugins_installed_success, Snackbar.LENGTH_LONG).show()
                        loadPlugins()
                    } catch (e: Exception) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Installation Error")
                            .setMessage(e.localizedMessage ?: "Unknown error")
                            .setPositiveButton(R.string.dialog_generic_button_okay, null)
                            .show()
                    } finally {
                        cacheFile.delete()
                    }
                }
                .setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
                    cacheFile.delete()
                }
                .setOnDismissListener {
                    cacheFile.delete()
                }
                .show()

        } catch (e: Exception) {
            cacheFile.delete()
            showImportError(e.localizedMessage ?: "Failed to read .rds file")
        }
    }

    private fun showImportError(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Invalid Plugin Archive")
            .setMessage(message)
            .setPositiveButton(R.string.dialog_generic_button_okay, null)
            .show()
    }
}

@Composable
fun PluginsScreen(
    manifests: List<PluginManifest>,
    pluginManager: PluginManager,
    onLoadClick: () -> Unit,
    onRefresh: () -> Unit,
    onUninstall: (PluginManifest) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = onLoadClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.plugins_load_button))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (manifests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.plugins_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(manifests, key = { it.id }) { manifest ->
                    PluginCard(
                        manifest = manifest,
                        pluginManager = pluginManager,
                        onUninstall = { onUninstall(manifest) },
                        onStateChanged = onRefresh
                    )
                }
            }
        }
    }
}

@Composable
fun PluginCard(
    manifest: PluginManifest,
    pluginManager: PluginManager,
    onUninstall: () -> Unit,
    onStateChanged: () -> Unit
) {
    var isEnabled by remember { mutableStateOf(pluginManager.isPluginEnabled(manifest.id)) }
    val context = LocalContext.current
    val activity = context as? Activity

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = manifest.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        try {
                            pluginManager.setPluginEnabled(manifest.id, checked)
                            isEnabled = checked
                            onStateChanged()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.localizedMessage ?: "Could not change plugin state",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }

            if (manifest.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = manifest.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "v${manifest.version} • By ${manifest.author.ifEmpty { "Unknown" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (pluginManager.hasSettings(manifest.id)) {
                    OutlinedButton(onClick = {
                        if (activity != null) {
                            pluginManager.openSettings(activity, manifest.id)
                        }
                    }) {
                        Text("Settings")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                OutlinedButton(onClick = onUninstall) {
                    Text(stringResource(R.string.plugins_uninstall))
                }
            }
        }
    }
}
