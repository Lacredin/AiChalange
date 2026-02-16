package com.example.aiadventchalengetestllmapi

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
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

    Window(
        onCloseRequest = ::exitApplication,
        title = "aiadventchalengetestllmapi",
    ) {
        App()
    }
}
