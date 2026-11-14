package com.kernelflasher

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AppBackground = Color(0xFF090A0F)
private val CardBackground = Color(0xFF13151D)
private val MutedText = Color(0xFF8A99AD)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = AppBackground,
                    surface = CardBackground,
                    primary = Color(0xFF3880FF),
                    error = Color(0xFFFF5252)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppBackground
                ) {
                    MainDashboard()
                }
            }
        }
    }
}

@Composable
fun MainDashboard() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            CustomBottomBar(selectedTab) { selectedTab = it }
        },
        containerColor = AppBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> OtgAndRomScreen()
                1 -> LocalKernelScreen()
                2 -> ToolsScreen()
                3 -> ConsoleLogsScreen()
            }
        }
    }
}

object SharedConsole {
    val logs = mutableStateListOf<String>()

    fun add(message: String) {
        logs.add(message)
        // Keep the in-memory console bounded during long sessions.
        while (logs.size > 500) logs.removeAt(0)
    }

    fun clear() {
        logs.clear()
    }
}

@Composable
fun OtgAndRomScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var usbConnected by remember { mutableStateOf(false) }
    var deviceName by remember { mutableStateOf("No Device Connected") }
    var selectedMode by remember { mutableStateOf("sideload") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var expandRomCard by remember { mutableStateOf(true) }
    var expandRebootCard by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = Flasher.displayName(context, uri)
        }
    }

    fun scanUsb() {
        val devices = Flasher.getConnectedUsbDevices(context)
        if (devices.isNotEmpty()) {
            val device = devices.first()
            usbConnected = true
            deviceName = device.productName ?: "Dispositivo Android OTG"
            Flasher.requestUsbPermission(context, device)
            SharedConsole.add("[USB] Detectado: $deviceName (VID:${device.vendorId})")
        } else {
            usbConnected = false
            deviceName = "No Device Connected"
            SharedConsole.add("[USB] Ningún dispositivo OTG detectado.")
        }
    }

    LaunchedEffect(Unit) {
        scanUsb()
    }

    LazyContentColumn {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ROM ",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFACC15)
                        )
                        Text(
                            "👑 FLASHER",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1F1624),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .border(1.dp, Color(0xFF582C4D), RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            " ⚡ All-In-One Android OTG Tool ",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = Color(0xFFF472B6),
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(
                    onClick = { scanUsb() },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E222D))
                        .border(1.dp, Color(0xFF333A4D), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Escanear OTG",
                        tint = Color(0xFF60A5FA)
                    )
                }
            }
        }

        item {
            DashboardCard(
                borderColor = if (usbConnected) Color(0xFF22C55E) else Color(0xFF2E3446)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF162436)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF38BDF8))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(deviceName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (usbConnected) Color(0xFF22C55E) else Color(0xFFEF4444))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (usbConnected) "OTG Conectado" else "Conecta vía cable OTG",
                                color = if (usbConnected) Color(0xFF4ADE80) else Color(0xFFF87171),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatusBadge("OTG HOST", usbConnected)
                        StatusBadge("READY", !busy)
                    }
                }
            }
        }

        item {
            DashboardCard(borderColor = Color(0xFF2D3748)) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandRomCard = !expandRomCard },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF132A2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF2DD4BF))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Flashing ROM's & Img (OTG)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Custom ROMs Sideload, Fastboot & Particiones", color = MutedText, fontSize = 11.sp)
                        }
                        Icon(
                            imageVector = if (expandRomCard) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }

                    AnimatedVisibility(visible = expandRomCard) {
                        Column(
                            modifier = Modifier.padding(top = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("1. Modo de Flasheo en Destino:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ModeChip("ROM Sideload", selectedMode == "sideload", Modifier.weight(1f)) {
                                    selectedMode = "sideload"
                                }
                                ModeChip("Fastboot ROM", selectedMode == "fastboot_update", Modifier.weight(1f)) {
                                    selectedMode = "fastboot_update"
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ModeChip("Boot.img", selectedMode == "boot", Modifier.weight(1f)) { selectedMode = "boot" }
                                ModeChip("Recovery.img", selectedMode == "recovery", Modifier.weight(1f)) { selectedMode = "recovery" }
                                ModeChip("Vendor_boot", selectedMode == "vendor_boot", Modifier.weight(1f)) { selectedMode = "vendor_boot" }
                            }

                            OutlinedButton(
                                onClick = { filePicker.launch(arrayOf("*/*")) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    if (fileName.isNotEmpty()) fileName else "2. Seleccionar Archivo (.zip o .img)",
                                    color = Color.White
                                )
                            }

                            Button(
                                onClick = {
                                    val uri = selectedUri ?: return@Button
                                    busy = true
                                    SharedConsole.add("=== INICIANDO FLASHEO OTG ===")
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                Flasher.flashOtgFile(context, uri, selectedMode) { line ->
                                                    launch(Dispatchers.Main) { SharedConsole.add(line) }
                                                }
                                            }
                                        } catch (error: Exception) {
                                            SharedConsole.add("[ERROR OTG] ${error.message}")
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                enabled = selectedUri != null && !busy,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    if (busy) "Flasheando al otro celular..." else "⚡ Flashear al Dispositivo OTG",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            DashboardCard(borderColor = Color(0xFF3F2329)) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandRebootCard = !expandRebootCard },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2D161C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFF87171))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reboot Options (OTG)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("System | Bootloader | Recovery | FastbootD", color = MutedText, fontSize = 11.sp)
                        }
                        Icon(
                            imageVector = if (expandRebootCard) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }

                    AnimatedVisibility(visible = expandRebootCard) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RebootBtn("System", Modifier.weight(1f)) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            Flasher.runOtgCommand(context, "fastboot", listOf("reboot")) {
                                                launch(Dispatchers.Main) { SharedConsole.add(it) }
                                            }
                                        }
                                    }
                                }
                                RebootBtn("Bootloader", Modifier.weight(1f)) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            Flasher.runOtgCommand(context, "fastboot", listOf("reboot-bootloader")) {
                                                launch(Dispatchers.Main) { SharedConsole.add(it) }
                                            }
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RebootBtn("Recovery", Modifier.weight(1f)) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            Flasher.runOtgCommand(context, "fastboot", listOf("reboot", "recovery")) {
                                                launch(Dispatchers.Main) { SharedConsole.add(it) }
                                            }
                                        }
                                    }
                                }
                                RebootBtn("FastbootD", Modifier.weight(1f)) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            Flasher.runOtgCommand(context, "fastboot", listOf("reboot", "fastboot")) {
                                                launch(Dispatchers.Main) { SharedConsole.add(it) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (SharedConsole.logs.isNotEmpty()) {
            item {
                DashboardCard(borderColor = Color(0xFF1E293B)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Terminal en Vivo", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Limpiar",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { SharedConsole.clear() }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        SharedConsole.logs.takeLast(6).forEach { line ->
                            Text(
                                line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF4ADE80)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalKernelScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<Boolean?>(null) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var backup by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { selected ->
        if (selected != null) {
            uri = selected
            name = Flasher.displayName(context, selected)
        }
    }

    LaunchedEffect(Unit) {
        root = withContext(Dispatchers.IO) { Flasher.hasRoot() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Local Kernel Flasher (Este Teléfono)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        DashboardCard(
            borderColor = if (root == true) Color(0xFF22C55E) else Color(0xFFEF4444)
        ) {
            Text(
                when (root) {
                    true -> "✅ Acceso Root Activo en este teléfono"
                    false -> "❌ Se requiere Root para flasheo local"
                    null -> "Comprobando acceso Root..."
                },
                color = Color.White,
                fontSize = 13.sp
            )
        }
        DashboardCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/zip")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (name.isNotEmpty()) name else "Elegir AnyKernel3 ZIP", color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = backup, onCheckedChange = { backup = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Backup automático de boot.img", color = Color.White, fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        val selectedUri = uri ?: return@Button
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    Flasher.flash(context, selectedUri, backup) { message ->
                                        launch(Dispatchers.Main) { SharedConsole.add(message) }
                                    }
                                }
                            } catch (error: Exception) {
                                SharedConsole.add("[ERROR] ${error.message}")
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = root == true && uri != null && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "Flasheando..." else "Flashear Kernel Local")
                }
            }
        }
    }
}

@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Diagnóstico OTG (ADB / Fastboot)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Button(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        Flasher.runOtgCommand(context, "fastboot", listOf("devices")) {
                            launch(Dispatchers.Main) { SharedConsole.add(it) }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Verificar Fastboot Devices")
        }
        Button(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        Flasher.runOtgCommand(context, "adb", listOf("devices")) {
                            launch(Dispatchers.Main) { SharedConsole.add(it) }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Verificar ADB Devices")
        }
        Button(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        Flasher.runOtgCommand(context, "fastboot", listOf("getvar", "all")) {
                            launch(Dispatchers.Main) { SharedConsole.add(it) }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Leer Variables del Dispositivo (getvar all)")
        }
    }
}

@Composable
fun ConsoleLogsScreen() {
    val logs = SharedConsole.logs
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Historial Completo", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Limpiar",
                color = Color(0xFF60A5FA),
                modifier = Modifier.clickable { SharedConsole.clear() }
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF05070B))
                .border(1.dp, Color(0xFF263244), RoundedCornerShape(12.dp))
                .verticalScroll(scrollState)
                .padding(12.dp)
        ) {
            if (logs.isEmpty()) {
                Text("Todavía no hay registros.", color = MutedText, fontSize = 13.sp)
            } else {
                logs.forEach { line ->
                    Text(
                        line,
                        color = Color(0xFF4ADE80),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color(0xFF293244),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
fun StatusBadge(label: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (active) Color(0xFF123324) else Color(0xFF332027)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = if (active) Color(0xFF4ADE80) else Color(0xFFF87171),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) Color(0xFF17345C) else Color(0xFF202633),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color(0xFF3880FF) else Color(0xFF343D4F)
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 10.dp),
            color = if (selected) Color.White else MutedText,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun RebootBtn(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
fun CustomBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        Triple("OTG / ROM", Icons.Default.Home, 0),
        Triple("Kernel", Icons.Default.Settings, 1),
        Triple("Tools", Icons.Default.Build, 2),
        Triple("Console", Icons.Default.Code, 3)
    )

    NavigationBar(
        containerColor = Color(0xFF11141C),
        contentColor = Color.White
    ) {
        tabs.forEach { (label, icon, index) ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF60A5FA),
                    selectedTextColor = Color(0xFF60A5FA),
                    indicatorColor = Color(0xFF1C3558),
                    unselectedIconColor = Color(0xFF8994A7),
                    unselectedTextColor = Color(0xFF8994A7)
                )
            )
        }
    }
}

/**
 * A LazyColumn wrapper that keeps the LazyListScope receiver explicit,
 * so conditional item blocks remain inside the list DSL.
 */
@Composable
private fun LazyContentColumn(
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

