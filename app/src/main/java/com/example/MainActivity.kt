package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class VirtualImage(
    val id: String,
    val name: String,
    val description: String,
    val size: String,
    val isExe: Boolean,
    val downloadProgress: Float = 0f,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val isMounted: Boolean = false,
)

class DriveDroidViewModel : ViewModel() {
    var isUsbConnected by mutableStateOf(false)
        private set
    var isSimulatingDrive by mutableStateOf(false)
        private set
    var rootAccessWarningDismissed by mutableStateOf(false)

    var images by mutableStateOf(
        listOf(
            VirtualImage("1", "Ubuntu 22.04 LTS", "Bootable Linux distribution (ISO)", "3.4 GB", false),
            VirtualImage("2", "Windows 11 PE", "Preinstallation Environment (ISO)", "1.2 GB", false),
            VirtualImage("3", "Hiren's BootCD", "Emergency diagnostic tools (ISO)", "2.8 GB", false),
            VirtualImage("4", "Rufus Toolkit.exe", "Boot drive creator utility (EXE)", "1.4 MB", true),
            VirtualImage("5", "MemTest86", "Memory diagnostic tool (ISO)", "500 MB", false)
        )
    )
        private set

    fun toggleUsbConnection() {
        isUsbConnected = !isUsbConnected
        if (!isUsbConnected) {
            isSimulatingDrive = false
        }
    }

    fun toggleDriveSimulation() {
        if (!isUsbConnected) return
        isSimulatingDrive = !isSimulatingDrive
    }

    fun downloadImage(id: String) {
        val index = images.indexOfFirst { it.id == id }
        if (index == -1 || images[index].isDownloading || images[index].isDownloaded) return

        val img = images[index]
        images = images.toMutableList().apply {
            set(index, img.copy(isDownloading = true))
        }

        viewModelScope.launch {
            var progress = 0f
            while (progress < 1f) {
                delay((100..400).random().toLong())
                progress += (if (img.isExe) 0.2f else 0.05f)
                if (progress >= 1f) progress = 1f

                val currentIndex = images.indexOfFirst { it.id == id }
                if (currentIndex != -1) {
                    images = images.toMutableList().apply {
                        set(currentIndex, images[currentIndex].copy(downloadProgress = progress))
                    }
                }
            }

            val finalIndex = images.indexOfFirst { it.id == id }
            if (finalIndex != -1) {
                images = images.toMutableList().apply {
                    set(finalIndex, images[finalIndex].copy(
                        isDownloading = false,
                        isDownloaded = true,
                        downloadProgress = 1f
                    ))
                }
            }
        }
    }

    fun toggleMount(id: String) {
        val index = images.indexOfFirst { it.id == id }
        if (index == -1 || !images[index].isDownloaded) return

        val currentMountState = images[index].isMounted
        val imageToMount = images[index]

        viewModelScope.launch {
            if (!currentMountState) {
                // Try to perform a real mount using root fallback
                val rootSuccess = com.example.backend.UsbGadgetManager.mountImage(imageToMount.id)
                // We'll update UI regardless for simulation purposes, but on a real rooted device this would take effect.
            } else {
                com.example.backend.UsbGadgetManager.unmountImage()
            }

            images = images.mapIndexed { i, image ->
                if (i == index) {
                    image.copy(isMounted = !currentMountState)
                } else {
                    image.copy(isMounted = false)
                }
            }
        }
    }

