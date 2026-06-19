package com.example.appbanmypham.model

data class ReturnRequest(
    val id        : String = "",
    val orderId   : String = "",
    val userId    : String = "",
    val reason    : String = "",
    val note      : String = "",
    val status    : String = "pending",   // pending, approved, rejected, completed
    val adminNote : String = "",
    val createdAt : Long   = 0L,
    val updatedAt : Long   = 0L
)