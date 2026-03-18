package com.example.aiadventchalengetestllmapi

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

private fun configureUtf8Console() {
    if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return

    runCatching {
        val utf8 = StandardCharsets.UTF_8
        System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, utf8))
        System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, utf8))
    }
}

fun main() = application {
    configureUtf8Console()
    val windowState = rememberWindowState(width = 1200.dp, height = 900.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "aiadventchalengetestllmapi",
        state = windowState,
    ) {
        RootApp()
    }
}

