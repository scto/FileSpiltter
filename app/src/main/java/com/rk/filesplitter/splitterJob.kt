package com.rk.filesplitter


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.rk.filesplitter.pages.splitting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import android.content.Context
import android.net.Uri
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

private var fileName by mutableStateOf<String>("")
private var progressState by mutableFloatStateOf(0f)






suspend fun split(
    context: Context,
    file: Uri,
    destination: Uri,
    parts: Int,
    partNamePrefix: String
) = withContext(Dispatchers.IO) {
    require(parts > 1) { "Parts must be greater than one" }

    val resolver = context.contentResolver

    val inputStream = resolver.openInputStream(file)
        ?: throw IllegalArgumentException("Cannot open input stream")

    val size = resolver.query(file, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (cursor.moveToFirst()) cursor.getLong(index) else null
    } ?: throw IllegalStateException("Failed to get file size")

    val partSize = size / parts
    val remaining = size % parts

    val destDir = DocumentFile.fromTreeUri(context, destination)
        ?: throw IllegalArgumentException("Invalid destination directory")

    val buffer = ByteArray(8192)
    var totalRead = 0L

    val partHashes = mutableListOf<String>()
    val partSizes = mutableListOf<Long>()
    val originalDigest = MessageDigest.getInstance("SHA-256")

    // Track leftover bytes from previous read
    var leftoverBytes = 0
    var leftoverStart = 0

    inputStream.use { input ->
        for (i in 1..parts) {
            val partFileName = "${partNamePrefix}.part$i"
            fileName = partFileName
            val partFile = destDir.createFile("application/octet-stream", partFileName)
                ?: throw IllegalStateException("Failed to create part file")

            val outputStream = resolver.openOutputStream(partFile.uri)
                ?: throw IllegalStateException("Cannot open output stream")

            val partDigest = MessageDigest.getInstance("SHA-256")
            val targetSize = partSize + if (i == parts) remaining else 0L
            var written = 0L

            outputStream.use { output ->
                // First, write any leftover bytes from previous part
                if (leftoverBytes > 0) {
                    val toWrite = minOf(leftoverBytes.toLong(), targetSize - written).toInt()
                    output.write(buffer, leftoverStart, toWrite)
                    partDigest.update(buffer, leftoverStart, toWrite)
                    written += toWrite
                    totalRead += toWrite
                    progressState = totalRead.toFloat() / size

                    leftoverStart += toWrite
                    leftoverBytes -= toWrite

                    if (leftoverBytes == 0) {
                        leftoverStart = 0
                    }
                }

                // Continue reading and writing
                while (isActive && written < targetSize) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    // Update original hash with all bytes read
                    originalDigest.update(buffer, 0, bytesRead)

                    val toWrite = minOf(bytesRead.toLong(), targetSize - written).toInt()

                    // Write to current part
                    output.write(buffer, 0, toWrite)
                    partDigest.update(buffer, 0, toWrite)

                    written += toWrite
                    totalRead += toWrite
                    progressState = totalRead.toFloat() / size

                    // Store leftover bytes for next part
                    if (toWrite < bytesRead) {
                        leftoverBytes = bytesRead - toWrite
                        leftoverStart = toWrite
                        break // Part is complete, move to next
                    }
                }
            }

            partHashes.add(partDigest.digest().joinToString("") { "%02x".format(it) })
            partSizes.add(written)
        }
    }

    // Create metadata JSON using JSONObject API
    val metadata = JSONObject().apply {
        put("version", 1)
        put("originalFileName", DocumentFile.fromSingleUri(context, file)?.name ?: "unknown")
        put("originalSize", size)
        put("originalHash", originalDigest.digest().joinToString("") { "%02x".format(it) })

        val partsArray = JSONArray()
        partHashes.forEachIndexed { index, hash ->
            val partObj = JSONObject().apply {
                put("partNumber", index + 1)
                put("fileName", "${partNamePrefix}.part${index + 1}")
                put("size", partSizes[index])
                put("hash", hash)
            }
            partsArray.put(partObj)
        }
        put("parts", partsArray)
    }

    // Write metadata to file
    val metaFileName = "${partNamePrefix}.split_metadata"
    val metaFile = destDir.createFile("application/json", metaFileName)
        ?: throw IllegalStateException("Failed to create metadata file")
    resolver.openOutputStream(metaFile.uri)?.use { it.write(metadata.toString(4).toByteArray()) }

    withContext(Dispatchers.Main) {
        Toast.makeText(context, "Split complete and metadata saved!", Toast.LENGTH_SHORT).show()
    }
    splitting = false
}


@Composable
fun Splitting(
    filename: String = fileName,
    progress: Float = progressState,
    onCancel: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = filename,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Cancel button
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Cancel")
        }
    }
}

