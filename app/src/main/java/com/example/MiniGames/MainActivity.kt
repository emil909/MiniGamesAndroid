package com.example.MiniGames

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import com.example.MiniGames.ui.theme.MyApplicationTheme
import kotlin.random.Random
import kotlinx.coroutines.isActive
import kotlin.math.min
import android.content.Context

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var screen by remember { mutableStateOf(Screen.Menu) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (screen) {
                        Screen.Menu -> MainMenu(
                            modifier = Modifier.padding(innerPadding),
                            onStart = { screen = Screen.Game }
                        )
                        Screen.Game -> FlappyBirdGame(
                            modifier = Modifier.padding(innerPadding),
                            onExitToMenu = { screen = Screen.Menu }
                        )
                    }
                }
            }
        }
    }
}

private enum class Screen { Menu, Game }

@Composable
fun FlappyBirdGame(modifier: Modifier = Modifier, onExitToMenu: () -> Unit = {}) {
    // World/state
    var size by remember { mutableStateOf(IntSize.Zero) }
    var birdY by remember { mutableStateOf(0f) }
    var birdVel by remember { mutableStateOf(0f) }
    val pipes = remember { mutableStateListOf<Pipe>() }
    var score by remember { mutableStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("flappy_prefs", Context.MODE_PRIVATE) }
    var highScore by remember { mutableStateOf(prefs.getInt("high_score", 0)) }

    // Constants (in px/sec or proportions, calibrated in update loop)
    val gravity = 2200f // px/s^2
    val flapVelocity = 1300f // px/s upward
    val gapHeightFraction = 0.26f
    val pipeWidthFraction = 0.14f
    val birdXFraction = 0.28f
    val birdRadiusFraction = 0.035f
    var pipeSpeed = 270f // px/s left
    var pipeSpacing = 680f // px between pipe starts
    var current = 0;

    fun reset() {
        if (size.width == 0 || size.height == 0) return
        birdY = size.height * 0.5f
        birdVel = 0f
        score = 0
        gameOver = false
        // Init pipes
        pipes.clear()
        var x = size.width * 1.2f
        repeat(3) {
            pipes.add(
                Pipe(
                    x = x,
                    gapY = Random.nextFloat() * (size.height * 0.6f) + size.height * 0.2f
                )
            )
            x += pipeSpacing
        }
    }

    // Initialize when size known
    LaunchedEffect(size) {
        if (size.width > 0 && size.height > 0) reset()
    }

    // Game loop
    LaunchedEffect(gameOver, size) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        var lastTime: Long? = null
        while (!gameOver && isActive) {
            val now = withFrameNanos { it }
            var dt = if (lastTime == null) 0f else (now - lastTime!!) / 1_000_000_000f
            // Clamp dt to avoid big jumps if a frame stalls
            if (dt > 1f / 30f) dt = 1f / 30f
            lastTime = now

            // Physics
            birdVel += gravity * dt
            birdY += birdVel * dt

            // Move pipes
            if (score %2 == 0 && current == score) // Increase speed every 2 points
            {
                current = score + 2
                pipeSpeed += 100f
                pipeSpacing += 100f
            }
            for (i in pipes.indices) {
                pipes[i].x -= pipeSpeed * dt
            }

            // Recycle pipes and scoring
            val birdX = size.width * birdXFraction
            val pipeWidth = size.width * pipeWidthFraction
            val gapH = size.height * gapHeightFraction
            if (pipes.isNotEmpty() && pipes.first().x + pipeWidth < 0f) {
                val lastX = pipes.last().x
                pipes.removeAt(0)
                pipes.add(
                    Pipe(
                        x = lastX + pipeSpacing,
                        gapY = Random.nextFloat() * (size.height * 0.6f) + size.height * 0.2f
                    )
                )
            }

            // Score when passing center of pipe
            pipes.forEach { p ->
                if (!p.scored && birdX > p.x + pipeWidth) {
                    p.scored = true
                    score += 1
                    if (score > highScore) {
                        highScore = score
                        prefs.edit().putInt("high_score", highScore).apply()
                    }
                }
            }

            // Collision
            val birdR = min(size.width, size.height).toFloat() * birdRadiusFraction
            val bx = birdX
            val bY = birdY
            pipes.forEach { p ->
                val left = p.x
                val right = p.x + pipeWidth
                val gapTop = p.gapY - gapH / 2f
                val gapBottom = p.gapY + gapH / 2f

                val hitX = bx + birdR > left && bx - birdR < right
                val hitYTop = bY - birdR < gapTop
                val hitYBottom = bY + birdR > gapBottom
                if (hitX && (hitYTop || hitYBottom)) gameOver = true
            }

            if (bY - birdR < 0f || bY + birdR > size.height) gameOver = true

            // Frame pacing handled by withFrameNanos
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures {
                    if (gameOver) {
                        reset()
                    } else {
                        birdVel = -flapVelocity
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width == 0 || size.height == 0) return@Canvas
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            val birdR = min(width, height) * birdRadiusFraction
            val birdX = width * birdXFraction
            val pipeWidth = width * pipeWidthFraction
            val gapH = height * gapHeightFraction

            // Background
            drawRect(color = Color(0xFF87CEEB)) // sky

            // Ground strip
            drawRect(
                color = Color(0xFFDEB887),
                topLeft = Offset(0f, height - 40f),
                size = androidx.compose.ui.geometry.Size(width, 40f)
            )

            // Pipes
            pipes.forEach { p ->
                val left = p.x
                val right = p.x + pipeWidth
                val gapTop = p.gapY - gapH / 2f
                val gapBottom = p.gapY + gapH / 2f
                // Top pipe
                drawRect(
                    color = Color(0xFF2E8B57),
                    topLeft = Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(right - left, gapTop)
                )
                // Bottom pipe
                drawRect(
                    color = Color(0xFF2E8B57),
                    topLeft = Offset(left, gapBottom),
                    size = androidx.compose.ui.geometry.Size(right - left, height - gapBottom)
                )
            }

            // Bird
            drawCircle(
                color = Color.Yellow,
                radius = birdR,
                center = Offset(birdX, birdY)
            )
        }

        // UI overlay: score and game over text
        Text(
            text = if (gameOver) "Game Over! Tap to restart\nScore: $score  High: $highScore" else "Score: $score  High: $highScore",
            color = Color.White,
            modifier = Modifier
                .padding(16.dp)
        )

        // Top-right Menu button
        TextButton(
            onClick = onExitToMenu,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) { Text("Menu") }
    }
}

private data class Pipe(
    var x: Float,
    var gapY: Float,
    var scored: Boolean = false
)

@Preview(showBackground = true)
@Composable
fun GamePreview() {
    MyApplicationTheme {
        FlappyBirdGame()
    }
}

@Preview(showBackground = true)
@Composable
fun MenuPreview() {
    MyApplicationTheme {
        MainMenu()
    }
}