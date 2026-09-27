package com.example.salusalo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.salusalo.data.PhoneLocation
import com.example.salusalo.data.PhoneLocationResult
import com.example.salusalo.model.DEFAULT_MAP_CENTER
import com.example.salusalo.model.DEFAULT_MAP_ZOOM
import com.example.salusalo.model.GeoPin
import com.example.salusalo.model.PIN_MAP_ZOOM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Where the map tiles come from. Every map in the app uses [MAP_TILES], so
 * another tile provider can replace it here without touching the screens.
 */
internal data class MapTileSource(
    /** Also the name of the tiles' cache. */
    val name: String,
    val baseUrl: String,
    /** Shown on every map. */
    val attribution: String,
    /** Opened when the attribution is tapped. */
    val attributionUrl: String,
    val maxZoom: Int,
    /** Downloads at the same time. */
    val maxConcurrent: Int
)

/**
 * OpenStreetMap's own tile servers. Their usage policy
 * (https://operations.osmfoundation.org/policies/tiles/) asks for a stable
 * User-Agent naming the app, visible attribution, caching that follows the
 * HTTP headers, and no bulk or offline downloading; osmdroid's policy flags
 * below enforce the download side. These free servers suit testing and a small
 * user base; a large public release should consider a tile provider meant for
 * apps (its key would stay out of the repository, like the Supabase keys).
 */
internal val MAP_TILES = MapTileSource(
    name = "OpenStreetMap",
    baseUrl = "https://tile.openstreetmap.org/",
    attribution = "© OpenStreetMap contributors",
    attributionUrl = "https://www.openstreetmap.org/copyright",
    maxZoom = 19,
    maxConcurrent = 2
)

private val tileSource: OnlineTileSourceBase by lazy {
    XYTileSource(
        MAP_TILES.name, 0, MAP_TILES.maxZoom, 256, ".png", arrayOf(MAP_TILES.baseUrl), MAP_TILES.attribution,
        TileSourcePolicy(
            MAP_TILES.maxConcurrent,
            TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE or
                    TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
        )
    )
}

/** Tiles may take up to 50 MB of the app's cache folder; above that, osmdroid trims to 40 MB. */
private const val TILE_CACHE_MAX_BYTES = 50L * 1024 * 1024
private const val TILE_CACHE_TRIM_BYTES = 40L * 1024 * 1024

private var osmdroidConfigured = false

/**
 * Sets up osmdroid once, before the first map: the app's own User-Agent and
 * the tile cache in the app's cache folder (no storage permission). osmdroid's
 * Configuration.load() is not called: it would store settings and replace the
 * User-Agent with a generic one.
 */
private fun configureOsmdroid(context: Context) {
    if (osmdroidConfigured) return
    val config = Configuration.getInstance()
    config.userAgentValue = "SaluSalo/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"
    val base = File(context.applicationContext.cacheDir, "osmdroid")
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles")
    config.tileFileSystemCacheMaxBytes = TILE_CACHE_MAX_BYTES
    config.tileFileSystemCacheTrimBytes = TILE_CACHE_TRIM_BYTES
    osmdroidConfigured = true
}

/** A request to move a map's view. A new [id] moves it again, even to the same place. */
internal data class MapCameraRequest(
    val center: GeoPin,
    val zoom: Double,
    val id: Long = System.nanoTime()
)

/** Below this zoom, a tap on the map also zooms in on the new pin. */
private const val TAP_ZOOM_BELOW = 15.0

/** Zoom for an approximate phone position, which can be about 2 km off. */
private const val APPROXIMATE_MAP_ZOOM = 14.0

/** A MapView that keeps a drag for itself, so the page around it doesn't scroll instead. */
private class PinMapView(context: Context) :
    MapView(context, MapTileProviderBasic(context.applicationContext, tileSource)) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        return super.dispatchTouchEvent(event)
    }
}

/**
 * An OpenStreetMap map with the attribution in its corner and, when [pin] is
 * set, a marker there. It starts at [start] and [startZoom]; pinch and drag
 * move it. With [onTap], a tap reports the position (for placing the pin).
 * Without a connection the map stays blank and the rest of the page works.
 */
