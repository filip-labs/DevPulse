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
import com.github.filiplabs.devpulse.model.PomodoroSnapshot
import com.github.filiplabs.devpulse.model.PomodoroState
import com.github.filiplabs.devpulse.services.DevPulsePomodoroService
import com.github.filiplabs.devpulse.storage.DevPulseStatsService
import com.github.filiplabs.devpulse.tracking.ActiveFileTracker
import com.github.filiplabs.devpulse.tracking.DevPulseFocusTracker
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.Gray
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.io.File
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager

class DevPulseDashboardPanel(
    private val project: Project
) : JBPanel<DevPulseDashboardPanel>(BorderLayout()), Disposable {

    private val statsService = project.service<DevPulseStatsService>()
    private val activeFileTracker = project.service<ActiveFileTracker>()
    private val focusTracker = project.service<DevPulseFocusTracker>()
    private val pomodoroService = project.service<DevPulsePomodoroService>()

    private val headerStatusIndicator = createStatusIndicator()
    private val overviewStatusIndicator = createStatusIndicator()

    private val overviewFocusTotalValue = valueLabel()
    private val overviewCurrentFileValue = valueLabel()
    private val overviewCurrentFileTimeValue = valueLabel()
    private val overviewPomodoroStateValue = valueLabel()
    private val overviewEditSummaryValue = valueLabel()

    private val currentFocusContent = JBPanel<JBPanel<*>>(BorderLayout())
    private val currentFocusActivePanel = verticalPanel()
    private val currentFocusEmptyPanel = createEmptyState(
        AllIcons.FileTypes.Unknown,
        "No active file",
        "Open a file and start typing to begin tracking."
    )
    private val currentFocusNameValue = createIconLabel(AllIcons.FileTypes.Any_type, "")
    private val currentFocusTimeValue = valueLabel()
    private val currentFocusStatusValue = valueLabel()
    private val focusTotalValue = valueLabel()
    private val focusTopFilesContent = verticalPanel()

    private val pomodoroStateValue = valueLabel()
    private val pomodoroRemainingValue = JBLabel().apply {
        font = font.deriveFont(Font.BOLD, font.size2D + 12f)
    }
    private val pomodoroSessionsValue = valueLabel()

    private val typedCharsValue = valueLabel()
    private val typedPercentValue = secondaryLabel("")
    private val typedProgressBar = createProgressBar()
    private val pastedCharsValue = valueLabel()
    private val pastedPercentValue = secondaryLabel("")
    private val pastedProgressBar = createProgressBar()
    private val insertedCharsValue = valueLabel()
    private val insertedPercentValue = secondaryLabel("")
    private val insertedProgressBar = createProgressBar()

    private val topFilesContent = verticalPanel()

    private val startStopButton = JButton("Start").apply {
        icon = AllIcons.Actions.Execute
    }
    private val resetButton = JButton("Reset").apply {
        icon = AllIcons.General.Reset
    }

    private val refreshTimer = Timer(1_000) { refreshUi() }

    init {
        border = JBUI.Borders.empty(10)

        buildCurrentFocusContent()

        add(createMainHeader(), BorderLayout.NORTH)
        add(createTabs(), BorderLayout.CENTER)

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

    private fun createMainHeader(): JPanel {
        val titleLabel = createIconLabel(AllIcons.Toolwindows.ToolWindowProfiler, "DevPulse").apply {
            font = font.deriveFont(Font.BOLD, font.size2D + 5f)
        }
        val subtitleLabel = secondaryLabel("Today's coding pulse")

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(10)
            add(
                verticalPanel().apply {
                    add(titleLabel)
                    add(Box.createVerticalStrut(2))
                    add(subtitleLabel)
                },
                BorderLayout.CENTER
            )
            add(headerStatusIndicator, BorderLayout.SOUTH)
        }
    }

    private fun createTabs(): JBTabbedPane {
        return JBTabbedPane().apply {
            addTab("Overview", createScrollableTab(createOverviewTab()))
            addTab("Focus", createScrollableTab(createFocusTab()))
            addTab("Pomodoro", createScrollableTab(createPomodoroTab()))
            addTab("Edit Breakdown", createScrollableTab(createEditBreakdownTab()))
            addTab("Top Files", createScrollableTab(createTopFilesTab()))
        }
    }

    private fun createOverviewTab(): JPanel {
        return verticalPanel().apply {
            add(createSectionCard("Status", AllIcons.General.InspectionsEye, overviewStatusIndicator))
            add(Box.createVerticalStrut(8))
            add(
                createSectionCard(
                    "Today at a Glance",
                    AllIcons.Actions.StopWatch,
                    verticalPanel().apply {
                        add(createLabelValueRow("Total focus time", overviewFocusTotalValue))
                        add(Box.createVerticalStrut(6))
                        add(createLabelValueRow("Current file/class", overviewCurrentFileValue))
                        add(Box.createVerticalStrut(6))
                        add(createLabelValueRow("Focused here", overviewCurrentFileTimeValue))
                    }
                )
            )
            add(Box.createVerticalStrut(8))
            add(
                createSectionCard(
                    "Activity Summary",
                    AllIcons.Actions.Edit,
                    verticalPanel().apply {
                        add(createLabelValueRow("Pomodoro state", overviewPomodoroStateValue))
                        add(Box.createVerticalStrut(6))
                        add(createLabelValueRow("Edit breakdown", overviewEditSummaryValue))
                    }
                )
            )
            add(Box.createVerticalGlue())
        }
    }

    private fun createFocusTab(): JPanel {
        return verticalPanel().apply {
            add(createSectionCard("Current Focus", AllIcons.General.Locate, currentFocusContent))
            add(Box.createVerticalStrut(8))
            add(
                createSectionCard(
                    "Focus Today",
                    AllIcons.Actions.StopWatch,
                    verticalPanel().apply {
                        add(createLabelValueRow("Total focus time", focusTotalValue))
                    }
                )
            )
            add(Box.createVerticalStrut(8))
            add(createSectionCard("Top Files / Classes", AllIcons.Nodes.Class, focusTopFilesContent))
            add(Box.createVerticalGlue())
        }
    }

    private fun createPomodoroTab(): JPanel {
        val buttonRow = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(startStopButton)
            add(Box.createHorizontalStrut(6))
            add(resetButton)
            add(Box.createHorizontalGlue())
        }

        return verticalPanel().apply {
            add(
                createSectionCard(
                    "Pomodoro",
                    AllIcons.Actions.Profile,
                    verticalPanel().apply {
                        add(createLabelValueRow("Timer state", pomodoroStateValue))
                        add(Box.createVerticalStrut(8))
                        add(pomodoroRemainingValue)
                        add(Box.createVerticalStrut(8))
                        add(createLabelValueRow("Completed sessions", pomodoroSessionsValue))
                        add(Box.createVerticalStrut(10))
                        add(buttonRow)
                    }
                )
            )
            add(Box.createVerticalGlue())
        }
    }

    private fun createEditBreakdownTab(): JPanel {
        return verticalPanel().apply {
            add(
                createSectionCard(
                    "Edit Breakdown",
                    AllIcons.Actions.Edit,
                    verticalPanel().apply {
                        add(
                            createBreakdownRow(
                                AllIcons.Actions.Edit,
                                "Typed",
                                typedCharsValue,
                                typedPercentValue,
                                typedProgressBar
                            )
                        )
                        add(Box.createVerticalStrut(8))
                        add(
                            createBreakdownRow(
                                AllIcons.Actions.MenuPaste,
                                "Pasted",
                                pastedCharsValue,
                                pastedPercentValue,
                                pastedProgressBar
                            )
                        )
                        add(Box.createVerticalStrut(8))
                        add(
                            createBreakdownRow(
                                AllIcons.Nodes.Method,
                                "Inserted",
                                insertedCharsValue,
                                insertedPercentValue,
                                insertedProgressBar
                            )
                        )
                    }
                )
            )
            add(Box.createVerticalGlue())
        }
    }

    private fun createTopFilesTab(): JPanel {
        return verticalPanel().apply {
            add(createSectionCard("Top Files", AllIcons.Nodes.Class, topFilesContent))
            add(Box.createVerticalGlue())
        }
    }

    private fun buildCurrentFocusContent() {
        currentFocusActivePanel.add(currentFocusNameValue)
        currentFocusActivePanel.add(Box.createVerticalStrut(8))
        currentFocusActivePanel.add(createLabelValueRow("Focused here", currentFocusTimeValue))
        currentFocusActivePanel.add(Box.createVerticalStrut(6))
        currentFocusActivePanel.add(createLabelValueRow("Status", currentFocusStatusValue))
    }

    private fun createScrollableTab(content: JPanel): JBScrollPane {
        return JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
        }
    }

    private fun createSectionCard(
        title: String,
        icon: Icon,
        content: Component
    ): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.compound(
                IdeBorderFactory.createBorder(),
                JBUI.Borders.empty(8)
            )
            add(createSectionHeader(title, icon), BorderLayout.NORTH)
            add(
                JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    border = JBUI.Borders.emptyTop(8)
                    add(content, BorderLayout.CENTER)
                },
                BorderLayout.CENTER
            )
        }
    }

    private fun createSectionHeader(title: String, icon: Icon): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(
                createIconLabel(icon, title).apply {
                    font = font.deriveFont(Font.BOLD)
                },
                BorderLayout.CENTER
            )
        }
    }

    private fun createStatusIndicator(): TrafficLightStatusIndicator {
        return TrafficLightStatusIndicator()
    }

    private fun updateStatusIndicator(status: DevPulseUiStatus) {
        headerStatusIndicator.update(status)
        overviewStatusIndicator.update(status)
    }

    private fun createLabelValueRow(label: String, valueLabel: JBLabel): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(8), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            add(secondaryLabel(label), BorderLayout.WEST)
            add(valueLabel, BorderLayout.EAST)
        }
    }

    private fun createIconLabel(icon: Icon, text: String): JBLabel {
        return JBLabel(text, icon, JBLabel.LEFT).apply {
            iconTextGap = JBUI.scale(6)
        }
    }

    private fun createEmptyState(
        icon: Icon,
        title: String,
        helperText: String
    ): JPanel {
        return JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty(8, 0)

            val titleLabel = createIconLabel(icon, title).apply {
                font = font.deriveFont(Font.BOLD)
                alignmentX = LEFT_ALIGNMENT
            }
            val helperLabel = secondaryLabel(helperText).apply {
                alignmentX = LEFT_ALIGNMENT
            }

            add(titleLabel)
            add(Box.createVerticalStrut(4))
            add(helperLabel)
        }
    }

    private fun createBreakdownRow(
        icon: Icon,
        label: String,
        charsLabel: JBLabel,
        percentLabel: JBLabel,
        progressBar: JProgressBar
    ): JPanel {
        val header = JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(8), 0)).apply {
            add(createIconLabel(icon, label), BorderLayout.WEST)
            add(
                JBPanel<JBPanel<*>>().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    add(charsLabel)
                    add(Box.createHorizontalStrut(6))
                    add(percentLabel)
                },
                BorderLayout.EAST
            )
        }

        return verticalPanel().apply {
            add(header)
            add(Box.createVerticalStrut(4))
            add(progressBar)
        }
    }

    private fun createTopFileRow(
        rank: Int,
        filePath: String,
        duration: Long
    ): JPanel {
        val rankLabel = secondaryLabel(rank.toString()).apply {
            preferredSize = JBUI.size(22, preferredSize.height)
        }
        val nameLabel = createIconLabel(AllIcons.FileTypes.Any_type, displayName(filePath)).apply {
            toolTipText = filePath
        }
        val durationLabel = valueLabel(formatDuration(duration))

        return JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(8), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            add(rankLabel, BorderLayout.WEST)
            add(nameLabel, BorderLayout.CENTER)
            add(durationLabel, BorderLayout.EAST)
        }
    }

    private fun refreshUi() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { refreshUi() }
            return
        }

        val stats = statsService.getTodayStats()
        val pomodoro = pomodoroService.getSnapshot()
        val activeFilePath = activeFileTracker.getActiveFilePath()
        val activeFileName = activeFilePath?.let { displayName(it) }
        val focusStatus = focusTracker.getStatus()
        val uiStatus = determineUiStatus(activeFilePath, focusStatus)

        updateStatusIndicator(uiStatus)
        refreshOverview(stats, pomodoro, activeFilePath, activeFileName)
        refreshCurrentFocus(stats, activeFilePath, activeFileName, focusStatus)
        refreshPomodoro(pomodoro, stats)
        refreshEditBreakdown(stats)
        refreshTopFiles(focusTopFilesContent, stats)
        refreshTopFiles(topFilesContent, stats)
    }

    private fun refreshOverview(
        stats: DayStats,
        pomodoro: PomodoroSnapshot,
        activeFilePath: String?,
        activeFileName: String?
    ) {
        overviewFocusTotalValue.text = formatDuration(stats.totalFocusSeconds)
        overviewCurrentFileValue.text = activeFileName ?: "No active file"
        overviewCurrentFileTimeValue.text = formatDuration(
            activeFilePath?.let { stats.timePerFileOrClass[it] } ?: 0L
        )
        overviewPomodoroStateValue.text = formatPomodoroSummary(pomodoro, stats)
        overviewEditSummaryValue.text = formatEditSummary(stats)
    }

    private fun refreshCurrentFocus(
        stats: DayStats,
        activeFilePath: String?,
        activeFileName: String?,
        focusStatus: FocusStatus
    ) {
        currentFocusContent.removeAll()

        if (activeFilePath.isNullOrBlank() || activeFileName == null) {
            currentFocusContent.add(currentFocusEmptyPanel, BorderLayout.CENTER)
        } else {
            currentFocusNameValue.text = activeFileName
            currentFocusNameValue.toolTipText = activeFilePath
            currentFocusTimeValue.text = formatDuration(stats.timePerFileOrClass[activeFilePath] ?: 0L)
            currentFocusStatusValue.text = formatFocusStatus(focusStatus)
            currentFocusContent.add(currentFocusActivePanel, BorderLayout.CENTER)
        }

        currentFocusContent.revalidate()
        currentFocusContent.repaint()
        focusTotalValue.text = formatDuration(stats.totalFocusSeconds)
    }

    private fun refreshPomodoro(pomodoro: PomodoroSnapshot, stats: DayStats) {
        pomodoroStateValue.text = formatPomodoroState(pomodoro.state)
        pomodoroRemainingValue.text = formatDuration(pomodoro.remainingSeconds)
        pomodoroSessionsValue.text = stats.pomodoroCompletedSessions.toString()
        startStopButton.text = if (pomodoro.state == PomodoroState.IDLE) "Start" else "Stop"
    }

    private fun refreshEditBreakdown(stats: DayStats) {
        val typed = stats.editCountersByType[EditType.TYPED] ?: 0
        val pasted = stats.editCountersByType[EditType.PASTED] ?: 0
        val inserted = stats.editCountersByType[EditType.INSERTED] ?: 0
        val total = stats.totalWrittenCharacters.coerceAtLeast(0)

        updateBreakdownValues(typed, total, typedCharsValue, typedPercentValue, typedProgressBar)
        updateBreakdownValues(pasted, total, pastedCharsValue, pastedPercentValue, pastedProgressBar)
        updateBreakdownValues(inserted, total, insertedCharsValue, insertedPercentValue, insertedProgressBar)
    }

    private fun refreshTopFiles(content: JPanel, stats: DayStats) {
        content.removeAll()

        val topFiles = stats.timePerFileOrClass.entries
            .sortedByDescending { it.value }
            .take(TOP_FILE_LIMIT)

        if (topFiles.isEmpty()) {
            content.add(
                createEmptyState(
                    AllIcons.FileTypes.Any_type,
                    "No focus data yet",
                    "Start editing a file to see your top files here."
                )
            )
        } else {
            topFiles.forEachIndexed { index, entry ->
                content.add(createTopFileRow(index + 1, entry.key, entry.value))
                if (index < topFiles.lastIndex) {
                    content.add(Box.createVerticalStrut(6))
                }
            }
        }

        content.revalidate()
        content.repaint()
    }

    private fun updateBreakdownValues(
        chars: Int,
        total: Int,
        charsLabel: JBLabel,
        percentLabel: JBLabel,
        progressBar: JProgressBar
    ) {
        val percent = if (total > 0) (chars * 100) / total else 0
        charsLabel.text = "$chars chars"
        percentLabel.text = "$percent%"
        progressBar.value = percent
    }

    private fun determineUiStatus(
        activeFilePath: String?,
        focusStatus: FocusStatus
    ): DevPulseUiStatus {
        if (project.isDisposed) {
            return DevPulseUiStatus.INACTIVE
        }

        return when {
            activeFilePath.isNullOrBlank() -> DevPulseUiStatus.WAITING
            focusStatus == FocusStatus.ACTIVE -> DevPulseUiStatus.ACTIVE
            focusStatus == FocusStatus.IDLE -> DevPulseUiStatus.IDLE
            else -> DevPulseUiStatus.INACTIVE
        }
    }

    private fun formatEditSummary(stats: DayStats): String {
        val typed = stats.editCountersByType[EditType.TYPED] ?: 0
        val pasted = stats.editCountersByType[EditType.PASTED] ?: 0
        val inserted = stats.editCountersByType[EditType.INSERTED] ?: 0
        val total = stats.totalWrittenCharacters

        if (total <= 0) {
            return "No edits recorded yet"
        }

        return "Typed ${percentage(typed, total)}%, Pasted ${percentage(pasted, total)}%, " +
            "Inserted ${percentage(inserted, total)}%"
    }

    private fun formatPomodoroSummary(pomodoro: PomodoroSnapshot, stats: DayStats): String {
        return "${formatPomodoroState(pomodoro.state)} - " +
            "${formatDuration(pomodoro.remainingSeconds)} remaining - " +
            "${stats.pomodoroCompletedSessions} completed"
    }

    private fun formatFocusStatus(status: FocusStatus): String {
        return when (status) {
            FocusStatus.ACTIVE -> "Active"
            FocusStatus.IDLE -> "Idle"
            FocusStatus.NO_FILE -> "No active file"
        }
    }

    private fun formatPomodoroState(state: PomodoroState): String {
        return when (state) {
            PomodoroState.IDLE -> "Ready to start"
            PomodoroState.WORK -> "Work session"
            PomodoroState.BREAK -> "Break"
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val hours = safeSeconds / 3_600
        val minutes = (safeSeconds % 3_600) / 60
        val seconds = safeSeconds % 60

        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun percentage(value: Int, total: Int): Int {
        return if (total > 0) (value * 100) / total else 0
    }

    private fun displayName(filePath: String): String {
        return File(filePath).name.ifBlank { filePath }
    }

    private fun valueLabel(text: String = ""): JBLabel {
        return JBLabel(text).apply {
            font = font.deriveFont(Font.BOLD)
        }
    }

    private fun secondaryLabel(text: String): JBLabel {
        return JBLabel(text).apply {
            foreground = secondaryForeground()
        }
    }

    private fun secondaryForeground(): Color {
        return UIManager.getColor("Label.disabledForeground")
            ?: UIManager.getColor("Label.foreground")
            ?: JBColor.GRAY
    }

    private fun createProgressBar(): JProgressBar {
        return JProgressBar(0, 100).apply {
            isStringPainted = false
            isBorderPainted = false
            preferredSize = Dimension(0, JBUI.scale(6))
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(6))
        }
    }

    private fun verticalPanel(): JBPanel<JBPanel<*>> {
        return JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty()
        }
    }

    private enum class DevPulseUiStatus(
        val text: String,
        val color: JBColor
    ) {
        ACTIVE(
            "Active",
            JBColor(Color(0x3D, 0x80, 0x51), Color(0x6A, 0x99, 0x55))
        ),
        INACTIVE(
            "Inactive",
            JBColor(Color(0xA3, 0x4D, 0x4D), Color(0xB8, 0x5C, 0x5C))
        ),
        IDLE(
            "Idle",
            JBColor(Color(0xA0, 0x78, 0x2A), Color(0xB8, 0x8A, 0x33))
        ),
        WAITING(
            "Waiting for activation",
            JBColor(Gray._140, Gray._122)
        )
    }

    private class TrafficLightStatusIndicator : JPanel() {

        private val dot = StatusDot()
        private val textLabel = JBLabel()

        init {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(UIUtil.getBoundsColor()),
                JBUI.Borders.empty(3, 8)
            )
            isOpaque = false

            add(dot)
            add(Box.createHorizontalStrut(6))
            add(textLabel)
            add(Box.createHorizontalGlue())

            update(DevPulseUiStatus.WAITING)
        }

        fun update(status: DevPulseUiStatus) {
            dot.updateColor(status.color)
            textLabel.text = status.text
        }
    }

    private class StatusDot : JComponent() {

        private var color: Color = DevPulseUiStatus.WAITING.color

        init {
            isOpaque = false
            preferredSize = JBUI.size(10, 16)
            minimumSize = preferredSize
            maximumSize = preferredSize
        }

        fun updateColor(color: Color) {
            this.color = color
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)

            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = color

                val size = JBUI.scale(8)
                val x = (width - size) / 2
                val y = (height - size) / 2
                g2.fillOval(x, y, size, size)
            } finally {
                g2.dispose()
            }
        }
    }

    companion object {
        private const val TOP_FILE_LIMIT = 5
    }
}
