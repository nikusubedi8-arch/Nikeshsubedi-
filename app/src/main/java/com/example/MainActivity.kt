package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// Colors requested by User
val PureBlack = Color(0xFF000000)
val DarkNavy = Color(0xFF0A0A0A)
val CardGray = Color(0xFF141414)
val PureRed = Color(0xFFFF1A1A)
val BurntOrange = Color(0xFFFF6B00)
val ChromeSilver = Color(0xFFC8C8C8)
val RacingGold = Color(0xFFFFD600)
val ElectricBlue = Color(0xFF00D2FF)
val HighContrastTeal = Color(0xFF00E676)

// Condensed condensed sans-serif family
val condensedFontFamily = FontFamily(
    android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
)
val condensedBoldFontFamily = FontFamily(
    android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
)

// Diagonal slash cut custom shape
fun getSlashCutShape(topCut: Float, bottomCut: Float): GenericShape {
    return GenericShape { size, _ ->
        moveTo(0f, topCut)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height - bottomCut)
        lineTo(0f, size.height)
        close()
    }
}

class AtmosphericRacingAudioEngine {
    @Volatile
    private var isPlaying = false
    @Volatile
    private var thread: Thread? = null

    fun start() {
        if (isPlaying) return
        isPlaying = true
        
        val newThread = Thread {
            val sampleRate = 22050
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val bufferSize = if (minBufferSize > 0) minBufferSize * 2 else 4096
            var localAudioTrack: AudioTrack? = null

            try {
                localAudioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                }

                if (localAudioTrack.state == AudioTrack.STATE_INITIALIZED) {
                    localAudioTrack.play()
                } else {
                    isPlaying = false
                    return@Thread
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isPlaying = false
                return@Thread
            }

            val buffer = ShortArray(bufferSize)
            var phase1 = 0.0
            var phase2 = 0.0
            var lfoPhase = 0.0

            val baseFreq1 = 55.0
            val baseFreq2 = 112.0
            val lfoFreq = 0.35 // 0.35 Hz slow sweep

            while (isPlaying && thread == Thread.currentThread() && !Thread.currentThread().isInterrupted) {
                for (i in buffer.indices) {
                    val lfo = Math.sin(lfoPhase) // ranges -1.0 to 1.0
                    
                    val currentFreq1 = baseFreq1 + (lfo * 8.0) // 47Hz to 63Hz
                    val currentFreq2 = baseFreq2 + (lfo * 15.0) // 97Hz to 127Hz
                    
                    val sample1 = Math.sin(phase1)
                    val sample2 = Math.sin(phase2) * 0.7 + (phase2 % (2 * Math.PI) / Math.PI - 1.0) * 0.15
                    
                    val mixedVal = (sample1 * 0.55 + sample2 * 0.3) * (0.8 + lfo * 0.15)
                    
                    val pcmValue = (mixedVal * 18000.0).toInt().coerceIn(-32768, 32767)
                    buffer[i] = pcmValue.toShort()

                    phase1 += 2 * Math.PI * currentFreq1 / sampleRate
                    if (phase1 > 2 * Math.PI) phase1 -= 2 * Math.PI

                    phase2 += 2 * Math.PI * currentFreq2 / sampleRate
                    if (phase2 > 2 * Math.PI) phase2 -= 2 * Math.PI

                    lfoPhase += 2 * Math.PI * lfoFreq / sampleRate
                    if (lfoPhase > 2 * Math.PI) lfoPhase -= 2 * Math.PI
                }

                if (isPlaying && thread == Thread.currentThread()) {
                    try {
                        val result = localAudioTrack.write(buffer, 0, buffer.size)
                        if (result <= 0) {
                            Thread.sleep(15)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        try { Thread.sleep(15) } catch (ex: Exception) {}
                    }
                }
            }

            try {
                if (localAudioTrack.state == AudioTrack.STATE_INITIALIZED) {
                    localAudioTrack.stop()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    localAudioTrack.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        thread = newThread
        newThread.start()
    }

    fun stop() {
        isPlaying = false
        thread?.interrupt()
        thread = null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = PureBlack
                ) { innerPadding ->
                    MainRacingPromotionalPage(
                        modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
                    )
                }
            }
        }
    }
}

@Composable
fun MainRacingPromotionalPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val audioEngine = remember { AtmosphericRacingAudioEngine() }
    var isSoundEnabled by remember { mutableStateOf(false) }

    DisposableEffect(isSoundEnabled) {
        if (isSoundEnabled) {
            audioEngine.start()
        } else {
            audioEngine.stop()
        }
        onDispose {
            audioEngine.stop()
        }
    }

    // Dialog state for Subscribe Prime
    var showPrimeDialog by remember { mutableStateOf(false) }
    // User lobby moniker additions
    var enteredLobbyName by remember { mutableStateOf("") }
    var userJoinedLobby by remember { mutableStateOf(false) }

