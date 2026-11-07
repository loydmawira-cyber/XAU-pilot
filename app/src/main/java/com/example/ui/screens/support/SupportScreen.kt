package com.example.ui.screens.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.network.ApiClient
import kotlinx.coroutines.launch

@Composable
fun SupportScreen() {
    var conversations by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var subject by remember { mutableStateOf("") }
    var initialMessage by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch { busy = true; try { val r = ApiClient.apiService.getSupportConversations(); if (r.isSuccessful) conversations = r.body().orEmpty() else notice = "Please sign in to use support (${r.code()})." } catch (e: Exception) { notice = e.message ?: "Connection error." }; busy = false } }
    fun open(id: String) { scope.launch { try { val r = ApiClient.apiService.getSupportConversation(id); if (r.isSuccessful) messages = (r.body()?.get("messages") as? List<Map<String, Any?>>).orEmpty() else notice = "Unable to load conversation." } catch (e: Exception) { notice = e.message } } }
    LaunchedEffect(Unit) { refresh() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("XAU Pilot support", style = MaterialTheme.typography.headlineSmall); Text("Contact the platform team about your EA, account or marketplace listing.", style = MaterialTheme.typography.bodyMedium); notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }; TextButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") } }
        item { Text("Open a support request", style = MaterialTheme.typography.titleLarge) }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(initialMessage, { initialMessage = it }, label = { Text("Describe the issue") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Button(onClick = { scope.launch { val r = ApiClient.apiService.createSupportConversation(mapOf("subject" to subject.trim(), "message" to initialMessage.trim())); notice = if (r.isSuccessful) { subject = ""; initialMessage = ""; refresh(); "Support request created." } else "Could not create request (${r.code()})." } }, enabled = subject.trim().length >= 3 && initialMessage.isNotBlank()) { Text("Send request") }
        } }
        item { Divider(); Text("Your conversations", style = MaterialTheme.typography.titleLarge) }
        if (conversations.isEmpty()) item { Text("No support requests yet.") }
        items(conversations, key = { it["id"].toString() }) { c ->
            val id = c["id"].toString()
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(c["subject"].toString(), style = MaterialTheme.typography.titleMedium); Text("Status: ${c["status"]}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { selectedId = id; open(id) }) { Text(if (selectedId == id) "Refresh messages" else "View messages") }
                if (selectedId == id) {
                    messages.forEach { m -> Text("${m["sender_name"] ?: "Support"}: ${m["body"]}") }
                    if (c["status"] != "CLOSED") {
                        OutlinedTextField(reply, { reply = it }, label = { Text("Reply") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { scope.launch { val r = ApiClient.apiService.sendSupportMessage(id, mapOf("body" to reply)); notice = if (r.isSuccessful) { reply = ""; open(id); "Message sent." } else "Message failed (${r.code()})." } }, enabled = reply.isNotBlank()) { Text("Send reply") }
                    }
                }
            } }
        }
    }
}
