package com.artraw.thumbgen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.artraw.thumbgen.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThumbGenAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MatteBlack
                ) {
                    ThumbnailGeneratorScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThumbnailGeneratorScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val prefsManager = remember { PreferencesManager(context) }
    val apiKeysConfig by prefsManager.apiKeysFlow.collectAsState(initial = ApiKeysConfig())
    val engine = remember { ThumbnailEngine(context) }

    // UI States
    var showApiSettingsDialog by remember { mutableStateOf(false) }
    var topic by remember { mutableStateOf("") }
    var selectedNiche by remember { mutableStateOf(Niche.COOKING) }
    var selectedRatio by remember { mutableStateOf(AspectRatio.RATIO_16_9) }
    var referenceImageUri by remember { mutableStateOf<Uri?>(null) }
    var removeBackgroundToggle by remember { mutableStateOf(true) }

    // Generation & Live Editing States
    var isGenerating by remember { mutableStateOf(false) }
    var currentStepText by remember { mutableStateOf("") }
    var currentProgress by remember { mutableFloatStateOf(0f) }

    var lastGeneratedPrompt by remember { mutableStateOf("") }
    var currentTextHook by remember { mutableStateOf("") }
    var isTextYellow by remember { mutableStateOf(true) }

    var baseCompositeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var finalThumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            referenceImageUri = uri
        }
    }

    // Function to re-render text on top of composite in real time
    fun updateTextOverlay(newText: String, yellow: Boolean) {
        val base = baseCompositeBitmap ?: return
        finalThumbnailBitmap = engine.renderTextOnComposite(base, newText, yellow)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = CrimsonRed,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("▶", color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "ThumbGen AI",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = PureWhite
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showApiSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "API Keys Settings",
                            tint = PureWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MatteBlack
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MatteBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Reference Image Section (Upload & Auto-Cutout)
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "1. REFERENCE SUBJECT (CREATOR / PRODUCT)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (referenceImageUri == null) {
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = PureWhite)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Subject from Gallery", color = PureWhite)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AsyncImage(
                                model = referenceImageUri,
                                contentDescription = "Reference Preview",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, CrimsonRed, RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Auto-Cutout (ML Kit)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PureWhite
                                    )
                                    Switch(
                                        checked = removeBackgroundToggle,
                                        onCheckedChange = { removeBackgroundToggle = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = PureWhite,
                                            checkedTrackColor = CrimsonRed
                                        )
                                    )
                                }
                                Text(
                                    "Removes background offline on-device",
                                    fontSize = 11.sp,
                                    color = MutedWhite
                                )
                            }
                            IconButton(onClick = { referenceImageUri = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MutedWhite)
                            }
                        }
                    }
                }
            }

            // 2. Topic & Niche Section
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "2. VIDEO TOPIC & TITLE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        placeholder = { Text("e.g. Desi Mutton Handi Dhaba Style or M4 iPad Pro Review", color = MutedWhite) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonRed,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            cursorColor = CrimsonRed
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Select Niche:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedWhite)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Niche.values().forEach { niche ->
                            val isSelected = selectedNiche == niche
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedNiche = niche },
                                label = {
                                    Text(
                                        niche.displayName,
                                        fontSize = 12.sp,
                                        color = if (isSelected) PureWhite else MutedWhite,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CrimsonRed,
                                    containerColor = SurfaceVariantDark
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) CrimsonRed else BorderDark
                                )
                            )
                        }
                    }
                }
            }

            // 3. Aspect Ratio Selector
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "3. SIZE & ASPECT RATIO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AspectRatio.values().forEach { ratio ->
                            val isSelected = selectedRatio == ratio
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedRatio = ratio },
                                label = {
                                    Text(
                                        ratio.label,
                                        fontSize = 12.sp,
                                        color = if (isSelected) PureWhite else MutedWhite,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CrimsonRed,
                                    containerColor = SurfaceVariantDark
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) CrimsonRed else BorderDark
                                )
                            )
                        }
                    }
                }
            }

            // 4. Start Button & Live Status
            Button(
                onClick = {
                    if (topic.isBlank()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Please enter a video topic or title first!")
                        }
                        return@Button
                    }
                    isGenerating = true
                    currentProgress = 0.1f
                    currentStepText = "Step 1: Generating Strategy..."

                    coroutineScope.launch {
                        try {
                            // Step 1: Strategy
                            val strategy = engine.generateStrategy(topic, selectedNiche, apiKeysConfig, selectedRatio)
                            lastGeneratedPrompt = strategy.imagePrompt
                            currentTextHook = strategy.textHook
                            currentProgress = 0.35f
                            currentStepText = "Step 2: Rendering AI Background..."

                            // Step 2: Image Gen with Multi-Provider Fallback
                            val bgBitmap = engine.generateImage(
                                prompt = strategy.imagePrompt,
                                aspectRatio = selectedRatio,
                                apiKeys = apiKeysConfig
                            ) { status ->
                                currentStepText = status
                            }

                            currentProgress = 0.75f
                            currentStepText = "Step 3: Compositing Cutout & High-CTR Text..."

                            // Step 3: Base Composite + Text Overlay
                            val baseComposite = engine.createBaseComposite(
                                bgBitmap = bgBitmap,
                                referenceUri = referenceImageUri,
                                removeBg = removeBackgroundToggle
                            ) { status ->
                                currentStepText = status
                            }
                            baseCompositeBitmap = baseComposite

                            finalThumbnailBitmap = engine.renderTextOnComposite(
                                baseComposite = baseComposite,
                                textHook = currentTextHook,
                                isYellow = isTextYellow
                            )

                            currentProgress = 1.0f
                            currentStepText = "✅ Completed successfully!"
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Error: ${e.message}")
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonRed,
                    disabledContainerColor = CrimsonRed.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        color = PureWhite,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("GENERATING THUMBNAIL...", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PureWhite)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PureWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🚀 START GENERATING", fontWeight = FontWeight.Black, fontSize = 16.sp, color = PureWhite)
                }
            }

            // Live Step Status Indicator
            if (isGenerating || currentStepText.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                currentStepText,
                                fontSize = 13.sp,
                                color = PureWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${(currentProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = CrimsonRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { currentProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = CrimsonRed,
                            trackColor = SurfaceVariantDark
                        )
                    }
                }
            }

            // 5. Result Preview & Live Text Hook Editor
            finalThumbnailBitmap?.let { bitmap ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "FINAL THUMBNAIL PREVIEW",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonRed
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // High-res Image Preview
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Rendered Thumbnail",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(selectedRatio.width.toFloat() / selectedRatio.height.toFloat())
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, BorderDark, RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live Text Hook Editor
                        Text(
                            "Edit Text Hook (Max 3-4 Words):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = currentTextHook,
                                onValueChange = {
                                    currentTextHook = it
                                    updateTextOverlay(it, isTextYellow)
                                },
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CrimsonRed,
                                    unfocusedBorderColor = BorderDark,
                                    focusedTextColor = PureWhite,
                                    unfocusedTextColor = PureWhite
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Color Toggle Buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = {
                                        isTextYellow = true
                                        updateTextOverlay(currentTextHook, true)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTextYellow) HighCtrYellow else SurfaceVariantDark
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("🟡", fontSize = 14.sp)
                                }
                                Button(
                                    onClick = {
                                        isTextYellow = false
                                        updateTextOverlay(currentTextHook, false)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isTextYellow) PureWhite else SurfaceVariantDark
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("⚪", fontSize = 14.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons: Save to Gallery & Copy Prompt
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val savedUri = engine.saveToGallery(bitmap, topic)
                                        if (savedUri != null) {
                                            snackbarHostState.showSnackbar("✅ Saved to Gallery (Pictures/ThumbGen)!")
                                        } else {
                                            snackbarHostState.showSnackbar("Failed to save image.")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = PureWhite)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save to Gallery", fontWeight = FontWeight.Bold, color = PureWhite)
                            }

                            if (lastGeneratedPrompt.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("AI Prompt", lastGeneratedPrompt))
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Prompt copied to clipboard!")
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark)),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = PureWhite)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // API Keys Modal Dialog
    if (showApiSettingsDialog) {
        ApiSettingsDialog(
            currentConfig = apiKeysConfig,
            onDismiss = { showApiSettingsDialog = false },
            onSave = { updatedConfig ->
                coroutineScope.launch {
                    prefsManager.saveApiKeys(updatedConfig)
                    showApiSettingsDialog = false
                    snackbarHostState.showSnackbar("API Keys saved successfully!")
                }
            }
        )
    }
}

