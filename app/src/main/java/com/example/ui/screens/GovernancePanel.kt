package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DatabaseEncryption
import com.example.model.AuditChainStatus
import com.example.model.AuditLogEntity
import com.example.model.ConsentMethod
import com.example.model.ProjectEntity
import com.example.model.SubjectProfile
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
private fun fmt(millis: Long): String = if (millis <= 0L) "—" else stamp.format(Date(millis))

/**
 * Governance tab (roadmap Phase 2): consent record, chain of custody, erasure, retention, app lock,
 * and the hash-chained audit log with its verification status.
 */
@Composable
fun GovernancePanel(
    subject: SubjectProfile,
    auditEntries: List<AuditLogEntity>,
    auditChain: AuditChainStatus?,
    retentionDays: Int,
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    onDeleteSubject: () -> Unit,
    onSetRetentionDays: (Int) -> Unit,
    onSetAppLock: (Boolean) -> Unit,
    onVerifyAuditChain: () -> Unit,
    modifier: Modifier = Modifier,
    storageEncryption: DatabaseEncryption.Status = DatabaseEncryption.status,
    projectControls: ProjectControls? = null
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeleteProject by remember { mutableStateOf(false) }
    val subjectRef = remember(subject.id) { AuditLogEntity.subjectRefFor(subject.id) }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("governance_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 0. Project (isolation boundary)
        val controls = projectControls
        val project = controls?.active
        if (controls != null && project != null) {
            item {
                GovCard("PROJECT · ${project.name}", CyanTelemetry) {
                    GovLine("Isolation", "Subjects, artifacts, graph and triplets stay in this project")
                    GovLine("Retention", if (project.retentionDays > 0) "${project.retentionDays} days" else "Keep")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Links to other projects", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Mind maps may link to subjects in other projects only when both projects allow it",
                                color = TextSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                        Switch(
                            checked = project.allowCrossProjectLinks,
                            onCheckedChange = controls.onSetCrossLinks,
                            colors = SwitchDefaults.colors(checkedTrackColor = PhosphorGreenDim, checkedThumbColor = PhosphorGreen),
                            modifier = Modifier.testTag("gov_cross_links_switch")
                        )
                    }
                    if (project.id != ProjectEntity.DEFAULT_ID) {
                        GovButton("ERASE PROJECT", AlarmRed, Modifier.testTag("gov_delete_project_btn")) {
                            confirmDeleteProject = true
                        }
                    }
                }
            }
        }

        // 1. Consent record
        item {
            GovCard("CONSENT RECORD", PhosphorGreen) {
                val method = ConsentMethod.fromName(subject.consentMethod)
                GovLine("Status", subject.consentStatus.label + if (subject.consentExpired) " (EXPIRED)" else "")
                GovLine("Method", method?.label ?: "None recorded")
                GovLine("Recorded", fmt(subject.consentRecordedAt))
                GovLine("Expires", if (subject.consentExpiresAt > 0) fmt(subject.consentExpiresAt) else "No expiry")
                GovLine("Restricted outputs", if (subject.restrictedOutputsUnlocked) "Computed" else "Withheld")
            }
        }

        // 2. Chain of custody
        item {
            val withCustody = subject.scrapedArtifacts.filter { it.rawResponseSha256.isNotBlank() }
            GovCard("CHAIN OF CUSTODY", CyanTelemetry) {
                GovLine("Artifacts", "${subject.scrapedArtifacts.size} stored, ${withCustody.size} with custody")
                GovLine("Provenance", subject.dataProvenance.ifBlank { "Unknown" }.take(120))
                withCustody.take(4).forEach { art ->
                    Text(
                        text = "${art.platform} · HTTP ${art.httpStatus} · ${fmt(art.fetchedAtMillis)}\n${art.sourceUrl.take(70)}\nsha256 ${art.rawResponseSha256.take(16)}… · ${art.connectorRunId.take(28)}",
                        color = TextSecondary,
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBg, RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    )
                }
                if (withCustody.isEmpty()) {
                    Text(
                        "No custody records yet: demo data, manual entries and data collected before v3.7 have none.",
                        color = TextMuted,
                        fontSize = 9.5.sp
                    )
                }
            }
        }

        // 3. Erasure
        item {
            GovCard("ERASURE", AlarmRed) {
                Text(
                    "Permanently erase this subject: profile, collected artifacts, linguistic profile, mind-map edges, knowledge triplets and cached exports. The audit log keeps only a pseudonymous reference.",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                GovButton("ERASE ${subject.handle}", AlarmRed, Modifier.testTag("gov_delete_subject_btn")) {
                    confirmDelete = true
                }
            }
        }

        // 4. Retention + app lock
        item {
            GovCard("RETENTION & DEVICE PROTECTION", AmberSignal) {
                Text("Erase this project's subjects not updated within:", color = TextSecondary, fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "KEEP", 30 to "30 D", 90 to "90 D", 365 to "1 YR").forEach { (days, label) ->
                        val selected = retentionDays == days
                        Box(
                            modifier = Modifier
                                .testTag("gov_retention_$days")
                                .background(if (selected) AmberSignal else ObsidianBg, RoundedCornerShape(4.dp))
                                .border(1.dp, if (selected) AmberSignal else PhosphorGreenDim, RoundedCornerShape(4.dp))
                                .clickable(enabled = !selected) { onSetRetentionDays(days) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = if (selected) ObsidianBg else PhosphorGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App lock", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (appLockAvailable) "Ask for biometric or screen lock on launch and on return"
                            else "Unavailable: set a screen lock on this device first",
                            color = TextSecondary,
                            fontSize = 9.5.sp
                        )
                    }
                    Switch(
                        checked = appLockEnabled,
                        onCheckedChange = onSetAppLock,
                        enabled = appLockAvailable || appLockEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = PhosphorGreenDim, checkedThumbColor = PhosphorGreen),
                        modifier = Modifier.testTag("gov_app_lock_switch")
                    )
                }
                Column(modifier = Modifier.testTag("gov_storage_encryption")) {
                    Text("Storage", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        storageEncryption.label,
                        color = if (storageEncryption == DatabaseEncryption.Status.ENCRYPTED) PhosphorGreen else AmberSignal,
                        fontSize = 9.5.sp
                    )
                }
            }
        }

        // 5. Audit log
        item {
            GovCard("AUDIT LOG (APPEND-ONLY, HASH-CHAINED)", VioletHf) {
                val chainText = when {
                    auditChain == null -> "Not verified yet"
                    auditChain.intact -> "CHAIN INTACT · ${auditChain.entries} entries verified"
                    else -> "CHAIN BROKEN at entry #${auditChain.firstBrokenSeq} of ${auditChain.entries}"
                }
                Text(
                    chainText,
                    color = if (auditChain?.intact == false) AlarmRed else PhosphorGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("gov_audit_chain_status")
                )
                GovButton("RE-VERIFY CHAIN", VioletHf, Modifier.testTag("gov_verify_chain_btn"), onVerifyAuditChain)
                if (auditEntries.isEmpty()) {
                    Text("No entries yet.", color = TextMuted, fontSize = 9.5.sp)
                }
            }
        }
        items(auditEntries.take(30), key = { it.seq }) { entry ->
            val mine = entry.subjectRef.isNotBlank() && entry.subjectRef == subjectRef
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianPanel, RoundedCornerShape(4.dp))
                    .border(1.dp, if (mine) VioletHf.copy(alpha = 0.6f) else PhosphorGreenDim.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    "#${entry.seq} · ${fmt(entry.timestampMillis)} · ${entry.action}${if (mine) " · THIS SUBJECT" else ""}",
                    color = AmberSignal,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(entry.detail, color = TextPrimary, fontSize = 10.sp, lineHeight = 13.sp)
                Text(
                    "hash ${entry.entryHash.take(12)}… ← prev ${entry.prevHash.take(12)}…",
                    color = TextMuted,
                    fontSize = 8.5.sp
                )
            }
        }
    }

    val deletingProject = projectControls?.active
    if (confirmDeleteProject && projectControls != null && deletingProject != null) {
        val project = deletingProject
        AlertDialog(
            onDismissRequest = { confirmDeleteProject = false },
            containerColor = ObsidianPanel,
            title = { Text("ERASE PROJECT \"${project.name}\"?", color = AlarmRed, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                Text(
                    "Every subject in this project is erased with its artifacts, linguistic profile, mind-map edges, " +
                        "triplets and cached exports. Other projects are not touched. This cannot be undone.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteProject = false
                        projectControls.onDeleteProject()
                    },
                    modifier = Modifier.testTag("gov_delete_project_confirm")
                ) { Text("ERASE PROJECT", color = AlarmRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteProject = false }) { Text("CANCEL") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Erase ${subject.handle}?") },
            text = {
                Text(
                    "This permanently deletes the profile, ${subject.scrapedArtifacts.size} collected artifacts, the linguistic profile, " +
                        "graph edges, triplets and cached exports. It cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteSubject()
                    },
                    modifier = Modifier.testTag("gov_delete_confirm_btn")
                ) { Text("ERASE PERMANENTLY", color = AlarmRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
private fun GovCard(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianPanel, RoundedCornerShape(6.dp))
            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun GovLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 10.sp)
        Text(value, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GovButton(label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(ObsidianBg, RoundedCornerShape(4.dp))
            .border(1.dp, accent, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, color = accent, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
    }
}