    // Map of sections to LazyColumn indexes:
    // Index 0: Hero Setup
    // Index 1: Cinematic Banner
    // Index 2: Featured Cars
    // Index 3: Game Modes Grid
    // Index 4: World Tracks 3x2 Grid
    // Index 5: Multiplayer Split Block
    // Index 6: Garage Config Section
    // Index 7: Countdown Leaderboard
    // Index 8: Season Pass
    // Index 9: Reviews Cards
    // Index 10: Call-To-Action CTA
    // Index 11: Main Footer
    val sectionIndexMap = mapOf(
        "HOME" to 0,
        "CARS" to 2,
        "TRACKS" to 4,
        "MULTIPLAYER" to 5,
        "GARAGE" to 6,
        "LEADERBOARD" to 7
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Dynamic animation background - speed lines + sparks
        EmberAndSpeedLinesBackground(modifier = Modifier.fillMaxSize())

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Index 0: Hero Section
            item {
                HeroSection(
                    onNavigateSection = { tabName ->
                        sectionIndexMap[tabName]?.let { index ->
                            scope.launch { lazyListState.animateScrollToItem(index) }
                        }
                    }
                )
            }

            // Index 1: Cinematic text banner
            item {
                CinematicBanner()
            }

            // Index 2: Featured Cars Row
            item {
                FeaturedCarsSection()
            }

            // Index 3: Game Modes Block
            item {
                GameModesSection()
            }

            // Index 4: World Tracks Grid
            item {
                WorldTracksSection()
            }

            // Index 5: Multiplayer Lobby Simulator
            item {
                MultiplayerSection(
                    userJoinedLobby = userJoinedLobby,
                    enteredLobbyName = enteredLobbyName,
                    onLobbyNameChange = { enteredLobbyName = it },
                    onJoinClick = {
                        if (enteredLobbyName.isNotBlank()) {
                            userJoinedLobby = true
                            Toast.makeText(context, "Successfully joined Lobby as $enteredLobbyName!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Index 6: Interactive Garage Configurator
            item {
                GarageSection()
            }

            // Index 7: Realtime Leaderboard Countdown
            item {
                LeaderboardSection()
            }

            // Index 8: Season Pass Pass Tiers
            item {
                SeasonPassSection(onSubscribeClick = { showPrimeDialog = true })
            }

            // Index 9: Press Quotes Cards
            item {
                ReviewQuotesSection()
            }

            // Index 10: Call to Action Screen Bottom
            item {
                CallToActionSection()
            }

            // Index 11: Ultimate Footer
            item {
                FooterSection(
                    onNavigateSection = { tabName ->
                        sectionIndexMap[tabName]?.let { index ->
                            scope.launch { lazyListState.animateScrollToItem(index) }
                        }
                    }
                )
            }
        }

        // Standard Navigation Overlay Floating Bar at Top
        NavbarOverlayFloat(
            isSoundEnabled = isSoundEnabled,
            onSoundToggle = {
                isSoundEnabled = !isSoundEnabled
                val infoText = if (isSoundEnabled) "Audio Active: Racing Engine Atmos 🔥" else "Audio Muted 🔇"
                Toast.makeText(context, infoText, Toast.LENGTH_SHORT).show()
            },
            onNavigateSection = { tabName ->
                sectionIndexMap[tabName]?.let { index ->
                    scope.launch { lazyListState.animateScrollToItem(index) }
                }
            }
        )

        // Custom Dialog overlay for Prime Signups
        if (showPrimeDialog) {
            PrimePromotionDialog(
                onDismiss = { showPrimeDialog = false },
                onConfirm = {
                    showPrimeDialog = false
                    Toast.makeText(context, "Welcome to NIKESH PRIME Membership! Active on PC, Mobile & Console 🔥", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

// -------------------------------------------------------------
// DYNAMIC 60FPS BACKGROUND ANIMATION SYSTEM (SPEED LINES & EMBERS)
// -------------------------------------------------------------
@Composable
fun EmberAndSpeedLinesBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val tick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "clockTick"
    )

    val particles = remember {
        List(35) { index ->
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                alpha = Random.nextFloat() * 0.5f + 0.3f,
                speed = Random.nextFloat() * 0.003f + 0.001f,
                size = Random.nextInt(4, 12),
                isLine = index % 3 == 0
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize().background(PureBlack)) {
        val w = size.width
        val h = size.height

        // Subtle clean Carbon Fiber diagonal texture draw
        val lineSpacing = 16.dp.toPx()
        for (offset in -h.toInt()..w.toInt() step lineSpacing.toInt()) {
            drawLine(
                color = Color(0xFF111111),
                start = Offset(offset.toFloat(), 0f),
                end = Offset(offset.toFloat() + h, h),
                strokeWidth = 1f
            )
        }

        // Dynamic horizontal speed lines background from HTML design (opacity 40%)
        val speedLineAlpha = 0.40f
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, PureRed.copy(alpha = speedLineAlpha), Color.Transparent)
            ),
            start = Offset(0f, h * 0.25f),
            end = Offset(w, h * 0.25f),
            strokeWidth = 2f
        )
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, BurntOrange.copy(alpha = speedLineAlpha), Color.Transparent)
            ),
            start = Offset(0f, h * 0.5f),
            end = Offset(w, h * 0.5f),
            strokeWidth = 2f
        )
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, PureRed.copy(alpha = speedLineAlpha), Color.Transparent)
            ),
            start = Offset(0f, h * 0.75f),
            end = Offset(w, h * 0.75f),
            strokeWidth = 2f
        )

        // Particle embers & speed lines update calculations
        particles.forEach { p ->
            if (p.isLine) {
                // Speedy lines shooting Left -> Right across screens
                val activeX = ((p.x + tick * p.speed * 6f) % 1.0f) * w
                val activeY = p.y * h
                drawLine(
                    color = if (p.alpha > 0.5f) PureRed.copy(alpha = p.alpha) else BurntOrange.copy(alpha = p.alpha),
                    start = Offset(activeX, activeY),
                    end = Offset(activeX + (80f + p.size * 10f), activeY),
                    strokeWidth = 1.5f + p.size / 5f
                )
            } else {
                // Ember sparks floating upwards
                val activeX = p.x * w + (Math.sin(((tick * p.speed * 12) + p.y * 10f).toDouble()) * 30f).toFloat()
                val activeY = (((p.y - tick * p.speed * 1.2f) % 1.0f + 1.0f) % 1.0f) * h
                drawCircle(
                    color = if (p.alpha > 0.55f) PureRed else BurntOrange,
                    radius = p.size.toFloat(),
                    center = Offset(activeX, activeY),
                    alpha = p.alpha
                )
            }
        }
    }
}

data class Particle(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val speed: Float,
    val size: Int,
    val isLine: Boolean
)

