package com.studyapp.pomodoro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.studyapp.pomodoro.ads.AdsConsentManager
import com.studyapp.pomodoro.ui.theme.Border
import com.studyapp.pomodoro.ui.theme.Forest
import com.studyapp.pomodoro.ui.theme.Ink
import com.studyapp.pomodoro.ui.theme.MintLight
import com.studyapp.pomodoro.ui.theme.Muted
import com.studyapp.pomodoro.ui.theme.Paper
import com.studyapp.pomodoro.ui.theme.Parchment

private const val PrivacyPolicyUrl = "https://drankit2029-sys.github.io/pomodoro-privacy/"

private data class PolicyClause(
    val eyebrow: String,
    val title: String,
    val body: String,
)

private val PolicyClauses = listOf(
    PolicyClause(
        eyebrow = "01 · CONTROLLER, CONTACT & SCOPE",
        title = "Developer Identity & Local-First Sanctuary",
        body = "Pomodoro (com.studyapp.pomodoro) is developed and published by Nexdert (Privacy Point of Contact: noxedus2008@gmail.com). All study features are unlocked for every user with zero subscriptions, user accounts, or proprietary cloud servers.",
    ),
    PolicyClause(
        eyebrow = "02 · ON-DEVICE STUDY LEDGER",
        title = "Data Stored Locally on Your Device (Never Shared)",
        body = "Your completed focus sessions, focused minutes, Flow Overtime credits, 30-day & 365-day heatmaps, study subject tags, daily tasks, and alarm/sound settings are saved exclusively inside your device's private sandbox (SharedPreferences). Tapping CSV exports your ledger to Downloads or Android's share sheet only when you initiate it.",
    ),
    PolicyClause(
        eyebrow = "03 · GOOGLE PLAY DATA SAFETY & TLS SECURITY",
        title = "Personal & Sensitive Data Collected, Shared & Encrypted",
        body = "We never collect or share your study logs. To fund the app via advertisements, the embedded Google Mobile Ads (AdMob) and UMP SDKs collect and share with Google AdMob and consented ad technology providers: Device or other IDs (Advertising ID / AD_ID), Approximate Location (derived from IP address only, never GPS), App Activity (ad views and clicks), and App Info & Performance (SDK diagnostics). All SDK network traffic is encrypted in transit via HTTPS/TLS.",
    ),
    PolicyClause(
        eyebrow = "04 · EXCLUSIVE ADVERTISING & EU USER CONSENT",
        title = "Google AdMob Banner & Post-Focus Interstitial Ads",
        body = "Pomodoro shows a single sponsored banner on the timer completion screen and a short interstitial video when a Focus session ends and a break begins. No ads appear during a running timer, in notifications, or on the home-screen widget. In compliance with Google's EU User Consent Policy (GDPR / UK GDPR / ePrivacy), Google's User Messaging Platform (UMP) requests affirmative consent before using local storage or personal data for personalized or non-personalized ads, and the in-app Privacy choices entry lets you change that consent at any time.",
    ),
    PolicyClause(
        eyebrow = "05 · ACCESSIBILITYSERVICE API DISCLOSURE",
        title = "Focus Mode & FocusLockAccessibilityService",
        body = "Pomodoro is not a disability tool (android:isAccessibilityTool=\"false\"). When you explicitly accept the in-app Prominent Disclosure and enable Pomodoro in Android Accessibility Settings, FocusLockAccessibilityService listens only for window state changes and navigation keys (Back, Home, Recents, notification shade) to prevent exiting the app during Focus Mode while exempting phone calls and emergency dialers. Configured with canRetrieveWindowContent=\"false\", it never reads screen text, keystrokes, or passwords, and never collects, stores, or shares any data.",
    ),
    PolicyClause(
        eyebrow = "06 · SYSTEM PERMISSIONS & PROCEDURAL AUDIO",
        title = "Notifications, Foreground Timer & Zero Microphone Access",
        body = "Pomodoro uses POST_NOTIFICATIONS, FOREGROUND_SERVICE_SPECIAL_USE, WAKE_LOCK (capped at 2 hours), REORDER_TASKS, and VIBRATE to keep your countdown accurate and ring completion alarms. All ambient soundscapes (Soft rain, Brown noise, Library murmur, Warm hearth, Forest wind) are synthesized mathematically on-device via AudioTrack with zero microphone or camera access.",
    ),
    PolicyClause(
        eyebrow = "07 · DATA RETENTION, DELETION & USER CHOICES",
        title = "365-Day On-Device Retention & Instant Deletion Controls",
        body = "Local study history is retained on-device for up to 365 days and pruned automatically. Because Pomodoro has no user accounts or developer servers, server retention is 0 days. You can erase local data anytime via RESET TODAY or Android Settings → Apps → Pomodoro → Storage → Clear storage, manage ad consent via Alarm Settings → Privacy choices, or reset/delete your Advertising ID in Android Settings → Google → Ads.",
    ),
    PolicyClause(
        eyebrow = "08 · CHILDREN'S PRIVACY & POLICY UPDATES",
        title = "General Audience Utility & Contact Mechanism",
        body = "Pomodoro is a general-audience study utility not directed to children under 13. Material updates to this policy are reflected by updating the effective date (October 30, 2026). For privacy questions or data inquiries, contact Nexdert at noxedus2008@gmail.com.",
    ),
)

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit,
    showPrivacyOptions: Boolean = false,
    onOpenPrivacyOptions: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val maxDialogHeight = (configuration.screenHeightDp.dp - 32.dp).coerceAtLeast(260.dp)
    val uriHandler = LocalUriHandler.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .softRaised(Paper, 28.dp, 20.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MintLight, CircleShape)
                            .border(1.dp, Forest.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Forest,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CALM / SYSTEM · DATA SANCTUARY",
                            color = Forest,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.4.sp,
                            ),
                        )
                        Text(
                            text = "Privacy policy",
                            color = Ink,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 28.sp,
                                lineHeight = 32.sp,
                            ),
                        )
                    }
                    SoftIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "Close privacy policy",
                        onClick = onDismiss,
                        size = 40.dp,
                        iconSize = 16.dp,
                    )
                }

                HorizontalDivider(color = Border, thickness = 1.dp)

                PolicyClauses.forEach { clause ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Parchment, RoundedCornerShape(16.dp))
                            .border(1.dp, Border, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = clause.eyebrow,
                            color = Forest,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                letterSpacing = 1.2.sp,
                            ),
                        )
                        Text(
                            text = clause.title,
                            color = Ink,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Text(
                            text = clause.body,
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                lineHeight = 16.5.sp,
                            ),
                        )
                    }
                }

                SoftButton(
                    label = "Read the full policy online",
                    onClick = {
                        AdsConsentManager.onExternalAdOrPolicyNavigationStarted()
                        uriHandler.openUri(PrivacyPolicyUrl)
                    },
                    variant = SoftButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = PrivacyPolicyUrl,
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    ),
                )

                if (showPrivacyOptions) {
                    SoftButton(
                        label = "Manage ad consent preferences",
                        onClick = onOpenPrivacyOptions,
                        variant = SoftButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SoftButton(
                    label = "Done",
                    onClick = onDismiss,
                    variant = SoftButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
