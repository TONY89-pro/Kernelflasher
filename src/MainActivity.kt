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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF090A0F),
                    surface = Color(0xFF13151D),
                    primary = Color(0xFF3880FF),
                    error = Color(0xFFFF5252)
                )
            ) {
                Surface(Modifier.fillMaxSize().systemBarsPadding()) {
                    MainDashboard()
                }
            }
        }
    }
}

object SharedConsole {
    val logs = mutableStateListOf<String>()
    fun add(msg: String) { logs.add(msg) }
    fun clear() { logs.clear() }
}

@Composable
fun MainDashboard() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            Surface(color = Color(0xFF0F111A), modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomBarItem(Icons.Default.Star, "OTG / ROMs", selectedTab == 0) { selectedTab = 0 }
                    BottomBarItem(Icons.Default.CheckCircle, "Kernel Root", selectedTab == 1) { selectedTab = 1 }
                    BottomBarItem(Icons.Default.Build, "Tools", selectedTab == 2) { selectedTab = 2 }
                    BottomBarItem(Icons.Default.List, "Logs", selectedTab == 3) { selectedTab = 3 }
                }
            }
        },
        containerColor = Color(0xFF090A0F)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> OtgAndRomScreen()
                1 -> LocalKernelScreen()
                2 -> ToolsScreen()
                3 -> ConsoleLogsScreen()
            }
        }
    }
}

