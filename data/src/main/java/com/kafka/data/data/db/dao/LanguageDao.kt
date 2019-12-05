package com.kafka.data.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kafka.data.model.LanguageModel
import kotlinx.coroutines.flow.Flow

/**
 * @author Vipul Kumar; dated 29/11/18.
 */
@Dao
abstract class LanguageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertLanguages(items: List<LanguageModel>)

    @Query("select * from LanguageModel")
    abstract fun languageFlow(): Flow<List<LanguageModel>>

    @Query("select * from LanguageModel")
    abstract fun languages(): List<LanguageModel>
}
