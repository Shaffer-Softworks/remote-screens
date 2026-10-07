package dev.remotescreens.player

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import dev.remotescreens.shared.ActionRef
import dev.remotescreens.shared.PlayerMessage
import dev.remotescreens.shared.ScreenSettings
import dev.remotescreens.shared.ServerMessage
import dev.remotescreens.shared.Slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs get() = (application as PlayerApp).prefs
    private lateinit var host: RcHost
    private lateinit var screen: ScreenControl
    private lateinit var connection: Connection
    private lateinit var discovery: Discovery
    private lateinit var updater: Updater
    private lateinit var frame: SwipeFrame
    private lateinit var info: LinearLayout
    private lateinit var title: TextView
    private lateinit var code: TextView
    private lateinit var body: TextView
    private lateinit var addressButton: Button
    private lateinit var badge: TextView
    private var size = Triple(0, 0, 0f)
    private var paired = false
    private var statusJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = RcHost(this, onAction = ::action, onPage = { page -> send(PlayerMessage.PageShown(page)); sendState() })
        frame = SwipeFrame(this, touched = { screen.touched() }, swiped = { next ->
            if (!host.overlayShown) if (next) host.next() else host.previous()
        })
        frame.setBackgroundColor(getColor(R.color.page))
        frame.addView(host.view, match())
        info = infoView()
        frame.addView(info, match())
        badge = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC000000.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        frame.addView(badge, match())
        val blackout = View(this).apply { setBackgroundColor(Color.BLACK); visibility = View.GONE; isClickable = false }
        frame.addView(blackout, match())
        setContentView(frame)

        screen = ScreenControl(this, blackout, goHome = { host.closeTop(); host.showPage(host.homePage) }, awakeChanged = { sendState() })
        connection = Connection(scope, ::hello) { msg -> withContext(Dispatchers.Main) { receive(msg) } }
        updater = Updater(this, connection.client)
        discovery = Discovery(this) { server -> runOnUiThread { prefs.server = server; connect(server) } }
        size = measure()
        screen.start()

        statusJob = scope.launch {
            connection.status.collectLatest { s ->
                when (s) {
                    Connection.Status.CONNECTING -> if (!host.hasDocument && !paired) showInfo(getString(R.string.connecting, connection.server ?: ""))
                    Connection.Status.DISCONNECTED -> if (!host.hasDocument) showInfo(getString(R.string.disconnected)) else Unit
                    else -> Unit
                }
            }
        }

        intent.getStringExtra(EXTRA_SERVER)?.let { prefs.server = it }
        val server = prefs.server
        if (server != null) connect(server) else search()
    }

    override fun onResume() {
        super.onResume()
        screen.immersive()
    }

    override fun onDestroy() {
        statusJob?.cancel()
        discovery.stop()
        connection.stop()
        screen.stop()
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Back closes a card first, then goes home.")
    override fun onBackPressed() {
        if (!host.closeTop() && host.page != host.homePage) host.showPage(host.homePage)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        frame.post {
            val now = measure()
            if (now != size) {
                size = now
                connection.reconnect()
            }
        }
    }

    private fun search() {
        showInfo(getString(R.string.searching), address = true)
        discovery.start()
    }

    private fun connect(server: String) {
        discovery.stop()
        connection.connect(server)
    }

    private fun hello(): PlayerMessage.Hello {
        val (w, h, d) = size
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else it.versionCode }
        return PlayerMessage.Hello(
            deviceId = prefs.deviceId,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            token = prefs.token,
            widthDp = w,
            heightDp = h,
            density = d,
            appVersion = version,
            capabilities = listOf("predictions", "bitmaps", "alerts", "cards", "settings"),
            darkSystem = night,
        )
    }

    /** The size of the whole window in dp, without the hidden system bars. */
    private fun measure(): Triple<Int, Int, Float> {
        val m = resources.displayMetrics
        val (wPx, hPx) = if (Build.VERSION.SDK_INT >= 30) {
            windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            @Suppress("DEPRECATION")
            android.util.DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        }
        return Triple((wPx / m.density).toInt(), (hPx / m.density).toInt(), m.density)
    }

    private fun receive(msg: ServerMessage) {
        when (msg) {
            is ServerMessage.Welcome -> welcome(msg)
            is ServerMessage.Document -> {
                host.show(msg)
                if (msg.slot == Slot.MAIN) hideInfo()
                if (msg.slot != Slot.MAIN) screen.touched()
            }
            is ServerMessage.Vars -> host.vars(msg)
            is ServerMessage.Bitmap -> scope.launch {
                val b = withContext(Dispatchers.Default) {
                    runCatching { Base64.decode(msg.data, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()
                }
                if (b != null) host.bitmap(msg.name, b)
            }
            is ServerMessage.Settings -> {
                screen.apply(msg.settings)
                frame.enabledSwipe = msg.settings.swipe
            }
            is ServerMessage.OpenPage -> {
                host.closeTop()
                host.showPage(msg.index)
                screen.touched()
            }
            is ServerMessage.Close -> host.close(msg.slot)
            is ServerMessage.Identify -> identify(msg.seconds)
            is ServerMessage.Wake -> screen.wake(msg.awake)
            is ServerMessage.UpdateAvailable -> offerUpdate(msg)
            is ServerMessage.Toast -> Toast.makeText(this, msg.text, Toast.LENGTH_LONG).show()
            ServerMessage.Pong -> Unit
        }
    }

    private fun welcome(w: ServerMessage.Welcome) {
        host.close(Slot.OVERLAY)
        host.close(Slot.ALERT)
        if (w.token != null) {
            prefs.token = w.token
            prefs.screenName = w.screenName
        }
        val pairing = w.pairingCode
        paired = pairing == null
        if (pairing != null) {
            if (w.token == null && prefs.token != null) prefs.forget()
            host.clear()
            showInfo(getString(R.string.pair_body), title = getString(R.string.pair_title), code = pairing.chunked(3).joinToString(" "))
        } else {
            host.revealMain()
            if (host.hasDocument) hideInfo()
        }
        sendState()
    }

    private fun action(slot: Slot, rev: Int, metadata: String) {
        screen.touched()
        val ref = ActionRef.decode(metadata) ?: return
        if (ref.local) {
            when (ref.verb) {
                "page" -> ref.arg.toIntOrNull()?.let { host.showPage(it) }
                "next" -> host.next()
                "prev" -> host.previous()
                "back" -> host.showPage(ref.arg.toIntOrNull() ?: host.homePage)
                "close" -> host.close(slot)
                "settings" -> settingsSheet()
                "noop" -> Unit
            }
            return
        }
        host.predict(slot, metadata)
        send(PlayerMessage.Action(slot, rev, metadata))
    }

    private fun send(msg: PlayerMessage) {
        scope.launch { connection.send(msg) }
    }

    private fun sendState() {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (l >= 0 && s > 0) l * 100 / s else null
        }
        val charging = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 }
        send(PlayerMessage.DeviceState(screen.awake, host.page, level, charging))
    }

    private fun identify(seconds: Int) {
        badge.text = getString(R.string.identify, prefs.screenName ?: Build.MODEL)
        badge.visibility = View.VISIBLE
        screen.wake(true)
        badge.removeCallbacks(hideBadge)
        badge.postDelayed(hideBadge, seconds.coerceIn(1, 60) * 1000L)
    }

    private val hideBadge = Runnable { badge.visibility = View.GONE }

    private fun offerUpdate(u: ServerMessage.UpdateAvailable) {
        val server = connection.server ?: return
        val token = prefs.token ?: return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.update_ready, u.versionName))
            .setPositiveButton(R.string.install) { _, _ ->
                if (!updater.canInstall()) {
                    Toast.makeText(this, R.string.update_permission, Toast.LENGTH_LONG).show()
                    updater.askPermission()
                } else scope.launch { updater.install(server, u.path, token) }
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun settingsSheet() {
        val s = screen.settings
        val pad = dp(20)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        fun label(text: String) = TextView(this).apply { this.text = text; setPadding(0, pad / 2, 0, 0) }

        box.addView(label(getString(R.string.brightness)))
        val brightness = SeekBar(this).apply { max = 99; progress = s.brightness - 1 }
        box.addView(brightness)

        val dark = Switch(this).apply { text = getString(R.string.dark_mode); isChecked = s.darkMode }
        box.addView(dark)

        val choices = listOf(0, 1, 5, 10, 30, 60)
        val standbyLabel = label("")
        fun standbyText(m: Int) = getString(R.string.standby) + ": " + if (m == 0) getString(R.string.never) else getString(R.string.minutes, m)
        val standby = SeekBar(this).apply {
            max = choices.size - 1
            progress = choices.indexOfFirst { it >= s.standbyMinutes }.takeIf { it >= 0 } ?: (choices.size - 1)
        }
        standbyLabel.text = standbyText(choices[standby.progress])
        standby.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, p: Int, fromUser: Boolean) { standbyLabel.text = standbyText(choices[p]) }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        box.addView(standbyLabel)
        box.addView(standby)

        box.addView(label(getString(R.string.server, connection.server ?: "—")))
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ ->
                val changed = s.copy(brightness = brightness.progress + 1, darkMode = dark.isChecked, standbyMinutes = choices[standby.progress])
                if (changed != s) {
                    screen.apply(changed)
                    send(PlayerMessage.SettingsChanged(changed))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.change_server) { _, _ -> askAddress() }
            .create()
        brightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, p: Int, fromUser: Boolean) {
                window.attributes = window.attributes.apply { screenBrightness = (p + 1) / 100f }
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        dialog.setOnDismissListener { screen.apply(screen.settings); screen.immersive() }
        val forget = Button(this).apply {
            text = getString(R.string.forget)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(R.string.forget_body)
                    .setPositiveButton(R.string.forget) { _, _ ->
                        dialog.dismiss()
                        prefs.forget()
                        host.clear()
                        connection.reconnect()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
        box.addView(forget)
        if (!isHomeApp() && !android.provider.Settings.canDrawOverlays(this)) {
            // Android only lets an app open itself (after a restart or an update) as the home app or with this.
            box.addView(Button(this).apply {
                text = getString(R.string.allow_self_start)
                setOnClickListener {
                    dialog.dismiss()
                    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            })
        }
        dialog.show()
    }

    private fun isHomeApp(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    private fun askAddress() {
        val field = EditText(this).apply {
            hint = getString(R.string.address_hint)
            setText(prefs.server ?: "")
            setSingleLine()
        }
        val pad = dp(20)
        val box = FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(field) }
        AlertDialog.Builder(this)
            .setTitle(R.string.address_title)
            .setMessage(R.string.address_body)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = field.text.toString().trim().removePrefix("http://").removePrefix("ws://").trimEnd('/')
                if (text.isEmpty()) return@setPositiveButton
                val server = if (':' in text) text else "$text:8100"
                prefs.server = server
                host.clear()
                connect(server)
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { screen.immersive() }
            .show()
    }

    private fun infoView(): LinearLayout {
        val pad = dp(32)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(getColor(R.color.page))
            title = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTextColor(getColor(R.color.ink))
                gravity = Gravity.CENTER
            }
            code = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
                setTextColor(getColor(R.color.accent))
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                letterSpacing = 0.1f
                gravity = Gravity.CENTER
                setPadding(0, pad / 2, 0, pad / 2)
            }
            body = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(getColor(R.color.ink_soft))
                gravity = Gravity.CENTER
            }
            addressButton = Button(context).apply {
                text = getString(R.string.enter_address)
                setOnClickListener { askAddress() }
            }
            addView(title)
            addView(code)
            addView(body)
            addView(addressButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = pad
            })
        }
    }

    private fun showInfo(text: String, title: String = getString(R.string.app_name), code: String? = null, address: Boolean = false) {
        this.title.text = title
        this.code.text = code ?: ""
        this.code.visibility = if (code == null) View.GONE else View.VISIBLE
        body.text = text
        addressButton.visibility = if (address || code == null) View.VISIBLE else View.GONE
        info.visibility = View.VISIBLE
    }

    private fun hideInfo() {
        info.visibility = View.GONE
    }

    private fun match() = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        /** `host:port` of the add-on, for setting a screen up over adb: `am start -n … --es server 10.0.0.5:8100`. */
        const val EXTRA_SERVER = "server"
    }
}
