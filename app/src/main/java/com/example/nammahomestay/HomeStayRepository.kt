package com.example.nammahomestay

import com.google.firebase.firestore.FirebaseFirestore

class HomeStayRepository {
    private val db = FirebaseFirestore.getInstance()
    private val homeStayCollection = db.collection("HomeStays")

    // Function to add a new HomeStay
    fun addHomeStay(homeStay: HomeStay) {
        homeStayCollection.document(homeStay.id).set(homeStay)
    }

    // You can add more functions here later to fetch data
}