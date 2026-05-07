package com.joy.subtool.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LocalSubtitleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LocalSubtitleEntity> entities);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LocalSubtitleEntity entity);

    @Update
    void update(LocalSubtitleEntity entity);

    @Query("SELECT * FROM local_subtitle WHERE mediaUri = :uri ORDER BY lineIndex ASC")
    List<LocalSubtitleEntity> getByMediaUri(String uri);

    @Query("DELETE FROM local_subtitle WHERE mediaUri = :uri")
    void deleteByMediaUri(String uri);

    @Query("DELETE FROM local_subtitle WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM local_subtitle")
    void clear();
}
