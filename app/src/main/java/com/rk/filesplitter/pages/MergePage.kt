package com.rk.filesplitter.pages

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow


private var metadataFiles = mutableStateListOf<DocumentFile>()
private var parentDir by mutableStateOf<DocumentFile?>(null)

@Composable
fun MergePage() {
    val scope = rememberCoroutineScope()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val context = LocalContext.current
        val directoryPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    val dir = DocumentFile.fromTreeUri(context, uri)

                    val files = dir?.listFiles()
                        ?.filter { it.name?.endsWith(".split_metadata.json") == true }
                        ?: emptyList()

                    if (dir == null) {
                        return@launch
                    }

                    if (files.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context.applicationContext,
                                "No metadata file found.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    parentDir = dir

                    // Update list instead of reassigning
                    metadataFiles.clear()
                    metadataFiles.addAll(files)
                }
            }
        }

        PreferenceGroup(heading = "Input") {
            SettingsToggle(
                label = parentDir?.name ?: "Select Directory",
                default = false,
                showSwitch = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp),
                        imageVector = if (parentDir == null) {
                            Icons.Outlined.Add
                        } else {
                            Icons.Outlined.Refresh
                        },
                        contentDescription = null
                    )
                },
                sideEffect = {
                    directoryPicker.launch(null)
                })
        }

        var metadata by remember { mutableStateOf<DocumentFile?>(null) }

        if (metadata == null && metadataFiles.isNotEmpty()) {
            metadata = metadataFiles.first()
        }

        if (metadataFiles.size > 1) {
            PreferenceGroup(heading = "metadata") {
                metadataFiles.forEach { file ->
                    SettingsToggle(
                        label = file.name,
                        showSwitch = false,
                        default = false,
                        startWidget = {
                            RadioButton(selected = metadata == file, onClick = {
                                metadata = file
                            })
                        },
                        sideEffect = {
                            metadata = file
                        })
                }
            }
        }

        var metadataJson by remember { mutableStateOf<JSONObject?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var isVerifying by remember { mutableStateOf(false) }

        LaunchedEffect(metadata) {
            if (metadata == null) {
                metadataJson = null
                error = null
                return@LaunchedEffect
            }

            isVerifying = true
            error = null
            metadataJson = null

            try {
                val text = readTextFromDocumentFile(context, metadata!!)
                val json = JSONObject(text ?: "")

                // Verify all parts exist and are valid
                val verificationError = verifyPartsIntegrity(context, parentDir!!, json)

                if (verificationError != null) {
                    error = verificationError
                } else {
                    metadataJson = json
                }
            } catch (e: Exception) {
                error = "Failed to read metadata: ${e.message}"
            } finally {
                isVerifying = false
            }
        }

        if (metadata != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    isVerifying -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Verifying parts integrity...")
                    }

                    error != null -> {
                        Text("Verification Failed",
                            fontWeight = FontWeight.Bold,
                            color = Color.Red)
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = Color.Red)
                    }

                    metadataJson != null -> {
                        val json = metadataJson!!
                        val originalName = json.optString("originalFileName", "Unknown")
                        val originalSize = json.optLong("originalSize", 0L)
                        val originalHash = json.optString("originalHash", "N/A")
                        val parts = json.optJSONArray("parts")

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            // Status header
                            Text(
                                text = "✅ Verification Passed",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(Modifier.height(24.dp))

                            // File Information Section
                            Text(
                                text = "File Information",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            InfoRow(label = "Name", value = originalName)
                            InfoRow(label = "Size", value = formatBytes(originalSize))
                            InfoRow(label = "Parts", value = "${parts?.length() ?: 0}")
                            InfoRow(
                                label = "Hash",
                                value = originalHash.take(16) + "...",
                                valueStyle = MaterialTheme.typography.bodySmall
                            )

                            // Parts Details Section
                            if (parts != null && parts.length() > 0) {
                                Spacer(Modifier.height(20.dp))

                                Text(
                                    text = "Parts Details",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (parts.length() <= 10) {
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        val partSize = part.getLong("size")
                                        val fileName = part.getString("fileName")
                                        val partNumber = part.getInt("partNumber")

                                        PartInfoRow(
                                            partNumber = partNumber,
                                            fileName = fileName,
                                            size = formatBytes(partSize)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "  ${parts.length()} parts ready to merge",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(32.dp))


                        val createFileLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.CreateDocument("application/octet-stream")
                        ) { uri: Uri? ->
                            if (uri != null && parentDir != null && metadataJson != null) {
                                val outputFile = DocumentFile.fromSingleUri(context.applicationContext,uri)
                                scope.launch {
                                    mergeFiles(
                                        context, outputFile!!, parentDir!!,
                                        metadata = metadataJson!!
                                    )
                                }
                            }
                        }

                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            shape = RoundedCornerShape(4.dp),
                            onClick = {
                                createFileLauncher.launch(originalName)
                            }
                        ) {
                            Text("Merge File")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Merges the split file parts back into the original file
 */
suspend fun mergeFiles(
    context: Context,
    outputFile: DocumentFile,
    partsParent: DocumentFile,
    metadata: JSONObject
) = withContext(Dispatchers.IO) {
    try {
        val originalName = metadata.getString("originalFileName")
        val parts = metadata.getJSONArray("parts")
        val originalHash = metadata.getString("originalHash")

        context.contentResolver.openOutputStream(outputFile.uri)?.use { output ->
            val digest = MessageDigest.getInstance("SHA-256")

            // Merge all parts
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val fileName = part.getString("fileName")
                val partFile = partsParent.findFile(fileName)
                    ?: throw Exception("Part file not found: $fileName")

                context.contentResolver.openInputStream(partFile.uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                    }
                }
            }

            // Verify merged file hash
            val mergedHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (mergedHash != originalHash) {
                outputFile.delete()
                throw Exception("Hash verification failed after merge")
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "File merged successfully: $originalName",
                Toast.LENGTH_LONG
            ).show()
        }

    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Merge failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

fun readTextFromDocumentFile(context: Context, documentFile: DocumentFile): String? {
    return try {
        val uri: Uri = documentFile.uri
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return String.format(
        Locale.getDefault(),
        "%.1f %s",
        bytes / 1024.0.pow(digitGroups.toDouble()),
        units[digitGroups]
    )
}

/**
 * Verifies the integrity of split file parts
 * Returns null if all checks pass, or an error message if something fails
 */
suspend fun verifyPartsIntegrity(
    context: Context,
    parentDir: DocumentFile,
    metadata: JSONObject
): String? = withContext(Dispatchers.IO) {
    try {
        val parts = metadata.optJSONArray("parts")
            ?: return@withContext "No parts found in metadata"

        val missingParts = mutableListOf<String>()
        val corruptedParts = mutableListOf<String>()
        val sizeMismatches = mutableListOf<String>()

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val fileName = part.getString("fileName")
            val expectedHash = part.getString("hash")
            val expectedSize = part.getLong("size")
            val partNumber = part.getInt("partNumber")

            // Check if file exists
            val partFile = parentDir.findFile(fileName)
            if (partFile == null || !partFile.exists()) {
                missingParts.add("Part $partNumber ($fileName)")
                continue
            }

            // Verify hash
            val actualHash = calculateFileHash(context, partFile)
            if (actualHash != expectedHash) {
                corruptedParts.add("Part $partNumber ($fileName)")
            }
        }

        // Build error message if any issues found
        val errors = mutableListOf<String>()
        if (missingParts.isNotEmpty()) {
            errors.add("❌ Missing parts:\n${missingParts.joinToString("\n")}")
        }
        if (corruptedParts.isNotEmpty()) {
            errors.add("🔴 Corrupted parts (hash mismatch):\n${corruptedParts.joinToString("\n")}")
        }

        if (errors.isNotEmpty()) {
            return@withContext errors.joinToString("\n\n")
        }

        return@withContext null // All checks passed

    } catch (e: Exception) {
        return@withContext "Verification failed: ${e.message}"
    }
}

/**
 * Calculates SHA-256 hash of a file
 */
suspend fun calculateFileHash(context: Context, file: DocumentFile): String =
    withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

/**
 * Displays a label-value pair with proper indentation
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Text(
            text = "$label:",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.3f),
            color = Color.Gray
        )
        Text(
            text = value,
            style = valueStyle,
            modifier = Modifier.weight(0.7f)
        )
    }
}

/**
 * Displays information about a file part
 */
@Composable
private fun PartInfoRow(
    partNumber: Int,
    fileName: String,
    size: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Part $partNumber",
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(0.3f)
            )
            Column(modifier = Modifier.weight(0.7f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                Text(
                    text = size,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}