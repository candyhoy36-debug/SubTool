package com.joy.subtool.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_media_history")
public class LocalMediaHistoryEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String fileName = "";

    @NonNull
    public String fileUri = "";

    @NonNull
    public String mediaType = "audio";

    public long durationMs;

    public long openedAt;

    public boolean hasCompletedSrt;

    @Nullable
    public Long trimStartMs;

    @Nullable
    public Long trimEndMs;
}
