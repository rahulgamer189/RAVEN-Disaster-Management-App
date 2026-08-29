package com.raven.application.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raven.application.OfflineAssistant
import com.raven.application.bluetooth.BluetoothDeviceDomain
import com.raven.application.bluetooth.BluetoothMessage
import com.raven.application.bluetooth.BluetoothViewModel
import com.raven.application.bluetooth.Camp
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI/UX direction: compact incident-response console. Screens use live mesh
 * flows, explicit empty/error states, and one visual system rather than route
 * files with independent hardcoded fixtures.
 */
private enum class AppScreen(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    MESH("Mesh", Icons.Default.Chat),
    MAP("Map", Icons.Default.Map),
    ASSISTANT("Guide", Icons.Default.Psychology),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun RavenApp(meshViewModel: BluetoothViewModel) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    val current = AppScreen.valueOf(screen)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                AppScreen.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination,
                        onClick = { screen = destination.name },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { padding ->
        when (current) {
            AppScreen.HOME -> HomeScreen(meshViewModel, padding) { screen = it.name }
            AppScreen.MESH -> MeshScreen(meshViewModel, padding)
            AppScreen.MAP -> MapScreen(meshViewModel, padding)
            AppScreen.ASSISTANT -> AssistantScreen(padding)
            AppScreen.SETTINGS -> SettingsScreen(meshViewModel, padding) { screen = it.name }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(viewModel: BluetoothViewModel, padding: PaddingValues, navigate: (AppScreen) -> Unit) {
    val connected by viewModel.connectedDevices.collectAsStateWithLifecycle()
    val peers by viewModel.peerTelemetry.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val paired by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val scanned by viewModel.scannedDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("RAVEN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 4.sp)
                    Text("Field network", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text("Live status from this device", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
                    Icon(Icons.Default.Shield, contentDescription = "Secure mesh", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp).size(28.dp))
                }
            }
        }
        item {
            EmergencyCard { viewModel.sendMessage("SOS emergency broadcast", type = "SOS") }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ActionCard(
                    modifier = Modifier.weight(1f), icon = Icons.Default.ShareLocation,
                    title = "Share location", detail = "Broadcast GPS", tint = MaterialTheme.colorScheme.secondary
                ) { viewModel.shareLocation() }
                ActionCard(
                    modifier = Modifier.weight(1f), icon = if (isScanning) Icons.Default.Refresh else Icons.Default.Search,
                    title = if (isScanning) "Scanning" else "Find nodes", detail = "${scanned.size} discovered", tint = MaterialTheme.colorScheme.primary
                ) { if (isScanning) viewModel.stopScanning() else viewModel.startScanning() }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("${connected.size}", "connected", Icons.Default.Bluetooth, Modifier.weight(1f))
                MetricCard("${peers.size}", "peer signals", Icons.Default.People, Modifier.weight(1f))
                MetricCard("${messages.size}", "messages", Icons.Default.Chat, Modifier.weight(1f))
            }
        }
        item { SectionLabel("Network pulse", "${paired.size} trusted · ${connected.size} live") }
        
        if (peers.isEmpty() && scanned.isEmpty()) {
            item { EmptyState(Icons.Default.SignalCellularAlt, "No nodes discovered", "Tap Find nodes above to start discovery.") }
        }

        if (peers.isNotEmpty()) {
            item { Text("Live Mesh Nodes", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(peers.values.toList(), key = { it.id }) { peer ->
                PeerCard(peer) {
                    viewModel.navigateTo(peer)
                    navigate(AppScreen.MAP)
                }
            }
        }

        val unconnectedScanned = scanned.filter { s -> connected.none { c -> c.address == s.address } }
        if (unconnectedScanned.isNotEmpty()) {
            item { Text("Discovered (Tap to connect)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(unconnectedScanned, key = { it.address }) { device ->
                ScannedDeviceRow(device) { viewModel.connectToDevice(device) }
            }
        }
        item {
            OutlinedButton(onClick = { navigate(AppScreen.MESH) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Chat, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open mesh communications")
            }
        }
    }
}

@Composable
private fun EmergencyCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(168.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(22.dp)) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(12.dp))
                Text("Emergency SOS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Broadcast an urgent alert with current telemetry", color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.72f))
            }
        }
    }
}

