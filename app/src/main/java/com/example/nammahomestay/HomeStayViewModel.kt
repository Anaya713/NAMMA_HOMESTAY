package com.example.nammahomestay

import androidx.lifecycle.ViewModel

class HomeStayViewModel : ViewModel() {
    private val repository = HomeStayRepository()

    fun addHomeStay(homeStay: HomeStay) {
        repository.addHomeStay(homeStay)
    }
}