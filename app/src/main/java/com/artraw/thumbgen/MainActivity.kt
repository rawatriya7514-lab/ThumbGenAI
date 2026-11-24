package com.artraw.thumbgen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
                    AppNavigationRoot()
                }
            }
        }
    }
}

@Composable
fun AppNavigationRoot() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefsManager = remember { PreferencesManager(context) }

    val isOnboarded by prefsManager.onboardingCompletedFlow.collectAsState(initial = null)
    val apiKeysConfig by prefsManager.apiKeysFlow.collectAsState(initial = ApiKeysConfig())

    if (isOnboarded == null) {
        // Loading state
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CrimsonRed)
        }
    } else if (!isOnboarded!!) {
        // Show Onboarding Screen at launch
        OnboardingScreen(
            currentConfig = apiKeysConfig,
            onComplete = { newConfig ->
                coroutineScope.launch {
                    prefsManager.saveApiKeys(newConfig)
                    prefsManager.setOnboardingCompleted(true)
                }
            }
        )
    } else {
        // Main App Screen
        MainAppStudio(
            apiKeysConfig = apiKeysConfig,
            onResetOnboarding = {
                coroutineScope.launch {
                    prefsManager.setOnboardingCompleted(false)
                }
            },
            onSaveApiKeys = { newConfig ->
                coroutineScope.launch {
                    prefsManager.saveApiKeys(newConfig)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppStudio(
    apiKeysConfig: ApiKeysConfig,
    onResetOnboarding: () -> Unit,
    onSaveApiKeys: (ApiKeysConfig) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val engine = remember { ThumbnailEngine(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Thumbnail Studio, 1: AI Image Gen
    var showApiSettingsDialog by remember { mutableStateOf(false) }

    // Shared state between tabs
    var importedBgFromImageGen by remember { mutableStateOf<Bitmap?>(null) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MatteBlack)) {
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
                                contentDescription = "Settings",
                                tint = PureWhite
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MatteBlack)
                )

                // Mode Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MatteBlack,
                    contentColor = CrimsonRed
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "🎯 Thumbnail Studio",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) PureWhite else MutedWhite,
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "🎨 AI Image Gen",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) PureWhite else MutedWhite,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MatteBlack
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> {
                    ThumbnailStudioTab(
                        apiKeysConfig = apiKeysConfig,
                        engine = engine,
                        snackbarHostState = snackbarHostState,
                        externalBg = importedBgFromImageGen
                    )
                }
                1 -> {
                    DirectImageGenTab(
                        apiKeysConfig = apiKeysConfig,
                        engine = engine,
                        snackbarHostState = snackbarHostState,
                        onSendToThumbnailStudio = { bmp ->
                            importedBgFromImageGen = bmp
                            selectedTab = 0
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Image sent to Thumbnail Studio!")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showApiSettingsDialog) {
        FullApiSettingsDialog(
            currentConfig = apiKeysConfig,
            engine = engine,
            onDismiss = { showApiSettingsDialog = false },
            onSave = { updated ->
                onSaveApiKeys(updated)
                showApiSettingsDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("API Keys saved successfully!")
                }
            },
            onReopenOnboarding = {
                showApiSettingsDialog = false
                onResetOnboarding()
            }
        )
    }
}

/**
 * Tab 1: Direct AI Image Generation from Custom Prompt with Many Sizes
 */
@Composable
fun DirectImageGenTab(
    apiKeysConfig: ApiKeysConfig,
    engine: ThumbnailEngine,
    snackbarHostState: SnackbarHostState,
    onSendToThumbnailStudio: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var userPrompt by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(ImageStyle.PHOTOREALISTIC) }
    var selectedRatio by remember { mutableStateOf(AspectRatio.RATIO_16_9) }
    var selectedProvider by remember { mutableStateOf("AUTO") } // AUTO, NVIDIA, CLOUDFLARE, POLLINATIONS

    var isEnhancing by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Prompt Input + AI Enhancer
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. ENTER IMAGE PROMPT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)

                    // AI Prompt Enhancer Button
                    TextButton(
                        onClick = {
                            if (userPrompt.isBlank()) {
                                coroutineScope.launch { snackbarHostState.showSnackbar("Enter a basic prompt first!") }
                                return@TextButton
                            }
                            coroutineScope.launch {
                                isEnhancing = true
                                val enhanced = engine.enhancePrompt(userPrompt, selectedStyle, apiKeysConfig)
                                if (enhanced.isNotEmpty()) {
                                    userPrompt = enhanced
                                    snackbarHostState.showSnackbar("Prompt enhanced with AI!")
                                }
                                isEnhancing = false
                            }
                        },
                        enabled = !isEnhancing && userPrompt.isNotBlank()
                    ) {
                        if (isEnhancing) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = HighCtrYellow)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("✨ AI Enhance", color = HighCtrYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    placeholder = {
                        Text(
                            "e.g. Sizzling spicy tandoori chicken on cast iron skillet, dramatic golden rim light, dark moody background, 8k",
                            color = MutedWhite,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Style Presets Row
                Text("Artistic Style Preset:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MutedWhite)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ImageStyle.values().forEach { style ->
                        val isSelected = selectedStyle == style
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedStyle = style },
                            label = { Text(style.displayName, fontSize = 11.sp, color = if (isSelected) PureWhite else MutedWhite) },
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

        // Section 2: Comprehensive Aspect Ratio / Size Picker
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("2. SELECT SIZE & ASPECT RATIO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AspectRatio.values().toList().chunked(2).forEach { rowRatios ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowRatios.forEach { ratio ->
                                val isSelected = selectedRatio == ratio
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) CrimsonRed.copy(alpha = 0.2f) else SurfaceVariantDark
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            1.dp,
                                            if (isSelected) CrimsonRed else BorderDark,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedRatio = ratio }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            ratio.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isSelected) PureWhite else MutedWhite
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${ratio.width}x${ratio.height} • ${ratio.subtitle}",
                                            fontSize = 9.sp,
                                            color = if (isSelected) HighCtrYellow else MutedWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Engine Provider Selector
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("3. AI MODEL ENGINE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "AUTO" to "⚡ Auto (Best Available)",
                        "NVIDIA" to "🟢 NVIDIA NIM FLUX.1-dev",
                        "CLOUDFLARE" to "☁️ Cloudflare FLUX Schnell",
                        "POLLINATIONS" to "🌐 Pollinations (Free)"
                    ).forEach { (id, label) ->
                        val isSelected = selectedProvider == id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedProvider = id },
                            label = { Text(label, fontSize = 11.sp, color = if (isSelected) PureWhite else MutedWhite) },
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

        // Large Start Button
        Button(
            onClick = {
                if (userPrompt.isBlank()) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Please enter a prompt first!") }
                    return@Button
                }
                isGenerating = true
                statusText = "Initializing generation pipeline..."

                coroutineScope.launch {
                    try {
                        val fullPrompt = "$userPrompt${selectedStyle.promptSuffix}"
                        val bmp = engine.generateImage(
                            prompt = fullPrompt,
                            aspectRatio = selectedRatio,
                            apiKeys = apiKeysConfig,
                            preferredProvider = selectedProvider
                        ) { update ->
                            statusText = update
                        }
                        generatedBitmap = bmp
                        statusText = "✅ Image rendered successfully!"
                    } catch (e: Exception) {
                        statusText = "Error: ${e.message}"
                        snackbarHostState.showSnackbar("Generation failed: ${e.message}")
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
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("GENERATING IMAGE...", fontWeight = FontWeight.Bold, color = PureWhite, fontSize = 14.sp)
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PureWhite)
                Spacer(modifier = Modifier.width(8.dp))
                Text("🎨 GENERATE AI IMAGE", fontWeight = FontWeight.Black, color = PureWhite, fontSize = 15.sp)
            }
        }

        if (statusText.isNotEmpty()) {
            Text(statusText, fontSize = 12.sp, color = MutedWhite, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        // Preview & Export Card
        generatedBitmap?.let { bmp ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("GENERATED AI IMAGE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                    Spacer(modifier = Modifier.height(10.dp))

                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Rendered Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(selectedRatio.width.toFloat() / selectedRatio.height.toFloat())
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val uri = engine.saveToGallery(bmp, "AI_Image")
                                    if (uri != null) {
                                        snackbarHostState.showSnackbar("✅ Saved to Pictures/ThumbGen!")
                                    } else {
                                        snackbarHostState.showSnackbar("Failed to save.")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = PureWhite)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save to Gallery", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PureWhite)
                        }

                        OutlinedButton(
                            onClick = { onSendToThumbnailStudio(bmp) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HighCtrYellow),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(HighCtrYellow)),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Text("🎯 Use as Thumbnail", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = HighCtrYellow)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 0: High-CTR YouTube Thumbnail Studio
 */
@Composable
fun ThumbnailStudioTab(
    apiKeysConfig: ApiKeysConfig,
    engine: ThumbnailEngine,
    snackbarHostState: SnackbarHostState,
    externalBg: Bitmap? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var topic by remember { mutableStateOf("") }
    var selectedNiche by remember { mutableStateOf(Niche.COOKING) }
    var selectedRatio by remember { mutableStateOf(AspectRatio.RATIO_16_9) }
    var referenceImageUri by remember { mutableStateOf<Uri?>(null) }
    var removeBackgroundToggle by remember { mutableStateOf(true) }

    var isGenerating by remember { mutableStateOf(false) }
    var currentStepText by remember { mutableStateOf("") }
    var currentProgress by remember { mutableFloatStateOf(0f) }

    var currentTextHook by remember { mutableStateOf("") }
    var isTextYellow by remember { mutableStateOf(true) }

    var baseCompositeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var finalThumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) referenceImageUri = uri
    }

    // If an image was passed from AI Image Gen tab, composite it immediately
    LaunchedEffect(externalBg) {
        if (externalBg != null) {
            baseCompositeBitmap = externalBg
            finalThumbnailBitmap = engine.renderTextOnComposite(externalBg, currentTextHook.ifEmpty { "VIRAL SECRET!" }, isTextYellow)
        }
    }

    fun updateTextOverlay(newText: String, yellow: Boolean) {
        val base = baseCompositeBitmap ?: return
        finalThumbnailBitmap = engine.renderTextOnComposite(base, newText, yellow)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Reference Image Section
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("1. REFERENCE SUBJECT (CREATOR / PRODUCT)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
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
                        Text("Select Subject from Gallery", color = PureWhite, fontSize = 13.sp)
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
                                Text("Auto-Cutout (ML Kit)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = PureWhite)
                                Switch(
                                    checked = removeBackgroundToggle,
                                    onCheckedChange = { removeBackgroundToggle = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = PureWhite,
                                        checkedTrackColor = CrimsonRed
                                    )
                                )
                            }
                            Text("Removes background offline on-device", fontSize = 10.sp, color = MutedWhite)
                        }
                        IconButton(onClick = { referenceImageUri = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MutedWhite)
                        }
                    }
                }
            }
        }

        // 2. Video Topic & Niche
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("2. VIDEO TOPIC & TITLE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    placeholder = { Text("e.g. Desi Mutton Handi Dhaba Style or iPhone 16 Pro Max", color = MutedWhite, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Select Niche:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MutedWhite)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Niche.values().forEach { niche ->
                        val isSelected = selectedNiche == niche
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedNiche = niche },
                            label = { Text(niche.displayName, fontSize = 11.sp, color = if (isSelected) PureWhite else MutedWhite) },
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
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("3. SIZE & ASPECT RATIO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AspectRatio.values().forEach { ratio ->
                        val isSelected = selectedRatio == ratio
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedRatio = ratio },
                            label = { Text(ratio.label, fontSize = 11.sp, color = if (isSelected) PureWhite else MutedWhite) },
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

        // Start Button
        Button(
            onClick = {
                if (topic.isBlank()) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Enter a video topic first!") }
                    return@Button
                }
                isGenerating = true
                currentProgress = 0.1f
                currentStepText = "Step 1: Generating Strategy..."

                coroutineScope.launch {
                    try {
                        val strategy = engine.generateStrategy(topic, selectedNiche, apiKeysConfig, selectedRatio)
                        currentTextHook = strategy.textHook
                        currentProgress = 0.35f
                        currentStepText = "Step 2: Rendering AI Background..."

                        val bgBitmap = engine.generateImage(
                            prompt = strategy.imagePrompt,
                            aspectRatio = selectedRatio,
                            apiKeys = apiKeysConfig
                        ) { status -> currentStepText = status }

                        currentProgress = 0.75f
                        currentStepText = "Step 3: Compositing Cutout & High-CTR Text..."

                        val baseComposite = engine.createBaseComposite(
                            bgBitmap = bgBitmap,
                            referenceUri = referenceImageUri,
                            removeBg = removeBackgroundToggle
                        ) { status -> currentStepText = status }
                        baseCompositeBitmap = baseComposite

                        finalThumbnailBitmap = engine.renderTextOnComposite(
                            baseComposite = baseComposite,
                            textHook = currentTextHook,
                            isYellow = isTextYellow
                        )

                        currentProgress = 1.0f
                        currentStepText = "✅ Thumbnail complete!"
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
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("GENERATING THUMBNAIL...", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PureWhite)
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PureWhite)
                Spacer(modifier = Modifier.width(8.dp))
                Text("🚀 START GENERATING", fontWeight = FontWeight.Black, fontSize = 15.sp, color = PureWhite)
            }
        }

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
                        Text(currentStepText, fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
                        Text("${(currentProgress * 100).toInt()}%", fontSize = 12.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
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

        // Preview & Live Text Hook Editor
        finalThumbnailBitmap?.let { bitmap ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("FINAL THUMBNAIL PREVIEW", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                    Spacer(modifier = Modifier.height(10.dp))

                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Thumbnail Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(selectedRatio.width.toFloat() / selectedRatio.height.toFloat())
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Edit Text Hook Live:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PureWhite)
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
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonRed,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

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
                            ) { Text("🟡", fontSize = 13.sp) }

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
                            ) { Text("⚪", fontSize = 13.sp) }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val savedUri = engine.saveToGallery(bitmap, "Thumb")
                                if (savedUri != null) {
                                    snackbarHostState.showSnackbar("✅ Saved to Pictures/ThumbGen!")
                                } else {
                                    snackbarHostState.showSnackbar("Failed to save.")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = PureWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save to Gallery", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PureWhite)
                    }
                }
            }
        }
    }
}

