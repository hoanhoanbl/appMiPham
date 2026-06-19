package com.example.appbanmypham.data.dao

import androidx.room.*
import com.example.appbanmypham.model.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<Product>)

    @Update
    suspend fun update(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR brandName LIKE '%' || :query || '%'")
    fun searchFlow(query: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE category = :category")
    fun getByCategoryFlow(category: String): Flow<List<Product>>
}