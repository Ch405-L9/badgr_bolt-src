package com.badgr.orbreader.ui.account

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.badgr.orbreader.billing.InAppPurchaseManager
import com.badgr.orbreader.ui.theme.ReaderColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(vm: AccountViewModel = viewModel()) {
    val uiState      by vm.uiState.collectAsState()
    val isPro        by vm.isPro.collectAsState()
    val isVerified   by vm.isEmailVerified.collectAsState()
    val activeSku    by vm.activeSku.collectAsState()
    val resendStatus by vm.resendStatus.collectAsState()
    val isDeleting   by vm.isDeletingAccount.collectAsState()
    val activity      = LocalContext.current as Activity

    var email               by remember { mutableStateOf("") }
    var password            by remember { mutableStateOf("") }
    var passwordVisible     by remember { mutableStateOf(false) }
    var isSignUp            by remember { mutableStateOf(false) }
    var showDeleteDialog      by remember { mutableStateOf(false) }
    var deletePassword        by remember { mutableStateOf("") }
    var deletePasswordVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(resendStatus) {
        resendStatus?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearResendStatus()
        }
    }

    val isLifetime = activeSku == InAppPurchaseManager.SKU_PRO_LIFETIME

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDeleteDialog = false
                    deletePassword = ""
                }
            },
            title = { Text("Delete account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This permanently deletes your BADGR Bolt account and cloud-synced library data. " +
                        "Local files on this device are not removed."
                    )
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isDeleting,
                        visualTransformation = if (deletePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { deletePasswordVisible = !deletePasswordVisible }) {
                                Icon(
                                    if (deletePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (deletePasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        vm.deleteAccount(deletePassword)
                        showDeleteDialog = false
                        deletePassword = ""
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        showDeleteDialog = false
                        deletePassword = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Account",
                        color      = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {

                is AccountUiState.SignedIn -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Signed in as",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            state.email,
                            color      = MaterialTheme.colorScheme.onBackground,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // ── Pro status card ───────────────────────────────
                        if (isPro) {
                            val cardColor = if (isLifetime) Color(0xFFE040FB) else ReaderColors.orpFocal
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color    = cardColor.copy(alpha = 0.08f),
                                shape    = RoundedCornerShape(14.dp),
                                border   = BorderStroke(1.dp, cardColor.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier            = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        if (isLifetime) "LIFETIME MEMBER" else "BADGR BOLT PRO",
                                        fontFamily    = FontFamily.Monospace,
                                        fontSize      = 9.sp,
                                        fontWeight    = FontWeight.Bold,
                                        color         = cardColor,
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (isLifetime) "Lifetime Member" else "BADGR Bolt Pro",
                                        color      = cardColor,
                                        fontWeight = FontWeight.Black,
                                        fontSize   = 22.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    if (isLifetime) {
                                        Text(
                                            "You have permanent access to every Pro feature.",
                                            color     = ReaderColors.textDimmed,
                                            fontSize  = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Thank you for supporting BADGR Bolt.",
                                            color      = cardColor,
                                            fontSize   = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign  = TextAlign.Center
                                        )
                                    } else {
                                        Text(
                                            "Your monthly subscription is active.",
                                            color     = ReaderColors.textDimmed,
                                            fontSize  = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "All Pro features are unlocked.",
                                            color     = ReaderColors.textDimmed,
                                            fontSize  = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            "Manage or cancel anytime in Google Play.",
                                            color      = ReaderColors.textDimmed,
                                            fontSize   = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        // ── Email verification banner (TD-007) ────────────
                        if (!isVerified) {
                            // Poll every 5 s — picks up verification without requiring log-out/in.
                            LaunchedEffect(Unit) {
                                while (true) {
                                    delay(5_000)
                                    vm.refreshVerificationStatus()
                                }
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color    = ReaderColors.orpFocal.copy(alpha = 0.06f),
                                shape    = RoundedCornerShape(10.dp),
                                border   = BorderStroke(1.dp, ReaderColors.orpFocal.copy(alpha = 0.25f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Verify your email to enable cloud sync",
                                        color      = ReaderColors.textWarm,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize   = 13.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Check your inbox and spam folder for a verification link. " +
                                        "Cloud sync is paused until your email is confirmed.",
                                        color    = ReaderColors.textDimmed,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row {
                                        TextButton(onClick = { vm.refreshVerificationStatus() }) {
                                            Text(
                                                "I've verified — refresh",
                                                color    = ReaderColors.orpFocal,
                                                fontSize = 12.sp
                                            )
                                        }
                                        TextButton(onClick = { vm.resendVerificationEmail() }) {
                                            Text(
                                                "Resend email",
                                                color    = ReaderColors.textDimmed,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Upgrade section (free users) ──────────────────
                        if (!isPro) {
                            Text(
                                "Upgrade to Pro to unlock all features.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick  = { vm.launchSubscription(activity) },
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    "Subscribe - Monthly",
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            OutlinedButton(
                                onClick  = { vm.launchLifetime(activity) },
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Lifetime Access - One-time")
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { vm.signOut() },
                            enabled = !isDeleting,
                            colors  = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Sign Out")
                        }

                        HorizontalDivider(color = ReaderColors.guideLine)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Account deletion",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Delete your account and cloud-synced BADGR Bolt data.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                enabled = !isDeleting,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                )
                            ) {
                                Text(if (isDeleting) "Deleting..." else "Delete Account")
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                is AccountUiState.Loading -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                is AccountUiState.SignedOut,
                is AccountUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))

                        Text(
                            if (isSignUp) "Create Account" else "Sign In",
                            color      = MaterialTheme.colorScheme.onBackground,
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(4.dp))

                        if (state is AccountUiState.Error) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text     = state.message,
                                    color    = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp),
                                    style    = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        OutlinedTextField(
                            value           = email,
                            onValueChange   = { email = it },
                            label           = { Text("Email") },
                            singleLine      = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType   = KeyboardType.Email,
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect    = false
                            ),
                            modifier        = Modifier.fillMaxWidth(),
                            colors          = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                                focusedLabelColor    = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                                cursorColor          = MaterialTheme.colorScheme.primary
                            )
                        )

                        OutlinedTextField(
                            value                = password,
                            onValueChange        = { password = it },
                            label                = { Text("Password") },
                            singleLine           = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon         = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier             = Modifier.fillMaxWidth(),
                            colors               = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                                focusedLabelColor    = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                                cursorColor          = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick  = {
                                if (isSignUp) vm.signUp(email, password)
                                else          vm.signIn(email, password)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                if (isSignUp) "Create Account" else "Sign In",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        TextButton(onClick = { isSignUp = !isSignUp; vm.clearError() }) {
                            Text(
                                if (isSignUp) "Already have an account? Sign In"
                                else "No account? Create one",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isSignUp) {
                            TextButton(onClick = { vm.resetPassword(email) }) {
                                Text(
                                    "Forgot password?",
                                    color    = ReaderColors.orpFocal,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = ReaderColors.guideLine)
                        Spacer(Modifier.height(8.dp))

                        // ── Free user info card ───────────────────────────
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color    = ReaderColors.orpFocal.copy(alpha = 0.05f),
                            shape    = RoundedCornerShape(12.dp),
                            border   = BorderStroke(1.dp, ReaderColors.guideLine)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "No account required.",
                                    color      = ReaderColors.textWarm,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize   = 14.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "BADGR Bolt works fully offline with no account. " +
                                    "Your books and reading progress are stored on this device.",
                                    color    = ReaderColors.textDimmed,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Create a free account to enable Pro features including cloud sync. " +
                                    "Your library follows you across devices and survives reinstalls.",
                                    color    = ReaderColors.textDimmed,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Legal links ──────────────────────────────────
                        val uriHandler = LocalUriHandler.current
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = {
                                uriHandler.openUri("https://badgrtech.com/privacy/badgr-bolt")
                            }) {
                                Text(
                                    "Privacy Policy",
                                    color    = ReaderColors.textDimmed,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                "·",
                                color    = ReaderColors.textDimmed,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            TextButton(onClick = {
                                uriHandler.openUri("https://badgrtech.com/terms")
                            }) {
                                Text(
                                    "Terms",
                                    color    = ReaderColors.textDimmed,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
