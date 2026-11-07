package com.example.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.network.ApiClient
import com.example.core.model.User
import kotlinx.coroutines.launch

@Composable
fun AdminConsoleScreen(user: User?) {
    val role = user?.role?.name.orEmpty()
    val isSuperAdmin = role == "SUPER_ADMINISTRATOR"
    val canAdmin = isSuperAdmin || role == "ADMINISTRATOR"
    var pending by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var admins by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var users by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var conversations by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var selectedConversation by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var reply by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadAll() { scope.launch {
        loading = true
        try {
            val q = ApiClient.apiService.getPendingEaReviews()
            if (q.isSuccessful) pending = q.body().orEmpty() else message = "EA queue unavailable (${q.code()})."
            val c = ApiClient.apiService.getSupportConversations()
            if (c.isSuccessful) conversations = c.body().orEmpty() else message = "Support inbox unavailable (${c.code()})."
            if (isSuperAdmin) {
                val a = ApiClient.apiService.getAdministrators(); if (a.isSuccessful) admins = a.body().orEmpty()
                val u = ApiClient.apiService.getPlatformUsers(); if (u.isSuccessful) users = u.body().orEmpty()
            }
        } catch (e: Exception) { message = e.message ?: "Network error." }
        loading = false
    } }
    fun loadConversation(id: String) { scope.launch {
        try { val r = ApiClient.apiService.getSupportConversation(id)
            if (r.isSuccessful) messages = (r.body()?.get("messages") as? List<Map<String, Any?>>).orEmpty()
            else message = "Could not open conversation (${r.code()})."
        } catch (e: Exception) { message = e.message }
    } }
    LaunchedEffect(role) { if (canAdmin) loadAll() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Platform administration", style = MaterialTheme.typography.headlineSmall)
            Text("Signed in as ${user?.fullName ?: "Unknown"} · $role", style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { loadAll() }, enabled = !loading) { Text("Refresh all") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (!canAdmin) item { Text("This area is restricted to platform administrators.") }
        else {
            item { Text("EA verification queue", style = MaterialTheme.typography.titleLarge) }
            if (pending.isEmpty()) item { Text("No EAs are waiting for review.") }
            items(pending, key = { it["id"].toString() }) { product ->
                val id = product["id"]?.toString().orEmpty()
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(product["name"]?.toString() ?: "EA submission", style = MaterialTheme.typography.titleMedium)
                    Text("Version: ${product["version"] ?: "Not supplied"} · ID: $id", style = MaterialTheme.typography.bodySmall)
                    Text("Manifest: ${product["manifest_hash"] ?: "Not available"}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { val r = ApiClient.apiService.approveEaReview(id); message = if (r.isSuccessful) "EA approved." else "Approval failed (${r.code()})."; loadAll() } }, enabled = id.isNotBlank()) { Text("Approve") }
                        OutlinedButton(onClick = { scope.launch { val r = ApiClient.apiService.rejectEaReview(id, mapOf("note" to "Does not meet marketplace review requirements")); message = if (r.isSuccessful) "EA rejected." else "Rejection failed (${r.code()})."; loadAll() } }, enabled = id.isNotBlank()) { Text("Reject") }
                    }
                } }
            }
            if (isSuperAdmin) {
                item { Divider(); Text("Create administrator", style = MaterialTheme.typography.titleLarge); Text("You can create multiple restricted administrators. They cannot access the Super Admin user directory or manage other admins.", style = MaterialTheme.typography.bodySmall) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(password, { password = it }, label = { Text("Temporary password (14+ characters)") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { scope.launch { val r = ApiClient.apiService.createRestrictedAdmin(mapOf("full_name" to fullName.trim(), "email" to email.trim(), "password" to password)); message = if (r.isSuccessful) { fullName = ""; email = ""; password = ""; loadAll(); "Administrator created." } else "Creation failed (${r.code()}): ${r.errorBody()?.string().orEmpty()}" } }, enabled = fullName.trim().length >= 2 && email.contains("@") && password.length >= 14) { Text("Create administrator") }
                    }
                }
                item { Text("Administrator accounts", style = MaterialTheme.typography.titleLarge) }
                items(admins, key = { it["id"].toString() }) { a ->
                    val id = a["id"].toString(); val active = a["is_active"] as? Boolean ?: false
                    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { Text(a["full_name"].toString()); Text(a["email"].toString(), style = MaterialTheme.typography.bodySmall); Text(if (active) "Active" else "Deactivated", style = MaterialTheme.typography.bodySmall) }
                        OutlinedButton(onClick = { scope.launch { val r = ApiClient.apiService.setAdministratorStatus(id, mapOf("is_active" to !active)); message = if (r.isSuccessful) "Administrator status updated." else "Update failed (${r.code()})."; loadAll() } }) { Text(if (active) "Deactivate" else "Activate") }
                    } }
                }
                item { Divider(); Text("Platform user directory · Super Admin only", style = MaterialTheme.typography.titleLarge); Text("Sensitive authentication secrets are never returned.", style = MaterialTheme.typography.bodySmall) }
                items(users, key = { "u_" + it["id"].toString() }) { u ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(u["full_name"].toString()); Text(u["email"].toString(), style = MaterialTheme.typography.bodySmall); Text("${u["role"]} · ${if (u["is_active"] == true) "Active" else "Inactive"}", style = MaterialTheme.typography.bodySmall) } }
                }
            }
            item { Divider(); Text("EA owner support inbox", style = MaterialTheme.typography.titleLarge); Text("Messages are stored on the server and visible only to the conversation owner and platform support staff.", style = MaterialTheme.typography.bodySmall) }
            if (conversations.isEmpty()) item { Text("No support conversations yet.") }
            items(conversations, key = { "c_" + it["id"].toString() }) { c ->
                val id = c["id"].toString()
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c["subject"].toString(), style = MaterialTheme.typography.titleMedium)
                    Text("${c["owner_name"]} · ${c["owner_email"]} · ${c["status"]}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { selectedConversation = id; loadConversation(id) }) { Text(if (selectedConversation == id) "Refresh thread" else "Open conversation") }
                    if (selectedConversation == id) {
                        messages.forEach { m -> Text("${m["sender_name"]}: ${m["body"]}", style = MaterialTheme.typography.bodyMedium) }
                        OutlinedTextField(reply, { reply = it }, label = { Text("Reply to EA owner") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { val r = ApiClient.apiService.sendSupportMessage(id, mapOf("body" to reply)); message = if (r.isSuccessful) { reply = ""; loadConversation(id); "Reply sent." } else "Reply failed (${r.code()})." } }, enabled = reply.isNotBlank()) { Text("Send reply") }
                            OutlinedButton(onClick = { scope.launch { val r = ApiClient.apiService.setSupportStatus(id, mapOf("status" to if (c["status"] == "CLOSED") "OPEN" else "CLOSED")); message = if (r.isSuccessful) "Conversation status updated." else "Status update failed."; loadAll() } }) { Text(if (c["status"] == "CLOSED") "Reopen" else "Close") }
                        }
                    }
                } }
            }
        }
    }
}
