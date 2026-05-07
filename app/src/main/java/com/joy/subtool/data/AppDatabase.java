package com.joy.subtool.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                LocalMediaHistoryEntity.class,
                LocalSubtitleEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract LocalMediaHistoryDao localMediaHistoryDao();

    public abstract LocalSubtitleDao localSubtitleDao();

    public static AppDatabase create(Context context) {
        return Room.databaseBuilder(
                context.getApplicationContext(),
                AppDatabase.class,
                "subtool.db"
        ).build();
    }
}
