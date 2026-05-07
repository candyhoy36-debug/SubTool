package com.joy.subtool.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_subtitle")
public class LocalSubtitleEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String mediaUri = "";

    public int lineIndex;

    @NonNull
    public String text = "";

    public long startMs = -1;

    public long endMs = -1;
}
