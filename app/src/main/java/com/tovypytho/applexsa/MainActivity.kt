package com.tovypytho.applexsa

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tovypytho.applexsa.automation.*
import com.tovypytho.applexsa.core.*
import com.tovypytho.applexsa.data.LexsaStore
import com.tovypytho.applexsa.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var ui: ActivityMainBinding
    private lateinit var store: LexsaStore
    private val scope = MainScope()
    private var profile = Profile()
    private var latestReport = "Belum ada diagnosis. Gunakan Periksa struktur layar WhatsApp."
    private var serviceStatus = ""
    private var confirmationOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)
        val padding = (20 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(ui.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        store = LexsaStore(this)
        ui.homeTab.setOnClickListener { page(0) }
        ui.settingsTab.setOnClickListener { page(1) }
        ui.diagnosticsTab.setOnClickListener { page(2) }
        ui.accessibility.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Akses layar dan tindakan otomatis")
                .setMessage(getString(R.string.service_description))
                .setNegativeButton("Batal", null).setPositiveButton("Buka pengaturan") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.show()
        }
        ui.enable.setOnClickListener {
            if (LexsaService.instance == null) { toast("Aktifkan Accessibility AppLexsa terlebih dahulu."); return@setOnClickListener }
            if (profile.channel.isBlank() || profile.contact == null) { page(1); toast("Ikat saluran dan konfirmasikan kontak terlebih dahulu."); return@setOnClickListener }
            change { it.copy(enabled = !it.enabled) }
        }
        ui.openWa.setOnClickListener { openWhatsApp() }
        ui.bindChannel.setOnClickListener { setup(SetupMode.CHANNEL, "Buka saluran yang dituju di WhatsApp, lalu tekan “Ikat saluran ini” pada tombol AppLexsa.") }
        ui.bindContact.setOnClickListener { setup(SetupMode.CONTACT, "Di saluran, ketuk teruskan pada satu postingan, lalu pilih tepat satu kontak. Tekan “Konfirmasi tujuan” pada tombol AppLexsa. AppLexsa tidak menekan kirim selama penyiapan; batalkan pemilih setelah selesai.") }
        ui.calibrate.setOnClickListener { setup(SetupMode.CALIBRATE, "Buka saluran yang sudah diikat, gulir ke postingan terakhir sampai lengkap, lalu tekan “Tandai ikon teruskan”. Kalibrasi harus diulang jika tata letak berubah.") }
        ui.inspect.setOnClickListener { setup(SetupMode.INSPECT, "Buka layar saluran atau pemilih penerima di WhatsApp, lalu tekan “Rekam diagnosis layar”. Teks pesan dan nama kontak tidak disertakan dalam laporan.") }
        ui.reset.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Izinkan forward ulang?").setMessage("Reset menghapus blokir postingan terakhir. Gunakan hanya setelah memeriksa apakah pesan sebelumnya sudah dikirim.")
                .setNegativeButton("Batal", null).setPositiveButton("Reset") { _, _ -> change { it.copy(lastAttempt = "") } }.show()
        }
        ui.export.setOnClickListener { exportReport() }
        scope.launch { store.profile.collect { profile = it; render() } }
        scope.launch { store.report.collect { if (it.isNotBlank()) latestReport = it; render() } }
        scope.launch { LexsaService.status.collect { serviceStatus = it; render() } }
        page(savedInstanceState?.getInt("page") ?: 0)
    }
    private var currentPage = 0
    private fun page(index: Int) {
        currentPage = index
        ui.homePage.visibility = if (index == 0) View.VISIBLE else View.GONE
        ui.settingsPage.visibility = if (index == 1) View.VISIBLE else View.GONE
        ui.diagnosticsPage.visibility = if (index == 2) View.VISIBLE else View.GONE
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("page", currentPage); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); if (::ui.isInitialized) { render(); showCapture() } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); showCapture() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private fun render() {
        val connected = LexsaService.instance != null
        ui.status.text = "Accessibility: ${if (connected) "tersambung" else "belum aktif"}\nTombol: ${if (profile.enabled) "aktif" else "nonaktif"}\n\n$serviceStatus"
        ui.profile.text = "Saluran: ${profile.channel.ifBlank { "belum diikat" }}\nPenjual: ${profile.contact?.name ?: "belum dikonfirmasi"}\nKalibrasi: ${if (profile.calibration != null) "tersimpan" else "belum ada"}"
        ui.enable.setText(if (profile.enabled) R.string.disable else R.string.enable)
        ui.report.text = latestReport
    }
    private fun change(transform: (Profile) -> Profile) { scope.launch {
        runCatching { store.update(transform) }.onFailure { toast("Pengaturan tidak dapat disimpan.") }
    } }
    private fun setup(mode: SetupMode, explanation: String) {
        val service = LexsaService.instance ?: run { toast("Aktifkan Accessibility AppLexsa terlebih dahulu."); return }
        if (mode == SetupMode.CALIBRATE && profile.channel.isBlank()) { toast("Ikat saluran terlebih dahulu."); return }
        AlertDialog.Builder(this).setTitle("Siapkan di WhatsApp").setMessage(explanation)
            .setNegativeButton("Batal", null).setPositiveButton("Buka WhatsApp") { _, _ ->
                service.beginSetup(mode); openWhatsApp()
            }.show()
    }
    private fun showCapture() {
        val capture = LexsaService.capture ?: return
        if (confirmationOpen) return
        confirmationOpen = true
        val message = when (capture) {
            is Capture.Channel -> "Gunakan saluran “${capture.name}”?"
            is Capture.Destination -> "Gunakan kontak “${capture.contact.name}” yang baru Anda pilih?\nIdentitas lokal: ${capture.contact.signature.take(8)}\nPastikan ini kontak penjual yang benar."
            is Capture.Point -> "Simpan titik ikon teruskan (${capture.calibration.x}, ${capture.calibration.y}) untuk tata letak ini?"
        }
        AlertDialog.Builder(this).setTitle("Konfirmasi pengaturan").setMessage(message)
            .setNegativeButton("Batal", null).setPositiveButton("Simpan") { _, _ -> change { p ->
                when (capture) {
                    is Capture.Channel -> p.copy(channel = capture.name, enabled = false, calibration = null, lastAttempt = "")
                    is Capture.Destination -> p.copy(contact = capture.contact, enabled = false)
                    is Capture.Point -> p.copy(calibration = capture.calibration, enabled = false)
                }
            } }.setOnDismissListener { LexsaService.capture = null; confirmationOpen = false }.show()
    }
    private fun openWhatsApp() {
        val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent == null) toast("WhatsApp biasa belum terpasang.") else startActivity(intent)
    }
    private fun exportReport() { scope.launch {
        runCatching {
            val report = store.report.first().ifBlank { latestReport }
            val file = File(cacheDir, "reports/lexsa-diagnosis.txt").apply { parentFile?.mkdirs(); writeText(report) }
            val uri: Uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Ekspor diagnosis"))
        }.onFailure { toast("Diagnosis tidak dapat diekspor.") }
    } }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
