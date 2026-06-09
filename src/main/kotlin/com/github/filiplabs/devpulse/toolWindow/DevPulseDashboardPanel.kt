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
import com.github.filiplabs.devpulse.settings.DevPulseMemoryMode
import com.github.filiplabs.devpulse.settings.DevPulseSettingsService
import com.github.filiplabs.devpulse.storage.DevPulseStatsService
import com.github.filiplabs.devpulse.tracking.ActiveFileTracker
import com.github.filiplabs.devpulse.tracking.DevPulseFocusTracker
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
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
    private val settingsService = service<DevPulseSettingsService>()

    private val headerStatusIndicator = createStatusIndicator()
    private val headerPulseLine = PulseLineComponent()
    private val focusAccent = AccentLine(DevPulseUiStatus.WAITING.color)
    private var wasShowing = false

    private val currentFocusContent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        isOpaque = false
    }
    private val currentFocusActivePanel = verticalPanel()
    private val currentFocusNameValue = createIconLabel(AllIcons.FileTypes.Any_type, "").apply {
        font = JBFont.label().asBold().deriveFont(font.size2D + 2f)
        foreground = TYPED_ACCENT
    }
    private val currentFocusTimeValue = valueLabel()
    private val currentFocusStatusValue = valueLabel()
    private val focusTotalValue = valueLabel()
    private val focusPomodoroSessionsValue = valueLabel()
    private val focusPomodoroSummaryValue = valueLabel()

    private val pomodoroStateValue = valueLabel()
    private val pomodoroRemainingValue = JBLabel().apply {
        font = JBFont.label().asBold().deriveFont(font.size2D + 12f)
    }
    private val pomodoroStateDot = StatusDot()
    private val pomodoroProgressBar = createProgressBar(POMODORO_ACCENT)

    private val typedCharsValue = valueLabel()
    private val typedPercentValue = secondaryLabel("")
    private val typedProgressBar = createProgressBar(TYPED_ACCENT)
    private val pastedCharsValue = valueLabel()
    private val pastedPercentValue = secondaryLabel("")
    private val pastedProgressBar = createProgressBar(PASTED_ACCENT)
    private val insertedCharsValue = valueLabel()
    private val insertedPercentValue = secondaryLabel("")
    private val insertedProgressBar = createProgressBar(INSERTED_ACCENT)

    private val topFilesContent = verticalPanel()

    private val startStopButton = JButton("Start").apply {
        icon = AllIcons.Actions.Execute
    }
    private val resetButton = JButton("Reset").apply {
        icon = AllIcons.General.Reset
    }

    private val refreshTimer = Timer(1_000) { refreshUi() }

    init {
        border = JBUI.Borders.empty()
        background = UIUtil.getPanelBackground()

        buildCurrentFocusContent()

        add(createDashboardScrollPane(), BorderLayout.CENTER)
        installToolWindowOpenResetHandler()

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

    private fun createDashboardPanel(): JPanel {
        return verticalPanel().apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(cardBorderColor()),
                JBUI.Borders.empty(8)
            )
            background = dashboardBackground()
            isOpaque = true
            add(createHeaderPanel())
            add(Box.createVerticalStrut(8))
            add(createCurrentFocusCard())
            add(Box.createVerticalStrut(8))
            add(createFocusSummaryCard())
            add(Box.createVerticalStrut(8))
            add(createPomodoroCard())
            add(Box.createVerticalStrut(8))
            add(createEditBreakdownCard())
            add(Box.createVerticalStrut(8))
            add(createSectionCard("Top Files / Classes", AllIcons.Nodes.Class, topFilesContent))
            add(Box.createVerticalGlue())
        }
    }

    private fun createHeaderPanel(): JPanel {
        val titleLabel = JBLabel("DevPulse").apply {
            font = JBFont.label().asBold().deriveFont(font.size2D + 5f)
        }
        val subtitleLabel = secondaryLabel("Today’s coding pulse")
        val titleRow = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            add(titleLabel)
            add(Box.createHorizontalStrut(8))
            add(headerStatusIndicator)
            add(Box.createHorizontalGlue())
        }
        val pulseLine = createPulseLineComponent()
        val settingsButton = createSettingsButton()
        val rightIconContent = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            add(pulseLine)
            add(Box.createHorizontalStrut(8))
            add(settingsButton)
        }
        val rightIconPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyLeft(12)
            add(rightIconContent, BorderLayout.NORTH)
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty(2, 2, 4, 2)
            isOpaque = false
            add(
                JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    isOpaque = false
                    add(
                        verticalPanel().apply {
                            isOpaque = false
                            add(titleRow)
                            add(Box.createVerticalStrut(3))
                            add(subtitleLabel)
                        },
                        BorderLayout.CENTER
                    )
                    add(rightIconPanel, BorderLayout.EAST)
                },
                BorderLayout.CENTER
            )

            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    pulseLine.isVisible = width >= JBUI.scale(260)
                }
            })
        }
    }

    private fun createPulseLineComponent(): JComponent {
        return headerPulseLine
    }

    private fun createSettingsButton(): JButton {
        return JButton(AllIcons.General.Settings).apply {
            toolTipText = "DevPulse Settings"
            isFocusable = false
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            margin = JBUI.emptyInsets()
            preferredSize = JBUI.size(28, 28)
            minimumSize = preferredSize
            maximumSize = preferredSize
            addActionListener {
                DevPulseSettingsDialog(project) {
                    refreshUi()
                }.show()
            }
        }
    }

    private fun installToolWindowOpenResetHandler() {
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) {
                return@addHierarchyListener
            }

            val showing = isShowing
            if (showing && !wasShowing) {
                resetStatisticsWhenConfiguredForToolWindowOpen()
            }
            wasShowing = showing
        }
    }

    private fun resetStatisticsWhenConfiguredForToolWindowOpen() {
        if (settingsService.snapshot().memoryMode == DevPulseMemoryMode.RESET_WHEN_TOOL_WINDOW_OPENS) {
            statsService.resetStatistics()
            refreshUi()
        }
    }

    private fun createDashboardScrollPane(): JBScrollPane {
        return JBScrollPane(createDashboardPanel()).apply {
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
            background = dashboardBackground()
            viewport.background = dashboardBackground()
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }
    }

    private fun createCurrentFocusCard(): JPanel {
        return createSectionCard(
            "Current Focus",
            AllIcons.General.Locate,
            currentFocusContent,
            focusAccent
        )
    }

    private fun createFocusSummaryCard(): JPanel {
        return createSectionCard(
            "Focus Summary",
            AllIcons.Actions.StopWatch,
            verticalPanel().apply {
                add(createLabelValueRow("Total focus time", focusTotalValue))
                add(Box.createVerticalStrut(6))
                add(createLabelValueRow("Completed sessions", focusPomodoroSessionsValue))
                add(Box.createVerticalStrut(6))
                add(createLabelValueRow("Current state", focusPomodoroSummaryValue))
            }
        )
    }

    private fun createPomodoroCard(): JPanel {
        val statusRow = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            add(pomodoroStateDot)
            add(Box.createHorizontalStrut(6))
            add(pomodoroStateValue)
            add(Box.createHorizontalGlue())
        }

        val buttonRow = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            add(startStopButton)
            add(Box.createHorizontalStrut(6))
            add(resetButton)
            add(Box.createHorizontalGlue())
        }

        return createSectionCard(
            "Pomodoro",
            AllIcons.Actions.StopWatch,
            verticalPanel().apply {
                pomodoroRemainingValue.alignmentX = LEFT_ALIGNMENT
                add(pomodoroRemainingValue)
                add(Box.createVerticalStrut(6))
                add(statusRow)
                add(Box.createVerticalStrut(8))
                add(pomodoroProgressBar)
                add(Box.createVerticalStrut(10))
                add(buttonRow)
            }
        )
    }

    private fun createEditBreakdownCard(): JPanel {
        return createSectionCard(
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
    }

    private fun buildCurrentFocusContent() {
        currentFocusActivePanel.add(currentFocusNameValue)
        currentFocusActivePanel.add(Box.createVerticalStrut(8))
        currentFocusActivePanel.add(createLabelValueRow("Focused for", currentFocusTimeValue))
        currentFocusActivePanel.add(Box.createVerticalStrut(6))
        currentFocusActivePanel.add(createLabelValueRow("Tracking status", currentFocusStatusValue))
    }

    private fun createSectionCard(
        title: String,
        icon: Icon,
        content: Component,
        accentLine: Component? = null
    ): JPanel {
        val cardBody = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(9, if (accentLine == null) 10 else 8, 9, 10)
            add(createSectionHeader(title, icon), BorderLayout.NORTH)
            add(
                JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.emptyTop(8)
                    add(content, BorderLayout.CENTER)
                },
                BorderLayout.CENTER
            )
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.customLine(cardBorderColor())
            background = cardBackground()
            isOpaque = true
            accentLine?.let { add(it, BorderLayout.WEST) }
            add(cardBody, BorderLayout.CENTER)
        }
    }

    private fun createSectionHeader(title: String, icon: Icon): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            add(
                createIconLabel(icon, title.uppercase()).apply {
                    font = JBFont.small().asBold()
                    foreground = secondaryForeground()
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
        headerPulseLine.updateStatus(status)
        focusAccent.updateColor(status.color)
    }

    private fun createLabelValueRow(label: String, valueLabel: JBLabel): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(8), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
            add(secondaryLabel(label), BorderLayout.WEST)
            add(valueLabel, BorderLayout.EAST)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun createIconLabel(icon: Icon, text: String): JBLabel {
        return JBLabel(text, icon, JBLabel.LEFT).apply {
            iconTextGap = JBUI.scale(6)
        }
    }

    private fun createWaitingCurrentFocusContent(): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(8), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
            border = JBUI.Borders.empty(4, 0)

            val iconLabel = JBLabel(AllIcons.FileTypes.Unknown).apply {
                foreground = secondaryForeground()
                isEnabled = false
            }
            val textPanel = verticalPanel().apply {
                isOpaque = false
                add(JBLabel(NO_ACTIVE_FILE_TEXT).apply {
                    font = JBFont.label().asBold()
                    foreground = waitingForeground()
                    alignmentX = LEFT_ALIGNMENT
                })
                add(Box.createVerticalStrut(3))
                add(secondaryLabel("Open a file and start typing to begin").apply {
                    alignmentX = LEFT_ALIGNMENT
                })
                add(secondaryLabel("tracking.").apply {
                    alignmentX = LEFT_ALIGNMENT
                })
            }

            add(iconLabel, BorderLayout.WEST)
            add(textPanel, BorderLayout.CENTER)
        }
    }

    private fun createBreakdownRow(
        icon: Icon,
        label: String,
        charsLabel: JBLabel,
        percentLabel: JBLabel,
        progressBar: JProgressBar
    ): JPanel {
        val labelPanel = JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            preferredSize = JBUI.size(86, preferredSize.height)
            add(JBLabel(icon), BorderLayout.WEST)
            add(secondaryLabel(label), BorderLayout.CENTER)
        }

        val valuesPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            add(charsLabel)
            add(Box.createHorizontalStrut(8))
            add(percentLabel)
        }

        val topRow = JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(8), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
            add(labelPanel, BorderLayout.WEST)
            add(valuesPanel, BorderLayout.EAST)
        }

        return verticalPanel().apply {
            add(topRow)
            add(Box.createVerticalStrut(4))
            add(progressBar)
        }
    }

    private fun createTopFileRow(
        rank: Int,
        icon: Icon,
        filePath: String,
        duration: Long
    ): JPanel {
        val rankLabel = secondaryLabel(rank.toString()).apply {
            horizontalAlignment = JBLabel.CENTER
            preferredSize = JBUI.size(24, preferredSize.height)
        }
        val nameLabel = createIconLabel(icon, displayName(filePath)).apply {
            toolTipText = filePath
            foreground = TYPED_ACCENT
        }
        val durationLabel = valueLabel(formatDuration(duration))

        return JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(8), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
            border = JBUI.Borders.empty(1, 0)
            add(rankLabel, BorderLayout.WEST)
            add(nameLabel, BorderLayout.CENTER)
            add(durationLabel, BorderLayout.EAST)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
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

        if (uiStatus == DevPulseUiStatus.WAITING) {
            updateWaitingState(pomodoro)
            return
        }

        updateStatusIndicator(uiStatus)
        refreshCurrentFocus(stats, activeFilePath, activeFileName, focusStatus)
        refreshFocusSummary(stats, uiStatus)
        refreshPomodoro(pomodoro)
        refreshEditBreakdown(stats)
        refreshTopFiles(topFilesContent, stats)
    }

    private fun updateWaitingState(pomodoro: PomodoroSnapshot) {
        updateStatusIndicator(DevPulseUiStatus.WAITING)
        currentFocusContent.removeAll()
        currentFocusContent.add(createWaitingCurrentFocusContent(), BorderLayout.CENTER)
        currentFocusContent.revalidate()
        currentFocusContent.repaint()

        createWaitingFocusSummaryContent()
        refreshPomodoro(pomodoro)
        refreshEditBreakdownValues(0, 0, 0)
        refreshTopFiles(topFilesContent, null)
    }

    private fun createWaitingFocusSummaryContent() {
        focusTotalValue.text = "00:00:00"
        focusTotalValue.foreground = waitingForeground()
        focusPomodoroSessionsValue.text = "0"
        focusPomodoroSessionsValue.foreground = waitingForeground()
        focusPomodoroSummaryValue.text = "Waiting"
        focusPomodoroSummaryValue.foreground = DevPulseUiStatus.WAITING.color
    }

    private fun refreshCurrentFocus(
        stats: DayStats,
        activeFilePath: String?,
        activeFileName: String?,
        focusStatus: FocusStatus
    ) {
        currentFocusContent.removeAll()

        if (activeFilePath.isNullOrBlank() || activeFileName == null) {
            currentFocusContent.add(createWaitingCurrentFocusContent(), BorderLayout.CENTER)
        } else {
            currentFocusNameValue.text = activeFileName
            currentFocusNameValue.toolTipText = activeFilePath
            currentFocusTimeValue.text = formatDuration(stats.timePerFileOrClass[activeFilePath] ?: 0L)
            currentFocusStatusValue.text = formatFocusStatus(focusStatus)
            currentFocusStatusValue.foreground = when (focusStatus) {
                FocusStatus.ACTIVE -> DevPulseUiStatus.ACTIVE.color
                FocusStatus.IDLE -> DevPulseUiStatus.IDLE.color
                FocusStatus.NO_FILE -> DevPulseUiStatus.WAITING.color
            }
            currentFocusContent.add(currentFocusActivePanel, BorderLayout.CENTER)
        }

        currentFocusContent.revalidate()
        currentFocusContent.repaint()
    }

    private fun refreshFocusSummary(stats: DayStats, uiStatus: DevPulseUiStatus) {
        focusTotalValue.text = formatDuration(stats.totalFocusSeconds)
        focusTotalValue.foreground = UIUtil.getLabelForeground()
        focusPomodoroSessionsValue.text = stats.pomodoroCompletedSessions.toString()
        focusPomodoroSessionsValue.foreground = UIUtil.getLabelForeground()
        focusPomodoroSummaryValue.text = uiStatus.text
        focusPomodoroSummaryValue.foreground = uiStatus.color
    }

    private fun refreshPomodoro(pomodoro: PomodoroSnapshot) {
        val isRunning = pomodoro.state != PomodoroState.IDLE

        pomodoroStateValue.text = formatPomodoroState(pomodoro.state)
        pomodoroStateValue.foreground = pomodoroUiStatus(pomodoro.state).color
        pomodoroRemainingValue.text = formatTimerDuration(pomodoro.remainingSeconds)
        pomodoroRemainingValue.foreground = if (pomodoro.state == PomodoroState.IDLE) {
            waitingForeground()
        } else {
            UIUtil.getLabelForeground()
        }
        pomodoroStateDot.updateStatus(pomodoroUiStatus(pomodoro.state))
        pomodoroProgressBar.value = pomodoroProgressPercent(pomodoro)
        startStopButton.text = if (isRunning) "Pause" else "Start"
        startStopButton.icon = if (isRunning) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
        startStopButton.foreground = if (isRunning) UIUtil.getLabelForeground() else POMODORO_ACCENT
        resetButton.foreground = secondaryForeground()
    }

    private fun refreshEditBreakdown(stats: DayStats) {
        val typed = stats.editCountersByType[EditType.TYPED] ?: 0
        val pasted = stats.editCountersByType[EditType.PASTED] ?: 0
        val inserted = stats.editCountersByType[EditType.INSERTED] ?: 0
        val total = stats.totalWrittenCharacters.coerceAtLeast(0)

        refreshEditBreakdownValues(typed, pasted, inserted, total)
    }

    private fun refreshEditBreakdownValues(
        typed: Int,
        pasted: Int,
        inserted: Int,
        total: Int = typed + pasted + inserted
    ) {
        val safeTotal = total.coerceAtLeast(0)
        updateBreakdownValues(typed, safeTotal, typedCharsValue, typedPercentValue, typedProgressBar)
        updateBreakdownValues(pasted, safeTotal, pastedCharsValue, pastedPercentValue, pastedProgressBar)
        updateBreakdownValues(inserted, safeTotal, insertedCharsValue, insertedPercentValue, insertedProgressBar)
    }

    private fun refreshTopFiles(content: JPanel, stats: DayStats?) {
        content.removeAll()

        val topFiles = stats?.timePerFileOrClass.orEmpty().entries
            .sortedByDescending { it.value }
            .take(TOP_FILE_LIMIT)

        if (topFiles.isEmpty()) {
            content.add(createEmptyTopFilesState())
        } else {
            topFiles.forEachIndexed { index, entry ->
                content.add(createTopFileRow(index + 1, AllIcons.FileTypes.Any_type, entry.key, entry.value))
                if (index < topFiles.lastIndex) {
                    content.add(Box.createVerticalStrut(6))
                }
            }
        }

        content.revalidate()
        content.repaint()
    }

    private fun createEmptyTopFilesState(): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
            border = JBUI.Borders.empty(6, 0)
            add(
                secondaryLabel("No focus data yet.").apply {
                    horizontalAlignment = JBLabel.CENTER
                },
                BorderLayout.CENTER
            )
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun updateBreakdownValues(
        chars: Int,
        total: Int,
        charsLabel: JBLabel,
        percentLabel: JBLabel,
        progressBar: JProgressBar
    ) {
        val percent = if (total > 0) ((chars.toLong() * 100L) / total).toInt() else 0
        charsLabel.text = "%,d chars".format(chars)
        percentLabel.text = "$percent%"
        progressBar.value = percent
    }

    private fun pomodoroUiStatus(state: PomodoroState): DevPulseUiStatus {
        return when (state) {
            PomodoroState.IDLE -> DevPulseUiStatus.WAITING
            PomodoroState.WORK -> DevPulseUiStatus.ACTIVE
            PomodoroState.BREAK -> DevPulseUiStatus.IDLE
        }
    }

    private fun pomodoroProgressPercent(pomodoro: PomodoroSnapshot): Int {
        val totalSeconds = when (pomodoro.state) {
            PomodoroState.IDLE,
            PomodoroState.WORK -> settingsService.workDurationSeconds().coerceAtLeast(pomodoro.remainingSeconds)
            PomodoroState.BREAK -> {
                val shortBreak = settingsService.shortBreakSeconds()
                val longBreak = settingsService.longBreakSeconds()
                if (pomodoro.remainingSeconds > shortBreak) longBreak else shortBreak
            }
        }.coerceAtLeast(1L)
        val elapsedSeconds = (totalSeconds - pomodoro.remainingSeconds).coerceIn(0L, totalSeconds)

        return ((elapsedSeconds * 100L) / totalSeconds).toInt()
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

    private fun formatFocusStatus(status: FocusStatus): String {
        return when (status) {
            FocusStatus.ACTIVE -> "\u25cf Tracking active"
            FocusStatus.IDLE -> "\u25cf Idle"
            FocusStatus.NO_FILE -> "\u25cf Waiting for editor"
        }
    }

    private fun formatPomodoroState(state: PomodoroState): String {
        return when (state) {
            PomodoroState.IDLE -> "Ready"
            PomodoroState.WORK -> "Work session"
            PomodoroState.BREAK -> "Break"
        }
    }

    private fun formatTimerDuration(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val hours = safeSeconds / 3_600
        val minutes = (safeSeconds % 3_600) / 60
        val seconds = safeSeconds % 60

        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
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

    private fun valueLabel(text: String = ""): JBLabel {
        return JBLabel(text).apply {
            font = JBFont.label().asBold()
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

    private fun waitingForeground(): Color {
        return JBColor(Gray._115, Gray._130)
    }

    private fun cardBackground(): Color {
        val base = UIManager.getColor("Panel.background")
            ?: UIUtil.getPanelBackground()
        return JBColor(
            slightlyShift(base, 6),
            slightlyShift(base, -7)
        )
    }

    private fun dashboardBackground(): Color {
        return UIUtil.getPanelBackground()
    }

    private fun cardBorderColor(): Color {
        return UIManager.getColor("Component.borderColor")
            ?: UIUtil.getBoundsColor()
    }

    private fun createProgressBar(accentColor: Color): JProgressBar {
        return AccentProgressBar(accentColor)
    }

    private fun slightlyShift(color: Color, amount: Int): Color {
        fun channel(value: Int): Int = (value + amount).coerceIn(0, 255)
        return Color(channel(color.red), channel(color.green), channel(color.blue), color.alpha)
    }

    private fun verticalPanel(): JBPanel<JBPanel<*>> {
        return JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty()
            isOpaque = false
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
            border = JBUI.Borders.empty()
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT

            add(dot)
            add(Box.createHorizontalStrut(6))
            add(textLabel)

            update(DevPulseUiStatus.WAITING)
        }

        fun update(status: DevPulseUiStatus) {
            dot.updateStatus(status)
            textLabel.text = status.text
            textLabel.foreground = status.color
        }
    }

    private class StatusDot : JComponent() {

        private var color: Color = DevPulseUiStatus.WAITING.color
        private var active = false

        init {
            isOpaque = false
            preferredSize = JBUI.size(12, 16)
            minimumSize = preferredSize
            maximumSize = preferredSize
        }

        fun updateStatus(status: DevPulseUiStatus) {
            color = status.color
            active = status == DevPulseUiStatus.ACTIVE
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)

            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val size = JBUI.scale(8)
                val x = (width - size) / 2
                val y = (height - size) / 2

                if (active) {
                    g2.color = Color(color.red, color.green, color.blue, 45)
                    g2.fillOval(x - JBUI.scale(2), y - JBUI.scale(2), size + JBUI.scale(4), size + JBUI.scale(4))
                }

                g2.color = color
                g2.fillOval(x, y, size, size)
            } finally {
                g2.dispose()
            }
        }
    }

    private class AccentLine(color: Color) : JComponent() {

        private var lineColor = color

        init {
            isOpaque = false
            preferredSize = JBUI.size(3, 1)
            minimumSize = preferredSize
        }

        fun updateColor(color: Color) {
            lineColor = color
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)

            val g2 = g.create() as Graphics2D
            try {
                g2.color = lineColor
                g2.fillRect(0, 0, JBUI.scale(3), height)
            } finally {
                g2.dispose()
            }
        }
    }

    private class PulseLineComponent : JComponent() {

        private var lineColor: Color = DevPulseUiStatus.WAITING.color

        init {
            isOpaque = false
            preferredSize = JBUI.size(62, 24)
            minimumSize = JBUI.size(0, 0)
        }

        fun updateStatus(status: DevPulseUiStatus) {
            lineColor = when (status) {
                DevPulseUiStatus.ACTIVE -> TYPED_ACCENT
                DevPulseUiStatus.IDLE -> DevPulseUiStatus.IDLE.color
                DevPulseUiStatus.INACTIVE,
                DevPulseUiStatus.WAITING -> DevPulseUiStatus.WAITING.color
            }
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)

            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = lineColor
                g2.stroke = BasicStroke(JBUI.scale(1).toFloat())

                val mid = height / 2
                val left = JBUI.scale(4)
                val right = width - JBUI.scale(4)
                val points = intArrayOf(
                    left,
                    mid,
                    left + width / 6,
                    mid,
                    left + width / 4,
                    mid - JBUI.scale(10),
                    left + width / 3,
                    mid + JBUI.scale(8),
                    left + width / 2,
                    mid,
                    left + (width * 2 / 3),
                    mid,
                    left + (width * 3 / 4),
                    mid - JBUI.scale(6),
                    left + (width * 5 / 6),
                    mid + JBUI.scale(6),
                    right,
                    mid
                )

                for (i in 0 until points.size - 2 step 2) {
                    g2.drawLine(points[i], points[i + 1], points[i + 2], points[i + 3])
                }
            } finally {
                g2.dispose()
            }
        }
    }

    private class AccentProgressBar(
        private val accentColor: Color
    ) : JProgressBar(0, 100) {

        init {
            isStringPainted = false
            isBorderPainted = false
            isOpaque = false
            preferredSize = Dimension(0, JBUI.scale(6))
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(6))
            minimumSize = Dimension(0, JBUI.scale(6))
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                val arc = JBUI.scale(6)
                val barHeight = JBUI.scale(5)
                val y = (height - barHeight) / 2
                val fillWidth = ((width * percentComplete).toInt()).coerceIn(0, width)
                val track = UIManager.getColor("Component.borderColor")
                    ?: UIUtil.getBoundsColor()

                g2.color = Color(track.red, track.green, track.blue, 80)
                g2.fillRoundRect(0, y, width, barHeight, arc, arc)

                if (fillWidth > 0) {
                    g2.color = accentColor
                    g2.fillRoundRect(0, y, fillWidth, barHeight, arc, arc)
                }
            } finally {
                g2.dispose()
            }
        }
    }

    companion object {
        private const val NO_ACTIVE_FILE_TEXT = "No active file"
        private const val TOP_FILE_LIMIT = 5

        private val TYPED_ACCENT = JBColor(Color(0x2E, 0x78, 0xA6), Color(0x5E, 0xB6, 0xD8))
        private val PASTED_ACCENT = JBColor(Color(0x9A, 0x63, 0x25), Color(0xD0, 0x8A, 0x3D))
        private val INSERTED_ACCENT = JBColor(Color(0x78, 0x55, 0xA2), Color(0xA6, 0x7F, 0xD6))
        private val POMODORO_ACCENT = JBColor(Color(0x4D, 0x7E, 0x54), Color(0x6D, 0xA8, 0x69))
    }
}
