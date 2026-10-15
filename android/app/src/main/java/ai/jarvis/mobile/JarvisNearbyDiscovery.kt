package ai.jarvis.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** Nearby devices are discoveries, NOT authenticated or paired Jarvis controls.
 * Never expose network addresses / BLE identifiers to the language model.
 */
internal data class JarvisNearbyDevice(
    val key: String, val name: String, val transport: String, val protocol: String
)

internal object JarvisNearbyProtocols {
    // The advertised DNS-SD type is not evidence that remote control is authorized.
    val wifiServices = linkedMapOf(
        "_googlecast._tcp." to "Google Cast",
        "_airplay._tcp." to "AirPlay",
        "_hap._tcp." to "HomeKit (HAP)",
        "_esphomelib._tcp." to "ESPHome",
        "_matter._tcp." to "Matter"
    )
    const val WIFI_DURATION_MS = 12_000L
    const val BLE_DURATION_MS = 10_000L
    const val MAX_RESULTS = 60

    fun label(name: String?, fallback: String): String =
        name.orEmpty().trim().filter { it != '\n' && it != '\r' }.take(90)
            .ifBlank { fallback }

    fun wifiTypeLabel(type: String): String? =
        wifiServices.entries.firstOrNull {
            type.startsWith(it.key.removeSuffix("."), ignoreCase = true)
        }?.value

    fun requiresBlePermission(sdk: Int): Boolean = sdk >= 31
}

/** Time-limited, strictly user-triggered Android platform discovery.
 * Disconnects and unregisters on leaving Smart Home; no background scanning.
 */
internal class JarvisNearbyScanner(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var multicast: WifiManager.MulticastLock? = null
    private var wifiCallback: ((JarvisNearbyDevice) -> Unit)? = null
    private var wifiFinish: (() -> Unit)? = null
    private var wifiTimeout: Runnable? = null

    private var bleCallback: ScanCallback? = null
    private var bleFinish: (() -> Unit)? = null
    private var bleTimeout: Runnable? = null
    private var bleScanner: android.bluetooth.le.BluetoothLeScanner? = null

    fun blePermissionsGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** DNS-SD/mDNS discovery. This does not reveal every Wi-Fi device on the router. */
    fun scanWifi(onFound: (JarvisNearbyDevice) -> Unit, onEnd: (String) -> Unit) {
        if (wifiTimeout != null) return
        val found = linkedSetOf<String>()
        wifiCallback = onFound
        wifiFinish = { onEnd("Wi-Fi felderítés befejeződött.") }
        try {
            multicast = wifi.createMulticastLock("jarvis-smart-home-on-demand").apply {
                setReferenceCounted(false)
                acquire()
            }
            for ((type, protocol) in JarvisNearbyProtocols.wifiServices) {
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit
                    override fun onServiceFound(info: NsdServiceInfo) {
                        val name = JarvisNearbyProtocols.label(info.serviceName, "Hálózati eszköz")
                        val key = type + ":" + name
                        if (found.add(key) && found.size <= JarvisNearbyProtocols.MAX_RESULTS) {
                            wifiCallback?.invoke(JarvisNearbyDevice(key, name, "Wi-Fi", protocol))
                        }
                    }
                    override fun onServiceLost(info: NsdServiceInfo) = Unit
                    override fun onDiscoveryStopped(serviceType: String) = Unit
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        runCatching { nsd.stopServiceDiscovery(this) }
                        listeners.remove(this)
                    }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                }
                listeners.add(listener)
                runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
                    .onFailure { listeners.remove(listener) }
            }
            if (listeners.isEmpty()) {
                stopWifi()
                onEnd("A hálózati felderítés nem indult el.")
                return
            }
            val timeout = Runnable { stopWifi() }
            wifiTimeout = timeout
            handler.postDelayed(timeout, JarvisNearbyProtocols.WIFI_DURATION_MS)
        } catch (_: Exception) {
            stopWifi()
            onEnd("A Wi-Fi felderítés nem elérhető ezen a hálózaton.")
        }
    }

    fun stopWifi() {
        wifiTimeout?.let { handler.removeCallbacks(it) }
        wifiTimeout = null
        val snapshot = listeners.toList()
        listeners.clear()
        snapshot.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        runCatching { multicast?.takeIf { it.isHeld }?.release() }
        multicast = null
        wifiCallback = null
        val complete = wifiFinish
        wifiFinish = null
        complete?.invoke()
    }

    /** Scan BLE advertisements only: no pairing, GATT write, or vendor control. */
    @SuppressLint("MissingPermission")
    fun scanBle(onFound: (JarvisNearbyDevice) -> Unit, onEnd: (String) -> Unit) {
        if (bleTimeout != null) return
        if (!blePermissionsGranted()) {
            onEnd("Előbb engedélyezd a közeli eszközök hozzáférést.")
            return
        }
        val bluetooth = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter
        val scanner = bluetooth?.bluetoothLeScanner
        if (scanner == null) {
            onEnd("Kapcsold be a Bluetooth-t a telefonon.")
            return
        }
        val seen = linkedSetOf<String>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val id = result.device?.address ?: return
                if (!seen.add(id) || seen.size > JarvisNearbyProtocols.MAX_RESULTS) return
                val advertised = result.scanRecord?.deviceName
                val name = JarvisNearbyProtocols.label(advertised, "Ismeretlen BLE eszköz")
                onFound(JarvisNearbyDevice("ble:" + id, name, "Bluetooth LE", "BLE / GATT"))
            }
            override fun onScanFailed(errorCode: Int) {
                stopBle()
                onEnd("A Bluetooth-felderítés nem indult el.")
            }
        }
        try {
            bleScanner = scanner
            bleCallback = callback
            bleFinish = { onEnd("Bluetooth-felderítés befejeződött.") }
            val timeout = Runnable { stopBle() }
            bleTimeout = timeout
            scanner.startScan(callback)
            handler.postDelayed(timeout, JarvisNearbyProtocols.BLE_DURATION_MS)
        } catch (_: Exception) {
            stopBle()
            onEnd("A Bluetooth-felderítés nem indult el.")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBle() {
        bleTimeout?.let { handler.removeCallbacks(it) }
        bleTimeout = null
        val callback = bleCallback
        bleCallback = null
        if (callback != null) runCatching { bleScanner?.stopScan(callback) }
        bleScanner = null
        val end = bleFinish
        bleFinish = null
        end?.invoke()
    }

    fun close() {
        stopWifi()
        stopBle()
    }
}
