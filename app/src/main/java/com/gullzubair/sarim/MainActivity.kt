package com.gullzubair.sarim

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.gullzubair.sarim.data.firebase.authStateFlow
import com.gullzubair.sarim.ui.components.AuthScreen
import com.gullzubair.sarim.ui.screens.MainFeatureScreen
import com.gullzubair.sarim.ui.theme.SarimTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
    val user = currentUser

    if (user == null) {
        SarimTheme(themeMode = "dark") {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        key(user.uid) {
            MainFeatureScreen(currentUserId = user.uid)
        }
    }
}
