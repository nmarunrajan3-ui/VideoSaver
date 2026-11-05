package com.instasaver.app.data.local

enum class DownloadStatus(val label: String) {
    PENDING("Queued"),
    RESOLVING("Resolving"),
    DOWNLOADING("Downloading"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}
