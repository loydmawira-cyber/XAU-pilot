package com.example.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.model.User
import com.example.core.network.ApiClient
import kotlinx.coroutines.launch

/** Staff-only incoming inbox. Unlike the customer SupportScreen, staff cannot create tickets. */
@Composable
fun AdminSupportScreen(user: User?) {
    val role = user?.role?.name.orEmpty()
    val isSuperAdmin = role == "SUPER_ADMINISTRATOR"
    val canReceiveTickets = isSuperAdmin || role == "ADMINISTRATOR"
    var tickets by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var reply by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch { loading = true; try { val r = ApiClient.apiService.getSupportConversations(); if (r.isSuccessful) tickets = r.body().orEmpty() else notice = "Could not load incoming support (" + r.code() + ")." } catch (e: Exception) { notice = e.message ?: "Connection error." }; loading = false } }
    fun openTicket(id: String) { scope.launch { try { val r = ApiClient.apiService.getSupportConversation(id); if (r.isSuccessful) { messages = (r.body()?.get("messages") as? List<Map<String, Any?>>).orEmpty(); selectedId = id } else notice = "Could not open ticket (" + r.code() + ")." } catch (e: Exception) { notice = e.message ?: "Connection error." } } }
    LaunchedEffect(role) { if (canReceiveTickets) refresh() }
    if (!canReceiveTickets) { Column(Modifier.fillMaxSize().padding(20.dp)) { Text("Access restricted", style = MaterialTheme.typography.headlineSmall); Text("This inbox is available to platform administrators only.") }; return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Incoming support", style = MaterialTheme.typography.headlineSmall); Text(if (isSuperAdmin) "All customer and EA-owner support requests across the platform." else "Requests assigned to you and the unassigned support queue.", style = MaterialTheme.typography.bodyMedium); notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }; TextButton(onClick = { refresh() }, enabled = !loading) { Text("Refresh inbox") }; if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { Text("Received requests", style = MaterialTheme.typography.titleLarge) }
        if (tickets.isEmpty()) item { Text("No incoming support requests.") }
        items(tickets, key = { it["id"].toString() }) { ticket ->
            val id = ticket["id"].toString()
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(ticket["subject"]?.toString() ?: "Support request", style = MaterialTheme.typography.titleMedium)
                Text("From: " + (ticket["owner_name"] ?: "Customer") + " · " + (ticket["owner_email"] ?: ""), style = MaterialTheme.typography.bodySmall)
                Text("Status: " + (ticket["status"] ?: "OPEN") + if (ticket["assigned_admin_id"] == null) " · Unassigned" else " · Assigned", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { openTicket(id) }) { Text(if (selectedId == id) "Refresh ticket" else "Open incoming request") }
                if (selectedId == id) { Divider(); messages.forEach { entry -> Text((entry["sender_name"] ?: "User").toString() + ": " + (entry["body"] ?: ""), style = MaterialTheme.typography.bodyMedium) }
                    if (ticket["status"] != "CLOSED") { OutlinedTextField(value = reply, onValueChange = { reply = it }, label = { Text("Reply to requester") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Button(onClick = { scope.launch { val r = ApiClient.apiService.sendSupportMessage(id, mapOf("body" to reply.trim())); if (r.isSuccessful) { reply = ""; notice = "Reply sent to requester."; openTicket(id); refresh() } else notice = "Reply failed (" + r.code() + ")." } }, enabled = reply.isNotBlank()) { Text("Send reply") }
                    }
                    OutlinedButton(onClick = { scope.launch { val next = if (ticket["status"] == "CLOSED") "OPEN" else "CLOSED"; val r = ApiClient.apiService.setSupportStatus(id, mapOf("status" to next)); notice = if (r.isSuccessful) "Ticket status updated." else "Status update failed (" + r.code() + ")."; refresh() } }) { Text(if (ticket["status"] == "CLOSED") "Reopen ticket" else "Close ticket") }
                }
            } }
        }
    }
}