@Composable
fun OtgAndRomScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var usbConnected by remember { mutableStateOf(false) }
    var deviceName by remember { mutableStateOf("No Device Connected") }
    var selectedMode by remember { mutableStateOf("sideload") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    var expandRomCard by remember { mutableStateOf(true) }
    var expandRebootCard by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = Flasher.displayName(ctx, uri)
        }
    }

    fun scanUsb() {
        val devices = Flasher.getConnectedUsbDevices(ctx)
        if (devices.isNotEmpty()) {
            val dev = devices.first()
            usbConnected = true
            deviceName = dev.productName ?: "Dispositivo Android OTG"
            Flasher.requestUsbPermission(ctx, dev)
            SharedConsole.add("[USB] Detectado: $deviceName")
        } else {
            usbConnected = false
            deviceName = "No Device Connected"
            SharedConsole.add("[USB] Ningún dispositivo OTG detectado.")
        }
    }

    LaunchedEffect(Unit) { scanUsb() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ROM ", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFACC15))
                        Text("👑 FLASHER", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1F1624),
                        modifier = Modifier.padding(top = 4.dp).border(1.dp, Color(0xFF582C4D), RoundedCornerShape(12.dp))
                    ) {
                        Text(" ⚡ All-In-One Android OTG Tool ", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = Color(0xFFF472B6), fontSize = 11.sp)
                    }
                }
                IconButton(
                    onClick = { scanUsb() },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E222D)).border(1.dp, Color(0xFF333A4D), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Escanear OTG", tint = Color(0xFF60A5FA))
                }
            }
        }

        item {
            DashboardCard(borderColor = if (usbConnected) Color(0xFF22C55E) else Color(0xFF2E3446)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF162436)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF38BDF8))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(deviceName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (usbConnected) Color(0xFF22C55E) else Color(0xFFEF4444)))
                            Spacer(Modifier.width(6.dp))
                            Text(if (usbConnected) "OTG Conectado" else "Conecta vía cable OTG", color = if (usbConnected) Color(0xFF4ADE80) else Color(0xFFF87171), fontSize = 11.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusBadge("OTG", usbConnected)
                        StatusBadge("READY", !busy)
                    }
                }
            }
        }

        item {
            DashboardCard(borderColor = Color(0xFF2D3748)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expandRomCard = !expandRomCard },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF132A2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF2DD4BF))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Flashing ROM's & Img (OTG)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Custom ROMs Sideload, Fastboot & Particiones", color = Color(0xFF8A99AD), fontSize = 11.sp)
                        }
                        Icon(
                            imageVector = if (expandRomCard) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, tint = Color.Gray
                        )
                    }

                    AnimatedVisibility(visible = expandRomCard) {
                        Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("1. Modo de Flasheo en Destino:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                ModeChip("ROM Sideload", selectedMode == "sideload", Modifier.weight(1f)) { selectedMode = "sideload" }
                                ModeChip("Fastboot ROM", selectedMode == "fastboot_update", Modifier.weight(1f)) { selectedMode = "fastboot_update" }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                ModeChip("Boot.img", selectedMode == "boot", Modifier.weight(1f)) { selectedMode = "boot" }
                                ModeChip("Recovery.img", selectedMode == "recovery", Modifier.weight(1f)) { selectedMode = "recovery" }
                                ModeChip("Vendor_boot", selectedMode == "vendor_boot", Modifier.weight(1f)) { selectedMode = "vendor_boot" }
                            }

                            OutlinedButton(
                                onClick = { filePicker.launch(arrayOf("*/*")) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (fileName.isNotEmpty()) fileName else "2. Seleccionar Archivo (.zip o .img)", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    val u = selectedUri ?: return@Button
                                    busy = true
                                    SharedConsole.add("=== INICIANDO FLASHEO OTG ===")
                                    scope.launch(Dispatchers.IO) {
                                        Flasher.flashOtgFile(ctx, u, selectedMode) { line ->
                                            scope.launch(Dispatchers.Main) { SharedConsole.add(line) }
                                        }
                                        withContext(Dispatchers.Main) { busy = false }
                                    }
                                },
                                enabled = selectedUri != null && !busy,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (busy) "Flasheando al otro celular..." else "⚡ Flashear al Dispositivo OTG", fontWeight = FontWeight.Bold)
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
                        modifier = Modifier.fillMaxWidth().clickable { expandRebootCard = !expandRebootCard },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2D161C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFF87171))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reboot Options (OTG)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("System | Bootloader | Recovery | FastbootD", color = Color(0xFF8A99AD), fontSize = 11.sp)
                        }
                        Icon(
                            imageVector = if (expandRebootCard) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, tint = Color.Gray
                        )
                    }

                    AnimatedVisibility(visible = expandRebootCard) {
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RebootBtn("System", Modifier.weight(1f)) {
                                    scope.launch(Dispatchers.IO) { Flasher.runOtgCommand(ctx, "fastboot", listOf("reboot")) { SharedConsole.add(it) } }
                                }
                                RebootBtn("Bootloader", Modifier.weight(1f)) {
                                    scope.launch(Dispatchers.IO) { Flasher.runOtgCommand(ctx, "fastboot", listOf("reboot-bootloader")) { SharedConsole.add(it) } }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RebootBtn("Recovery", Modifier.weight(1f)) {
                                    scope.launch(Dispatchers.IO) { Flasher.runOtgCommand(ctx, "fastboot", listOf("reboot", "recovery")) { SharedConsole.add(it) } }
                                }
                                RebootBtn("FastbootD", Modifier.weight(1f)) {
                                    scope.launch(Dispatchers.IO) { Flasher.runOtgCommand(ctx, "fastboot", listOf("reboot", "fastboot")) { SharedConsole.add(it) } }
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Terminal en Vivo", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Limpiar", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.clickable { SharedConsole.clear() })
                        }
                        Spacer(Modifier.height(6.dp))
                        SharedConsole.logs.takeLast(6).forEach { line ->
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4ADE80))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalKernelScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<Boolean?>(null) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var backup by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; name = Flasher.displayName(ctx, u) }
    }
    LaunchedEffect(Unit) { root = withContext(Dispatchers.IO) { Flasher.hasRoot() } }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Local Kernel Flasher (Este Teléfono)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        DashboardCard(borderColor = if (root == true) Color(0xFF22C55E) else Color(0xFFEF4444)) {
            Text(if (root == true) "✅ Acceso Root Activo en este teléfono" else "❌ Se requiere Root para flasheo local", color = Color.White, fontSize = 13.sp)
        }
        DashboardCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("application/zip")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (name.isNotEmpty()) name else "Elegir AnyKernel3 ZIP", color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = backup, onCheckedChange = { backup = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Backup automático de boot.img", color = Color.White, fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        busy = true
                        scope.launch(Dispatchers.IO) {
                            try { Flasher.flash(ctx, uri!!, backup) { SharedConsole.add(it) } }
                            catch (e: Exception) { SharedConsole.add("[ERROR] ${e.message}") }
                            withContext(Dispatchers.Main) { busy = false }
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
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Diagnóstico OTG (ADB / Fastboot)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Button(
            onClick = { scope.launch(Dispatchers.IO) { Flasher.runOtgCommand(ctx, "fastboot", listOf("devices")) { SharedConsole.add(it) } } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Verificar Fastboot Devices") }

        Button(
            onClick = { scope.launch(Dispatchers.IO) { Flasher.runOtgCommand(ctx, "adb", listOf("devices")) { SharedConsole.add(it) } } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Verificar ADB Devices") }

        Button(
            onClick = { scope.launch(Dispatchers.IO) { Flasher.runOtgCommand(ctx, "fastboot", listOf("getvar", "all")) { SharedConsole.add(it) } } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Leer Variables del Dispositivo") }
    }
}

@Composable
fun ConsoleLogsScreen() {
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Historial Completo de Consola", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            TextButton(onClick = { SharedConsole.clear() }) { Text("Limpiar", color = Color(0xFFF87171)) }
        }
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF050608), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp)).padding(10.dp)
        ) {
            LazyColumn(state = listState) {
                items(SharedConsole.logs.size) { i ->
                    Text(SharedConsole.logs[i], fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4ADE80))
                }
            }
        }
    }
}

@Composable
fun DashboardCard(borderColor: Color = Color(0xFF262E42), content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF131620)).border(1.dp, borderColor, RoundedCornerShape(14.dp)).padding(14.dp)
    ) { Column(content = content) }
}

@Composable
fun StatusBadge(label: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF1A1E29),
        modifier = Modifier.border(1.dp, if (active) Color(0xFF22C55E) else Color(0xFF334155), RoundedCornerShape(6.dp))
    ) {
        Text(
            text = " • $label ",
            color = if (active) Color(0xFF4ADE80) else Color(0xFF94A3B8),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ModeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF1D4ED8) else Color(0xFF1E293B),
        modifier = modifier.clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
            Text(text, color = Color.White, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun RebootBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(8.dp)) {
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun BottomBarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Color(0xFF38BDF8) else Color(0xFF64748B)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
