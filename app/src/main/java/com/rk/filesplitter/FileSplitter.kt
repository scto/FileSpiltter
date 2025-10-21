package com.rk.filesplitter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rk.filesplitter.pages.MergePage
import com.rk.filesplitter.pages.SplitPage
import com.rk.filesplitter.ui.theme.FileSplitterTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSplitterApp() {
    var selectedPage by rememberSaveable { mutableStateOf("split") }

    FileSplitterTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (selectedPage) {
                                "split" -> "FileSplitter"
                                "merge" -> "FileMerger"
                                else -> ""
                            }
                        )
                    }
                )
            },
            bottomBar = {
                BottomAppBar {
                    NavigationBarItem(
                        selected = selectedPage == "split",
                        onClick = { selectedPage = "split" },
                        icon = { Icon(Icons.Outlined.ContentCut, contentDescription = "Split") },
                        label = { Text("Split") }
                    )
                    NavigationBarItem(
                        selected = selectedPage == "merge",
                        onClick = { selectedPage = "merge" },
                        icon = { Icon(Icons.Outlined.Merge, contentDescription = "Merge") },
                        label = { Text("Merge") }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                when (selectedPage) {
                    "split" -> SplitPage()
                    "merge" -> MergePage()
                }
            }
        }
    }
}




