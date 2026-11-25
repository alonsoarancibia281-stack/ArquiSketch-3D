package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.ui.theme.ArquiSketchTheme
import com.example.ui.workspace.ArquiSketchWorkspaceScreen
import com.example.ui.workspace.CadWorkspaceViewModel
import com.example.ui.workspace.ExportFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = ProjectRepository(db.projectDao(), applicationContext)
        val factory = CadWorkspaceViewModel.Factory(repository)

        setContent {
            val workspaceViewModel: CadWorkspaceViewModel = viewModel(factory = factory)
            val uiState by workspaceViewModel.uiState.collectAsStateWithLifecycle()
            var pendingExportFormat by remember { mutableStateOf<ExportFormat?>(null) }

            val objExportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/plain")
            ) { uri ->
                val format = pendingExportFormat ?: ExportFormat.OBJ
                pendingExportFormat = null
                if (uri != null) {
                    writeExportToUri(uri, workspaceViewModel, format)
                }
            }

            val gltfExportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("model/gltf+json")
            ) { uri ->
                val format = pendingExportFormat ?: ExportFormat.GLTF
                pendingExportFormat = null
                if (uri != null) {
                    writeExportToUri(uri, workspaceViewModel, format)
                }
            }

            ArquiSketchTheme(darkTheme = uiState.darkTheme) {
                ArquiSketchWorkspaceScreen(
                    viewModel = workspaceViewModel,
                    onRequestExport = { format ->
                        pendingExportFormat = format
                        val fileName = workspaceViewModel.suggestedExportFileName(format)
                        when (format) {
                            ExportFormat.OBJ -> objExportLauncher.launch(fileName)
                            ExportFormat.GLTF -> gltfExportLauncher.launch(fileName)
                        }
                    }
                )
            }
        }
    }

    private fun writeExportToUri(
        uri: android.net.Uri,
        viewModel: CadWorkspaceViewModel,
        format: ExportFormat
    ) {
        runCatching {
            val content = viewModel.buildExportContent(format)
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("No se pudo abrir el archivo de destino")
        }.onSuccess {
            viewModel.showMessage("Modelo exportado correctamente (.${format.extension})")
        }.onFailure { err ->
            viewModel.showMessage("Error al exportar: ${err.localizedMessage ?: "desconocido"}")
        }
    }
}
