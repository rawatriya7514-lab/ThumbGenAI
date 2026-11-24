package com.artraw.thumbgen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artraw.thumbgen.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    currentConfig: ApiKeysConfig,
    onComplete: (ApiKeysConfig) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val engine = remember { ThumbnailEngine(context) }

    var currentStep by remember { mutableIntStateOf(0) }

    var llmKey by remember { mutableStateOf(currentConfig.llmKey) }
    var cfAccountId by remember { mutableStateOf(currentConfig.cfAccountId) }
    var cfApiToken by remember { mutableStateOf(currentConfig.cfApiToken) }
    var nvidiaKey by remember { mutableStateOf(currentConfig.nvidiaKey) }
    var pollinationsKey by remember { mutableStateOf(currentConfig.pollinationsKey) }

    // Test states
    var llmTestStatus by remember { mutableStateOf("") }
    var nvidiaTestStatus by remember { mutableStateOf("") }
    var cfTestStatus by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MatteBlack)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step Indicator Dots
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(6.dp)
                        .width(if (currentStep == index) 24.dp else 8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (currentStep == index) CrimsonRed else BorderDark)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (currentStep) {
                0 -> {
                    // Step 1: Welcome & Value Prop
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CrimsonRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(90.dp).border(2.dp, CrimsonRed, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚡", fontSize = 42.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            "Welcome to ThumbGen AI",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = PureWhite,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Create High-CTR YouTube Thumbnails and Generate Hyper-Realistic AI Images in Any Aspect Ratio.",
                            fontSize = 13.sp,
                            color = MutedWhite,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        FeatureHighlightCard(
                            icon = "🎯",
                            title = "High-CTR Strategy & Text Hooks",
                            description = "Automated prompt engine + dynamic bold typography with 0% text distortion."
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FeatureHighlightCard(
                            icon = "🖼️",
                            title = "Direct Prompt-to-Image AI",
                            description = "Type any custom prompt, select styles (8K, Anime, Cyberpunk, 3D), and pick 8+ sizes."
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FeatureHighlightCard(
                            icon = "✂️",
                            title = "Offline AI Subject Cutout",
                            description = "Google ML Kit extracts your face/product on-device and places it with rim glow."
                        )
                    }
                }
                1 -> {
                    // Step 2: API Keys Configuration
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Configure AI Providers",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = PureWhite
                        )
                        Text(
                            "Add your free API keys for maximum speed and quality. Or use Free Mode (zero keys needed)!",
                            fontSize = 12.sp,
                            color = MutedWhite,
                            lineHeight = 16.sp
                        )

                        // Card: Pollinations Instant Free Mode
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, CrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🌐", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Free Instant Access (Pollinations)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PureWhite)
                                    }
                                    Surface(
                                        color = HighCtrYellow,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("ACTIVE", color = MatteBlack, fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Ready right out of the box! No account or credit card required.",
                                    fontSize = 11.sp,
                                    color = MutedWhite
                                )
                            }
                        }

                        // Card: NVIDIA NIM API Key
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🟢 NVIDIA NIM (FLUX.1-dev 8K)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PureWhite)
                                    if (nvidiaTestStatus.isNotEmpty()) {
                                        Text(nvidiaTestStatus, fontSize = 10.sp, color = if (nvidiaTestStatus.startsWith("✅")) HighCtrYellow else CrimsonRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = nvidiaKey,
                                    onValueChange = {
                                        nvidiaKey = it
                                        nvidiaTestStatus = ""
                                    },
                                    placeholder = { Text("nvapi-...", fontSize = 11.sp, color = MutedWhite) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CrimsonRed,
                                        unfocusedBorderColor = BorderDark,
                                        focusedTextColor = PureWhite,
                                        unfocusedTextColor = PureWhite
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Free 1000 credits at build.nvidia.com", fontSize = 10.sp, color = MutedWhite)
                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                isTesting = true
                                                val (ok, msg) = engine.testApiKey("NVIDIA", nvidiaKey)
                                                nvidiaTestStatus = if (ok) "✅ OK" else "❌ Fail"
                                                isTesting = false
                                            }
                                        },
                                        enabled = nvidiaKey.isNotBlank() && !isTesting
                                    ) {
                                        Text("Test Key", fontSize = 11.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Card: LLM Key (Groq or OpenRouter)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🧠 LLM Key (Groq / OpenRouter)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PureWhite)
                                    if (llmTestStatus.isNotEmpty()) {
                                        Text(llmTestStatus, fontSize = 10.sp, color = if (llmTestStatus.startsWith("✅")) HighCtrYellow else CrimsonRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = llmKey,
                                    onValueChange = {
                                        llmKey = it
                                        llmTestStatus = ""
                                    },
                                    placeholder = { Text("gsk_... or sk-or-v1-...", fontSize = 11.sp, color = MutedWhite) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CrimsonRed,
                                        unfocusedBorderColor = BorderDark,
                                        focusedTextColor = PureWhite,
                                        unfocusedTextColor = PureWhite
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Free keys at groq.com or openrouter.ai", fontSize = 10.sp, color = MutedWhite)
                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                isTesting = true
                                                val (ok, msg) = engine.testApiKey("LLM", llmKey)
                                                llmTestStatus = if (ok) "✅ OK" else "❌ Fail"
                                                isTesting = false
                                            }
                                        },
                                        enabled = llmKey.isNotBlank() && !isTesting
                                    ) {
                                        Text("Test Key", fontSize = 11.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Card: Cloudflare Workers AI
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("☁️ Cloudflare Workers AI (FLUX Schnell)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PureWhite)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = cfAccountId,
                                    onValueChange = { cfAccountId = it },
                                    placeholder = { Text("Account ID", fontSize = 11.sp, color = MutedWhite) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CrimsonRed,
                                        unfocusedBorderColor = BorderDark,
                                        focusedTextColor = PureWhite,
                                        unfocusedTextColor = PureWhite
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = cfApiToken,
                                    onValueChange = { cfApiToken = it },
                                    placeholder = { Text("Workers AI API Token", fontSize = 11.sp, color = MutedWhite) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CrimsonRed,
                                        unfocusedBorderColor = BorderDark,
                                        focusedTextColor = PureWhite,
                                        unfocusedTextColor = PureWhite
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // Step 3: Ready to Create
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = HighCtrYellow.copy(alpha = 0.15f),
                            modifier = Modifier.size(90.dp).border(2.dp, HighCtrYellow, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🚀", fontSize = 42.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            "You are All Set!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = PureWhite,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            "ThumbGen AI will automatically pick the best available engine. You can change your keys or preferences anytime from the settings icon.",
                            fontSize = 13.sp,
                            color = MutedWhite,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("💡 Pro Tips for Viral Thumbnails:", fontWeight = FontWeight.Bold, color = CrimsonRed, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("1. Keep text hooks under 3–4 words for 80%+ mobile CTR.", fontSize = 11.sp, color = MutedWhite)
                                Text("2. Use the 'AI Enhance' button to automatically expand simple prompt ideas.", fontSize = 11.sp, color = MutedWhite)
                                Text("3. Pick the exact ratio: 16:9 for YouTube videos, 9:16 for Shorts & Reels.", fontSize = 11.sp, color = MutedWhite)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (currentStep > 0) {
                OutlinedButton(
                    onClick = { currentStep-- },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
                ) {
                    Text("Back", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = {
                    if (currentStep < 2) {
                        currentStep++
                    } else {
                        onComplete(
                            ApiKeysConfig(
                                llmKey = llmKey,
                                cfAccountId = cfAccountId,
                                cfApiToken = cfApiToken,
                                nvidiaKey = nvidiaKey,
                                pollinationsKey = pollinationsKey
                            )
                        )
                    }
                },
                modifier = Modifier.weight(if (currentStep == 0) 1f else 1.5f).height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
            ) {
                Text(
                    text = when (currentStep) {
                        0 -> "Get Started ➔"
                        1 -> "Save & Continue ➔"
                        else -> "🚀 Launch Studio"
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = PureWhite
                )
            }
        }
    }
}

@Composable
fun FeatureHighlightCard(icon: String, title: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PureWhite)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, fontSize = 11.sp, color = MutedWhite, lineHeight = 15.sp)
            }
        }
    }
}
