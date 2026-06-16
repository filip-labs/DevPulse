/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.ui.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

data class DevPulseDashboardUiState(
    val status: DevPulseUiStatus,
    val currentFileName: String?,
    val currentFileFocusTime: String,
    val totalFocusTime: String,
    val completedSessions: Int,
    val pomodoroTime: String,
    val pomodoroState: String,
    val typedChars: Int,
    val typedPercent: Int,
    val pastedChars: Int,
    val pastedPercent: Int,
    val insertedChars: Int,
    val insertedPercent: Int,
    val topFiles: List<DevPulseTopFileUiState>
)

data class DevPulseTopFileUiState(
    val rank: Int,
    val fileName: String,
    val focusTime: String
)

enum class DevPulseUiStatus(
    val label: String,
    val color: Color
) {
    ACTIVE("Active", Color(0xFF6A9955)),
    IDLE("Idle", Color(0xFFB88A33)),
    INACTIVE("Inactive", Color(0xFFB85C5C)),
    WAITING("Waiting for activation", Color(0xFF7A7A7A))
}

object DevPulsePreviewStates {
    fun active(): DevPulseDashboardUiState = DevPulseDashboardUiState(
        status = DevPulseUiStatus.ACTIVE,
        currentFileName = "DevPulseDashboardPanel.kt",
        currentFileFocusTime = "00:12:34",
        totalFocusTime = "01:24:10",
        completedSessions = 2,
        pomodoroTime = "25:00",
        pomodoroState = "Ready",
        typedChars = 1_280,
        typedPercent = 72,
        pastedChars = 180,
        pastedPercent = 10,
        insertedChars = 320,
        insertedPercent = 18,
        topFiles = listOf(
            DevPulseTopFileUiState(1, "DevPulseDashboardPanel.kt", "00:32:18"),
            DevPulseTopFileUiState(2, "PasteActionTracker.kt", "00:18:04"),
            DevPulseTopFileUiState(3, "ActiveFileTracker.kt", "00:12:56")
        )
    )

    fun waiting(): DevPulseDashboardUiState = DevPulseDashboardUiState(
        status = DevPulseUiStatus.WAITING,
        currentFileName = null,
        currentFileFocusTime = "00:00:00",
        totalFocusTime = "00:00:00",
        completedSessions = 0,
        pomodoroTime = "25:00",
        pomodoroState = "Ready",
        typedChars = 0,
        typedPercent = 0,
        pastedChars = 0,
        pastedPercent = 0,
        insertedChars = 0,
        insertedPercent = 0,
        topFiles = emptyList()
    )

    fun emptyStatistics(): DevPulseDashboardUiState = active().copy(
        currentFileFocusTime = "00:00:00",
        totalFocusTime = "00:00:00",
        completedSessions = 0,
        typedChars = 0,
        typedPercent = 0,
        pastedChars = 0,
        pastedPercent = 0,
        insertedChars = 0,
        insertedPercent = 0,
        topFiles = emptyList()
    )

    fun pomodoroRunning(): DevPulseDashboardUiState = active().copy(
        pomodoroTime = "23:42",
        pomodoroState = "Work session"
    )
}

@Composable
fun DevPulseDashboard(
    state: DevPulseDashboardUiState,
    onStartPomodoro: () -> Unit = {},
    onResetPomodoro: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .width(340.dp)
            .background(DashboardBackground)
            .border(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Header(state, onOpenSettings)
            CurrentFocusCard(state)
            FocusSummaryCard(state)
            PomodoroCard(state, onStartPomodoro, onResetPomodoro)
            EditBreakdownCard(state)
            TopFilesCard(state.topFiles)
        }
    }
}