/**
 * Full Settings & API Keys Dialog with Instant Connection Testing
 */
@Composable
fun FullApiSettingsDialog(
    currentConfig: ApiKeysConfig,
    engine: ThumbnailEngine,
    onDismiss: () -> Unit,
    onSave: (ApiKeysConfig) -> Unit,
    onReopenOnboarding: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var llmKey by remember { mutableStateOf(currentConfig.llmKey) }
    var cfAccountId by remember { mutableStateOf(currentConfig.cfAccountId) }
    var cfApiToken by remember { mutableStateOf(currentConfig.cfApiToken) }
    var nvidiaKey by remember { mutableStateOf(currentConfig.nvidiaKey) }
    var pollinationsKey by remember { mutableStateOf(currentConfig.pollinationsKey) }

    var testStatus by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = CrimsonRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("API Settings & Providers", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (testStatus.isNotEmpty()) {
                    Surface(
                        color = if (testStatus.startsWith("✅")) HighCtrYellow.copy(alpha = 0.2f) else CrimsonRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(testStatus, color = PureWhite, fontSize = 11.sp, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.SemiBold)
                    }
                }

                // NVIDIA Key
                Text("NVIDIA NIM API Key:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                OutlinedTextField(
                    value = nvidiaKey,
                    onValueChange = { nvidiaKey = it },
                    placeholder = { Text("nvapi-...", fontSize = 11.sp, color = MutedWhite) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // LLM Key
                Text("LLM Key (Groq or OpenRouter):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                OutlinedTextField(
                    value = llmKey,
                    onValueChange = { llmKey = it },
                    placeholder = { Text("gsk_... or sk-or-...", fontSize = 11.sp, color = MutedWhite) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Cloudflare
                Text("Cloudflare Account ID & Token:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                OutlinedTextField(
                    value = cfAccountId,
                    onValueChange = { cfAccountId = it },
                    placeholder = { Text("Account ID", fontSize = 11.sp, color = MutedWhite) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cfApiToken,
                    onValueChange = { cfApiToken = it },
                    placeholder = { Text("Workers AI Token", fontSize = 11.sp, color = MutedWhite) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Reopen Onboarding button
                OutlinedButton(
                    onClick = onReopenOnboarding,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedWhite),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔁 Re-run Setup Onboarding", fontSize = 11.sp, color = MutedWhite)
                }
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
                Text("Save", fontWeight = FontWeight.Bold, color = PureWhite)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedWhite)
            }
        }
    )
}
