package com.kernelflasher

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

@Composable
fun MainDashboard() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            CustomBottomBar(selectedTab) { selectedTab = it }
        },
        containerColor = Color(0xFF090A0F)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> FlashingScreenTab()
                1 -> PlaceholderScreen("Sección Root y Magisk")
                2 -> PlaceholderScreen("Herramientas Avanzadas")
                3 -> PlaceholderScreen("Gestor de Archivos y Backups")
            }
        }
    }
}

@Composable
fun FlashingScreenTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<Boolean?>(null) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var backup by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; name = Flasher.displayName(ctx, u) }
    }
    LaunchedEffect(Unit) { root = withContext(Dispatchers.IO) { Flasher.hasRoot() } }
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Encabezado tipo Título
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("KERNEL 👑 FLASHER", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("All-In-One Advanced Tool", fontSize = 12.sp, color = Color(0xFF8A99AD))
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E222D),
                    modifier = Modifier.border(1.dp, Color(0xFF333A4D), RoundedCornerShape(20.dp))
                ) {
                    Text(" v2.0 ", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color(0xFF7C9CFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Estado del dispositivo / Root Card
        item {
            DashboardCard(
                borderColor = if (root == true) Color(0xFF238636) else Color(0xFF333A4D)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = if (root == true) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (root == true) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when (root) { null -> "Verificando permisos..."; true -> "Root Conectado y Verificado"; false -> "Root NO Detectado" },
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                        )
                        Text(
                            if (root == true) "Acceso total al sistema listo" else "Se requieren permisos root",
                            color = Color(0xFF8A99AD), fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Tarjeta de Selección y Flasheo de Kernel
        item {
            DashboardCard(borderColor = Color(0xFF3B82F6)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFF60A5FA))
                        Spacer(Modifier.width(8.dp))
                        Text("AnyKernel3 Flasher", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text("Selecciona un archivo ZIP de kernel compatible para flashearlo de forma segura.", color = Color(0xFF8A99AD), fontSize = 12.sp)

                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (name.isNotEmpty()) name else "Elegir ZIP de Kernel")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Switch(checked = backup, onCheckedChange = { backup = it }, enabled = !busy)
                        Spacer(Modifier.width(8.dp))
                        Text("Crear backup de boot.img antes", color = Color.White, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { confirm = true },
                        enabled = root == true && uri != null && !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (busy) "Procesando Destino..." else "¡Flashear Kernel Ahora!", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Consola de Logs Estilizada
        if (logs.isNotEmpty() || busy) {
            item {
                Column {
                    Text("Consola de Ejecución", color = Color(0xFF8A99AD), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFF050608), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF1F293D), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(logs.size) { index ->
                                Text(logs[index], fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4ADE80))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Color(0xFF161A23),
            title = { Text("¿Flashear Kernel?", color = Color.White) },
            text = { Text("Un kernel incorrecto puede causar un bootloop. Asegúrate de tener tu boot.img original respaldado.", color = Color(0xFF9CA3AF)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false; busy = true; logs.clear()
                    val u = uri!!
                    scope.launch(Dispatchers.IO) {
                        try {
                            Flasher.flash(ctx, u, backup) { line -> scope.launch(Dispatchers.Main) { logs.add(line) } }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { logs.add("[ERROR] ${e.message}") }
                        }
                        withContext(Dispatchers.Main) { busy = false }
                    }
                }) { Text("Confirmar", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancelar", color = Color(0xFF9CA3AF)) }
            }
        )
    }
}

@Composable
fun DashboardCard(borderColor: Color = Color(0xFF262E42), content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF131620))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color(0xFF94A3B8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Próximamente en la siguiente actualización", color = Color(0xFF475569), fontSize = 12.sp)
        }
    }
}

@Composable
fun CustomBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Surface(
        color = Color(0xFF0F111A),
        modifier = Modifier.fillMaxWidth().height(65.dp),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarItem(icon = Icons.Default.FlashOn, label = "Flashing", selected = selectedTab == 0) { onTabSelected(0) }
            BottomBarItem(icon = Icons.Default.Security, label = "Root", selected = selectedTab == 1) { onTabSelected(1) }
            BottomBarItem(icon = Icons.Default.Build, label = "Tools", selected = selectedTab == 2) { onTabSelected(2) }
            BottomBarItem(icon = Icons.Default.Folder, label = "Files", selected = selectedTab == 3) { onTabSelected(3) }
        }
    }
}

@Composable
fun BottomBarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Color(0xFF3880FF) else Color(0xFF64748B)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
