package com.rk.filesplitter.pages

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup


private var selectedFileUri by mutableStateOf<Uri?>(null)
private var selectedFileName by mutableStateOf<String?>(null)
private var selectedFileSizeByte by mutableStateOf<Long?>(null)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPage() {
    Column {
        val context = LocalContext.current
        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                if (uri != null) {
                    val size = getFileSize(context,uri)

                    if (size == null){
                        Toast.makeText(context, "Unable to get file size", Toast.LENGTH_SHORT).show()
                        return@rememberLauncherForActivityResult
                    }

                    if (size < 2 * 1024) {
                        Toast.makeText(context, "File size must be at least 2 KB", Toast.LENGTH_SHORT).show()
                        return@rememberLauncherForActivityResult
                    }

                    selectedFileUri = uri
                    selectedFileName = getFileName(context, uri)
                    selectedFileSizeByte = size
                }
            }
        )

        PreferenceGroup(heading = "Input") {
            SettingsToggle(
                label = if (selectedFileName == null) {
                "Select File"
            } else {
                selectedFileName
            }, default = false, showSwitch = false, startWidget = {
                Icon(
                    modifier = Modifier.padding(start = 16.dp),
                    imageVector = if (selectedFileName == null) Icons.Outlined.Add else Icons.Outlined.Refresh,
                    contentDescription = null
                )
            }, sideEffect = {
                filePickerLauncher.launch(arrayOf("*/*"))
            })
        }

        if (selectedFileUri != null) {
            var partsInputValue by rememberSaveable { mutableStateOf("2") }
            var sizeInputValue by rememberSaveable { mutableStateOf("") }
            var splitBy by remember { mutableIntStateOf(0) }

            // Initialize on first composition
            LaunchedEffect(selectedFileSizeByte) {
                if (selectedFileSizeByte != null) {
                    val sizePerPartBytes = selectedFileSizeByte!! / 2
                    val sizePerPartMB = sizePerPartBytes / (1024.0 * 1024.0)
                    sizeInputValue = "%.2f".format(sizePerPartMB)
                }
            }

            fun updateFromParts(newParts: String): Boolean {
                val parts = newParts.toIntOrNull()
                if (parts != null && parts >= 2) {
                    val sizePerPartBytes = selectedFileSizeByte!! / parts
                    val sizePerPartMB = sizePerPartBytes / (1024.0 * 1024.0)

                    if (sizePerPartMB < 0.01){
                        return false
                    }

                    partsInputValue = newParts
                    sizeInputValue = "%.2f".format(sizePerPartMB)
                    return true
                }
                return false
            }

            fun updateFromSize(newSize: String): Boolean {
                val sizeMB = newSize.toDoubleOrNull()
                if (sizeMB != null && sizeMB > 0.01) {
                    val sizeBytes = (sizeMB * 1024.0 * 1024.0).toLong()
                    val maxBytes = selectedFileSizeByte!! / 2
                    val clampedSizeBytes = sizeBytes.coerceAtMost(maxBytes)
                    val parts = kotlin.math.ceil(selectedFileSizeByte!! / clampedSizeBytes.toDouble()).toInt().coerceAtLeast(2)
                    sizeInputValue = newSize
                    partsInputValue = parts.toString()
                    return true
                }
                return false
            }

            PreferenceGroup(heading = "Split By") {
                SettingsToggle(label = "Parts ${if (partsInputValue.isNotEmpty()){"- ($partsInputValue)"}else{""}}",
                    default = false, showSwitch = false, startWidget = {
                        RadioButton(selected = splitBy == 0, onClick = {
                            splitBy = 0
                        })
                    }, sideEffect = {
                        splitBy = 0
                    })

                SettingsToggle(label = "Part Size ${if (sizeInputValue.isNotEmpty()){"- (${sizeInputValue}MB)"}else{""}}",
                    default = false, showSwitch = false, startWidget = {
                        RadioButton(selected = splitBy == 1, onClick = {
                            splitBy = 1
                        })
                    }, sideEffect = {
                        splitBy = 1
                    })
            }

            if (splitBy == 0) {
                var localPartsInputValue by rememberSaveable { mutableStateOf(partsInputValue) }
                OutlinedTextField(
                    value = localPartsInputValue,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onValueChange = { new ->
                        if (new.isEmpty() || new.all { it.isDigit() }) {
                            localPartsInputValue = new
                            if (new.isNotEmpty()) {
                                if (updateFromParts(new)){
                                    partsInputValue = new
                                }
                            }
                        }
                    },
                    label = { Text("Number of parts") },
                    singleLine = true,
                    isError = localPartsInputValue != partsInputValue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
            } else {
                var localSizeInputValue by rememberSaveable { mutableStateOf(sizeInputValue) }
                OutlinedTextField(
                    value = localSizeInputValue,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    onValueChange = { new ->
                        if (new.isEmpty() || new.all { it.isDigit() || it == '.' }) {
                            localSizeInputValue = new
                            if (new.isNotEmpty() && new != ".") {
                                if (updateFromSize(new)){
                                    sizeInputValue = new
                                }
                            }
                        }
                    },
                    label = { Text("Part size (MB)") },
                    singleLine = true,
                    isError = localSizeInputValue != sizeInputValue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }


    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && nameIndex != -1) {
            return it.getString(nameIndex)
        }
    }
    // fallback if display name not found
    return uri.lastPathSegment
}

fun getFileSize(context: Context, uri: Uri): Long? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
        if (it.moveToFirst() && sizeIndex != -1) {
            return it.getLong(sizeIndex)
        }
    }
    // fallback if size not available
    return null
}