@Composable
fun ApiSettingsDialog(
    currentConfig: ApiKeysConfig,
    onDismiss: () -> Unit,
    onSave: (ApiKeysConfig) -> Unit
) {
    var llmKey by remember { mutableStateOf(currentConfig.llmKey) }
    var cfAccountId by remember { mutableStateOf(currentConfig.cfAccountId) }
    var cfApiToken by remember { mutableStateOf(currentConfig.cfApiToken) }
    var nvidiaKey by remember { mutableStateOf(currentConfig.nvidiaKey) }
    var pollinationsKey by remember { mutableStateOf(currentConfig.pollinationsKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = CrimsonRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("API Keys Configuration", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Keys are saved securely on-device via DataStore. Fallback to open endpoints operates if left empty.",
                    fontSize = 11.sp,
                    color = MutedWhite
                )

                OutlinedTextField(
                    value = llmKey,
                    onValueChange = { llmKey = it },
                    label = { Text("LLM Key (OpenRouter or Groq)", fontSize = 12.sp) },
                    placeholder = { Text("sk-or-v1-... or gsk_...", fontSize = 11.sp, color = MutedWhite) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedLabelColor = CrimsonRed,
                        unfocusedLabelColor = MutedWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = cfAccountId,
                    onValueChange = { cfAccountId = it },
                    label = { Text("Cloudflare Account ID", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedLabelColor = CrimsonRed,
                        unfocusedLabelColor = MutedWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = cfApiToken,
                    onValueChange = { cfApiToken = it },
                    label = { Text("Cloudflare Workers AI Token", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedLabelColor = CrimsonRed,
                        unfocusedLabelColor = MutedWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nvidiaKey,
                    onValueChange = { nvidiaKey = it },
                    label = { Text("NVIDIA NIM API Key", fontSize = 12.sp) },
                    placeholder = { Text("nvapi-...", fontSize = 11.sp, color = MutedWhite) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedLabelColor = CrimsonRed,
                        unfocusedLabelColor = MutedWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pollinationsKey,
                    onValueChange = { pollinationsKey = it },
                    label = { Text("Pollinations.ai Key (Optional)", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedLabelColor = CrimsonRed,
                        unfocusedLabelColor = MutedWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ApiKeysConfig(
                            llmKey = llmKey,
                            cfAccountId = cfAccountId,
                            cfApiToken = cfApiToken,
                            nvidiaKey = nvidiaKey,
                            pollinationsKey = pollinationsKey
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
            ) {
                Text("Save Keys", fontWeight = FontWeight.Bold, color = PureWhite)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedWhite)
            }
        }
    )
}
