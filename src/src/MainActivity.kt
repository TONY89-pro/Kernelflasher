package com.kernelflasher

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C9CFF))) {
                Surface(Modifier.fillMaxSize().systemBarsPadding()) { FlasherScreen() }
            }
        }
    }
}

@Composable
fun FlasherScreen() {
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

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Kernel Flasher", style = MaterialTheme.typography.headlineMedium)
        Text(
            when (root) { null -> "Comprobando root..."; true -> "Root: disponible"; false -> "Root: NO disponible" },
            color = if (root == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        OutlinedButton(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
            enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Elegir ZIP de kernel (AnyKernel3)") }
        if (name.isNotEmpty()) Text("Seleccionado: $name")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = backup, onCheckedChange = { backup = it }, enabled = !busy)
            Spacer(Modifier.width(8.dp))
            Text("Backup de boot antes de flashear")
        }
        Button(onClick = { confirm = true }, enabled = root == true && uri != null && !busy,
            modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Flasheando..." else "Flashear") }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(Color(0xFF111111), RoundedCornerShape(8.dp)).padding(8.dp)
        ) {
            items(logs) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFB8F5B8)) }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("¿Flashear este kernel?") },
        text = { Text("Un kernel incompatible puede dejar el telefono sin arrancar. Verifica que sea para tu modelo y ROM. Ten a mano el boot.img original.") },
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
            }) { Text("Flashear") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
    )
}
