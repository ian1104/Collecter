package com.collecter.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_entries",
    indices = [Index(value = ["title"])]
)
data class VideoEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val page: Int
)
