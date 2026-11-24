package com.swasthsaathi.voice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swasthsaathi.voice.core.Lang
import com.swasthsaathi.voice.core.TriageResult
import com.swasthsaathi.voice.data.PdfExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Teal = Color(0xFF0F6E56)
private val levelColor = mapOf("emergency" to Color(0xFFB3261E), "see_doctor" to Color(0xFFB26A00), "self_care" to Color(0xFF2E7D32))

@Composable
fun VoiceScreen(vm: AppViewModel, onNotebook: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Swasth Saathi", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Teal, modifier = Modifier.weight(1f))
            TextButton(onClick = onNotebook) { Text("Notebook") }
        }
        LanguageRow(vm.lang) { vm.lang = it }
        LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.messages) { Bubble(it.fromUser, it.text, it.result, it.care) }
            if (vm.partial.isNotBlank()) item { Bubble(true, vm.partial + "…", null, null) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(typed, { typed = it }, Modifier.weight(1f), placeholder = { Text("Or type here") }, singleLine = true)
            Button(onClick = { if (typed.isNotBlank()) { vm.handle(typed); typed = "" } }) { Text("Send") }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(if (vm.listening) Color(0xFFB3261E) else Teal), contentAlignment = Alignment.Center) {
                IconButton(onClick = { vm.toggleMic() }, Modifier.fillMaxSize()) { Text(if (vm.listening) "■" else "🎤", fontSize = 30.sp, color = Color.White) }
            }
        }
    }
}

@Composable
private fun LanguageRow(sel: Lang, onPick: (Lang) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Lang.entries.forEach { FilterChip(selected = it == sel, onClick = { onPick(it) }, label = { Text(it.label, fontSize = 12.sp) }) }
    }
}

@Composable
private fun Bubble(fromUser: Boolean, text: String, r: TriageResult?, care: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp))
            .background(if (fromUser) Teal else Color(0xFFEFF3F1)).padding(12.dp)) {
            Text(text, color = if (fromUser) Color.White else Color.Black)
            r?.let { ResultCard(it) }
            care?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun ResultCard(r: TriageResult) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(r.level.replace('_', ' ').uppercase(), color = levelColor[r.level] ?: Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        r.selfCare.forEach {
            Text("• ${it.symptom.replace('_', ' ')}: ${it.drug}\n  ${it.dose}", fontSize = 13.sp)
            Text("Max: ${it.max}", fontSize = 12.sp); Text("Water: ${it.hydration}", fontSize = 12.sp)
            Text("Rest: ${it.rest}", fontSize = 12.sp); Text("Diet: ${it.dietTip}", fontSize = 12.sp)
        }
        if (r.tests.isNotEmpty()) Text("Suggested tests: ${r.tests.joinToString()}", fontSize = 13.sp)
        if (r.conditions.isNotEmpty()) Text("Possible: ${r.conditions.take(3).joinToString { "${it.name} (${it.icd10})" }}", fontSize = 12.sp)
        Text(r.disclaimer, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun NotebookScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    var lab by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Health Notebook", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { PdfExporter.share(ctx, vm.exportPdf()) }) { Text("Export PDF") }
        }
        Text("Progress", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        if (vm.trends.isEmpty()) Text("Nothing logged yet.", color = Color.Gray)
        vm.trends.take(3).forEach { t ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(t.message, fontSize = 14.sp)
                    Text("Last 4 weeks: ${t.weeklyCounts.joinToString(" · ")}", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        OutlinedTextField(lab, { lab = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Add a lab result (e.g. Hb 11.2, 24 Nov)") },
            trailingIcon = { TextButton(onClick = { if (lab.isNotBlank()) { vm.addLabResult(lab); lab = "" } }) { Text("Save") } })
        Text("Log", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.entries) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${fmt.format(Date(e.timestamp))} · ${e.level.replace('_', ' ')}", fontSize = 12.sp, color = levelColor[e.level] ?: Color.Gray)
                        Text("“${e.transcript}”", fontSize = 14.sp)
                        if (e.recommendation.isNotBlank()) Text(e.recommendation, fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }
}
