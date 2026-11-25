package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.cad.model.CadScene
import com.example.cad.model.CameraState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.workspacePrefsDataStore by preferencesDataStore(name = "arqui_workspace_prefs")

data class WorkspacePreferences(
    val darkTheme: Boolean = false,
    val gridSpacingMeters: Float = 1.0f,
    val showGrid: Boolean = true,
    val snapToGrid: Boolean = true
)

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val appContext: Context
) {
    private object PrefKeys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val GRID_SPACING = floatPreferencesKey("grid_spacing_meters")
        val SHOW_GRID = booleanPreferencesKey("show_grid")
        val SNAP_TO_GRID = booleanPreferencesKey("snap_to_grid")
    }

    val projects: Flow<List<ProjectEntity>> = projectDao.observeAllProjects()

    val preferences: Flow<WorkspacePreferences> = appContext.workspacePrefsDataStore.data.map { prefs ->
        WorkspacePreferences(
            darkTheme = prefs[PrefKeys.DARK_THEME] ?: false,
            gridSpacingMeters = prefs[PrefKeys.GRID_SPACING] ?: 1.0f,
            showGrid = prefs[PrefKeys.SHOW_GRID] ?: true,
            snapToGrid = prefs[PrefKeys.SNAP_TO_GRID] ?: true
        )
    }

    suspend fun saveProject(
        existingId: Long?,
        name: String,
        scene: CadScene,
        camera: CameraState,
        gridSpacingMeters: Float
    ): Long {
        val json = SceneSerializer.encode(scene, camera)
        val entity = ProjectEntity(
            id = existingId ?: 0L,
            name = name.trim().ifEmpty { "Proyecto sin título" },
            updatedAt = System.currentTimeMillis(),
            sceneJson = json,
            faceCount = scene.faces.size,
            gridSpacingMeters = gridSpacingMeters
        )
        return projectDao.upsertProject(entity)
    }

    suspend fun loadProject(id: Long): Pair<ProjectEntity, Pair<CadScene, CameraState>>? {
        val entity = projectDao.getProjectById(id) ?: return null
        val decoded = SceneSerializer.decode(entity.sceneJson) ?: return null
        return entity to decoded
    }

    suspend fun deleteProject(id: Long) {
        projectDao.deleteProjectById(id)
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        appContext.workspacePrefsDataStore.edit { it[PrefKeys.DARK_THEME] = enabled }
    }

    suspend fun setGridSettings(spacingMeters: Float, showGrid: Boolean, snapToGrid: Boolean) {
        appContext.workspacePrefsDataStore.edit {
            it[PrefKeys.GRID_SPACING] = spacingMeters
            it[PrefKeys.SHOW_GRID] = showGrid
            it[PrefKeys.SNAP_TO_GRID] = snapToGrid
        }
    }
}
