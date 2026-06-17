package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grid.tv.data.db.entity.ProviderHealthAggregateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderHealthAggregateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderHealthAggregateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ProviderHealthAggregateEntity>)

    @Query("SELECT * FROM provider_health_aggregate WHERE providerId = :providerId")
    suspend fun get(providerId: Long): ProviderHealthAggregateEntity?

    @Query("SELECT * FROM provider_health_aggregate WHERE providerId = :providerId")
    fun observe(providerId: Long): Flow<ProviderHealthAggregateEntity?>

    @Query("DELETE FROM provider_health_aggregate")
    suspend fun deleteAll()
}
