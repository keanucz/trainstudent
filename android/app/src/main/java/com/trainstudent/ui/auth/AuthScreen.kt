package com.trainstudent.ui.auth

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainstudent.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Auth Flow Steps
enum class AuthStep {
    SIGNUP,
    EMAIL_VERIFICATION,
    TOPCASHBACK_CHECK,
    TOPCASHBACK_SIGNUP,
    TOPCASHBACK_LOGIN,
    QUIDCO_CHECK,
    QUIDCO_SIGNUP,
    QUIDCO_LOGIN,
    RAILCARD_CHECK,
    SANTANDER_OFFER,
    COMPLETE
}

data class AuthUiState(
    val currentStep: AuthStep = AuthStep.SIGNUP,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val verificationCode: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val emailVerified: Boolean = false,
    val hasTopCashback: Boolean = false,
    val hasQuidco: Boolean = false,
    val hasRailcard1625: Boolean = false,
    val santanderOfferUrl: String = "https://www.quidco.com/merchant/santander-edge-student-current-account/",
    // For cashback login
    val cashbackEmail: String = "",
    val cashbackPassword: String = ""
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    init {
        // Check if user is already partially through onboarding
        viewModelScope.launch {
            val authState = authRepository.getAuthState()
            if (authState.isLoggedIn) {
                // Skip to appropriate step based on what's already done
                val nextStep = when {
                    !authState.topcashbackLinked -> AuthStep.TOPCASHBACK_CHECK
                    !authState.quidcoLinked -> AuthStep.QUIDCO_CHECK
                    else -> AuthStep.RAILCARD_CHECK
                }
                _uiState.update { it.copy(
                    currentStep = nextStep,
                    email = authState.email ?: "",
                    firstName = authState.firstName ?: "",
                    lastName = authState.lastName ?: "",
                    hasTopCashback = authState.topcashbackLinked,
                    hasQuidco = authState.quidcoLinked,
                    hasRailcard1625 = authState.hasRailcard1625
                )}
            }
        }
    }
    
    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }
    
    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }
    
    fun updateConfirmPassword(password: String) {
        _uiState.update { it.copy(confirmPassword = password, errorMessage = null) }
    }
    
    fun updateFirstName(name: String) {
        _uiState.update { it.copy(firstName = name) }
    }
    
    fun updateLastName(name: String) {
        _uiState.update { it.copy(lastName = name) }
    }
    
    fun updateVerificationCode(code: String) {
        _uiState.update { it.copy(verificationCode = code, errorMessage = null) }
    }
    
    fun updateCashbackEmail(email: String) {
        _uiState.update { it.copy(cashbackEmail = email, errorMessage = null) }
    }
    
    fun updateCashbackPassword(password: String) {
        _uiState.update { it.copy(cashbackPassword = password, errorMessage = null) }
    }
    
    fun submitSignup() {
        val state = _uiState.value
        
        // Validation
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password are required") }
            return
        }
        
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }
        
        if (state.password.length < 8) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            val result = authRepository.register(
                email = state.email,
                password = state.password,
                firstName = state.firstName,
                lastName = state.lastName
            )
            
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            currentStep = AuthStep.EMAIL_VERIFICATION,
                            successMessage = "Verification code sent to your email"
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Registration failed"
                        )
                    }
                }
            )
        }
    }
    
    fun submitVerificationCode() {
        val state = _uiState.value
        
        if (state.verificationCode.length != 4) {
            _uiState.update { it.copy(errorMessage = "Please enter the 4-digit code") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            val result = authRepository.verifyEmail(state.email, state.verificationCode)
            
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            emailVerified = true,
                            currentStep = AuthStep.TOPCASHBACK_CHECK
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Verification failed"
                        )
                    }
                }
            )
        }
    }
    
    fun setHasTopCashback(has: Boolean) {
        _uiState.update { 
            it.copy(
                hasTopCashback = has,
                currentStep = if (has) AuthStep.TOPCASHBACK_LOGIN else AuthStep.TOPCASHBACK_SIGNUP,
                cashbackEmail = "",
                cashbackPassword = "",
                errorMessage = null
            )
        }
    }
    
    fun completeTopCashbackSignup() {
        viewModelScope.launch {
            authRepository.setTopcashbackLinked(true)
            _uiState.update { 
                it.copy(
                    hasTopCashback = true,
                    currentStep = AuthStep.QUIDCO_CHECK
                )
            }
        }
    }
    
    fun submitTopCashbackLogin() {
        val state = _uiState.value
        
        if (state.cashbackEmail.isBlank() || state.cashbackPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password required") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            val result = authRepository.linkCashbackAccount(
                provider = "topcashback",
                email = state.cashbackEmail,
                password = state.cashbackPassword
            )
            
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            hasTopCashback = true,
                            currentStep = AuthStep.QUIDCO_CHECK,
                            cashbackEmail = "",
                            cashbackPassword = ""
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Login failed"
                        )
                    }
                }
            )
        }
    }
    
    fun setHasQuidco(has: Boolean) {
        _uiState.update { 
            it.copy(
                hasQuidco = has,
                currentStep = if (has) AuthStep.QUIDCO_LOGIN else AuthStep.QUIDCO_SIGNUP,
                cashbackEmail = "",
                cashbackPassword = "",
                errorMessage = null
            )
        }
    }
    
    fun completeQuidcoSignup() {
        viewModelScope.launch {
            authRepository.setQuidcoLinked(true)
            _uiState.update { 
                it.copy(
                    hasQuidco = true,
                    currentStep = AuthStep.RAILCARD_CHECK
                )
            }
        }
    }
    
    fun submitQuidcoLogin() {
        val state = _uiState.value
        
        if (state.cashbackEmail.isBlank() || state.cashbackPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password required") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            val result = authRepository.linkCashbackAccount(
                provider = "quidco",
                email = state.cashbackEmail,
                password = state.cashbackPassword
            )
            
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            hasQuidco = true,
                            currentStep = AuthStep.RAILCARD_CHECK,
                            cashbackEmail = "",
                            cashbackPassword = ""
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Login failed"
                        )
                    }
                }
            )
        }
    }
    
    fun setHasRailcard(has: Boolean) {
        viewModelScope.launch {
            authRepository.setHasRailcard(has)
            _uiState.update { 
                it.copy(
                    hasRailcard1625 = has,
                    currentStep = if (has) AuthStep.COMPLETE else AuthStep.SANTANDER_OFFER
                )
            }
        }
    }
    
    fun completeSantanderOffer() {
        _uiState.update { it.copy(currentStep = AuthStep.COMPLETE) }
    }
    
    fun skipCashbackSetup(provider: String) {
        when (provider) {
            "topcashback" -> {
                _uiState.update { it.copy(currentStep = AuthStep.QUIDCO_CHECK) }
            }
            "quidco" -> {
                _uiState.update { it.copy(currentStep = AuthStep.RAILCARD_CHECK) }
            }
        }
    }
    
    fun goBackToCashbackCheck(provider: String) {
        when (provider) {
            "topcashback" -> {
                _uiState.update { it.copy(currentStep = AuthStep.TOPCASHBACK_CHECK) }
            }
            "quidco" -> {
                _uiState.update { it.copy(currentStep = AuthStep.QUIDCO_CHECK) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthComplete: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == AuthStep.COMPLETE) {
            onAuthComplete()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
            },
            label = "auth_step"
        ) { step ->
            when (step) {
                AuthStep.SIGNUP -> SignupStep(
                    uiState = uiState,
                    onEmailChange = viewModel::updateEmail,
                    onPasswordChange = viewModel::updatePassword,
                    onConfirmPasswordChange = viewModel::updateConfirmPassword,
                    onFirstNameChange = viewModel::updateFirstName,
                    onLastNameChange = viewModel::updateLastName,
                    onSubmit = viewModel::submitSignup
                )
                AuthStep.EMAIL_VERIFICATION -> EmailVerificationStep(
                    uiState = uiState,
                    onCodeChange = viewModel::updateVerificationCode,
                    onSubmit = viewModel::submitVerificationCode
                )
                AuthStep.TOPCASHBACK_CHECK -> CashbackCheckStep(
                    provider = "TopCashback",
                    onHasAccount = { viewModel.setHasTopCashback(true) },
                    onNoAccount = { viewModel.setHasTopCashback(false) }
                )
                AuthStep.TOPCASHBACK_SIGNUP -> CashbackSignupWebView(
                    url = "https://www.topcashback.co.uk/ref/guestone63",
                    provider = "TopCashback",
                    onComplete = viewModel::completeTopCashbackSignup,
                    onBack = { viewModel.goBackToCashbackCheck("topcashback") }
                )
                AuthStep.TOPCASHBACK_LOGIN -> CashbackLoginStep(
                    provider = "TopCashback",
                    uiState = uiState,
                    onEmailChange = viewModel::updateCashbackEmail,
                    onPasswordChange = viewModel::updateCashbackPassword,
                    onSubmit = viewModel::submitTopCashbackLogin,
                    onSkip = { viewModel.skipCashbackSetup("topcashback") },
                    onBack = { viewModel.goBackToCashbackCheck("topcashback") }
                )
                AuthStep.QUIDCO_CHECK -> CashbackCheckStep(
                    provider = "Quidco",
                    onHasAccount = { viewModel.setHasQuidco(true) },
                    onNoAccount = { viewModel.setHasQuidco(false) }
                )
                AuthStep.QUIDCO_SIGNUP -> CashbackSignupWebView(
                    url = "https://quidco.com/raf/13519657/",
                    provider = "Quidco",
                    onComplete = viewModel::completeQuidcoSignup,
                    onBack = { viewModel.goBackToCashbackCheck("quidco") }
                )
                AuthStep.QUIDCO_LOGIN -> CashbackLoginStep(
                    provider = "Quidco",
                    uiState = uiState,
                    onEmailChange = viewModel::updateCashbackEmail,
                    onPasswordChange = viewModel::updateCashbackPassword,
                    onSubmit = viewModel::submitQuidcoLogin,
                    onSkip = { viewModel.skipCashbackSetup("quidco") },
                    onBack = { viewModel.goBackToCashbackCheck("quidco") }
                )
                AuthStep.RAILCARD_CHECK -> RailcardCheckStep(
                    onHasRailcard = { viewModel.setHasRailcard(true) },
                    onNoRailcard = { viewModel.setHasRailcard(false) }
                )
                AuthStep.SANTANDER_OFFER -> SantanderOfferStep(
                    affiliateUrl = uiState.santanderOfferUrl,
                    onComplete = viewModel::completeSantanderOffer
                )
                AuthStep.COMPLETE -> {
                    // Will be handled by LaunchedEffect
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignupStep(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Icon(
            imageVector = Icons.Default.Train,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "💡 Use your .ac.uk student email to unlock additional discounts!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // First & Last Name Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.firstName,
                onValueChange = onFirstNameChange,
                label = { Text("First Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = uiState.lastName,
                onValueChange = onLastNameChange,
                label = { Text("Last Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            placeholder = { Text("yourname@university.ac.uk") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            leadingIcon = { Icon(Icons.Default.Email, null) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = uiState.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            modifier = Modifier.fillMaxWidth()
        )
        
        if (uiState.errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onSubmit,
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Create Account", style = MaterialTheme.typography.titleMedium)
            }
        }
        
        Spacer(modifier = Modifier.height(64.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailVerificationStep(
    uiState: AuthUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MarkEmailRead,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Check Your Email",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "We've sent a 4-digit verification code to:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = uiState.email,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = uiState.verificationCode,
            onValueChange = { if (it.length <= 4) onCodeChange(it) },
            label = { Text("Verification Code") },
            placeholder = { Text("0000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { 
                focusManager.clearFocus()
                onSubmit() 
            }),
            modifier = Modifier.width(200.dp),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
        )
        
        if (uiState.errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        if (uiState.successMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.successMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onSubmit,
            enabled = !uiState.isLoading && uiState.verificationCode.length == 4,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Verify Email")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Check your spam folder if you don't see the email",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CashbackCheckStep(
    provider: String,
    onHasAccount: () -> Unit,
    onNoAccount: () -> Unit
) {
    val icon = if (provider == "TopCashback") Icons.Default.AccountBalance else Icons.Default.LocalOffer
    val color = if (provider == "TopCashback") Color(0xFF1E88E5) else Color(0xFFE91E63)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = color
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Do you have a $provider account?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = color.copy(alpha = 0.1f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💰 Get cashback on train tickets!",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$provider gives you money back every time you buy train tickets through their links. It stacks with other discounts!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onHasAccount,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Yes, I have $provider")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onNoAccount,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("No, sign me up!")
        }
    }
}

@Composable
private fun CashbackLoginStep(
    provider: String,
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val color = if (provider == "TopCashback") Color(0xFF1E88E5) else Color(0xFFE91E63)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Link $provider Account",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Enter your $provider credentials to link your account for automatic cashback tracking",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = uiState.cashbackEmail,
            onValueChange = onEmailChange,
            label = { Text("$provider Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            leadingIcon = { Icon(Icons.Default.Email, null) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = uiState.cashbackPassword,
            onValueChange = onPasswordChange,
            label = { Text("$provider Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        if (uiState.errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onSubmit,
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Link Account")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
            TextButton(onClick = onSkip) {
                Text("Skip for now")
            }
        }
    }
}

@Composable
private fun CashbackSignupWebView(
    url: String,
    provider: String,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
                
                Text(
                    text = "Sign up for $provider",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = onComplete,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done")
                }
            }
        }
        
        // Info banner
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Create your account, then tap 'Done' when finished",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
                textAlign = TextAlign.Center
            )
        }
        
        // WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            // Keep navigation within the WebView
                            return false
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun RailcardCheckStep(
    onHasRailcard: () -> Unit,
    onNoRailcard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CreditCard,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Do you have a 16-25 Railcard?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🎫 16-25 Railcard Benefits",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• 1/3 off most train fares\n• Costs £30/year (or get it FREE!)\n• Stacks with student discounts\n• Available ages 16-25 or mature students",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onHasRailcard,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Yes, I have one")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onNoRailcard,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.CardGiftcard, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("No, show me how to get one FREE!")
        }
    }
}

@Composable
private fun SantanderOfferStep(
    affiliateUrl: String,
    onComplete: () -> Unit
) {
    var showWebView by remember { mutableStateOf(false) }
    
    if (showWebView) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showWebView = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    
                    Text(
                        text = "Santander via Quidco",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Button(
                        onClick = onComplete,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Done")
                    }
                }
            }
            
            // WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        loadUrl(affiliateUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CardGiftcard,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFFEC0000) // Santander red
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Get a FREE 16-25 Railcard!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFEC0000).copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Santander Edge Student Account",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEC0000)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• FREE 4-year 16-25 Railcard (worth £120!)\n• Plus earn cashback via Quidco\n• No monthly fees for students\n• £10-20 cashback for opening",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Open a Santander Edge Student account to get a free 4-year railcard. We'll take you through Quidco so you also earn cashback!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { showWebView = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEC0000)
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open via Quidco (earns cashback)")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now")
            }
        }
    }
}
