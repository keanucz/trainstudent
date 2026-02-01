package com.trainstudent.ui

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.trainstudent.repository.AuthRepository
import com.trainstudent.ui.auth.AuthScreen
import com.trainstudent.ui.live.LiveTrainsScreen
import com.trainstudent.ui.offers.OffersScreen
import com.trainstudent.ui.onboarding.OnboardingScreen
import com.trainstudent.ui.onboarding.OnboardingViewModel
import com.trainstudent.ui.save.SaveMoneyScreen
import com.trainstudent.ui.settings.SettingsScreen
import com.trainstudent.ui.theme.TrainStudentTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrainStudentTheme {
                TrainStudentAppWithOnboarding()
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object SaveMoney : Screen("save", "Save", Icons.Default.Search)
    data object LiveTrains : Screen("live", "Live", Icons.Default.Train)
    data object Offers : Screen("offers", "Offers", Icons.Default.Home)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.SaveMoney,
    Screen.LiveTrains,
    Screen.Offers,
    Screen.Settings
)

// App Flow States
enum class AppFlowState {
    LOADING,
    PERMISSIONS_ONBOARDING,
    AUTH_ONBOARDING,
    MAIN_APP
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _flowState = MutableStateFlow(AppFlowState.LOADING)
    val flowState: StateFlow<AppFlowState> = _flowState.asStateFlow()
    
    init {
        checkAppState()
    }
    
    private fun checkAppState() {
        viewModelScope.launch {
            val authState = authRepository.getAuthState()
            _flowState.value = when {
                // Check if auth onboarding is complete (logged in + cashback setup done)
                authState.isLoggedIn && authState.onboardingComplete -> AppFlowState.MAIN_APP
                // If logged in but auth onboarding not complete, go to auth flow
                authState.isLoggedIn -> AppFlowState.AUTH_ONBOARDING
                // Otherwise start fresh
                else -> AppFlowState.PERMISSIONS_ONBOARDING
            }
        }
    }
    
    fun onPermissionsComplete() {
        _flowState.value = AppFlowState.AUTH_ONBOARDING
    }
    
    fun onAuthComplete() {
        viewModelScope.launch {
            authRepository.completeAuthOnboarding()
            _flowState.value = AppFlowState.MAIN_APP
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _flowState.value = AppFlowState.AUTH_ONBOARDING
        }
    }
}

@Composable
fun TrainStudentAppWithOnboarding(
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val flowState by viewModel.flowState.collectAsState()
    var permissionsOnboardingComplete by remember { mutableStateOf<Boolean?>(null) }
    
    // Check if permissions onboarding is complete
    LaunchedEffect(Unit) {
        permissionsOnboardingComplete = OnboardingViewModel.isOnboardingComplete(context)
    }
    
    // Update flow state based on permissions onboarding
    LaunchedEffect(permissionsOnboardingComplete) {
        if (permissionsOnboardingComplete == false && flowState == AppFlowState.LOADING) {
            // Permissions not done yet, will show permissions onboarding
        }
    }
    
    when (flowState) {
        AppFlowState.LOADING -> {
            // Loading state - check if permissions onboarding is done
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                // Show splash or loading indicator
            }
            
            // If permissions already done, skip to auth
            LaunchedEffect(permissionsOnboardingComplete) {
                if (permissionsOnboardingComplete == true) {
                    // Permissions done, check auth state (MainViewModel will handle this)
                }
            }
        }
        
        AppFlowState.PERMISSIONS_ONBOARDING -> {
            // Check if permissions onboarding needs to be shown
            if (permissionsOnboardingComplete == false) {
                OnboardingScreen(
                    onComplete = {
                        permissionsOnboardingComplete = true
                        viewModel.onPermissionsComplete()
                    }
                )
            } else {
                // Permissions already done, move to auth
                LaunchedEffect(Unit) {
                    viewModel.onPermissionsComplete()
                }
            }
        }
        
        AppFlowState.AUTH_ONBOARDING -> {
            AuthScreen(
                onAuthComplete = {
                    viewModel.onAuthComplete()
                }
            )
        }
        
        AppFlowState.MAIN_APP -> {
            TrainStudentApp(
                onLogout = { viewModel.logout() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TrainStudentApp(onLogout: () -> Unit = {}) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Check audio permission for mic button feedback
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "TrainStudent",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.SaveMoney.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.SaveMoney.route) {
                SaveMoneyScreen(
                    onMicPermissionRequired = {
                        // Check permission and request if needed
                        if (audioPermissionState.status !is PermissionStatus.Granted) {
                            audioPermissionState.launchPermissionRequest()
                            Toast.makeText(
                                context,
                                "Microphone permission required for voice input",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
            composable(Screen.LiveTrains.route) {
                LiveTrainsScreen()
            }
            composable(Screen.Offers.route) {
                OffersScreen()
            }
            composable(Screen.Settings.route) {
                com.trainstudent.ui.settings.SettingsScreen(
                    onLogout = onLogout
                )
            }
        }
    }
}