@Composable
internal fun PickupMap(
    pin: GeoPin?,
    start: GeoPin,
    startZoom: Double,
    description: String,
    modifier: Modifier = Modifier,
    camera: MapCameraRequest? = null,
    onTap: ((GeoPin) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentOnTap by rememberUpdatedState(onTap)
    val mapView = remember {
        configureOsmdroid(context)
        PinMapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            isTilesScaledToDpi = true
            setMinZoomLevel(5.0)
            setMaxZoomLevel(MAP_TILES.maxZoom.toDouble())
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            controller.setZoom(startZoom)
            controller.setCenter(GeoPoint(start.latitude, start.longitude))
            val map = this
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                    val report = currentOnTap ?: return false
                    val tapped = GeoPin(point.latitude, point.longitude)
                    if (!tapped.isValid) return false
                    report(tapped)
                    if (map.zoomLevelDouble < TAP_ZOOM_BELOW) {
                        map.controller.animateTo(point, PIN_MAP_ZOOM, 400L)
                    }
                    return true
                }

                override fun longPressHelper(point: GeoPoint): Boolean = false
            }))
        }
    }
    val marker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setInfoWindow(null)
            // A tap on the marker still reaches the map, so the pin can be moved there.
            setOnMarkerClickListener { _, _ -> false }
        }
    }
    val appliedCamera = remember { longArrayOf(0L) }

    // Declared before the resume effect, so it is disposed after it: pause, then detach.
    DisposableEffect(mapView) {
        onDispose { mapView.onDetach() }
    }
    LifecycleResumeEffect(mapView) {
        mapView.onResume()
        onPauseOrDispose { mapView.onPause() }
    }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.matchParentSize(),
            update = { view ->
                view.contentDescription = description
                if (pin != null) {
                    marker.position = GeoPoint(pin.latitude, pin.longitude)
                    if (marker !in view.overlays) view.overlays.add(marker)
                } else {
                    view.overlays.remove(marker)
                }
                if (camera != null && camera.id != appliedCamera[0]) {
                    appliedCamera[0] = camera.id
                    view.controller.animateTo(
                        GeoPoint(camera.center.latitude, camera.center.longitude), camera.zoom, 400L
                    )
                }
                view.invalidate()
            }
        )

        Text(
            MAP_TILES.attribution,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.White.copy(alpha = 0.85f))
                .clickable {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MAP_TILES.attributionUrl)))
                    } catch (_: ActivityNotFoundException) {
                    }
                }
                .padding(horizontal = 5.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = Color(0xFF333333)
        )
    }
}

/**
 * Opens directions to [pin]: Google Maps (app or website), else any maps app
 * through a geo: link. The numbers are always written with a dot. Returns false
 * when nothing can open either link.
 */
internal fun openDirections(context: Context, pin: GeoPin): Boolean {
    val destination = pin.toQueryText()
    val links = listOf(
        "https://www.google.com/maps/dir/?api=1&destination=$destination",
        "geo:$destination?q=$destination"
    )
    for (link in links) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            return true
        } catch (_: ActivityNotFoundException) {
        }
    }
    return false
}

/**
 * Finds the phone's position for one screen, asking for location access first
 * when it isn't allowed yet. The result goes only to the caller's callback;
 * nothing is stored.
 */
internal class LocationFinder(private val context: Context, private val scope: CoroutineScope) {
    var finding by mutableStateOf(false)
        private set

    internal var askPermission: () -> Unit = {}
    private var pending: Request? = null
    private var job: Job? = null

    private class Request(
        val maxAgeMillis: Long,
        val preferFresh: Boolean,
        val onResult: (PhoneLocationResult) -> Unit
    )

    /**
     * Looks the position up. Without location access it asks for it when
     * [askIfNeeded], and otherwise reports NoPermission at once.
     */
    fun find(
        maxAgeMillis: Long,
        preferFresh: Boolean = false,
        askIfNeeded: Boolean = true,
        onResult: (PhoneLocationResult) -> Unit
    ) {
        val request = Request(maxAgeMillis, preferFresh, onResult)
        when {
            PhoneLocation.hasPermission(context) -> run(request)
            askIfNeeded -> {
                pending = request
                askPermission()
            }
            else -> onResult(PhoneLocationResult.NoPermission)
        }
    }

    internal fun onPermissionResult() {
        val request = pending ?: return
        pending = null
        if (PhoneLocation.hasPermission(context)) run(request)
        else request.onResult(PhoneLocationResult.NoPermission)
    }

