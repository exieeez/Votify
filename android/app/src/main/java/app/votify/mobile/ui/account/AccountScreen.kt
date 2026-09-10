package app.votify.mobile.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import app.votify.mobile.R
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors

/**
 * «Аккаунт»: login / registration / password recovery against the user's own Votify server
 * (POST /api/auth endpoints). On success the JWT is stored and every request is signed with it.
 */
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Successful login/register leaves the screen automatically.
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it is AccountEvent.LoggedIn) onBack() }
    }

    var googleIdDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var googleHelpDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var googleNoGmsDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var googleInFlight by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Field state survives mode switches so the email doesn't vanish mid-recovery.
    var email by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.nav_back), tint = VotifyColors.TextPrimary)
            }
            Text(
                stringResource(R.string.account_title),
                style = MaterialTheme.typography.titleLarge,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Signed-in users never see this form: VotifyRoot renders ProfileScreen instead.

        // Login / register tabs (hidden inside the recovery wizard)
        if (state.mode == AccountMode.Login || state.mode == AccountMode.Register) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeTab(stringResource(R.string.account_login), state.mode == AccountMode.Login, Modifier.weight(1f)) { viewModel.setMode(AccountMode.Login) }
                ModeTab(stringResource(R.string.account_register), state.mode == AccountMode.Register, Modifier.weight(1f)) { viewModel.setMode(AccountMode.Register) }
            }
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.account_forgot_title),
                style = MaterialTheme.typography.titleMedium,
                color = VotifyColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (state.backend == AccountBackend.None) {
            VotifyCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Text(stringResource(R.string.account_no_backend_title), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.account_no_backend_sub), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
            }
            Text(
                stringResource(R.string.account_hint),
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            return@Column
        }

        VotifyCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column {
                when (state.mode) {
                    AccountMode.Login -> {
                        VotifyTextField(email, { email = it }, stringResource(R.string.account_email))
                        Spacer(Modifier.height(12.dp))
                        VotifyTextField(password, { password = it }, stringResource(R.string.account_password), password = true)
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            text = stringResource(R.string.account_login_btn),
                            loading = state.loading,
                            enabled = email.isNotBlank() && password.isNotBlank(),
                        ) { viewModel.login(email, password) }
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = { viewModel.setMode(AccountMode.ForgotEmail) },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text(stringResource(R.string.account_forgot), color = VotifyColors.TextMuted) }
                    }

                    AccountMode.Register -> {
                        VotifyTextField(email, { email = it }, stringResource(R.string.account_email))
                        Spacer(Modifier.height(12.dp))
                        VotifyTextField(username, { username = it }, stringResource(R.string.account_username))
                        Spacer(Modifier.height(12.dp))
                        VotifyTextField(password, { password = it }, stringResource(R.string.account_password), password = true)
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            text = stringResource(R.string.account_register_btn),
                            loading = state.loading,
                            enabled = email.isNotBlank() && password.isNotBlank(),
                        ) { viewModel.register(email, username, password) }
                    }

                    AccountMode.ForgotEmail -> {
                        if (state.resetSent) {
                            // Firebase backend: a reset link was emailed, no code step.
                            Text(stringResource(R.string.account_reset_sent), style = MaterialTheme.typography.bodyMedium, color = VotifyColors.TextSecondary)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.account_reset_sent_sub), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(text = stringResource(R.string.account_back_to_login), loading = false, enabled = true) {
                                viewModel.setMode(AccountMode.Login)
                            }
                        } else {
                            Text(
                                if (state.backend == AccountBackend.Firebase) stringResource(R.string.account_forgot_hint_fb)
                                else stringResource(R.string.account_forgot_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = VotifyColors.TextMuted,
                            )
                            Spacer(Modifier.height(12.dp))
                            VotifyTextField(email, { email = it }, stringResource(R.string.account_email))
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(
                                text = stringResource(R.string.account_send_code),
                                loading = state.loading,
                                enabled = email.isNotBlank(),
                            ) { viewModel.sendResetCode(email) }
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = { viewModel.setMode(AccountMode.Login) },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) { Text(stringResource(R.string.account_back_to_login), color = VotifyColors.TextMuted) }
                        }
                    }

                    AccountMode.ForgotCode -> {
                        // The dev backend returns the code inline (SMTP is optional) — surface it.
                        Text(
                            if (state.resetCode != null) stringResource(R.string.account_dev_code, state.resetCode ?: "")
                            else stringResource(R.string.account_code_sent),
                            style = MaterialTheme.typography.bodySmall,
                            color = VotifyColors.TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        VotifyTextField(email, { email = it }, stringResource(R.string.account_email))
                        Spacer(Modifier.height(12.dp))
                        VotifyTextField(code, { code = it }, stringResource(R.string.account_code))
                        Spacer(Modifier.height(12.dp))
                        VotifyTextField(newPassword, { newPassword = it }, stringResource(R.string.account_new_password), password = true)
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            text = stringResource(R.string.account_reset_btn),
                            loading = state.loading,
                            enabled = email.isNotBlank() && code.isNotBlank() && newPassword.isNotBlank(),
                        ) { viewModel.resetPassword(email, code, newPassword) }
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = { viewModel.setMode(AccountMode.Login) },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text(stringResource(R.string.account_back_to_login), color = VotifyColors.TextMuted) }
                    }
                }

                // Google Sign-In (Firebase): Credential Manager → identitytoolkit.
                if (state.backend == AccountBackend.Firebase && (state.mode == AccountMode.Login || state.mode == AccountMode.Register)) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        onClick = {
                            // Guard against double taps: one Credential Manager request at a time.
                            val gmsOk = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                                .isGooglePlayServicesAvailable(context) ==
                                com.google.android.gms.common.ConnectionResult.SUCCESS
                            when {
                                googleInFlight -> Unit
                                state.googleClientId.isBlank() -> googleIdDialog = true
                                // Chinese ROMs often ship without Google Play services at all —
                                // detect it upfront instead of failing with a cryptic error.
                                !gmsOk -> googleNoGmsDialog = true
                                else -> {
                                    googleInFlight = true
                                    scope.launch {
                                        runCatching {
                                            val cm = androidx.credentials.CredentialManager.create(context)
                                            // GetSignInWithGoogleOption: the «Sign in with Google» bottom
                                            // sheet — works where the plain GoogleIdOption flow fails
                                            // with «No credentials available».
                                            val option = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
                                                .Builder(state.googleClientId)
                                                .build()
                                            val result = cm.getCredential(
                                                context,
                                                androidx.credentials.GetCredentialRequest(listOf(option)),
                                            )
                                            val cred = result.credential
                                            if (cred is androidx.credentials.CustomCredential &&
                                                cred.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                            ) {
                                                com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(cred.data).idToken
                                            } else null
                                        }.onSuccess { token ->
                                            googleInFlight = false
                                            if (token != null) viewModel.googleSignIn(token)
                                        }.onFailure { e ->
                                            googleInFlight = false
                                            when (e) {
                                                // Package+SHA not registered in Firebase, or no Google
                                                // account on the device — both fixable, explain.
                                                is androidx.credentials.exceptions.NoCredentialException -> googleHelpDialog = true
                                                is androidx.credentials.exceptions.GetCredentialProviderConfigurationException,
                                                is androidx.credentials.exceptions.GetCredentialUnsupportedException,
                                                -> googleNoGmsDialog = true
                                                else -> android.widget.Toast.makeText(
                                                    context,
                                                    context.getString(R.string.account_google_failed, e.message ?: ""),
                                                    android.widget.Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !googleInFlight,
                        shape = RoundedCornerShape(20.dp),
                        color = VotifyColors.TextPrimary,
                        contentColor = VotifyColors.PitchBlack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (googleInFlight) {
                                CircularProgressIndicator(
                                    color = VotifyColors.PitchBlack,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                stringResource(R.string.account_google),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.Error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Text(
            stringResource(R.string.account_hint),
            style = MaterialTheme.typography.bodySmall,
            color = VotifyColors.TextMuted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }

    if (googleIdDialog) {
        var draft by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(state.googleClientId) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { googleIdDialog = false },
            containerColor = VotifyColors.SurfaceContainerHigh,
            titleContentColor = VotifyColors.TextPrimary,
            textContentColor = VotifyColors.TextSecondary,
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.account_google_client_id)) },
            text = {
                Column {
                    Text("1. " + stringResource(R.string.account_google_step1), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextPrimary)
                    Text("2. " + stringResource(R.string.account_google_step2), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextPrimary)
                    Text("3. " + stringResource(R.string.account_google_step3), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextPrimary)
                    Text(
                        stringResource(R.string.account_google_open_console),
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                runCatching {
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://console.firebase.google.com/project/votify-f461a/authentication/providers"),
                                    ).let { context.startActivity(it) }
                                }
                            }
                            .padding(vertical = 6.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    VotifyTextField(draft, { draft = it }, "1234-abc.apps.googleusercontent.com")
                }
            },
            confirmButton = {
                Text(
                    stringResource(R.string.account_google_save_id),
                    color = VotifyColors.Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            viewModel.setGoogleClientId(draft)
                            googleIdDialog = false
                        }
                        .padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    stringResource(R.string.action_cancel),
                    color = VotifyColors.TextMuted,
                    modifier = Modifier.clickable { googleIdDialog = false }.padding(8.dp),
                )
            },
        )
    }

    if (googleHelpDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { googleHelpDialog = false },
            containerColor = VotifyColors.SurfaceContainerHigh,
            titleContentColor = VotifyColors.TextPrimary,
            textContentColor = VotifyColors.TextSecondary,
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.account_google_no_cred_title)) },
            text = {
                Text(
                    stringResource(R.string.account_google_no_cred_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextPrimary,
                )
            },
            confirmButton = {
                Text(
                    stringResource(R.string.account_google_no_cred_ok),
                    color = VotifyColors.Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { googleHelpDialog = false }
                        .padding(8.dp),
                )
            },
        )
    }

    if (googleNoGmsDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { googleNoGmsDialog = false },
            containerColor = VotifyColors.SurfaceContainerHigh,
            titleContentColor = VotifyColors.TextPrimary,
            textContentColor = VotifyColors.TextSecondary,
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.account_google_no_gms_title)) },
            text = {
                Text(
                    stringResource(R.string.account_google_no_gms_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextPrimary,
                )
            },
            confirmButton = {
                Text(
                    stringResource(R.string.account_google_no_cred_ok),
                    color = VotifyColors.Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { googleNoGmsDialog = false }
                        .padding(8.dp),
                )
            },
        )
    }
}

/** Segmented Login / Register tab. */
@Composable
private fun ModeTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) VotifyColors.Primary else VotifyColors.SurfaceContainerHigh,
        contentColor = if (selected) VotifyColors.OnPrimary else VotifyColors.TextSecondary,
        modifier = modifier.height(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Full-width pill CTA; swaps its label for a spinner while the request is in flight. */
@Composable
internal fun PrimaryButton(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        color = VotifyColors.Primary,
        contentColor = VotifyColors.OnPrimary,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (loading) {
                CircularProgressIndicator(color = VotifyColors.OnPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
