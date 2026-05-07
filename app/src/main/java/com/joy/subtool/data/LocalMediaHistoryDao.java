package com.joy.subtool.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LocalMediaHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(LocalMediaHistoryEntity entity);

    @Update
    void update(LocalMediaHistoryEntity entity);

    @Query("SELECT * FROM local_media_history ORDER BY openedAt DESC")
    List<LocalMediaHistoryEntity> getAll();

    @Query("SELECT * FROM local_media_history WHERE fileUri = :uri LIMIT 1")
    LocalMediaHistoryEntity findByUri(String uri);

    @Query("DELETE FROM local_media_history WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM local_media_history")
    void clear();
}
