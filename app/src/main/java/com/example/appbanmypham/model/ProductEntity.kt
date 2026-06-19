// com/example/appbanmypham/model/ProductEntity.kt
package com.example.appbanmypham.model

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id          : String,
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
) {

    fun toProduct() = Product(id, name, price, stock, description, brandId, brandName, imageUrl, category, isHidden, createdAt)

    companion object {
        // Chuyển từ Product sang Entity để lưu Room
        fun fromProduct(p: Product) = ProductEntity(
            p.id, p.name, p.price, p.stock, p.description,
            p.brandId, p.brandName, p.imageUrl, p.category, p.isHidden, p.createdAt
        )
    }
}