@Composable
private fun ActionCard(modifier: Modifier, icon: ImageVector, title: String, detail: String, tint: Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(126.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))) {
        Column(modifier = Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(9.dp).size(20.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.Start) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeshScreen(viewModel: BluetoothViewModel, padding: PaddingValues) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val devices by viewModel.connectedDevices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var draft by rememberSaveable { mutableStateOf("") }
    var assistantReply by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize().padding(padding),
        topBar = { TopAppBar(title = { Column { Text("Mesh communications", style = MaterialTheme.typography.titleLarge); Text("${devices.size} connected nodes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } }, actions = { IconButton(onClick = { viewModel.disconnect() }) { Icon(Icons.Default.Bluetooth, contentDescription = "Disconnect") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) },
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { assistantReply = OfflineAssistant.answer(context, draft) }) { Icon(Icons.Default.AutoAwesome, contentDescription = "Open offline guide", tint = MaterialTheme.colorScheme.primary) }
                    TextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Send an encrypted mesh message") }, maxLines = 3, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    IconButton(onClick = { viewModel.sendMessage("SOS emergency broadcast", type = "SOS") }) { Icon(Icons.Default.Warning, contentDescription = "Send SOS", tint = MaterialTheme.colorScheme.error) }
                    IconButton(onClick = { if (draft.isNotBlank()) { viewModel.sendMessage(draft.trim()); draft = "" } }, enabled = draft.isNotBlank()) { Icon(Icons.Default.Send, contentDescription = "Send message", tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    ) { inner ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (devices.isEmpty()) item { EmptyState(Icons.Default.Bluetooth, "No live connection", "Use Find nodes on Home to discover nearby devices.") }
            items(messages, key = { it.id }) { message -> MessageBubble(message, context) }
        }
    }
    assistantReply?.let { response ->
        AlertDialog(onDismissRequest = { assistantReply = null }, confirmButton = { TextButton(onClick = { assistantReply = null }) { Text("Close") } }, title = { Text("Offline emergency guide") }, text = { Text(response) })
    }
}

@Composable
private fun MessageBubble(message: BluetoothMessage, context: Context) {
    val isMine = message.senderName == context.getString(com.raven.application.R.string.label_sender_me)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Surface(shape = RoundedCornerShape(18.dp), color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
            Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
                if (!isMine) Text(message.senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(message.message, style = MaterialTheme.typography.bodyLarge)
                Text(formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.62f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapScreen(viewModel: BluetoothViewModel, padding: PaddingValues) {
    val peers by viewModel.peerTelemetry.collectAsStateWithLifecycle()
    val camps by viewModel.camps.collectAsStateWithLifecycle()
    val navigationTarget by viewModel.navigationTarget.collectAsStateWithLifecycle()
    val ownLocation by viewModel.ownLocation.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var hasCenteredInitially by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isScanning) viewModel.startScanning()
    }

    LaunchedEffect(navigationTarget) {
        navigationTarget?.let { target ->
            if (target.latitude != null && target.longitude != null) {
                mapView.controller.animateTo(GeoPoint(target.latitude, target.longitude))
                mapView.controller.setZoom(18.5)
            }
        }
    }

    LaunchedEffect(ownLocation) {
        if (navigationTarget == null && ownLocation != null && !hasCenteredInitially) {
            ownLocation?.let { loc ->
                if (loc.latitude != null && loc.longitude != null) {
                    mapView.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                    mapView.controller.setZoom(15.0)
                    hasCenteredInitially = true
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().padding(padding),
        topBar = {
            TopAppBar(
                title = { Text("Tactical map") },
                actions = {
                    IconButton(onClick = { 
                        ownLocation?.let { loc ->
                            if (loc.latitude != null && loc.longitude != null) {
                                mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                            }
                        }
                    }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Center on me", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { viewModel.shareLocation() }) {
                        Icon(Icons.Default.ShareLocation, contentDescription = "Share location")
                    }
                    if (navigationTarget != null) {
                        IconButton(onClick = { viewModel.clearNavigation() }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Clear navigation", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            AndroidView(
                factory = {
                    mapView.apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(12.0)
                    }
                },
                update = { view ->
                    view.overlays.clear()
                    
                    // Add My Location Marker
                    ownLocation?.let { loc ->
                        if (loc.latitude != null && loc.longitude != null) {
                            val marker = Marker(view)
                            marker.position = GeoPoint(loc.latitude, loc.longitude)
                            marker.title = "Me"
                            marker.icon = context.getDrawable(android.R.drawable.presence_online)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            view.overlays.add(marker)
                        }
                    }

                    // Add Peer Markers
                    peers.values.forEach { peer ->
                        if (peer.latitude != null && peer.longitude != null) {
                            val marker = Marker(view)
                            marker.position = GeoPoint(peer.latitude, peer.longitude)
                            marker.title = peer.senderName
                            marker.snippet = "Battery: ${peer.batteryPercentage}%"
                            if (peer.messageType == "SOS") {
                                marker.icon = context.getDrawable(android.R.drawable.ic_dialog_alert)
                                marker.subDescription = "EMERGENCY SOS"
                            }
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            view.overlays.add(marker)
                        }
                    }

                    // Add Camp Markers
                    camps.forEach { camp ->
                        val marker = Marker(view)
                        marker.position = GeoPoint(camp.latitude, camp.longitude)
                        marker.title = camp.name
                        marker.icon = context.getDrawable(android.R.drawable.ic_menu_myplaces)
                        view.overlays.add(marker)
                    }
                    
                    view.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            if (peers.isEmpty() && camps.isEmpty()) {
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                ) {
                    Text(
                        "Waiting for live mesh telemetry or base broadcasts...",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            navigationTarget?.let { target ->
                 Card(
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Navigation, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Navigating to ${target.senderName}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationRow(message: BluetoothMessage) {
    ListItem(headlineContent = { Text(message.senderName, fontWeight = FontWeight.Bold) }, supportingContent = { Text("${message.latitude ?: "—"}, ${message.longitude ?: "—"} · battery ${message.batteryPercentage ?: "—"}%") }, leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = if (message.messageType == "SOS") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) })
    Divider()
}

@Composable
private fun CampRow(camp: Camp) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        ListItem(headlineContent = { Text(camp.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text("${camp.latitude}, ${camp.longitude}") }, leadingContent = { Icon(Icons.Default.Navigation, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) })
    }
}

@Composable
private fun AssistantScreen(padding: PaddingValues) {
    val context = LocalContext.current
    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf<String?>(null) }
    val suggestions = listOf("Severe bleeding", "Possible fracture", "How to purify water", "Burn first aid", "CPR steps")

    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Column { Text("Offline emergency guide", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("Practical first-aid guidance stored on this device. It cannot diagnose or replace emergency services.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { OutlinedTextField(value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Describe the situation") }, leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null) }) }
        item { Button(onClick = { answer = OfflineAssistant.answer(context, question) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Get offline guidance") } }
        item { SectionLabel("Common situations", "") }
        items(suggestions) { suggestion -> AssistChip(onClick = { question = suggestion; answer = OfflineAssistant.answer(context, suggestion) }, label = { Text(suggestion) }, leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) }) }
        answer?.let { text -> item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(modifier = Modifier.padding(18.dp)) { Text("Guidance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(text) } } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(viewModel: BluetoothViewModel, padding: PaddingValues, navigate: (AppScreen) -> Unit) {
    val peers by viewModel.peerTelemetry.collectAsStateWithLifecycle()
    val connected by viewModel.connectedDevices.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(displayName) }

    Scaffold(modifier = Modifier.fillMaxSize().padding(padding), topBar = { TopAppBar(title = { Text("Settings") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) }) { inner ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { StatusCard(connected.size, peers.size) }
            item { SettingsRow(Icons.Default.Person, "Profile", displayName.ifBlank { "Set the identity shown on local screens" }) { name = displayName; showProfile = true } }
            item { SettingsRow(Icons.Default.Storage, "Data storage", "Review local mesh usage") { } }
            item { SettingsRow(Icons.Default.Palette, "Appearance", "Raven dark field theme") { } }
            item { SettingsRow(Icons.Default.Language, "Language", "System language") { } }
            item { SettingsRow(Icons.Default.Info, "About Raven", "Offline-first emergency communications") { } }
            item { Spacer(Modifier.height(8.dp)); Text("Runtime state is sourced from BluetoothService and local Android permissions. No seeded peer list is used.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (showProfile) {
        AlertDialog(onDismissRequest = { showProfile = false }, title = { Text("Profile") }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") }) }, confirmButton = { TextButton(onClick = { viewModel.updateDisplayName(name); showProfile = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showProfile = false }) { Text("Cancel") } })
    }
}

@Composable
private fun StatusCard(connected: Int, peers: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)); Spacer(Modifier.width(8.dp)); Text("RAVEN ACTIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp) }
            Spacer(Modifier.height(14.dp))
            Text("$connected connected nodes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("$peers peer telemetry streams visible", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { if (connected > 0) 1f else 0f }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.Navigation, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ScannedDeviceRow(device: BluetoothDeviceDomain, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        ListItem(
            headlineContent = { Text(device.name ?: "Unnamed node", fontWeight = FontWeight.Bold) },
            supportingContent = { Text(device.address) },
            leadingContent = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun PeerCard(message: BluetoothMessage, onClick: () -> Unit) {
    val isSos = message.messageType == "SOS"
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isSos) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isSos) Icons.Default.Warning else Icons.Default.Person, contentDescription = null, tint = if (isSos) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(message.senderName, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${message.batteryPercentage ?: "—"}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(5.dp))
                Text("${message.latitude ?: "—"}, ${message.longitude ?: "—"}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.BatteryStd, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.8.sp); Spacer(Modifier.weight(1f)); if (value.isNotBlank()) Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.outline); Spacer(Modifier.height(10.dp)); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
