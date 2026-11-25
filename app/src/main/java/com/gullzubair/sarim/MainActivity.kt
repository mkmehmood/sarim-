package com.gullzubair.sarim

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.gullzubair.sarim.data.firebase.authStateFlow
import com.gullzubair.sarim.ui.components.AuthScreen
import com.gullzubair.sarim.ui.components.SarimSplashScreen
import com.gullzubair.sarim.ui.screens.MainFeatureScreen
import com.gullzubair.sarim.ui.theme.SarimTheme
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var showSplash by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(1500L)
            showSplash = false
        }
    }

    Crossfade(
        targetState = Pair(showSplash, currentUser?.uid),
        animationSpec = tween(durationMillis = 360),
        label = "app_root_transition"
    ) { (splashActive, uid) ->
        if (splashActive) {
            SarimTheme(themeMode = "light") {
                SarimSplashScreen()
            }
        } else if (uid == null) {
            SarimTheme(themeMode = "light") {
                AuthScreen(onAuthSuccess = {})
            }
        } else {
            key(uid) {
                MainFeatureScreen(currentUserId = uid)
            }
        }
    }
}
