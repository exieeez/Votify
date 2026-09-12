package app.votify.mobile.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.votify.mobile.R
import app.votify.mobile.ui.theme.VotifyColors

/**
 * First-launch prompt shown once over the whole app: sign in (opens the account
 * screen — Google or email/password) or continue as a guest (one-time prompt) (everything works
 * without an account; sign-in stays available from Профиль).
 */
@Composable
fun WelcomeOverlay(onSignIn: () -> Unit, onGuest: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(VotifyColors.PitchBlack.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(28.dp)
                .widthIn(max = 420.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = VotifyColors.SurfaceContainer,
                border = BorderStroke(1.dp, VotifyColors.BorderSubtle),
            ) {
                Box(Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_votify_logo),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VotifyColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.welcome_text),
                style = MaterialTheme.typography.bodyMedium,
                color = VotifyColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Surface(
                onClick = onSignIn,
                shape = CircleShape,
                color = VotifyColors.Primary,
                contentColor = VotifyColors.OnPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.welcome_sign_in),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.welcome_guest),
                style = MaterialTheme.typography.bodyMedium,
                color = VotifyColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onGuest)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}
