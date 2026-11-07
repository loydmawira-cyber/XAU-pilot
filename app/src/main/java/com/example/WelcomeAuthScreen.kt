package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.XauPilotViewModel
import com.example.ui.theme.*

@Composable
fun WelcomeAuthScreen(viewModel: XauPilotViewModel) {
    var mode by remember { mutableStateOf("welcome") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val busy by viewModel.publisherBusy.collectAsState()
    val error by viewModel.authUiError.collectAsState()
    val session by viewModel.authSession.collectAsState()

    LaunchedEffect(session) { if (session != null) viewModel.leaveGuestMode() }

    Box(Modifier.fillMaxSize().background(CharcoalBackground), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.ShowChart, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(12.dp))
            Text("XAU PILOT", color = GoldLight, fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Automated trading platform", color = TextSecondaryDark, fontSize = 13.sp)
            Spacer(Modifier.height(36.dp))

            when (mode) {
                "welcome" -> {
                    Text("Welcome", color = TextPrimaryDark, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Sign in or create an account to manage your trading workspace.", color = TextSecondaryDark, fontSize = 14.sp)
                    Spacer(Modifier.height(26.dp))
                    Button(onClick = { mode = "register" }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = CharcoalBackground), shape = RoundedCornerShape(12.dp)) { Text("Create Account", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { mode = "login" }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) { Text("Login") }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.enterGuestMode() }) { Text("Continue as Guest", color = GoldLight) }
                }
                else -> {
                    Text(if (mode == "register") "Create your account" else "Welcome back", color = TextPrimaryDark, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(20.dp))
                    if (mode == "register") {
                        OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    if (!error.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { viewModel.authenticatePublisher(email, password, fullName, mode == "register") },
                        enabled = !busy && email.isNotBlank() && password.isNotBlank() && (mode != "register" || fullName.isNotBlank()),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = CharcoalBackground),
                        shape = RoundedCornerShape(12.dp)
                    ) { if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (mode == "register") "Create Account" else "Login", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { mode = "welcome" }) { Text("Back", color = GoldLight) }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Guest access is limited to public areas. Trading, account connections and EA uploads require sign-in.", color = TextSecondaryDark, fontSize = 11.sp)
        }
    }
}