    fun addRealFile(uri: Uri, context: Context) {
        var name = "Unknown File"
        var sizeBytes = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex != -1) name = cursor.getString(nameIndex)
                if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
            }
        }

        val isExe = name.endsWith(".exe", ignoreCase = true)
        val sizeStr = android.text.format.Formatter.formatShortFileSize(context, sizeBytes)
        
        val newFile = VirtualImage(
            id = uri.toString(),
            name = name,
            description = "Local file from device",
            size = sizeStr,
            isExe = isExe,
            downloadProgress = 1f,
            isDownloaded = true,
            isDownloading = false,
            isMounted = false
        )
        
        images = listOf(newFile) + images
    }

    fun deleteImage(id: String) {
        val index = images.indexOfFirst { it.id == id }
        if (index == -1) return
        
        if (images[index].description == "Local file from device") {
            images = images.toMutableList().apply { removeAt(index) }
        } else {
            images = images.toMutableList().apply {
                set(index, images[index].copy(
                    isDownloaded = false,
                    isDownloading = false,
                    downloadProgress = 0f,
                    isMounted = false
                ))
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: DriveDroidViewModel = viewModel()
                val context = LocalContext.current
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let { viewModel.addRealFile(it, context) }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Virtual USB Boot", fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Import ISO/EXE")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    VirtualUsbScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun VirtualUsbScreen(viewModel: DriveDroidViewModel, modifier: Modifier = Modifier) {
    val downloadedFiles = viewModel.images.filter { it.isDownloaded }
    val availableFiles = viewModel.images.filter { !it.isDownloaded }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            WarningBanner(
                visible = !viewModel.rootAccessWarningDismissed,
                onDismiss = { viewModel.rootAccessWarningDismissed = true }
            )
        }

        item {
            ConnectionStatusCard(
                isUsbConnected = viewModel.isUsbConnected,
                isSimulatingDrive = viewModel.isSimulatingDrive,
                onToggleConnection = viewModel::toggleUsbConnection,
                onToggleSimulation = viewModel::toggleDriveSimulation
            )
        }
        
        if (downloadedFiles.isNotEmpty()) {
            item {
                Text(
                    text = "VIRTUAL STORAGE (FILE EXPLORER)",
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            items(downloadedFiles) { file ->
                FileExplorerItemCard(
                    image = file,
                    isSimulatingDrive = viewModel.isSimulatingDrive,
                    onToggleMount = { viewModel.toggleMount(file.id) },
                    onDelete = { viewModel.deleteImage(file.id) }
                )
            }
        }

        if (availableFiles.isNotEmpty()) {
            item {
                Text(
                    text = "AVAILABLE IMAGES & EXECUTABLES",
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            items(availableFiles) { image ->
                ImageItemCard(
                    image = image,
                    isSimulatingDrive = viewModel.isSimulatingDrive,
                    onDownload = { viewModel.downloadImage(image.id) },
                    onToggleMount = { viewModel.toggleMount(image.id) }
                )
            }
        }
    }
}

@Composable
fun FileExplorerItemCard(
    image: VirtualImage,
    isSimulatingDrive: Boolean,
    onToggleMount: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { },
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.ElegantDarkOutline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = "File",
                    tint = if (image.isExe) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Titles
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        image.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${image.size} • ${if (image.isExe) "Executable" else "Disk Image"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_btn_${image.id}")) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val isMountEnabled = isSimulatingDrive && !image.isExe
            val mountText = if (image.isExe) {
                "Executable via USB"
            } else if (image.isMounted) {
                "Mounted"
            } else {
                "Not Mounted"
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mountText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (image.isMounted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                
                if (!image.isExe) {
                    Button(
                        onClick = onToggleMount,
                        enabled = isSimulatingDrive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (image.isMounted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp).testTag("mount_btn_${image.id}")
                    ) {
                        Text(if (image.isMounted) "UNMOUNT" else "MOUNT", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun WarningBanner(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.ElegantDarkOutline)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Simulation Mode Active",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Actual USB Mass Storage emulation requires root access or specific kernel patches. This interface simulates realistic interactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.padding(top = 4.dp).height(32.dp)
                    ) {
                        Text("DISMISS", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    isUsbConnected: Boolean,
    isSimulatingDrive: Boolean,
    onToggleConnection: () -> Unit,
    onToggleSimulation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.ElegantDarkOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ElegantDarkHeroBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Host Connection State",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // USB Cable Simulation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = "PC",
                    tint = if (isUsbConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                // Animated Line
                val cableColor by animateColorAsState(
                    targetValue = if (isUsbConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    label = "cableColor"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(cableColor, CircleShape)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = "Phone",
                    tint = if (isUsbConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isUsbConnected,
                    onCheckedChange = { onToggleConnection() },
                    modifier = Modifier.testTag("usb_toggle")
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Drive Emulation State
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "USB Virtual Drive",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isSimulatingDrive) "Active: Accessible as Mass Storage" else "Inactive: Standard MTP Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSimulatingDrive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSimulatingDrive,
                    onCheckedChange = { onToggleSimulation() },
                    enabled = isUsbConnected,
                    modifier = Modifier.testTag("drive_emulation_toggle")
                )
            }
        }
    }
}

@Composable
fun ImageItemCard(
    image: VirtualImage,
    isSimulatingDrive: Boolean,
    onDownload: () -> Unit,
    onToggleMount: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.ElegantDarkOutline),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ElegantDarkCardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (image.isExe) MaterialTheme.colorScheme.secondaryContainer 
                            else MaterialTheme.colorScheme.primaryContainer, 
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (image.isExe) Icons.Default.SystemUpdateAlt else Icons.Default.SystemUpdateAlt,
                        contentDescription = "Image Type",
                        tint = if (image.isExe) MaterialTheme.colorScheme.onSecondaryContainer 
                               else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Titles
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        image.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${image.size} • ${image.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Action Buttons
                if (!image.isDownloaded && !image.isDownloading) {
                    IconButton(
                        onClick = onDownload, 
                        modifier = Modifier.testTag("download_btn_${image.id}")
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (image.isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Progress Bar
            if (image.isDownloading) {
                Spacer(modifier = Modifier.height(16.dp))
                val animatedProgress by animateFloatAsState(targetValue = image.downloadProgress, label = "progress")
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFF44474E)
                )
            }

            // Mount Status
            if (image.isDownloaded) {
                Spacer(modifier = Modifier.height(16.dp))
                val isMountEnabled = isSimulatingDrive && !image.isExe
                val mountText = if (image.isExe) {
                    "EXE file - Executable via Virtual Drive when connected"
                } else if (image.isMounted) {
                    "Currently Mounted as Bootable Drive"
                } else {
                    "Ready to Mount"
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mountText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (image.isMounted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (!image.isExe) {
                        Button(
                            onClick = onToggleMount,
                            enabled = isSimulatingDrive,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (image.isMounted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp).testTag("mount_btn_${image.id}")
                        ) {
                            Text(if (image.isMounted) "UNMOUNT" else "MOUNT")
                        }
                    } else if (image.isExe && isSimulatingDrive) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Accessible via PC", 
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

