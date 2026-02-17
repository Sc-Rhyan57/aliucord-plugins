package com.rhyan57.svclone

object CloneSession {
    var isActive = false
    val logs = StringBuilder()
    var currentProgress = 0f
    var currentPct = 0

    var onLog: ((String) -> Unit)? = null
    var onProgress: ((Float) -> Unit)? = null
    var onComplete: ((Boolean, String) -> Unit)? = null

    fun start() {
        isActive = true
        logs.clear()
        currentProgress = 0f
        currentPct = 0
    }

    fun addLog(msg: String) {
        logs.append("\n").append(msg)
        onLog?.invoke(msg)
    }

    fun updateProgress(p: Float) {
        currentProgress = p
        currentPct = (p * 100).toInt().coerceIn(0, 100)
        onProgress?.invoke(p)
    }

    fun complete(success: Boolean, msg: String) {
        isActive = false
        addLog(msg)
        onComplete?.invoke(success, msg)
    }

    fun detachUI() {
        onLog = null
        onProgress = null
        onComplete = null
    }

    fun clear() {
        isActive = false
        logs.clear()
        currentProgress = 0f
        currentPct = 0
        detachUI()
    }
}