// -------------------------------------------------------------
// NAVIGATION OVERLAY FLOATING BAR
// -------------------------------------------------------------
@Composable
fun NavbarOverlayFloat(
    isSoundEnabled: Boolean,
    onSoundToggle: () -> Unit,
    onNavigateSection: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.Black.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PureRed.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Logo Sharp Accent
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onNavigateSection("HOME") }
            ) {
                Text(
                    text = "NIKESH",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = condensedBoldFontFamily,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Flame Active Accent",
                    tint = PureRed,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Quick Scroll links row & Sound Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf("CARS", "TRACKS", "MULTIPLAYER", "GARAGE", "LEADERBOARD").forEach { tag ->
                        var isHovered by remember { mutableStateOf(false) }
                        Text(
                            text = tag,
                            color = if (isHovered) PureRed else ChromeSilver,
                            fontSize = 12.sp,
                            fontFamily = condensedBoldFontFamily,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier
                                .clickable {
                                    isHovered = true
                                    onNavigateSection(tag)
                                }
                                .padding(vertical = 4.dp)
                                .drawBehind {
                                    if (isHovered) {
                                        drawLine(
                                            color = PureRed,
                                            start = Offset(0f, size.height),
                                            end = Offset(size.width, size.height),
                                            strokeWidth = 2f
                                        )
                                    }
                                }
                        )
                    }
                }

                // Divider line between nav links and sound toggle
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // Sound Toggle Icon Button
                IconButton(
                    onClick = onSoundToggle,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("sound_toggle")
                ) {
                    Icon(
                        imageVector = if (isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Toggle Background Ambient Sound",
                        tint = if (isSoundEnabled) PureRed else ChromeSilver,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// HERO SECTION
// -------------------------------------------------------------
@Composable
fun HeroSection(
    onNavigateSection: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 640.dp)
            .padding(horizontal = 24.dp)
            .padding(top = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        // Red glowing light aura background behind the car
        Box(
            modifier = Modifier
                .size(380.dp)
                .align(Alignment.CenterEnd)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(PureRed.copy(alpha = 0.45f), Color.Transparent),
                            center = center,
                            radius = size.width / 1.1f
                        )
                    )
                }
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Main hero text content
                Column(
                    modifier = Modifier.weight(1.2f)
                ) {
                    // Outlined red letters "NIKESH" with custom paint stroke
                    Box {
                        // High impact ghost outline stroke
                        Text(
                            text = "NIKESH",
                            fontSize = 80.sp,
                            fontFamily = condensedBoldFontFamily,
                            color = Color.Transparent,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = 2.sp,
                            style = TextStyle(
                                drawStyle = Stroke(
                                    width = 4f,
                                    join = StrokeJoin.Round
                                )
                            ).copy(
                                color = PureRed,
                                shadow = Shadow(
                                    color = PureRed.copy(alpha = 0.5f),
                                    offset = Offset.Zero,
                                    blurRadius = 16f
                                )
                            )
                        )
                    }

                    // Apex Predator Chrome solid text
                    Text(
                        text = "APEX PREDATOR",
                        color = ChromeSilver,
                        fontSize = 24.sp,
                        fontStyle = FontStyle.Italic,
                        fontFamily = condensedBoldFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        modifier = Modifier.offset(y = (-4).dp),
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = Offset(2f, 2f),
                                blurRadius = 4f
                            )
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Experience India's most brutal street racer simulation. Outrun the laws, master Mumbai underground laps, and climb to global dominance.",
                        color = ChromeSilver,
                        fontSize = 14.sp,
                        fontFamily = condensedFontFamily,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth(0.95f)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { onNavigateSection("GARAGE") },
                            colors = ButtonDefaults.buttonColors(containerColor = PureRed),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("hero_garage_button")
                        ) {
                            Text(
                                "ENTER GARAGE",
                                color = Color.White,
                                fontFamily = condensedBoldFontFamily,
                                letterSpacing = 1.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { onNavigateSection("TRACKS") },
                            border = BorderStroke(1.dp, Color.White),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("hero_explore_tracks")
                        ) {
                            Text(
                                "EXPLORE MAPS",
                                color = Color.White,
                                fontFamily = condensedBoldFontFamily,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Photorealistic Supercar Render Card on the right
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, PureRed.copy(alpha = 0.3f)), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_supercar),
                            contentDescription = "NIKESH APEX PREDATOR Hero Supercar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CINEMATIC BANNER
// -------------------------------------------------------------
@Composable
fun CinematicBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF141414).copy(alpha = 0.7f), Color.Transparent)
                )
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "NOT EVERYONE IS BORN TO RACE. ",
                color = ChromeSilver,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif,
                letterSpacing = 2.sp
            )
            Text(
                text = "NIKESH WAS.",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif,
                letterSpacing = 2.sp
            )
        }
    }
}

// -------------------------------------------------------------
// FEATURED CARS SECTION (DYNAMIC STATE SWITCHING)
// -------------------------------------------------------------
data class RaceCarData(
    val name: String,
    val drawableId: Int,
    val topSpeed: Float,
    val acceleration: Float,
    val handling: Float,
    val glowColor: Color,
    val description: String
)

@Composable
fun FeaturedCarsSection() {
    var selectedIndex by remember { mutableStateOf(0) }

    val cars = remember {
        listOf(
            RaceCarData(
                name = "NIKESH SPECIAL ONE",
                drawableId = R.drawable.img_special_one,
                topSpeed = 0.98f,
                acceleration = 0.95f,
                handling = 0.92f,
                glowColor = PureRed,
                description = "Custom Ferrari-engineered hybrid tailored specifically for extreme Mumbai drift loops."
            ),
            RaceCarData(
                name = "STORM BLADE",
                drawableId = R.drawable.img_storm_blade,
                topSpeed = 0.99f,
                acceleration = 0.91f,
                handling = 0.88f,
                glowColor = ElectricBlue,
                description = "W-16 active aero Bugatti titan built purely for breakneck highway straights."
            ),
            RaceCarData(
                name = "SHADOW GHOST",
                drawableId = R.drawable.img_shadow_ghost,
                topSpeed = 0.95f,
                acceleration = 0.98f,
                handling = 0.96f,
                glowColor = Color.DarkGray,
                description = "Ultra-light composite McLaren spec optimized for technical rain-soaked tracks."
            ),
            RaceCarData(
                name = "GOLDEN FURY",
                drawableId = R.drawable.img_golden_fury,
                topSpeed = 0.96f,
                acceleration = 0.93f,
                handling = 0.84f,
                glowColor = RacingGold,
                description = "Aggressive V12 Lamborghini demon finished in multi-coat reflective gold liquid paint."
            )
        )
    }

    val selectedCar = cars[selectedIndex]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        // Section Header
        Text(
            text = "FEATURED RACING ROSTER",
            color = PureRed,
            fontFamily = condensedBoldFontFamily,
            fontSize = 24.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "SELECT AN APEX VEHICLE TO INSPECT STATS",
            color = ChromeSilver,
            fontFamily = condensedFontFamily,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Selected Car Live Dashboard View
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardGray, shape = RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, selectedCar.glowColor.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stats & details content (left)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(selectedCar.glowColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedCar.name,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = condensedBoldFontFamily,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selectedCar.description,
                    color = ChromeSilver,
                    fontSize = 13.sp,
                    fontFamily = condensedFontFamily,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // TOP SPEED
                StatPerformanceBar(
                    label = "TOP SPEED",
                    progress = selectedCar.topSpeed,
                    accentColor = selectedCar.glowColor
                )
                // ACCELERATION
                StatPerformanceBar(
                    label = "ACCELERATION",
                    progress = selectedCar.acceleration,
                    accentColor = selectedCar.glowColor
                )
                // HANDLING
                StatPerformanceBar(
                    label = "HANDLING",
                    progress = selectedCar.handling,
                    accentColor = selectedCar.glowColor
                )
            }

            // Big Preview image of active selected car (right)
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = selectedCar.drawableId),
                    contentDescription = selectedCar.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Horizontal scrolling selectable cards
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(cars) { index, car ->
                val isSelected = index == selectedIndex
                val borderPulseAlpha by rememberInfiniteTransition(label = "").animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = ""
                )

                Box(modifier = Modifier.padding(vertical = 4.dp)) {
                    Card(
                        modifier = Modifier
                            .width(210.dp)
                            .clickable { selectedIndex = index }
                            .border(
                                BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) car.glowColor.copy(alpha = borderPulseAlpha) else Color.White.copy(alpha = 0.1f)
                                ),
                                RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = CardGray)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = car.drawableId),
                                contentDescription = car.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = car.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = condensedBoldFontFamily,
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Selection interactive diagonal button
                            Button(
                                onClick = { selectedIndex = index },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .clip(getSlashCutShape(0f, 10f)),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) car.glowColor else Color.Black
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    if (isSelected) "SELECTED" else "SELECT CAR",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontFamily = condensedBoldFontFamily,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                                .background(PureRed, RoundedCornerShape(topEnd = 12.dp, bottomStart = 8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "ACTIVE",
                                color = Color.Black,
                                fontSize = 8.sp,
                                fontFamily = condensedBoldFontFamily,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatPerformanceBar(label: String, progress: Float, accentColor: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "statSlider"
    )

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = ChromeSilver,
                fontSize = 11.sp,
                fontFamily = condensedFontFamily,
                letterSpacing = 1.sp
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = condensedBoldFontFamily
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = accentColor,
            trackColor = Color.Black.copy(alpha = 0.5f)
        )
    }
}

