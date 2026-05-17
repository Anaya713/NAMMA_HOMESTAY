package com.example.nammahomestay

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun AuthScreen(onLoginSuccess: (Boolean) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isHost by remember { mutableStateOf(false) }
    var isLoginMode by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    Column(modifier = Modifier.padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(if (isLoginMode) "Login to Namma-HomeStay" else "Register for Namma-HomeStay", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

        if (!isLoginMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Register as Host?")
                Switch(checked = isHost, onCheckedChange = { isHost = it })
            }
        }

        Button(onClick = {
            if (isLoginMode) {
                auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        db.collection("users").document(auth.currentUser!!.uid).get().addOnSuccessListener { doc ->
                            val role = doc.getString("role") ?: "guest"
                            onLoginSuccess(role == "host")
                        }
                    } else {
                        Toast.makeText(context, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                AuthService.registerUser(email, password, if (isHost) "host" else "guest") { success, errorMessage ->
                    if (success) {
                        onLoginSuccess(isHost)
                        Toast.makeText(context, "Registered!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Registration Failed: ${errorMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (isLoginMode) "Login" else "Sign Up") }

        TextButton(onClick = { isLoginMode = !isLoginMode }) {
            Text(if (isLoginMode) "Need an account? Sign Up" else "Already have an account? Login")
        }
    }
}

@Composable
fun HostBookingsScreen(onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    var bookings by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    // FIX: Use a refresh key instead of a nested fun to avoid recomposition bugs
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        db.collection("bookings")
            .whereEqualTo("hostId", auth.currentUser?.uid)
            .get()
            .addOnSuccessListener { result ->
                bookings = result.map { doc ->
                    doc.data.toMutableMap().apply { this["docId"] = doc.id }
                }
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Button(onClick = onBack) { Text("Back") }
        Text("Incoming Booking Requests", style = MaterialTheme.typography.headlineSmall)

        if (isLoading) {
            CircularProgressIndicator()
        } else if (bookings.isEmpty()) {
            Text("No pending requests yet.")
        } else {
            LazyColumn {
                items(bookings) { booking ->
                    val docId = booking["docId"] as String
                    Card(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Homestay ID: ${booking["homestayId"]}")
                            Text("Guest ID: ${booking["guestId"]}")
                            Text("Status: ${booking["status"]}", color = MaterialTheme.colorScheme.primary)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    db.collection("bookings").document(docId)
                                        .update("status", "Rejected")
                                        .addOnSuccessListener { refreshKey++ }
                                }) { Text("Reject", color = MaterialTheme.colorScheme.error) }

                                Button(onClick = {
                                    db.collection("bookings").document(docId)
                                        .update("status", "Confirmed")
                                        .addOnSuccessListener { refreshKey++ }
                                }) { Text("Approve") }
                            }
                        }
                    }
                }
            }
        }
    }
}