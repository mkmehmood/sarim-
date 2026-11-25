package com.gullzubair.sarim.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.gullzubair.sarim.R
import com.gullzubair.sarim.data.firebase.attemptAutoSignIn
import com.gullzubair.sarim.data.firebase.onEmailPasswordSignInClicked
import com.gullzubair.sarim.data.firebase.onGoogleSignInClicked
import com.gullzubair.sarim.ui.theme.CinzelFont
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.JetBrainsMonoFont
import com.gullzubair.sarim.ui.theme.PlayfairFont
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SplashMintHalo = Color(0xFFD6F3EA)
private val SplashEmerald = Color(0xFF0A8754)
private val SplashSunGold = Color(0xFFF59E0B)
private val SplashTrackGray = Color(0xFF94A3B8)
private val InkPrimary = Color(0xFF0F172A)
private val InkSecondary = Color(0xFF475569)

private val SPLASH_QUOTES = listOf(
    "The details are not the details. They make the design." to "Charles Eames",
    "Perfection is achieved not when there is nothing more to add, but when there is nothing left to take away." to "Antoine de Saint-Exupéry",
    "Price is what you pay. Value is what you get." to "Warren Buffett",
    "It is not the strongest of the species that survives, but the one most adaptable to change." to "Leon C. Megginson",
    "An investment in knowledge pays the best interest." to "Benjamin Franklin",
    "What gets measured gets managed." to "Peter Drucker",
    "The purpose of a business is to create a customer who creates customers." to "Shiv Singh",
    "Simplicity is the ultimate sophistication." to "Leonardo da Vinci",
    "Opportunities don't happen. You create them." to "Chris Grosser",
    "The best way to predict the future is to create it." to "Peter Drucker",
    "Chase the vision, not the money. The money will end up following you." to "Tony Hsieh",
    "Build something 100 people love, not something 1 million people kind of like." to "Brian Chesky",
    "Excellence is not an act but a habit." to "Will Durant",
    "Your most unhappy customers are your greatest source of learning." to "Bill Gates",
    "Well done is better than well said." to "Benjamin Franklin",
    "Honesty is the first chapter in the book of wisdom." to "Thomas Jefferson",
    "It takes twenty years to build a reputation and five minutes to ruin it." to "Warren Buffett",
    "Do not save what is left after spending; spend what is left after saving." to "Warren Buffett",
    "Be fearful when others are greedy, and greedy when others are fearful." to "Warren Buffett",
    "Risk comes from not knowing what you are doing." to "Warren Buffett",
    "A penny saved is a penny earned." to "Benjamin Franklin",
    "Beware of little expenses; a small leak will sink a great ship." to "Benjamin Franklin",
    "The best advertisement is a satisfied customer." to "Philip Kotler",
    "Efficiency is doing things right; effectiveness is doing the right things." to "Peter Drucker",
    "Quality means doing it right when no one is looking." to "Henry Ford",
    "Coming together is a beginning; keeping together is progress; working together is success." to "Henry Ford",
    "Whether you think you can or you think you can't, you're right." to "Henry Ford",
    "Genius is one percent inspiration and ninety-nine percent perspiration." to "Thomas Edison",
    "There is no substitute for hard work." to "Thomas Edison",
    "Success is the sum of small efforts, repeated day in and day out." to "Robert Collier",
    "Small deeds done are better than great deeds planned." to "Peter Marshall",
    "Start where you are. Use what you have. Do what you can." to "Arthur Ashe",
    "The only way to do great work is to love what you do." to "Steve Jobs",
    "Trust is the glue of life." to "Stephen Covey",
    "Customers don't expect you to be perfect. They expect you to fix things when they go wrong." to "Donald Porter",
    "A journey of a thousand miles begins with a single step." to "Lao Tzu",
    "Do the difficult things while they are easy and the great things while they are small." to "Lao Tzu",
    "Knowing is not enough; we must apply." to "Johann Wolfgang von Goethe",
    "It always seems impossible until it is done." to "Nelson Mandela",
    "Courage is not the absence of fear, but the triumph over it." to "Nelson Mandela",
    "Discipline is the bridge between goals and accomplishment." to "Jim Rohn",
    "Nothing will work unless you do." to "Maya Angelou",
    "Make each day your masterpiece." to "John Wooden",
    "Quality is never an accident; it is always the result of intelligent effort." to "John Ruskin",
    "Little by little, one travels far." to "J.R.R. Tolkien",
    "Slow and steady wins the race." to "Aesop",
    "No act of kindness, no matter how small, is ever wasted." to "Aesop",
    "Well begun is half done." to "Aristotle",
    "The best time to plant a tree was twenty years ago. The second best time is now." to "Chinese Proverb",
    "If you want to go fast, go alone. If you want to go far, go together." to "African Proverb",
    "Fall seven times, stand up eight." to "Japanese Proverb",
    "Never let the fear of striking out keep you from playing the game." to "Babe Ruth",
    "The expert in anything was once a beginner." to "Helen Hayes",
    "A smooth sea never made a skilled sailor." to "Franklin D. Roosevelt",
    "Do what you can, with what you have, where you are." to "Theodore Roosevelt",
    "Change your thoughts and you change your world." to "Norman Vincent Peale",
    "Whoever is happy will make others happy too." to "Anne Frank",
    "He who has a why to live can bear almost any how." to "Friedrich Nietzsche",
    "Innovation distinguishes between a leader and a follower." to "Steve Jobs",
    "The way to get started is to quit talking and begin doing." to "Walt Disney"
)