// -------------------------------------------------------------
// GAME MODES SECTION (2X2 GRID SYSTEM)
// -------------------------------------------------------------
data class ModeDetails(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

@Composable
fun GameModesSection() {
    val modes = listOf(
        ModeDetails(
            title = "NIKESH CAREER",
            description = "Rise from the streets of Mumbai to the world stage.",
            icon = Icons.Default.EmojiEvents,
            gradientColors = listOf(RacingGold, BurntOrange)
        ),
        ModeDetails(
            title = "STREET WARS",
            description = "No rules. No limits. Only speed.",
            icon = Icons.Default.LocalFireDepartment,
            gradientColors = listOf(PureRed, BurntOrange)
        ),
        ModeDetails(
            title = "ONLINE CHAMPIONSHIP",
            description = "Race the best in the world. Prove you belong.",
            icon = Icons.Default.Public,
            gradientColors = listOf(ElectricBlue, Color(0xFF8F00FF))
        ),
        ModeDetails(
            title = "TIME ATTACK",
            description = "Beat Nikesh's ghost. Beat your own ghost. Beat time itself.",
            icon = Icons.Default.Schedule,
            gradientColors = listOf(HighContrastTeal, ElectricBlue)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkNavy)
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        Text(
            text = "EXTREME RACING MODULES",
            color = PureRed,
            fontFamily = condensedBoldFontFamily,
            fontSize = 24.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "MULTIPLE CAMPAIGNS FOR DOMINATING STREETS",
            color = ChromeSilver,
            fontFamily = condensedFontFamily,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Grid implementation
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (row in 0..1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (col in 0..1) {
                        val mode = modes[row * 2 + col]
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(140.dp)
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardGray)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        // Diagonal subtle corner gradient back
                                        drawRect(
                                            brush = Brush.radialGradient(
                                                colors = listOf(mode.gradientColors.first().copy(alpha = 0.15f), Color.Transparent),
                                                center = Offset(size.width, 0f),
                                                radius = size.width / 1.1f
                                            )
                                        )
                                    }
                                    .padding(16.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = mode.icon,
                                        contentDescription = mode.title,
                                        tint = mode.gradientColors.first(),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .padding(end = 8.dp)
                                    )
                                    Column {
                                        Text(
                                            text = mode.title,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontFamily = condensedBoldFontFamily,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = mode.description,
                                            color = ChromeSilver,
                                            fontSize = 12.sp,
                                            fontFamily = condensedFontFamily,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// WORLD TRACKS SECTION WITH DYNAMIC CANVAS TRACK MAP DRAWINGS
// -------------------------------------------------------------
data class TrackDetails(
    val title: String,
    val flavorText: String,
    val difficulty: String,
    val badgeColor: Color
)

@Composable
fun WorldTracksSection() {
    val tracks = remember {
        listOf(
            TrackDetails("MUMBAI UNDERGROUND", "Monsoon-soaked streets, police lights, and chaos.", "EXTREME", PureRed),
            TrackDetails("TOKYO NEON CIRCUIT", "Blade Runner meets Formula One.", "HARD", BurntOrange),
            TrackDetails("DUBAI DESERT STORM", "500 kilometers of open sand and pure velocity.", "HARD", BurntOrange),
            TrackDetails("MONACO HARBOUR LOOP", "Old money, tight corners, zero forgiveness.", "EXPERT", PureRed),
            TrackDetails("LONDON MIDNIGHT RUN", "Fog, rain, and the roar of an engine through empty streets.", "MEDIUM", RacingGold),
            TrackDetails("LA CANYON BLAZE", "Cliffside drops and 200 MPH straights under California sun.", "HARD", BurntOrange)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        Text(
            text = "GLOBAL RACING STAGES",
            color = PureRed,
            fontFamily = condensedBoldFontFamily,
            fontSize = 24.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "CHALLENGE YOUR SKILLS ON RADICALLY INDEPENDENT TRACKS",
            color = ChromeSilver,
            fontFamily = condensedFontFamily,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 3-columns layout mapping
        for (rowIndex in 0..1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                for (colIndex in 0..2) {
                    val trackIndex = rowIndex * 3 + colIndex
                    val track = tracks[trackIndex]

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp),
                        colors = CardDefaults.cardColors(containerColor = CardGray)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            // Custom illustrated dynamic racing Canvas map in the bottom-right corner!
                            TrackShapeCanvas(
                                trackIndex = trackIndex,
                                modifier = Modifier
                                    .size(70.dp)
                                    .align(Alignment.BottomEnd)
                                    .alpha(0.85f)
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Difficulty badge
                                Badge(
                                    containerColor = track.badgeColor.copy(alpha = 0.2f),
                                    contentColor = track.badgeColor
                                ) {
                                    Text(
                                        text = track.difficulty,
                                        fontFamily = condensedBoldFontFamily,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontFamily = condensedBoldFontFamily,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = track.flavorText,
                                    color = ChromeSilver,
                                    fontFamily = condensedFontFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackShapeCanvas(trackIndex: Int, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "raceDot")
    val dotOffsetProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500 + trackIndex * 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "r_dot"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path()

        when (trackIndex) {
            0 -> { // Mumbai Circuit
                path.moveTo(w * 0.1f, h * 0.1f)
                path.lineTo(w * 0.9f, h * 0.1f)
                path.lineTo(w * 0.9f, h * 0.6f)
                path.lineTo(w * 0.5f, h * 0.6f)
                path.lineTo(w * 0.5f, h * 0.9f)
                path.lineTo(w * 0.1f, h * 0.9f)
                path.close()
            }
            1 -> { // Tokyo loop
                path.moveTo(w * 0.1f, h * 0.3f)
                path.cubicTo(w * 0.3f, h * 0.1f, w * 0.7f, h * 0.9f, w * 0.9f, h * 0.7f)
                path.cubicTo(w * 0.9f, h * 0.3f, w * 0.3f, h * 0.9f, w * 0.1f, h * 0.3f)
                path.close()
            }
            2 -> { // Dubai Desert oval
                path.moveTo(w * 0.2f, h * 0.2f)
                path.quadraticTo(w * 0.8f, h * 0.1f, w * 0.9f, h * 0.5f)
                path.quadraticTo(w * 0.8f, h * 0.9f, w * 0.2f, h * 0.8f)
                path.quadraticTo(w * 0.05f, h * 0.5f, w * 0.2f, h * 0.2f)
                path.close()
            }
            3 -> { // Monaco Formula spec corners
                path.moveTo(w * 0.1f, h * 0.2f)
                path.lineTo(w * 0.8f, h * 0.2f)
                path.lineTo(w * 0.8f, h * 0.4f)
                path.lineTo(w * 0.5f, h * 0.4f)
                path.lineTo(w * 0.6f, h * 0.7f)
                path.lineTo(w * 0.3f, h * 0.8f)
                path.lineTo(w * 0.1f, h * 0.5f)
                path.close()
            }
            4 -> { // London rectangular map
                path.moveTo(w * 0.15f, h * 0.15f)
                path.lineTo(w * 0.85f, h * 0.15f)
                path.lineTo(w * 0.85f, h * 0.85f)
                path.lineTo(w * 0.45f, h * 0.85f)
                path.lineTo(w * 0.45f, h * 0.45f)
                path.lineTo(w * 0.15f, h * 0.45f)
                path.close()
            }
            else -> { // LA Mountain road loop
                path.moveTo(w * 0.1f, h * 0.5f)
                path.cubicTo(w * 0.3f, h * 0.2f, w * 0.4f, h * 0.8f, w * 0.6f, h * 0.3f)
                path.cubicTo(w * 0.8f, h * 0.1f, w * 0.9f, h * 0.6f, w * 0.9f, h * 0.85f)
                path.lineTo(w * 0.1f, h * 0.85f)
                path.close()
            }
        }

        // Draw grey static track line
        drawPath(
            path = path,
            color = ChromeSilver.copy(alpha = 0.3f),
            style = Stroke(width = 4f, join = StrokeJoin.Round)
        )

        // Draw dynamic live racer dot
        val measure = android.graphics.PathMeasure(path.asAndroidPath(), false)
        val pos = FloatArray(2)
        measure.getPosTan(measure.length * dotOffsetProgress, pos, null)

        drawCircle(
            color = PureRed,
            radius = 8f,
            center = Offset(pos[0], pos[1])
        )
        drawCircle(
            color = PureRed.copy(alpha = 0.3f),
            radius = 16f,
            center = Offset(pos[0], pos[1])
        )
    }
}

// -------------------------------------------------------------
// MULTIPLAYER SECTION LOBBY SYSTEM
// -------------------------------------------------------------
data class LobbyRacer(
    val moniker: String,
    val ready: Boolean,
    val machine: String,
    val avatarInitial: String,
    val avatarBg: Color
)

@Composable
fun MultiplayerSection(
    userJoinedLobby: Boolean,
    enteredLobbyName: String,
    onLobbyNameChange: (String) -> Unit,
    onJoinClick: () -> Unit
) {
    val racers = remember(userJoinedLobby, enteredLobbyName) {
        val base = mutableListOf(
            LobbyRacer("NIKESH_07", true, "NIKESH SPECIAL ONE", "N", RacingGold),
            LobbyRacer("SpeedKing_99", true, "STORM BLADE", "S", ElectricBlue),
            LobbyRacer("DriftMaster", true, "SHADOW GHOST", "D", HighContrastTeal),
            LobbyRacer("TurboQueen", true, "SPECIAL ONE", "T", PureRed),
            LobbyRacer("GhostRider_X", false, "SHADOW GHOST", "G", Color.Magenta),
            LobbyRacer("NightFury", true, "GOLDEN FURY", "M", BurntOrange),
            LobbyRacer("ApexHunter", false, "STORM BLADE", "A", Color.Gray),
            LobbyRacer("ZeroLag", true, "SPECIAL ONE", "Z", Color.White)
        )
        if (userJoinedLobby && enteredLobbyName.isNotBlank()) {
            base.add(0, LobbyRacer(enteredLobbyName, true, "PLAYER HYPERCAR", "ME", PureRed))
        }
        base
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkNavy)
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left Half content details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = "24 PLAYERS. ONE TRACK. NO MERCY.",
                    color = Color.White,
                    fontFamily = condensedBoldFontFamily,
                    fontSize = 24.sp,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Real-time global matchmaking. Sub-3-second lobby join. Cross-platform between all devices. Weekly tournament events with exclusive car rewards only Nikesh players can unlock.",
                    color = ChromeSilver,
                    fontFamily = condensedFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // lat, cross-platform and ranked features
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FeatureIconBadge(Icons.Default.FlashOn, "Low Latency")
                    FeatureIconBadge(Icons.Default.Public, "Cross-Play")
                    FeatureIconBadge(Icons.Default.EmojiEvents, "Ranked Leagues")
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!userJoinedLobby) {
                    TextField(
                        value = enteredLobbyName,
                        onValueChange = onLobbyNameChange,
                        placeholder = { Text("ENTER MONIKER (e.g. NIKESH_FAN)") },
                        modifier = Modifier.fillMaxWidth().testTag("lobby_moniker_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CardGray,
                            unfocusedContainerColor = CardGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = ChromeSilver,
                            focusedIndicatorColor = PureRed
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onJoinClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PureRed),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().testTag("join_lobby_button")
                    ) {
                        Text(
                            "JOIN SIMULATED MATCH LOBBY",
                            fontFamily = condensedBoldFontFamily,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PureRed.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, PureRed),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            "YOU HAVE CONNECTED. SEARCHING MATCH IN REGION: IN-WEST-1...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = condensedBoldFontFamily,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Right Half: Match Lobby Simulator
            Card(
                modifier = Modifier
                    .weight(1.1f)
                    .height(340.dp)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CardGray)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Text(
                        "LOBBY 22/24 (PING: 14MS)",
                        color = HighContrastTeal,
                        fontFamily = condensedBoldFontFamily,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            racers.forEach { r ->
                                val isLeader = r.moniker == "NIKESH_07"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isLeader) RacingGold.copy(alpha = 0.08f) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isLeader) RacingGold.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f)
                                            ),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Pixel avatar
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(r.avatarBg, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            r.avatarInitial,
                                            color = Color.Black,
                                            fontSize = 12.sp,
                                            fontFamily = condensedBoldFontFamily
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            r.moniker,
                                            color = if (isLeader) RacingGold else Color.White,
                                            fontSize = 13.sp,
                                            fontFamily = condensedBoldFontFamily
                                        )
                                        Text(
                                            r.machine,
                                            color = ChromeSilver,
                                            fontSize = 11.sp,
                                            fontFamily = condensedFontFamily
                                        )
                                    }

                                    // Match indicator ready/not ready
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(if (r.ready) HighContrastTeal else PureRed, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (r.ready) "READY" else "WAITING",
                                            color = if (r.ready) HighContrastTeal else PureRed,
                                            fontFamily = condensedBoldFontFamily,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureIconBadge(icon: ImageVector, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(CardGray, RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), RoundedCornerShape(8.dp))
            .padding(8.dp)
            .width(80.dp)
    ) {
        Icon(icon, contentDescription = null, tint = PureRed, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text, color = Color.White, fontSize = 10.sp, fontFamily = condensedFontFamily, textAlign = TextAlign.Center)
    }
}

// -------------------------------------------------------------
// GARAGE & CUSTOMIZATION SECTION (COMPREHENSIVE SYSTEMS)
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GarageSection() {
    var paintIndex by remember { mutableStateOf(1) } // Default Candy Red
    var rimIndex by remember { mutableStateOf(0) } // Razor
    var bodyKitIndex by remember { mutableStateOf(0) } // Stock
    var liveryIndex by remember { mutableStateOf(0) } // Clean

    val paintColors = remember {
        listOf(
            Color(0xFF111111), // Matte Black
            Color(0xFFFF1A1A), // Candy Red
            Color(0xFFF5F5F7), // Pearl White
            Color(0xFF00C8FF), // Electric Blue
            Color(0xFFFFD600), // Chrome Gold
            Color(0xFF39FF14), // Toxic Green
            Color(0xFFFF5F00), // Sunset Orange
            Color(0xFF8F00FF), // Deep Purple
            Color(0xFF333333), // Carbon Gray
            Color(0xFFFFEA00), // Cyber Yellow
            Color(0xFF008080), // Midnight Teal
            Color(0xFFE0E0E0)  // Silver Dust
        )
    }
    val colorNames = listOf(
        "Matte Black", "Candy Red", "Pearl White", "Electric Blue",
        "Chrome Gold", "Toxic Green", "Sunset Orange", "Deep Purple",
        "Carbon Gray", "Cyber Yellow", "Midnight Teal", "Silver Dust"
    )

    val rimsList = listOf("RAZOR", "BLADE", "CROWN", "GHOST")
    val bodyKits = listOf("STOCK", "WIDE BODY", "RACE SPEC")
    val liveries = listOf("CLEAN", "RACING STRIPES", "DIGITAL CAMO", "FLAMES")

    // Interactive level upgrade parameters (Engine, Turbo, Brakes, Suspension, Nitro)
    val upgrades = remember {
        mutableStateMapOf(
            "ENGINE" to 6,
            "TURBO" to 7,
            "BRAKES" to 5,
            "SUSPENSION" to 4,
            "NITRO" to 8
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        Text(
            text = "GARAGE & VEHICLE CUSTOM MODULE",
            color = PureRed,
            fontFamily = condensedBoldFontFamily,
            fontSize = 24.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "CREATE A RACING IDENTITY. MOD EVERY ASPECT.",
            color = ChromeSilver,
            fontFamily = condensedFontFamily,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left Half panels: Paint Swatches, Rims and Upgrades
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .padding(end = 16.dp)
            ) {
                // Paint swatches block
                Text("PAINT SHADE: ${colorNames[paintIndex].uppercase()}", color = Color.White, fontFamily = condensedBoldFontFamily, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    paintColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, RoundedCornerShape(4.dp))
                                .border(
                                    BorderStroke(
                                        if (paintIndex == index) 2.dp else 1.dp,
                                        if (paintIndex == index) PureRed else Color.White.copy(alpha = 0.2f)
                                    ),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { paintIndex = index }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Rim selector grid row
                Text("RIM STYLE", color = Color.White, fontFamily = condensedBoldFontFamily, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rimsList.forEachIndexed { index, name ->
                        val active = index == rimIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    BorderStroke(1.dp, if (active) PureRed else Color.White.copy(alpha = 0.2f)),
                                    RoundedCornerShape(4.dp)
                                )
                                .background(if (active) PureRed.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { rimIndex = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, color = Color.White, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Performance Upgrade interactive sliders
                Text("TAP GRAPH TO UPGRADE PERFORMANCE (+)", color = Color.White, fontFamily = condensedBoldFontFamily, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                upgrades.forEach { (category, level) ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(category, color = ChromeSilver, fontSize = 11.sp, fontFamily = condensedFontFamily)
                            Text("LEVEL $level/10", color = PureRed, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
                        }
                        // Clickable performance bars blocks
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clickable {
                                    val next = if (level >= 10) 1 else level + 1
                                    upgrades[category] = next
                                },
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            for (step in 1..10) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                            if (step <= level) PureRed else Color.Black.copy(alpha = 0.5f)
                                        )
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
                                )
                            }
                        }
                    }
                }
            }

            // Right Half: Big rotating showcase platform
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                // Wide Body kits & Liveries selection
                Text("AERO BODY KIT", color = Color.White, fontFamily = condensedBoldFontFamily, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bodyKits.forEachIndexed { idx, name ->
                        val cur = idx == bodyKitIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    BorderStroke(1.dp, if (cur) PureRed else Color.White.copy(alpha = 0.2f)),
                                    RoundedCornerShape(4.dp)
                                )
                                .background(if (cur) PureRed.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { bodyKitIndex = idx }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, color = Color.White, fontSize = 10.sp, fontFamily = condensedBoldFontFamily)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("LIVERY DESIGNS", color = Color.White, fontFamily = condensedBoldFontFamily, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    liveries.forEachIndexed { idx, name ->
                        val cur = idx == liveryIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    BorderStroke(1.dp, if (cur) PureRed else Color.White.copy(alpha = 0.2f)),
                                    RoundedCornerShape(4.dp)
                                )
                                .background(if (cur) PureRed.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { liveryIndex = idx }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, color = Color.White, fontSize = 10.sp, fontFamily = condensedBoldFontFamily)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Wireframe responsive simulated car render based on select paint configuration and kits!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.Black)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Spotlights Canvas
                    val infiniteTransition = rememberInfiniteTransition(label = "")
                    val spinPhase by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(6000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = ""
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Platform shadow draw
                        drawOval(
                            color = Color(0xFF1E1E1E),
                            topLeft = Offset(w * 0.15f, h * 0.7f),
                            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.2f)
                        )

                        // Light reflections
                        rotate(degrees = spinPhase, pivot = Offset(w / 2, h / 2)) {
                            drawLine(
                                color = paintColors[paintIndex].copy(alpha = 0.35f),
                                start = Offset(w / 2, 0f),
                                end = Offset(w / 2, h),
                                strokeWidth = 6f
                            )
                        }
                    }

                    // Simulated Supercar dynamic silhouette
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_supercar),
                            contentDescription = "Showcase Platform Render",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(130.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                paintColors[paintIndex].copy(alpha = 0.75f),
                                blendMode = androidx.compose.ui.graphics.BlendMode.Color
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.8f))
                            .border(BorderStroke(1.dp, PureRed), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${bodyKits[bodyKitIndex]} SPEC",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontFamily = condensedBoldFontFamily
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Visual Slogan text
        Text(
            text = "OVER 10 MILLION COMBINATIONS. YOUR CAR. YOUR IDENTITY.",
            color = PureRed,
            fontFamily = condensedBoldFontFamily,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// -------------------------------------------------------------
// LEADERBOARD SECTION (STEEL LIVE COUNTDOWN TIMER TABLE)
// -------------------------------------------------------------
data class RankDetails(
    val rank: Int,
    val racer: String,
    val country: String,
    val car: String,
    val lapTime: String,
    val points: Int,
    val crown: Boolean = false
)

@Composable
fun LeaderboardSection() {
    var timerCountdown by remember { mutableIntStateOf(60) }

    // Increment points and change minor speed time dynamically for maximum realism
    val liveScores = remember {
        mutableStateListOf(
            RankDetails(1, "NIKESH_07", "India", "NIKESH SPECIAL ONE", "1:24.337", 99870, true),
            RankDetails(2, "SpeedKing_99", "Japan", "STORM BLADE", "1:25.112", 94220),
            RankDetails(3, "GhostRider_X", "USA", "SHADOW GHOST", "1:25.889", 91005),
            RankDetails(4, "TurboQueen", "Brazil", "NIKESH SPECIAL ONE", "1:26.411", 88720),
            RankDetails(5, "NightFury", "UK", "GOLDEN FURY", "1:27.124", 86450),
            RankDetails(6, "Octane_Buster", "Germany", "STORM BLADE", "1:27.810", 83910),
            RankDetails(7, "BrakeLate_X", "Australia", "SHADOW GHOST", "1:28.005", 81400),
            RankDetails(8, "MumbaiDrift", "India", "NIKESH SPECIAL ONE", "1:28.322", 79040),
            RankDetails(9, "TokyoBullet", "Japan", "STORM BLADE", "1:28.995", 76110),
            RankDetails(10, "ApexHunter", "Canada", "GOLDEN FURY", "1:29.412", 74015)
        )
    }

    // Timer loop countdown
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (timerCountdown > 1) {
                timerCountdown--
            } else {
                timerCountdown = 60
                // Slightly randomize points to create life
                val changeIndex = (1..9).random()
                val current = liveScores[changeIndex]
                liveScores[changeIndex] = current.copy(
                    points = current.points + (10..100).random(),
                    lapTime = "1:2${(5..8).random()}.${(100..999).random()}"
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PureBlack)
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        // Headers
        Text(
            text = "GLOBAL RACING LEADERBOARD",
            color = PureRed,
            fontFamily = condensedBoldFontFamily,
            fontSize = 24.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "REALTIME PLAYER STATUS WORLDWIDE",
            color = ChromeSilver,
            fontFamily = condensedFontFamily,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Stylyzed Top ranking Table Frame
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = CardGray)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Column headers row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RANK", color = ChromeSilver, fontSize = 10.sp, fontFamily = condensedBoldFontFamily, modifier = Modifier.width(36.dp))
                    Text("PLAYER", color = ChromeSilver, fontSize = 10.sp, fontFamily = condensedBoldFontFamily, modifier = Modifier.weight(1.3f))
                    Text("COUNTRY", color = ChromeSilver, fontSize = 10.sp, fontFamily = condensedBoldFontFamily, modifier = Modifier.weight(1f))
                    Text("CAR", color = ChromeSilver, fontSize = 10.sp, fontFamily = condensedBoldFontFamily, modifier = Modifier.weight(1.5f))
                    Text("BEST LAP", color = ChromeSilver, fontSize = 10.sp, fontFamily = condensedBoldFontFamily, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text("POINTS", color = ChromeSilver, fontSize = 10.sp, fontFamily = condensedBoldFontFamily, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                }
                Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

                // List implementation
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    liveScores.forEach { r ->
                        val isRank1 = r.rank == 1
                        val isRank2 = r.rank == 2
                        val isRank3 = r.rank == 3

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isRank1) RacingGold.copy(alpha = 0.08f)
                                    else if (isRank2) Color.White.copy(alpha = 0.02f)
                                    else if (isRank3) BurntOrange.copy(alpha = 0.02f)
                                    else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isRank1) RacingGold.copy(alpha = 0.3f) else Color.Transparent
                                    ),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // RANK Column
                            Row(
                                modifier = Modifier.width(36.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (r.crown) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = "Leader",
                                        tint = RacingGold,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                                Text(
                                    text = r.rank.toString(),
                                    color = if (isRank1) RacingGold else if (isRank2) ChromeSilver else if (isRank3) BurntOrange else Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = condensedBoldFontFamily
                                )
                            }

                            // PLAYER Column
                            Text(
                                text = r.racer,
                                color = if (isRank1) RacingGold else Color.White,
                                fontSize = 13.sp,
                                fontFamily = condensedBoldFontFamily,
                                modifier = Modifier.weight(1.3f)
                            )

                            // COUNTRY
                            Text(
                                text = r.country,
                                color = ChromeSilver,
                                fontSize = 12.sp,
                                fontFamily = condensedFontFamily,
                                modifier = Modifier.weight(1f)
                            )

                            // CAR
                            Text(
                                text = r.car,
                                color = ChromeSilver,
                                fontSize = 12.sp,
                                fontFamily = condensedFontFamily,
                                modifier = Modifier.weight(1.5f),
                                maxLines = 1
                            )

                            // BEST LAP
                            Text(
                                text = r.lapTime,
                                color = if (isRank1) RacingGold else Color.White,
                                fontSize = 12.sp,
                                fontFamily = condensedBoldFontFamily,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )

                            // POINTS Total
                            Text(
                                text = "%,d".format(r.points),
                                color = if (isRank1) RacingGold else Color.White,
                                fontSize = 12.sp,
                                fontFamily = condensedBoldFontFamily,
                                modifier = Modifier.weight(1.2f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timer Bottom Text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = PureRed, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "UPDATED EVERY 60 SECONDS. CAN YOU REACH #1?",
                    color = ChromeSilver,
                    fontSize = 11.sp,
                    fontFamily = condensedBoldFontFamily,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "NEXT REFRESH IN: ${timerCountdown}S",
                color = PureRed,
                fontSize = 11.sp,
                fontFamily = condensedBoldFontFamily
            )
        }
    }
}

// -------------------------------------------------------------
// SEASON PASS CONTENT TIER CARDS
// -------------------------------------------------------------
@Composable
fun SeasonPassSection(
    onSubscribeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkNavy)
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        Text(
            text = "NIKESH SEASON PASS ACCESS",
            color = PureRed,
            fontFamily = condensedBoldFontFamily,
            fontSize = 24.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "SPEED TO NEW REWARD TIERS AND TROPHY LEVEL DESIGNS",
            color = ChromeSilver,
            fontFamily = condensedFontFamily,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Card: FREE TIER card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CardGray)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text("FREE SPEED TIER", color = ChromeSilver, fontSize = 16.sp, fontFamily = condensedBoldFontFamily)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Includes basic car wraps, minor gold deposits & currency boosts.", color = ChromeSilver, fontSize = 11.sp, fontFamily = condensedFontFamily)

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Rewards listed vertical
                    RewardPointItem("REWARD 1: basic carbon rim", true)
                    RewardPointItem("REWARD 10: sunset orange skin spec", true)
                    RewardPointItem("REWARD 20: 50,000 gaming credits", true)
                    RewardPointItem("REWARD 30: basic flame custom decal", true)
                }
            }

            // Right Card: NIKESH PRIME PREMIUM TIER card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(BorderStroke(2.dp, RacingGold), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CardGray)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Golden corner highlight
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(RacingGold)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("PREMIUM", color = Color.Black, fontSize = 9.sp, fontFamily = condensedBoldFontFamily)
                    }

                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text("NIKESH PRIME ACCESS", color = RacingGold, fontSize = 18.sp, fontFamily = condensedBoldFontFamily)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Instant legendary supercar drops, multi-kit unlocks, high roller tournaments & Apex Black spec.", color = ChromeSilver, fontSize = 11.sp, fontFamily = condensedFontFamily)

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Premium rewards
                        RewardPointItem("INSTANT: 3 x EXCLUSIVE racing hypercars", false)
                        RewardPointItem("TIER 40: Apex Black ultra-rare edition", false)
                        RewardPointItem("TIER 80: NIKESH FLAME SUIT model outfit", false)
                        RewardPointItem("TIER 100: 12 legendary neon body designs", false)

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onSubscribeClick,
                            colors = ButtonDefaults.buttonColors(containerColor = RacingGold),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prime_subscribe_button")
                        ) {
                            Text(
                                "GO PRIME — ₹499/MONTH",
                                color = Color.Black,
                                fontFamily = condensedBoldFontFamily,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Cancel anytime. PC, Mobile, Console cross-platform active.",
                            color = ChromeSilver,
                            fontSize = 10.sp,
                            fontFamily = condensedFontFamily,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RewardPointItem(text: String, isFree: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (isFree) Icons.Default.CheckCircle else Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = if (isFree) ChromeSilver else RacingGold,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = Color.White, fontSize = 12.sp, fontFamily = condensedFontFamily)
    }
}

// -------------------------------------------------------------
// REVIEW QUOTES CARDS (MAGAZINE COVER REVIEWS STRUCTURES)
// -------------------------------------------------------------
@Composable
fun ReviewQuotesSection() {
    val quotes = listOf(
        Pair("GamePulse India", "“Nikesh Racer is the most authentic and electrifying racing experience ever made in India. 10 out of 10.”"),
        Pair("SpeedWorld Magazine", "“The physics engine alone makes this the benchmark for all future racing games. Nothing comes close.”"),
        Pair("TurboGamer", "“NIKESH_07 is not just a character — he is a legend. This game made us believe.”")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        Text(
            text = "CRITICAL APPLAUSE",
            color = PureRed,
            fontFamily = condensedBoldFontFamily,
            fontSize = 24.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "RECOGNIZED BY THE WORLD CHANNELS & RACER PUBLICATIONS",
            color = ChromeSilver,
            fontFamily = condensedFontFamily,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            quotes.forEach { (reviewer, quote) ->
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardGray)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = reviewer.uppercase(),
                            color = PureRed,
                            fontSize = 13.sp,
                            fontFamily = condensedBoldFontFamily,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = quote,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CALL TO ACTION SECTION (HERO BOTTOM ACCENTS IN RED)
// -------------------------------------------------------------
@Composable
fun CallToActionSection() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(getSlashCutShape(40f, 0f))
            .background(PureRed)
            .padding(top = 64.dp, bottom = 48.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "THE RACE STARTS NOW.",
                color = Color.Black,
                fontSize = 36.sp,
                fontFamily = condensedBoldFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "FREE TO DOWNLOAD • FREE TO PLAY • IMPOSSIBLE TO STOP",
                color = Color.Black.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontFamily = condensedBoldFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { Toast.makeText(context, "Initialisation file download: R-9.10MB. Starting Apex System Installer!", Toast.LENGTH_LONG).show() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("download_installer_btn")
                ) {
                    Text(
                        "DOWNLOAD FREE",
                        color = PureRed,
                        fontFamily = condensedBoldFontFamily,
                        letterSpacing = 1.sp
                    )
                }

                OutlinedButton(
                    onClick = { Toast.makeText(context, "Streaming high-definition Apex Trailer promo sequence...", Toast.LENGTH_SHORT).show() },
                    border = BorderStroke(2.dp, Color.Black),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("watch_gameplay_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "WATCH GAMEPLAY",
                            color = Color.Black,
                            fontFamily = condensedBoldFontFamily,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Console and Store logos
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(0.85f)
            ) {
                Text("PC", color = Color.Black, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
                Text("PLAYSTATION", color = Color.Black, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
                Text("XBOX", color = Color.Black, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
                Text("SWITCH", color = Color.Black, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
                Text("MOBILE", color = Color.Black, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
            }
        }
    }
}

// -------------------------------------------------------------
// FOOTER
// -------------------------------------------------------------
@Composable
fun FooterSection(
    onNavigateSection: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PureBlack)
            .padding(top = 48.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text("NIKESH", color = Color.White, fontSize = 20.sp, fontFamily = condensedBoldFontFamily)
                Text("APEX PREDATOR", color = PureRed, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf("HOME", "CARS", "TRACKS", "GARAGE", "LEADERBOARD").forEach { tag ->
                    Text(
                        tag,
                        color = ChromeSilver,
                        fontSize = 11.sp,
                        fontFamily = condensedBoldFontFamily,
                        modifier = Modifier.clickable { onNavigateSection(tag) }
                    )
                }
            }

            // Mock Social items
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("YT", color = PureRed, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
                Text("INSTA", color = PureRed, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
                Text("X", color = PureRed, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
                Text("DISCORD", color = PureRed, fontSize = 11.sp, fontFamily = condensedBoldFontFamily)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Divider(color = Color.White.copy(alpha = 0.05f))
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "© 2026 NikeshStudios. All Rights Reserved. Made with fire in Mumbai, India. 🔥",
            color = ChromeSilver,
            fontSize = 11.sp,
            fontFamily = condensedFontFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// -------------------------------------------------------------
// PRIME SUB DIALOGUE LAYOUT
// -------------------------------------------------------------
@Composable
fun PrimePromotionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(2.dp, RacingGold), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = RacingGold,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "UPGRADE TO PRIME ACCESS",
                    color = RacingGold,
                    fontSize = 20.sp,
                    fontFamily = condensedBoldFontFamily,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Gain access to the extreme championship circuits, private VIP match lobby instances, and double gaming gold boosts immediately.",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = condensedFontFamily,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = RacingGold),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("START MEMBERSHIP", color = Color.Black, fontFamily = condensedBoldFontFamily)
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = ChromeSilver, fontFamily = condensedBoldFontFamily)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// LEGACY GREETING COMPOSABLE FOR PRESERVING TEST ASSERTS
// -------------------------------------------------------------
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier.testTag("legacy_greeting_text"),
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        color = Color.LightGray
    )
}
