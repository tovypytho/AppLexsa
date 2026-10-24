package com.tovypytho.applexsa.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.tovypytho.applexsa.MainActivity
import com.tovypytho.applexsa.core.*
import com.tovypytho.applexsa.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SetupMode { NONE, CHANNEL, CONTACT, INSPECT, CALIBRATE }
sealed interface Capture {
    data class Channel(val name: String) : Capture
    data class Destination(val contact: Contact) : Capture
    data class Point(val calibration: Calibration) : Capture
}

class LexsaService : AccessibilityService() {
    companion object {
        var instance: LexsaService? = null; private set
        val status = MutableStateFlow("Accessibility belum tersambung")
        var capture: Capture? = null
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private lateinit var store: LexsaStore
    private lateinit var overlay: FloatingControl
    private var profile = Profile()
    private val engine = ForwardEngine(SystemClock::elapsedRealtime)
    private val diagnostics = Diagnostics()
    private var setup = SetupMode.NONE
    private var setupDeadline = 0L
    private var recordedStart = -1L
    private var latestTree: UiNode? = null
    private var calibrationScreen: Screen? = null
    private var poll: Job? = null

    override fun onServiceConnected() {
        instance = this
        store = LexsaStore(this)
        overlay = FloatingControl(this, { enqueue { pressed() } }, { stop() })
        status.value = "Accessibility tersambung"
        scope.launch {
            store.profile.collect { p ->
                mutex.withLock {
                    if (profile.channel != p.channel || profile.contact != p.contact || !p.enabled) engine.cancel("CONFIG_CHANGED")
                    profile = p
                    refresh()
                }
            }
        }
        poll = scope.launch {
            while (isActive) {
                delay(250)
                mutex.withLock { refresh() }
            }
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::overlay.isInitialized) return
        // Ignore our own overlay's events; never use it as the underlying WhatsApp tree.
        if (event?.packageName?.toString() == packageName) return
        enqueue { refresh() }
    }
    override fun onInterrupt() { stop() }
    override fun onDestroy() {
        engine.cancel("SERVICE_STOPPED")
        poll?.cancel(); scope.cancel()
        if (::overlay.isInitialized) overlay.destroy()
        if (instance === this) instance = null
        status.value = "Accessibility terputus"; capture = null
        super.onDestroy()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enqueue {
            engine.cancel("DISPLAY_CHANGED"); setup = SetupMode.NONE
            overlay.destroy(); calibrationScreen = null
            store.update { it.copy(calibration = null) }
            refresh()
        }
    }
    private fun enqueue(block: suspend () -> Unit) {
        scope.launch { mutex.withLock { runCatching { block() }.onFailure {
            engine.cancel("INTERNAL_ERROR"); status.value = "Perlu diperiksa • INTERNAL_ERROR"; overlay.destroy()
        } } }
    }
    fun beginSetup(mode: SetupMode) { enqueue {
        engine.cancel("SETUP_STARTED"); setup = mode; setupDeadline = SystemClock.elapsedRealtime() + 120_000
        overlay.dismissCalibration(); calibrationScreen = null
        refresh()
    } }
    fun stop() { if (!::store.isInitialized) return; enqueue {
        engine.cancel("USER_STOPPED"); setup = SetupMode.NONE; overlay.destroy()
        store.update { it.copy(enabled = false) }; status.value = "Nonaktif"
    } }
    private fun read(root: AccessibilityNodeInfo? = rootInActiveWindow): Screen {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return Screen(packageName = "locked")
        if (root == null) return Screen(packageName = "none")
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg != "com.whatsapp") return Screen(packageName = pkg)
        val tree = AndroidTree.snapshot(root)
        latestTree = tree
        val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        return WhatsAppSelectors.read(tree, bounds.width(), bounds.height(), pkg)
    }
    private suspend fun refresh() {
        if (!profile.enabled && setup == SetupMode.NONE && !engine.busy) {
            overlay.destroy()
            finishRecord()
            return
        }
        val screen = read()
        if (setup != SetupMode.NONE && SystemClock.elapsedRealtime() >= setupDeadline) {
            setup = SetupMode.NONE; calibrationScreen = null; overlay.destroy()
        }
        if (screen.packageName != "com.whatsapp") {
            engine.cancel("FOREGROUND_LOST"); overlay.destroy(); calibrationScreen = null
            finishRecord(); return
        }
        if (engine.busy) {
            engine.observe(screen)?.let { execute(it) }
            finishRecord()
        }
        if (calibrationScreen != null) return
        when (setup) {
            SetupMode.CHANNEL -> if (screen.kind == ScreenKind.SOURCE) overlay.show("Ikat saluran ini") else overlay.hide()
            SetupMode.CONTACT -> if (screen.kind == ScreenKind.PICKER) overlay.show("Konfirmasi tujuan") else overlay.hide()
            SetupMode.INSPECT -> overlay.show("Rekam diagnosis layar")
            SetupMode.CALIBRATE -> if (screen.kind == ScreenKind.SOURCE && screen.channel == profile.channel) overlay.show("Tandai ikon teruskan") else overlay.hide()
            SetupMode.NONE -> {
                val source = profile.enabled && screen.kind == ScreenKind.SOURCE && screen.channel == profile.channel
                if (source) {
                    val label = when {
                        engine.busy -> "Memproses…"
                        engine.stage == Stage.ERROR -> "Perlu diperiksa"
                        screen.post?.identity == profile.lastAttempt && profile.lastAttempt.isNotBlank() ->
                            if (engine.stage == Stage.COMPLETE) "Sudah diteruskan" else "Sudah diproses • diblokir"
                        !screen.atBottom || screen.post == null -> "Siapkan ujung saluran"
                        else -> "Siap • teruskan"
                    }
                    overlay.show(label)
                } else overlay.hide()
            }
        }
    }
    private suspend fun pressed() {
        val screen = read()
        when (setup) {
            SetupMode.CHANNEL -> {
                if (screen.kind != ScreenKind.SOURCE || screen.channel.isBlank()) return
                completeSetup(Capture.Channel(screen.channel))
            }
            SetupMode.CONTACT -> {
                val selected = screen.recipients.filter { it.checked }
                if (screen.kind != ScreenKind.PICKER || screen.selectedCount != 1 || selected.size != 1 ||
                    screen.recipients.count { it.contact == selected.single().contact } != 1) {
                    toast("Pilih tepat satu kontak yang identitasnya tidak ambigu."); return
                }
                completeSetup(Capture.Destination(selected.single().contact))
            }
            SetupMode.INSPECT -> {
                latestTree?.let { diagnostics.structure(WhatsAppSelectors.structuralReport(it)) }
                store.setReport(diagnostics.report())
                setup = SetupMode.NONE; overlay.hide(); openApp()
            }
            SetupMode.CALIBRATE -> {
                val post = screen.post
                if (!screen.atBottom || post == null || screen.channel != profile.channel) { toast("Postingan terakhir harus lengkap di ujung saluran."); return }
                calibrationScreen = screen
                overlay.calibrate { x, y -> enqueue {
                    val now = read()
                    overlay.dismissCalibration(); calibrationScreen = null
                    val stillSame = now.kind == ScreenKind.SOURCE && now.channel == screen.channel && now.atBottom && now.post == post && now.width == screen.width && now.height == screen.height
                    if (stillSame && post.box.contains(Box(x, y, x, y))) {
                        completeSetup(Capture.Point(Calibration(x, y, screen.width, screen.height, post.layout)))
                    } else { setup = SetupMode.NONE; toast("Layar berubah atau titik di luar postingan. Kalibrasi dibatalkan.") }
                } }
            }
            SetupMode.NONE -> {
                if (engine.busy) return
                engine.begin(screen, profile)?.let { execute(it) }
                finishRecord(); refresh()
            }
        }
    }
    private fun completeSetup(result: Capture) {
        setup = SetupMode.NONE; capture = result; overlay.hide(); openApp()
    }
    private fun openApp() { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }

