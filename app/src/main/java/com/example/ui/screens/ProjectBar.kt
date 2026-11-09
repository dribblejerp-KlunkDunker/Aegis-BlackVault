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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProjectEntity
import com.example.ui.theme.*

/** Everything the project controls need, passed as one value so screens don't grow ten parameters. */
data class ProjectControls(
    val projects: List<ProjectEntity>,
    val activeProjectId: String,
    val onSwitch: (String) -> Unit,
    val onCreate: (String) -> Unit,
    val onSetCrossLinks: (Boolean) -> Unit,
    val onDeleteProject: () -> Unit
) {
    val active: ProjectEntity? get() = projects.find { it.id == activeProjectId }
}

/** Always-visible strip naming the active project, with a switcher and "new project". */
@Composable
fun ProjectBar(controls: ProjectControls, subjectCount: Int, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianPanel)
            .border(1.dp, PhosphorGreenDim)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("project_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("PROJECT", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = "${controls.active?.name ?: "…"} ▾  ·  $subjectCount subject(s)",
                color = PhosphorGreen,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable { menuOpen = true }
                    .padding(vertical = 4.dp)
                    .testTag("project_menu_btn")
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                controls.projects.forEach { project ->
                    val selected = project.id == controls.activeProjectId
                    DropdownMenuItem(
                        text = {
                            Text(
                                (if (selected) "● " else "○ ") + project.name,
                                color = if (selected) PhosphorGreen else TextPrimary,
                                fontSize = 11.sp
                            )
                        },
                        onClick = {
                            menuOpen = false
                            if (!selected) controls.onSwitch(project.id)
                        },
                        modifier = Modifier.testTag("project_option_${project.id}")
                    )
                }
            }
        }
        Text(
            "+ NEW",
            color = CyanTelemetry,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .border(1.dp, CyanTelemetry, RoundedCornerShape(4.dp))
                .clickable { creating = true }
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .testTag("project_new_btn")
        )
    }

    if (creating) {
        NewProjectDialog(
            onDismiss = { creating = false },
            onCreate = { name ->
                creating = false
                controls.onCreate(name)
            }
        )
    }
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianPanel,
        title = { Text("NEW PROJECT", color = CyanTelemetry, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Subjects, artifacts and graph links stay inside their project. Links to other projects " +
                        "are off unless both projects allow them. Retention is set per project.",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    label = { Text("Project name", fontSize = 10.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanTelemetry,
                        unfocusedBorderColor = PhosphorGreenDim
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("project_new_name")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("project_new_confirm")
            ) { Text("CREATE", color = if (name.isNotBlank()) CyanTelemetry else TextMuted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

/** Shown instead of an endless spinner when the active project has no subjects yet. */
@Composable
fun EmptyProjectState(
    projectName: String,
    scraperUri: String,
    isScraping: Boolean,
    onUriChange: (String) -> Unit,
    onCollect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .background(ObsidianPanel, RoundedCornerShape(8.dp))
                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(8.dp))
                .padding(16.dp)
                .testTag("empty_project_state"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "“$projectName” has no subjects yet",
                color = PhosphorGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Collect from a public GitHub or Hugging Face account to start. New subjects start as Unverified: " +
                    "restricted outputs stay withheld until you record consent.",
                color = TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center
            )
            OutlinedTextField(
                value = scraperUri,
                onValueChange = onUriChange,
                singleLine = true,
                label = { Text("https://github.com/<handle>", fontSize = 10.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PhosphorGreen,
                    unfocusedBorderColor = PhosphorGreenDim
                ),
                modifier = Modifier.fillMaxWidth().testTag("empty_project_uri")
            )
            Text(
                if (isScraping) "COLLECTING…" else "COLLECT",
                color = if (scraperUri.isNotBlank() && !isScraping) ObsidianBg else TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(
                        if (scraperUri.isNotBlank() && !isScraping) PhosphorGreen else ObsidianBg,
                        RoundedCornerShape(4.dp)
                    )
                    .border(1.dp, PhosphorGreenDim, RoundedCornerShape(4.dp))
                    .clickable(enabled = scraperUri.isNotBlank() && !isScraping, onClick = onCollect)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("empty_project_collect")
            )
        }
    }
}