private val LoginMintTop = Color(0xFFEAF5F0)
private val LoginMintMid = Color(0xFFD9EDE4)
private val LoginBrandTeal = Color(0xFF0F766E)
private val LoginButtonDark = Color(0xFF065F46)
private val LoginButtonBright = Color(0xFF107A57)
private val InputBg = Color(0xFFF4F7F5)
private val InputBorder = Color(0xFFDFE7E2)
private val InputIconBg = Color(0xFFE7F3ED)
private val PlaceholderGray = Color(0xFF94A3B8)

@Composable
fun SarimSplashScreen() {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val cal = remember { Calendar.getInstance() }
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val hourFloat = hour24 + minute / 60f
    val isDay = hourFloat >= 6f && hourFloat < 18f
    val dialTarget = remember(hourFloat, isDay) {
        val raw = if (isDay) (hourFloat - 6f) / 12f else ((hourFloat - 18f + 24f) % 24f) / 12f
        raw.coerceIn(0.05f, 0.95f)
    }
    val periodColors = remember(hourFloat) {
        when {
            hourFloat < 5f || hourFloat >= 18f -> Triple(
                Color(0xFF6366F1).copy(alpha = 0.28f),
                Color(0xFF10B981).copy(alpha = 0.14f),
                Color(0xFFE2E8F0)
            )
            hourFloat < 8f -> Triple(
                Color(0xFFFBBF24).copy(alpha = 0.34f),
                Color(0xFF10B981).copy(alpha = 0.18f),
                Color(0xFFF59E0B)
            )
            hourFloat < 16f -> Triple(
                Color(0xFF10B981).copy(alpha = 0.28f),
                Color(0xFF0891B2).copy(alpha = 0.15f),
                Color(0xFFF59E0B)
            )
            else -> Triple(
                Color(0xFFFB923C).copy(alpha = 0.32f),
                Color(0xFF0891B2).copy(alpha = 0.16f),
                Color(0xFFF97316)
            )
        }
    }
    val greetingText = remember(hourFloat) {
        when {
            hourFloat < 5f -> "EARLY HOURS"
            hourFloat < 12f -> "GOOD MORNING"
            hourFloat < 17f -> "GOOD AFTERNOON"
            else -> "GOOD EVENING"
        }
    }
    val hour12 = remember(hour24) {
        val h = hour24 % 12
        if (h == 0) 12 else h
    }
    val timeDigits = remember(hour12, minute) {
        String.format(Locale.ENGLISH, "%d:%02d", hour12, minute)
    }
    val amPm = remember(hour24) { if (hour24 < 12) "AM" else "PM" }
    val dateHeader = remember(cal.timeInMillis) {
        SimpleDateFormat("EEEE · d MMMM", Locale.ENGLISH).format(cal.time).uppercase(Locale.ENGLISH)
    }
    val quotePair = remember { SPLASH_QUOTES.random() }

    val animatedProgress by animateFloatAsState(
        targetValue = if (appeared) dialTarget else 0.05f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "sun_dial_progress"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "splash_fade"
    )
    val pulse = rememberInfiniteTransition(label = "dot_pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("sarim_splash_screen")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val glowCenter = Offset(size.width * 0.5f, size.height * 0.43f)
            val glowRadius = size.maxDimension * 0.48f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        periodColors.first,
                        periodColors.second,
                        Color.Transparent
                    ),
                    center = glowCenter,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = glowCenter
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 360.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(212.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        val arcDiameter = minOf(size.width * 0.78f, 268.dp.toPx())
                        val radius = arcDiameter / 2f
                        val centerX = size.width / 2f
                        val centerY = size.height - 26.dp.toPx()
                        val topLeft = Offset(centerX - radius, centerY - radius)
                        val arcSize = Size(arcDiameter, arcDiameter)

                        for (i in 0..12) {
                            val angleDeg = 180f + i * 15f
                            val rad = angleDeg * (PI / 180.0)
                            val innerOffset = if (i == 0 || i == 6 || i == 12) 10.dp.toPx() else 11.dp.toPx()
                            val tickLen = if (i == 0 || i == 6 || i == 12) 13.dp.toPx() else 7.dp.toPx()
                            val startR = radius + innerOffset
                            val endR = startR + tickLen
                            val start = Offset(
                                centerX + (startR * cos(rad)).toFloat(),
                                centerY + (startR * sin(rad)).toFloat()
                            )
                            val end = Offset(
                                centerX + (endR * cos(rad)).toFloat(),
                                centerY + (endR * sin(rad)).toFloat()
                            )
                            drawLine(
                                color = if (i == 0 || i == 6 || i == 12) SplashTrackGray else SplashTrackGray.copy(alpha = 0.75f),
                                start = start,
                                end = end,
                                strokeWidth = if (i == 0 || i == 6 || i == 12) 2.2.dp.toPx() else 1.4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        drawArc(
                            color = SplashTrackGray.copy(alpha = 0.7f),
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = 2.2.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(2.dp.toPx(), 8.dp.toPx()),
                                    0f
                                )
                            )
                        )

                        val sweep = 180f * animatedProgress
                        drawArc(
                            color = SplashEmerald,
                            startAngle = 180f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = 3.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )

                        val orbAngleRad = (180f + sweep) * (PI / 180.0)
                        val orbCenter = Offset(
                            centerX + (radius * cos(orbAngleRad)).toFloat(),
                            centerY + (radius * sin(orbAngleRad)).toFloat()
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    periodColors.third.copy(alpha = 0.45f),
                                    Color.Transparent
                                ),
                                center = orbCenter,
                                radius = 19.dp.toPx()
                            ),
                            radius = 19.dp.toPx(),
                            center = orbCenter
                        )
                        if (isDay) {
                            drawCircle(
                                color = Color.White,
                                radius = 9.5.dp.toPx(),
                                center = orbCenter
                            )
                            drawCircle(
                                color = periodColors.third,
                                radius = 6.5.dp.toPx(),
                                center = orbCenter
                            )
                        } else {
                            drawCircle(
                                color = Color(0xFF475569),
                                radius = 8.5.dp.toPx(),
                                center = orbCenter
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 7.dp.toPx(),
                                center = Offset(orbCenter.x + 3.2.dp.toPx(), orbCenter.y - 2.2.dp.toPx())
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Text(
                            text = greetingText,
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 2.4.sp,
                            color = SplashEmerald
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = timeDigits,
                                fontFamily = PlayfairFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 44.sp,
                                lineHeight = 46.sp,
                                color = InkPrimary,
                                modifier = Modifier.alignByBaseline()
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = amPm,
                                fontFamily = JakartaFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp,
                                color = InkPrimary,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = dateHeader,
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            color = InkSecondary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.79f)
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isDay) "06" else "18",
                            fontFamily = JetBrainsMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = InkPrimary
                        )
                        Text(
                            text = if (isDay) "18" else "06",
                            fontFamily = JetBrainsMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = InkPrimary
                        )
                    }
                }

                Spacer(Modifier.height(34.dp))

                Text(
                    text = "GULL AND ZUBAIR",
                    fontFamily = CinzelFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                    letterSpacing = 2.4.sp,
                    color = InkPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "NASWAR DEALERS",
                    fontFamily = JakartaFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 3.2.sp,
                    color = SplashEmerald,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(22.dp))

                Box(
                    modifier = Modifier.width(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0xFFCBD5E1),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(SplashEmerald.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SplashEmerald)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "\"${quotePair.first}\"",
                    fontFamily = PlayfairFont,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = InkPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "— ${quotePair.second.uppercase(Locale.ENGLISH)}",
                    fontFamily = JakartaFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 2.2.sp,
                    color = InkSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size((14 * pulseScale).dp)
                        .clip(CircleShape)
                        .background(SplashEmerald.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(SplashEmerald)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "PREPARING YOUR DAY",
                    fontFamily = JakartaFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 2.2.sp,
                    color = InkSecondary
                )
            }
        }
    }
}

