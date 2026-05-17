package com.example.nammahomestay

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object AuthService {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // UPDATED: Now returns a success Boolean AND a String message
    fun registerUser(email: String, pass: String, role: String, onResult: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: ""
                val userMap = hashMapOf("uid" to uid, "email" to email, "role" to role)

                db.collection("users").document(uid).set(userMap)
                    .addOnSuccessListener {
                        onResult(true, null)
                    }
                    .addOnFailureListener { e ->
                        onResult(false, "Firestore Error: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                // This captures errors like "weak-password" or "email-already-in-use"
                onResult(false, e.message)
            }
    }
}