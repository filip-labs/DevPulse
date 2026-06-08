/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.toolWindow

import com.github.filiplabs.devpulse.model.PomodoroState
import com.github.filiplabs.devpulse.services.DevPulsePomodoroService
import com.github.filiplabs.devpulse.settings.DevPulseMemoryMode
import com.github.filiplabs.devpulse.settings.DevPulseSettingsService
import com.github.filiplabs.devpulse.settings.DevPulseSettingsState
import com.github.filiplabs.devpulse.storage.DevPulseStatsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class DevPulseSettingsDialog(
    private val project: Project,
    private val onSettingsChanged: () -> Unit
) : DialogWrapper(project) {

    private val settingsService = service<DevPulseSettingsService>()
    private val pomodoroService = project.service<DevPulsePomodoroService>()
    private val statsService = project.service<DevPulseStatsService>()
    private val initialState = settingsService.snapshot()

    private val workDurationSpinner = minutesSpinner(
        initialState.workDurationMinutes,
        DevPulseSettingsService.MIN_WORK_DURATION_MINUTES,
        DevPulseSettingsService.MAX_WORK_DURATION_MINUTES
    )
    private val shortBreakSpinner = minutesSpinner(
        initialState.shortBreakMinutes,
        DevPulseSettingsService.MIN_SHORT_BREAK_MINUTES,
        DevPulseSettingsService.MAX_SHORT_BREAK_MINUTES
    )
    private val longBreakSpinner = minutesSpinner(
        initialState.longBreakMinutes,
        DevPulseSettingsService.MIN_LONG_BREAK_MINUTES,
        DevPulseSettingsService.MAX_LONG_BREAK_MINUTES
    )
    private val sessionsBeforeLongBreakSpinner = minutesSpinner(
        initialState.sessionsBeforeLongBreak,
        DevPulseSettingsService.MIN_SESSIONS_BEFORE_LONG_BREAK,
        DevPulseSettingsService.MAX_SESSIONS_BEFORE_LONG_BREAK
    )
    private val autoStartNextSessionCheckbox = JBCheckBox("Auto-start next session", initialState.autoStartNextSession)
    private val memoryModeComboBox = ComboBox(DevPulseMemoryMode.values()).apply {
        selectedItem = initialState.memoryMode
    }
    private val resetStatsOnProjectOpenCheckbox = JBCheckBox(
        "Reset stats when project opens",
        initialState.resetStatsOnProjectOpen
    )

    init {
        title = "DevPulse Settings"
        setOKButtonText("Save")
        init()
    }

    override fun createCenterPanel(): JComponent {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 4)
            add(createSettingsForm(), BorderLayout.CENTER)
        }
    }

    override fun doValidate(): ValidationInfo? {
        return validateRange(
            workDurationSpinner,
            "Work duration",
            DevPulseSettingsService.MIN_WORK_DURATION_MINUTES,
            DevPulseSettingsService.MAX_WORK_DURATION_MINUTES
        )
            ?: validateRange(
                shortBreakSpinner,
                "Short break",
                DevPulseSettingsService.MIN_SHORT_BREAK_MINUTES,
                DevPulseSettingsService.MAX_SHORT_BREAK_MINUTES
            )
            ?: validateRange(
                longBreakSpinner,
                "Long break",
                DevPulseSettingsService.MIN_LONG_BREAK_MINUTES,
                DevPulseSettingsService.MAX_LONG_BREAK_MINUTES
            )
            ?: validateRange(
                sessionsBeforeLongBreakSpinner,
                "Sessions before long break",
                DevPulseSettingsService.MIN_SESSIONS_BEFORE_LONG_BREAK,
                DevPulseSettingsService.MAX_SESSIONS_BEFORE_LONG_BREAK
            )
    }

    override fun doOKAction() {
        val newState = readState()
        if (pomodoroService.getSnapshot().state != PomodoroState.IDLE && pomodoroSettingsChanged(newState)) {
            val answer = Messages.showYesNoDialog(
                project,
                "Pomodoro timer is currently running. Apply new duration after reset?",
                "DevPulse Settings",
                "Save",
                "Cancel",
                Messages.getQuestionIcon()
            )
            if (answer != Messages.YES) {
                return
            }
        }

        settingsService.update(newState)
        pomodoroService.applySettingsChanged()
        onSettingsChanged()
        super.doOKAction()
    }

    private fun createSettingsForm(): JPanel {
        var row = 0
        return JBPanel<JBPanel<*>>(GridBagLayout()).apply {
            border = JBUI.Borders.empty()

            addSectionLabel("Pomodoro", row++)
            addLabeledRow("Work duration:", workDurationSpinner, "minutes", row++)
            addLabeledRow("Short break:", shortBreakSpinner, "minutes", row++)
            addLabeledRow("Long break:", longBreakSpinner, "minutes", row++)
            addLabeledRow("Sessions before long break:", sessionsBeforeLongBreakSpinner, null, row++)
            addFullWidth(autoStartNextSessionCheckbox, row++)

            addSpacer(row++)
            addSectionLabel("Memory & Statistics", row++)
            addLabeledRow("Memory mode:", memoryModeComboBox, null, row++)
            addFullWidth(resetStatsOnProjectOpenCheckbox, row++)
            addFullWidth(createResetStatisticsButton(), row++)
        }
    }

    private fun JBPanel<JBPanel<*>>.addSectionLabel(text: String, row: Int) {
        add(
            JBLabel(text).apply {
                font = JBFont.label().asBold()
                border = JBUI.Borders.emptyTop(if (row == 0) 0 else 8)
            },
            constraints(row, 0, GridBagConstraints.WEST).apply {
                gridwidth = 3
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            }
        )
    }

    private fun JBPanel<JBPanel<*>>.addLabeledRow(
        label: String,
        component: JComponent,
        suffix: String?,
        row: Int
    ) {
        add(
            JBLabel(label),
            constraints(row, 0, GridBagConstraints.WEST)
        )
        add(
            component,
            constraints(row, 1, GridBagConstraints.WEST).apply {
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            }
        )
        add(
            JBLabel(suffix.orEmpty()),
            constraints(row, 2, GridBagConstraints.WEST)
        )
    }

    private fun JBPanel<JBPanel<*>>.addFullWidth(component: JComponent, row: Int) {
        add(
            component,
            constraints(row, 0, GridBagConstraints.WEST).apply {
                gridwidth = 3
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            }
        )
    }

    private fun JBPanel<JBPanel<*>>.addSpacer(row: Int) {
        add(
            JBPanel<JBPanel<*>>().apply {
                isOpaque = false
            },
            constraints(row, 0, GridBagConstraints.WEST).apply {
                gridwidth = 3
                fill = GridBagConstraints.HORIZONTAL
            }
        )
    }

    private fun createResetStatisticsButton(): JButton {
        return JButton("Reset statistics now").apply {
            addActionListener {
                val answer = Messages.showYesNoDialog(
                    project,
                    "Reset all DevPulse statistics for this session?",
                    "Reset DevPulse Statistics",
                    "Reset",
                    "Cancel",
                    Messages.getQuestionIcon()
                )
                if (answer == Messages.YES) {
                    statsService.resetStatistics()
                    onSettingsChanged()
                }
            }
        }
    }

    private fun constraints(
        row: Int,
        column: Int,
        anchor: Int
    ): GridBagConstraints {
        return GridBagConstraints().apply {
            gridx = column
            gridy = row
            this.anchor = anchor
            insets = JBUI.insets(4, if (column == 0) 0 else 8, 4, 0)
        }
    }

    private fun readState(): DevPulseSettingsState {
        return DevPulseSettingsState(
            workDurationMinutes = spinnerValue(workDurationSpinner),
            shortBreakMinutes = spinnerValue(shortBreakSpinner),
            longBreakMinutes = spinnerValue(longBreakSpinner),
            sessionsBeforeLongBreak = spinnerValue(sessionsBeforeLongBreakSpinner),
            autoStartNextSession = autoStartNextSessionCheckbox.isSelected,
            memoryMode = memoryModeComboBox.selectedItem as DevPulseMemoryMode,
            resetStatsOnProjectOpen = resetStatsOnProjectOpenCheckbox.isSelected
        )
    }

    private fun pomodoroSettingsChanged(newState: DevPulseSettingsState): Boolean {
        return newState.workDurationMinutes != initialState.workDurationMinutes ||
            newState.shortBreakMinutes != initialState.shortBreakMinutes ||
            newState.longBreakMinutes != initialState.longBreakMinutes ||
            newState.sessionsBeforeLongBreak != initialState.sessionsBeforeLongBreak ||
            newState.autoStartNextSession != initialState.autoStartNextSession
    }

    private fun validateRange(
        spinner: JSpinner,
        label: String,
        min: Int,
        max: Int
    ): ValidationInfo? {
        val value = spinnerValue(spinner)
        return if (value in min..max) {
            null
        } else {
            ValidationInfo("$label must be between $min and $max.", spinner)
        }
    }

    private fun spinnerValue(spinner: JSpinner): Int {
        return (spinner.value as Number).toInt()
    }

    private companion object {
        fun minutesSpinner(
            value: Int,
            min: Int,
            max: Int
        ): JSpinner {
            return JSpinner(SpinnerNumberModel(value, min, max, 1))
        }
    }
}