@Composable
private fun Header(
    state: DevPulseDashboardUiState,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp, 2.dp, 2.dp, 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DevPulse",
                    color = PrimaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(state.status)
            }
            Spacer(Modifier.height(3.dp))
            Text("Today’s coding pulse", color = SecondaryText, fontSize = 13.sp)
        }
        PulseLine(
            color = when (state.status) {
                DevPulseUiStatus.ACTIVE -> TypedAccent
                DevPulseUiStatus.IDLE -> state.status.color
                DevPulseUiStatus.INACTIVE,
                DevPulseUiStatus.WAITING -> state.status.color
            }
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "⚙",
            color = SecondaryText,
            fontSize = 18.sp,
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onOpenSettings)
                .padding(top = 2.dp)
        )
    }
}

@Composable
private fun StatusPill(status: DevPulseUiStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(status.color, status == DevPulseUiStatus.ACTIVE)
        Spacer(Modifier.width(6.dp))
        Text(status.label, color = status.color, fontSize = 13.sp)
    }
}

@Composable
private fun CurrentFocusCard(state: DevPulseDashboardUiState) {
    SectionCard(title = "Current Focus", accent = state.status.color) {
        if (state.currentFileName == null) {
            Row(verticalAlignment = Alignment.Top) {
                Text("◇", color = SecondaryText, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("No active file", color = WaitingText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text("Open a file and start typing to begin tracking.", color = SecondaryText, fontSize = 13.sp)
                }
            }
        } else {
            Text(
                text = "▣ ${state.currentFileName}",
                color = TypedAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            LabelValueRow("Focused for", state.currentFileFocusTime)
            Spacer(Modifier.height(6.dp))
            LabelValueRow("Tracking status", "● ${state.status.label}", state.status.color)
        }
    }
}

@Composable
private fun FocusSummaryCard(state: DevPulseDashboardUiState) {
    SectionCard(title = "Focus Summary") {
        LabelValueRow("Total focus time", state.totalFocusTime, if (state.status == DevPulseUiStatus.WAITING) WaitingText else PrimaryText)
        Spacer(Modifier.height(6.dp))
        LabelValueRow("Completed sessions", state.completedSessions.toString(), if (state.status == DevPulseUiStatus.WAITING) WaitingText else PrimaryText)
        Spacer(Modifier.height(6.dp))
        LabelValueRow("Current state", state.status.label, state.status.color)
    }
}

@Composable
private fun PomodoroCard(
    state: DevPulseDashboardUiState,
    onStartPomodoro: () -> Unit,
    onResetPomodoro: () -> Unit
) {
    val running = state.pomodoroState != "Ready"
    val progress = if (running) 8 else 0

    SectionCard(title = "Pomodoro") {
        Text(
            text = state.pomodoroTime,
            color = if (running) PrimaryText else WaitingText,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (running) DevPulseUiStatus.ACTIVE.color else DevPulseUiStatus.WAITING.color, running)
            Spacer(Modifier.width(6.dp))
            Text(
                text = state.pomodoroState,
                color = if (running) DevPulseUiStatus.ACTIVE.color else DevPulseUiStatus.WAITING.color,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        ProgressLine(progress, PomodoroAccent)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactButton(if (running) "Pause" else "Start", onStartPomodoro, running)
            CompactButton("Reset", onResetPomodoro, false)
        }
    }
}

@Composable
private fun EditBreakdownCard(state: DevPulseDashboardUiState) {
    SectionCard(title = "Edit Breakdown") {
        BreakdownRow("✎", "Typed", state.typedChars, state.typedPercent, TypedAccent)
        Spacer(Modifier.height(8.dp))
        BreakdownRow("▣", "Pasted", state.pastedChars, state.pastedPercent, PastedAccent)
        Spacer(Modifier.height(8.dp))
        BreakdownRow("ƒ", "Inserted", state.insertedChars, state.insertedPercent, InsertedAccent)
    }
}

@Composable
private fun TopFilesCard(topFiles: List<DevPulseTopFileUiState>) {
    SectionCard(title = "Top Files / Classes") {
        if (topFiles.isEmpty()) {
            Text(
                text = "No focus data yet.",
                color = SecondaryText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                fontSize = 13.sp
            )
        } else {
            topFiles.forEachIndexed { index, file ->
                TopFileRow(file)
                if (index < topFiles.lastIndex) {
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground)
            .border(1.dp, BorderColor)
    ) {
        if (accent != null) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(86.dp)
                    .background(accent)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(10.dp, 9.dp)
        ) {
            Text(title.uppercase(), color = SecondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LabelValueRow(
    label: String,
    value: String,
    valueColor: Color = PrimaryText
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = SecondaryText, fontSize = 13.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun BreakdownRow(
    icon: String,
    label: String,
    chars: Int,
    percent: Int,
    accent: Color
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$icon  $label", color = SecondaryText, modifier = Modifier.width(86.dp), fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text("%,d chars".format(chars), color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text("$percent%", color = SecondaryText, fontSize = 13.sp)
    }
    Spacer(Modifier.height(4.dp))
    ProgressLine(percent, accent)
}

@Composable
private fun TopFileRow(file: DevPulseTopFileUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(file.rank.toString(), color = SecondaryText, modifier = Modifier.width(24.dp), fontSize = 13.sp)
        Text(
            text = "▣ ${file.fileName}",
            color = TypedAccent,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp
        )
        Text(file.focusTime, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun CompactButton(
    text: String,
    onClick: () -> Unit,
    emphasized: Boolean
) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF303438))
            .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (emphasized) PrimaryText else PomodoroAccent,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun StatusDot(
    color: Color,
    active: Boolean
) {
    Box(
        modifier = Modifier
            .size(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (active) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.22f))
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun ProgressLine(
    percent: Int,
    accent: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BorderColor.copy(alpha = 0.6f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(6.dp)
                .background(accent)
        )
    }
}

@Composable
private fun PulseLine(color: Color) {
    Canvas(modifier = Modifier.size(62.dp, 24.dp)) {
        val mid = size.height / 2f
        val path = Path().apply {
            moveTo(4f, mid)
            lineTo(size.width / 6f, mid)
            lineTo(size.width / 4f, mid - 10f)
            lineTo(size.width / 3f, mid + 8f)
            lineTo(size.width / 2f, mid)
            lineTo(size.width * 2f / 3f, mid)
            lineTo(size.width * 3f / 4f, mid - 6f)
            lineTo(size.width * 5f / 6f, mid + 6f)
            lineTo(size.width - 4f, mid)
        }
        drawPath(path, color, style = Stroke(width = 1.4f, cap = StrokeCap.Round))
        drawCircle(color, radius = 2.5f, center = Offset(size.width - 4f, mid))
    }
}

@Preview
@Composable
fun DevPulseDashboardActivePreview() {
    DevPulseDashboard(
        state = DevPulsePreviewStates.active()
    )
}

@Preview
@Composable
fun DevPulseDashboardWaitingPreview() {
    DevPulseDashboard(
        state = DevPulsePreviewStates.waiting()
    )
}

@Preview
@Composable
fun DevPulseDashboardEmptyStatisticsPreview() {
    DevPulseDashboard(
        state = DevPulsePreviewStates.emptyStatistics()
    )
}

@Preview
@Composable
fun DevPulseDashboardPomodoroRunningPreview() {
    DevPulseDashboard(
        state = DevPulsePreviewStates.pomodoroRunning()
    )
}

private val DashboardBackground = Color(0xFF2B2D30)
private val CardBackground = Color(0xFF232629)
private val BorderColor = Color(0xFF3C3F41)
private val PrimaryText = Color(0xFFD6D6D6)
private val SecondaryText = Color(0xFF8F9499)
private val WaitingText = Color(0xFF7F858A)
private val TypedAccent = Color(0xFF5EB6D8)
private val PastedAccent = Color(0xFFD08A3D)
private val InsertedAccent = Color(0xFFA67FD6)
private val PomodoroAccent = Color(0xFF6DA869)

@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PrimaryText,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight
        )
    )
}
