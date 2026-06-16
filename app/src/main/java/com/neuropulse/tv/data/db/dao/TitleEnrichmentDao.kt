package com.neuropulse.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuropulse.tv.data.db.entity.TitleEnrichmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TitleEnrichmentDao {

    @Query("SELECT * FROM title_enrichment WHERE providerKey = :providerKey LIMIT 1")
    suspend fun get(providerKey: String): TitleEnrichmentEntity?

    @Query("SELECT * FROM title_enrichment WHERE providerKey = :providerKey LIMIT 1")
    fun observe(providerKey: String): Flow<TitleEnrichmentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TitleEnrichmentEntity)
}

