/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.toolWindow

import com.github.filiplabs.devpulse.model.DayStats
import com.github.filiplabs.devpulse.model.EditType
import com.github.filiplabs.devpulse.model.FocusStatus
import com.github.filiplabs.devpulse.model.PomodoroState
import com.github.filiplabs.devpulse.services.DevPulsePomodoroService
import com.github.filiplabs.devpulse.storage.DevPulseStatsService
import com.github.filiplabs.devpulse.tracking.ActiveFileTracker
import com.github.filiplabs.devpulse.tracking.DevPulseFocusTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.GridLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.Timer

class DevPulseDashboardPanel(
    private val project: Project
) : JBPanel<DevPulseDashboardPanel>(BorderLayout()), Disposable {

    private val statsService = project.service<DevPulseStatsService>()
    private val activeFileTracker = project.service<ActiveFileTracker>()
    private val focusTracker = project.service<DevPulseFocusTracker>()
    private val pomodoroService = project.service<DevPulsePomodoroService>()

    private val emptyStateLabel = JBLabel("Start typing, pasting, or running a focus session to populate today's dashboard.")
    private val focusTotalValue = JBLabel()
    private val activeFileValue = JBLabel()
    private val focusStatusValue = JBLabel()
    private val pomodoroStateValue = JBLabel()
    private val pomodoroRemainingValue = JBLabel()
    private val pomodoroSessionsValue = JBLabel()
    private val typedValue = JBLabel()
    private val pastedValue = JBLabel()
    private val insertedValue = JBLabel()
    private val startStopButton = JButton("Start")
    private val resetButton = JButton("Reset")
    private val topFilesModel = DefaultListModel<String>()
    private val topFilesList = JBList(topFilesModel)
    private val refreshTimer = Timer(1_000) { refreshUi() }

    init {
        border = JBUI.Borders.empty(12)

        val contentPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty()
        }

        contentPanel.add(createHeaderPanel())
        contentPanel.add(Box.createVerticalStrut(8))
        contentPanel.add(emptyStateLabel)
        contentPanel.add(Box.createVerticalStrut(8))
        contentPanel.add(createFocusSection())
        contentPanel.add(Box.createVerticalStrut(8))
        contentPanel.add(createPomodoroSection())
        contentPanel.add(Box.createVerticalStrut(8))
        contentPanel.add(createTopFilesSection())
        contentPanel.add(Box.createVerticalStrut(8))
        contentPanel.add(createEditBreakdownSection())
        contentPanel.add(Box.createVerticalGlue())

        topFilesList.isFocusable = false
        topFilesList.visibleRowCount = 5
        topFilesList.fixedCellHeight = 24

        add(JBScrollPane(contentPanel), BorderLayout.CENTER)

        startStopButton.addActionListener {
            pomodoroService.toggle()
            refreshUi()
        }
        resetButton.addActionListener {
            pomodoroService.reset()
            refreshUi()
        }

        refreshTimer.start()
        refreshUi()
    }

    override fun dispose() {
        refreshTimer.stop()
    }

    private fun createHeaderPanel(): JPanel {
        val headerPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.emptyBottom(4)
        }

        val titleLabel = JBLabel("DevPulse")
        val subtitleLabel = JBLabel("Today's coding pulse")

        headerPanel.add(titleLabel)
        headerPanel.add(Box.createVerticalStrut(2))
        headerPanel.add(subtitleLabel)
        return headerPanel
    }

    private fun createFocusSection(): JPanel {
        return createSectionPanel(
            "Focus Today",
            createMetricGrid(
                "Total focus time" to focusTotalValue,
                "Current file/class" to activeFileValue,
                "Status" to focusStatusValue
            )
        )
    }

    private fun createPomodoroSection(): JPanel {
        val content = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(
                createMetricGrid(
                    "Timer state" to pomodoroStateValue,
                    "Remaining time" to pomodoroRemainingValue,
                    "Completed sessions" to pomodoroSessionsValue
                ),
                BorderLayout.CENTER
            )
        }

        val buttonRow = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.emptyTop(8)
            add(startStopButton)
            add(Box.createHorizontalStrut(8))
            add(resetButton)
            add(Box.createHorizontalGlue())
        }

        content.add(buttonRow, BorderLayout.SOUTH)
        return createSectionPanel("Pomodoro", content)
    }

    private fun createTopFilesSection(): JPanel {
        val scrollPane = JBScrollPane(topFilesList).apply {
            preferredSize = Dimension(320, 140)
        }

        return createSectionPanel("Top Files/Classes by Focus", scrollPane)
    }

    private fun createEditBreakdownSection(): JPanel {
        return createSectionPanel(
            "Edit Breakdown",
            createMetricGrid(
                "Typed characters" to typedValue,
                "Pasted characters" to pastedValue,
                "Inserted characters" to insertedValue
            )
        )
    }

    private fun createSectionPanel(
        title: String,
        content: Component
    ): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border = BorderFactory.createCompoundBorder(
                IdeBorderFactory.createTitledBorder(title, false),
                JBUI.Borders.empty(8)
            )
            add(content, BorderLayout.CENTER)
        }
    }

    private fun createMetricGrid(vararg entries: Pair<String, JBLabel>): JPanel {
        return JBPanel<JBPanel<*>>().apply {
            layout = GridLayout(entries.size, 2, 12, 6)
            entries.forEach { (labelText, valueLabel) ->
                add(JBLabel(labelText))
                add(valueLabel)
            }
        }
    }

    private fun refreshUi() {
        val stats = statsService.getTodayStats()
        val pomodoro = pomodoroService.getSnapshot()
        val activeFileName = activeFileTracker.getActiveFileName() ?: "No active file"
        val focusStatus = focusTracker.getStatus()

        emptyStateLabel.isVisible = !statsService.hasAnyData()
        focusTotalValue.text = formatDuration(stats.totalFocusSeconds)
        activeFileValue.text = activeFileName
        focusStatusValue.text = when (focusStatus) {
            FocusStatus.ACTIVE -> "Active"
            FocusStatus.IDLE -> "Idle"
            FocusStatus.NO_FILE -> "No active file"
        }

        pomodoroStateValue.text = pomodoro.state.name
        pomodoroRemainingValue.text = formatDuration(pomodoro.remainingSeconds)
        pomodoroSessionsValue.text = stats.pomodoroCompletedSessions.toString()
        startStopButton.text = if (pomodoro.state == PomodoroState.IDLE) "Start" else "Stop"

        refreshTopFiles(stats)
        refreshEditBreakdown(stats)
    }

    private fun refreshTopFiles(stats: DayStats) {
        topFilesModel.clear()

        val topFiles = stats.timePerFileOrClass.entries
            .sortedByDescending { it.value }
            .take(5)

        if (topFiles.isEmpty()) {
            topFilesModel.addElement("No focus data yet")
            return
        }

        topFiles.forEachIndexed { index, entry ->
            topFilesModel.addElement(
                "${index + 1}. ${displayName(entry.key)} - ${formatDuration(entry.value)}"
            )
        }
    }

    private fun refreshEditBreakdown(stats: DayStats) {
        val total = stats.totalWrittenCharacters
        val typedCharacters = stats.editCountersByType[EditType.TYPED] ?: 0
        val pastedCharacters = stats.editCountersByType[EditType.PASTED] ?: 0
        val insertedCharacters = stats.editCountersByType[EditType.INSERTED] ?: 0

        typedValue.text = formatEditValue(typedCharacters, total)
        pastedValue.text = formatEditValue(pastedCharacters, total)
        insertedValue.text = formatEditValue(insertedCharacters, total)
    }

    private fun formatEditValue(
        count: Int,
        total: Int
    ): String {
        if (total <= 0) {
            return "$count chars"
        }

        val percentage = (count * 100) / total
        return "$count chars ($percentage%)"
    }

    private fun formatDuration(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val hours = safeSeconds / 3_600
        val minutes = (safeSeconds % 3_600) / 60
        val seconds = safeSeconds % 60

        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun displayName(filePath: String): String {
        return File(filePath).name.ifBlank { filePath }
    }
}
