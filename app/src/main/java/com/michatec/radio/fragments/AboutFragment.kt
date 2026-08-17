package com.michatec.radio.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.michatec.radio.BuildConfig
import com.michatec.radio.R
import com.michatec.radio.ui.theme.RadioTheme

class AboutFragment : Fragment() {

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
                        AboutScreen()
                    }
                }
            }
        }
    }

    @Composable
    private fun AboutScreen() {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        setImageDrawable(
                                            ctx.packageManager.getApplicationIcon(ctx.packageName)
                                        )
                                        scaleType = ImageView.ScaleType.CENTER_CROP
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.app_name),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = stringResource(
                                R.string.about_version,
                                BuildConfig.VERSION_NAME
                            ),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                alpha = 0.7f
                            )
                        )
                    }
                }
            }

            item {
                AboutSection(
                    icon = Icons.Default.Info,
                    title = "About Radio"
                ) {
                    Text(
                        text = "Radio is an application with a minimalist approach " +
                                "to listening to radio over the Internet.",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Radio offers a basic search option and allows " +
                                "audio streaming links to be imported from a web browser.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Radio supports Android TV and Google Cast, " +
                                "along with Web Control and a developer API.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Pull requests are welcome at any time.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item {
                AboutSection(
                    icon = Icons.Default.Language,
                    title = "Frequently Asked Questions"
                ) {

                    FaqItem(
                        question = "How can I add a radio station?",
                        answer = "There are three ways to add a radio station: " +
                                "use Search, add a playlist file address (M3U or PLS), " +
                                "or enter a raw stream address. " +
                                "Stations added using a raw stream address do not support updates."
                    )

                    FaqItem(
                        question = "How does the update feature work?",
                        answer = "The update feature tries to fetch the current stream " +
                                "address, updated station name and station image. " +
                                "It does not work for stations added using a raw stream " +
                                "address or stations imported from older versions of Radio."
                    )

                    FaqItem(
                        question = "Where do radio station search results come from?",
                        answer = "Radio searches the Radio Browser online database."
                    )
                }
            }
            item {
                AboutSection(
                    icon = Icons.Default.Radio,
                    title = "Supported Formats"
                ) {

                    val formats = listOf(
                        "AAC",
                        "AAC+",
                        "FLAC",
                        "HLS (M3U8)",
                        "M3U",
                        "MP3",
                        "OGG (Vorbis)",
                        "OPUS",
                        "PLS"
                    )

                    formats.forEach { format ->
                        FormatRow(format)
                    }
                }
            }
            item {
                AboutSection(
                    icon = Icons.Default.Tv,
                    title = "Android TV Controls"
                ) {

                    Text(
                        text = "When Edit Stations is enabled:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ControlRow(
                        key = "←",
                        description = "Open the detailed station editing area"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "General TV Controls",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ControlRow(
                        key = "0 / DEL",
                        description = "Remove the selected radio station"
                    )

                    ControlRow(
                        key = "1 / SPACE",
                        description = "Make the selected radio station a favourite"
                    )

                    ControlRow(
                        key = "2 / PLUS",
                        description = "Reorder the selected radio station"
                    )
                }
            }
            item {
                AboutSection(
                    icon = Icons.Default.Code,
                    title = "Credits"
                ) {

                    CreditItem(
                        name = "Radio Browser",
                        description = "Provides the radio station database",
                        url = "https://www.radio-browser.info"
                    )

                    CreditItem(
                        name = "Michatec",
                        description = "Developing and maintaining Radio"
                    )

                    CreditItem(
                        name = "Contributors",
                        description = "Everyone contributing through pull requests, " +
                                "issues and feedback"
                    )
                }
            }
        }
    }

    @Composable
    private fun AboutSection(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
                .focusable(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.size(12.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                content()
            }
        }
    }

    @Composable
    private fun FaqItem(
        question: String,
        answer: String
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp)
        ) {
            Text(
                text = question,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    private fun FormatRow(
        format: String
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = format,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "✓",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp
            )
        }
    }

    @Composable
    private fun ControlRow(
        key: String,
        description: String
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = key,
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 6.dp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    private fun CreditItem(
        name: String,
        description: String,
        url: String? = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .then(
                    if (url != null) {
                        Modifier.clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                url.toUri()
                            )
                            context?.startActivity(intent)
                        }
                        .focusable()
                    } else {
                        Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = if (url != null) {
                    Icons.AutoMirrored.Filled.OpenInNew
                } else {
                    Icons.Default.Code
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.size(12.dp))

            Column {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}