    private suspend fun execute(command: Command) {
        if (!engine.withinDeadline) { engine.cancel("STAGE_TIMEOUT"); return }
        // Re-snapshot before EVERY action. Paths from previous frames must never be clicked blindly.
        var root = rootInActiveWindow
        if (root?.packageName?.toString() != "com.whatsapp" || getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            engine.cancel("FOREGROUND_LOST"); return
        }
        val fresh = read(root)
        val success = when (command) {
            is Command.Forward -> {
                if (fresh.channel != profile.channel || !fresh.atBottom || fresh.post != command.post) false
                else if (command.post.forwardPath != null) AndroidTree.click(AndroidTree.resolve(root!!, command.post.forwardPath))
                else {
                    val c = command.calibration
                    if (c == null || c.width != fresh.width || c.height != fresh.height || c.layout != fresh.post?.layout) false
                    else dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(
                        Path().apply { moveTo(c.x.toFloat(), c.y.toFloat()) }, 0, 40)).build(), null, null)
                }
            }
            is Command.Select -> {
                val matches = fresh.recipients.filter { it.contact == command.recipient.contact }
                fresh.kind == ScreenKind.PICKER && fresh.selectedCount == 0 && matches.size == 1 &&
                    AndroidTree.click(AndroidTree.resolve(root!!, matches.single().path))
            }
            is Command.Search -> {
                if (fresh.kind != ScreenKind.PICKER || fresh.selectedCount != 0) false
                else if (command.edit) {
                    val path = fresh.searchFieldPath
                    path != null && AndroidTree.resolve(root!!, path)?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, command.query) }) == true
                } else fresh.searchPath?.let { AndroidTree.click(AndroidTree.resolve(root!!, it)) } == true
            }
            is Command.Send -> {
                // Durable before click. If persistence fails, the exception handler prevents sending.
                store.update { it.copy(lastAttempt = command.identity) }
                profile = profile.copy(lastAttempt = command.identity)
                root = rootInActiveWindow
                val lastCheck = read(root)
                val matches = lastCheck.recipients.filter { it.contact == profile.contact }
                val path = lastCheck.sendPath
                engine.withinDeadline && lastCheck.kind == ScreenKind.PICKER && lastCheck.selectedCount == 1 &&
                    lastCheck.recipients.count { it.checked } == 1 && matches.size == 1 && matches.single().checked &&
                    path != null && root?.packageName?.toString() == "com.whatsapp" && AndroidTree.click(AndroidTree.resolve(root!!, path))
            }
            Command.Back -> fresh.kind == ScreenKind.CHAT && normalized(fresh.chatContact) == normalized(profile.contact?.name.orEmpty()) &&
                performGlobalAction(GLOBAL_ACTION_BACK)
        }
        if (!success) engine.actionFailed()
        status.value = "${engine.stage} • ${engine.code}"
    }
    private suspend fun finishRecord() {
        status.value = when (engine.stage) {
            Stage.COMPLETE -> "Perintah kirim dijalankan; penerimaan pesan belum diverifikasi."
            Stage.ERROR -> "Perlu diperiksa • ${engine.code}"
            else -> "${engine.stage} • ${engine.code}"
        }
        if (engine.stage !in setOf(Stage.ERROR, Stage.COMPLETE) || recordedStart == engine.attemptId) return
        recordedStart = engine.attemptId
        diagnostics.record(Attempt(engine.stage, engine.code, SystemClock.elapsedRealtime() - engine.startedAt, engine.sendAt?.minus(engine.startedAt), engine.timeline.toList()))
        latestTree?.let { diagnostics.structure(WhatsAppSelectors.structuralReport(it)) }
        store.setReport(diagnostics.report())
    }
}
