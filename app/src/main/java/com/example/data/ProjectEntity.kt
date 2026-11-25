package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cad_projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val sceneJson: String,
    val faceCount: Int,
    val gridSpacingMeters: Float = 1.0f
)