@Composable
private fun AuthInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    keyboardType: KeyboardType = KeyboardType.Text,
    onImeAction: () -> Unit = {},
    testTag: String
) {
    var focused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = JakartaFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = Color(0xFF1E293B),
            modifier = Modifier.padding(start = 2.dp, bottom = 7.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(InputBg)
                .border(
                    width = if (focused) 1.5.dp else 1.dp,
                    color = if (focused) SplashEmerald else InputBorder,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(InputIconBg)
                    .border(1.dp, SplashEmerald.copy(alpha = 0.18f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = SplashEmerald,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = JakartaFont,
                        fontSize = 14.sp,
                        color = PlaceholderGray
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = JakartaFont,
                        fontSize = 14.sp,
                        color = InkPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(SplashEmerald),
                    visualTransformation = if (isPassword && !passwordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onImeAction() },
                        onDone = { onImeAction() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .testTag(testTag)
                )
            }

            if (isPassword && onTogglePasswordVisibility != null) {
                IconButton(
                    onClick = onTogglePasswordVisibility,
                    modifier = Modifier.testTag("toggle_password_visibility")
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = InkSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var isEmailLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    val isAnyLoading = isEmailLoading || isGoogleLoading
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    fun submitEmailPassword() {
        focusManager.clearFocus()
        errorMessage = null
        isEmailLoading = true
        onEmailPasswordSignInClicked(
            email = email,
            password = password,
            onAuthSuccess = {
                isEmailLoading = false
                onAuthSuccess()
            },
            onAuthError = { msg ->
                isEmailLoading = false
                errorMessage = msg
            },
            scope = scope
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        LoginMintTop,
                        LoginMintMid,
                        LoginMintTop
                    )
                )
            )
    ) {
        val screenHeight = maxHeight
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(340)) + slideInVertically(tween(340)) { it / 18 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .defaultMinSize(minHeight = screenHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Brand Showcase matching original login screenshot
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .padding(horizontal = 24.dp)
                        .padding(top = 18.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        shadowElevation = 10.dp,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .aspectRatio(1.40f)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(26.dp)
                            )
                    ) {
                        Image(
                            painter = painterResource(R.drawable.brand_logo),
                            contentDescription = "Gull and Zubair Naswar Dealers Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(26.dp))
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "GULL AND ZUBAIR",
                        fontFamily = CinzelFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 21.sp,
                        letterSpacing = 1.8.sp,
                        color = LoginBrandTeal,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = "NASWAR DEALERS",
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 3.2.sp,
                        color = InkPrimary,
                        textAlign = TextAlign.Center
                    )
                }

                // Bottom Elevated White Login Sheet matching original login screenshot
                Surface(
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = Color.White,
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = "Welcome back",
                            fontFamily = PlayfairFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = InkPrimary
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Sign in to unlock and sync your business data.",
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = InkSecondary
                        )

                        Spacer(Modifier.height(18.dp))

                        AuthInputField(
                            label = "Email address",
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "name@example.com",
                            leadingIcon = Icons.Outlined.Email,
                            imeAction = ImeAction.Next,
                            keyboardType = KeyboardType.Email,
                            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                            testTag = "email_input"
                        )

                        Spacer(Modifier.height(14.dp))

                        AuthInputField(
                            label = "Password",
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "Enter your password",
                            leadingIcon = Icons.Outlined.Lock,
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Password,
                            onImeAction = { submitEmailPassword() },
                            testTag = "password_input"
                        )

                        Spacer(Modifier.height(20.dp))

                        val buttonGradient = Brush.horizontalGradient(
                            listOf(LoginButtonBright, LoginButtonDark)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(999.dp),
                                    ambientColor = LoginButtonDark.copy(alpha = 0.3f),
                                    spotColor = LoginButtonDark.copy(alpha = 0.35f)
                                )
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    brush = buttonGradient,
                                    alpha = if (isAnyLoading) 0.72f else 1f
                                )
                                .clickable(enabled = !isAnyLoading) { submitEmailPassword() }
                                .testTag("email_sign_in_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isEmailLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.2.dp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Signing in...",
                                        fontFamily = JakartaFont,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Text(
                                    text = "Sign in",
                                    fontFamily = JakartaFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = InputBorder)
                            Text(
                                text = "or",
                                fontFamily = JakartaFont,
                                fontSize = 11.sp,
                                color = InkSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = InputBorder)
                        }

                        Spacer(Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White)
                                .border(1.dp, InputBorder, RoundedCornerShape(999.dp))
                                .clickable(enabled = !isAnyLoading) {
                                    errorMessage = null
                                    isGoogleLoading = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isGoogleLoading = false
                                            onAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isGoogleLoading = false
                                            errorMessage = msg
                                        },
                                        scope = scope,
                                        onAuthCancelled = { isGoogleLoading = false }
                                    )
                                }
                                .testTag("google_sign_in_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isGoogleLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = SplashEmerald
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Connecting Google...",
                                        fontFamily = JakartaFont,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = InkPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = SplashEmerald,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Sign in with Google",
                                        fontFamily = JakartaFont,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = InkPrimary
                                    )
                                }
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                color = Color(0xFFFEF2F2),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        Color(0xFFFCA5A5),
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB91C1C),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = buildAnnotatedString {
                                append("No account? ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = InkPrimary)) {
                                    append("Contact the administrator")
                                }
                                append(" to have yours added.")
                            },
                            fontFamily = JakartaFont,
                            fontSize = 12.sp,
                            color = InkSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(20.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = "Protected workspace",
                                tint = SplashEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Protected by Firebase Auth · Zero-Trust Rules",
                                fontFamily = JakartaFont,
                                fontSize = 11.sp,
                                color = InkSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
