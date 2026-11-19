package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.FinanceRepository
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.ui.AuthScreen
import com.example.ui.MainAppScaffold
import com.example.ui.theme.PennyLensTheme
import com.example.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent BEFORE UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            PennyLensTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ })
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: MainViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        MainViewModel(
                            repository = FinanceRepository(db, auth),
                            currentUserId = uid,
                            initialDisplayName = user.displayName.orEmpty(),
                            initialEmail = user.email.orEmpty()
                        )
                    }
                }
            )
            MainAppScaffold(viewModel = viewModel)
        }
    }
}
