package com.example.appbanmypham.data.dao

import androidx.room.*
import com.example.appbanmypham.model.CartItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items WHERE productId = :id")
    suspend fun getById(id: String): CartItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CartItemEntity)

    @Query("UPDATE cart_items SET quantity = :qty WHERE productId = :id")
    suspend fun updateQuantity(id: String, qty: Int)

    @Query("DELETE FROM cart_items WHERE productId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cart_items")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM cart_items")
    fun countFlow(): Flow<Int>

    @Query("SELECT SUM(price * quantity) FROM cart_items")
    fun totalPriceFlow(): Flow<Double?>
}