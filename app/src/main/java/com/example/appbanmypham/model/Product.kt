package com.example.appbanmypham.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String,
    val name        : String = "",
    val price       : Double = 0.0,
    val stock       : Int    = 0,
    val description : String = "",
    val brandId     : String = "",
    val brandName   : String = "",
    val imageUrl    : String = "",
    val category    : String = "",
    val isHidden    : Boolean = false,
    val createdAt   : Long   = System.currentTimeMillis()
)