    private fun run(request: Request) {
        job?.cancel()
        finding = true
        job = scope.launch {
            val result = PhoneLocation.find(context, request.maxAgeMillis, request.preferFresh)
            finding = false
            request.onResult(result)
        }
    }
}

@Composable
internal fun rememberLocationFinder(): LocationFinder {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val finder = remember { LocationFinder(context.applicationContext, scope) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        finder.onPermissionResult()
    }
    SideEffect { finder.askPermission = { launcher.launch(PhoneLocation.PERMISSIONS) } }
    return finder
}

private const val PIN_NOTE =
    "Everyone who can see this listing can see this pin. Put it where you'll hand over the food. " +
            "A public spot like a gate, store or plaza is fine; it doesn't have to be your home."

/** Where the picker's map starts without a pin: the phone's last known position, at most a day old. */
private const val START_POSITION_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L

/**
 * The pickup pin on Post and Edit: a map to tap, "Use my location", and the
 * note on who sees the pin. [savedPin] is the pin the listing had when Edit
 * opened (null on Post, and for older listings without one).
 */
@Composable
internal fun PickupPinPicker(
    pin: GeoPin?,
    onPinChange: (GeoPin) -> Unit,
    savedPin: GeoPin?,
    editing: Boolean,
    accent: Color
) {
    val context = LocalContext.current
    var placedNow by rememberSaveable { mutableStateOf(false) }
    var locationNote by rememberSaveable { mutableStateOf("") }
    var camera by remember { mutableStateOf<MapCameraRequest?>(null) }
    val finder = rememberLocationFinder()
    // The pin; else the phone's last known position if location access is
    // already allowed (never asks here); else Tagbilaran.
    val start = remember {
        pin?.let { it to PIN_MAP_ZOOM }
            ?: PhoneLocation.lastKnown(context, START_POSITION_MAX_AGE_MILLIS)?.let {
                it.position to if (it.approximate) APPROXIMATE_MAP_ZOOM else PIN_MAP_ZOOM
            }
            ?: (DEFAULT_MAP_CENTER to DEFAULT_MAP_ZOOM)
    }

    val status = when {
        placedNow -> "Pin placed. Tap the map again to move it."
        editing && savedPin == null ->
            "This listing has no pickup pin yet. Tap the map to add one. " +
                    "Without a pin, others see no map or distance."
        editing -> "Tap the map to move the pin."
        else -> "Tap the map to place the pin, or use your location."
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pickup pin", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(PIN_NOTE, fontSize = 11.sp, lineHeight = 15.sp)

        PickupMap(
            pin = pin,
            start = start.first,
            startZoom = start.second,
            description = "Map for the pickup pin. Tap to place the pin.",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp)),
            camera = camera,
            onTap = { tapped ->
                onPinChange(tapped.rounded())
                placedNow = true
                locationNote = ""
            }
        )

        Text(status, fontSize = 11.sp, lineHeight = 15.sp, color = accent)

        OutlinedButton(
            enabled = !finder.finding,
            onClick = {
                locationNote = ""
                finder.find(PhoneLocation.PIN_MAX_AGE_MILLIS, preferFresh = true) { result ->
                    when (result) {
                        is PhoneLocationResult.Found ->
                            if (result.approximate) {
                                camera = MapCameraRequest(result.position, APPROXIMATE_MAP_ZOOM)
                                locationNote = "Your phone shared only its approximate location, so the pin " +
                                        "wasn't placed. Zoom in and tap the exact spot."
                            } else {
                                onPinChange(result.position.rounded())
                                placedNow = true
                                camera = MapCameraRequest(result.position, PIN_MAP_ZOOM)
                            }

                        PhoneLocationResult.NoPermission ->
                            locationNote = "Location access is off for Salu-Salo. Tap the map to place the pin, " +
                                    "or allow location in Settings → Apps → Salu-Salo → Permissions → Location."

                        PhoneLocationResult.LocationOff ->
                            locationNote = "Your phone's location is turned off. Turn it on, or tap the map to place the pin."

                        PhoneLocationResult.NotFound ->
                            locationNote = "Your location could not be found. Try again, or tap the map to place the pin."
                    }
                }
            },
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (finder.finding) "Finding your location…" else "Use my location", color = accent)
        }

        if (locationNote.isNotBlank()) {
            Text(locationNote, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}
