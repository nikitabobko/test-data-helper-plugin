package org.jetbrains.kotlin.test.helper.gradle

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.test.helper.toFileNamesString
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.util.GradleConstants


fun runGradleCommandLine(
    e: AnActionEvent,
    fullCommandLine: String,
    debug: Boolean,
    useProjectBasePath: Boolean,
    title: String? = e.toFileNamesString()
) {
    val project = e.project ?: return
    val runSettings = createGradleRunAndConfigurationSettings(project, fullCommandLine, title, useProjectBasePath) ?: return

    ProgramRunnerUtil.executeConfiguration(
        runSettings,
        if (debug) DefaultDebugExecutor.getDebugExecutorInstance() else DefaultRunExecutor.getRunExecutorInstance()
    )

    val runManager = RunManager.getInstance(project)
    val existingConfiguration = runManager.findConfigurationByTypeAndName(runSettings.type, runSettings.name)
    if (existingConfiguration == null) {
        runManager.setTemporaryConfiguration(runSettings)
    } else {
        runManager.selectedConfiguration = existingConfiguration
    }
}

private fun createGradleRunAndConfigurationSettings(
    project: Project,
    fullCommandLine: String,
    title: String?,
    useProjectBasePath: Boolean,
): RunnerAndConfigurationSettings? {
    val settings = createGradleExternalSystemTaskExecutionSettings(project, fullCommandLine, useProjectBasePath)
    val runSettings = ExternalSystemUtil.createExternalSystemRunnerAndConfigurationSettings(
        settings,
        project,
        GradleConstants.SYSTEM_ID,
    )

    if (title != null) {
        runSettings?.name = title
    }
    return runSettings
}

fun createGradleExternalSystemTaskExecutionSettings(
    project: Project,
    fullCommandLine: String,
    useProjectBasePath: Boolean,
): ExternalSystemTaskExecutionSettings {
    return ExternalSystemTaskExecutionSettings().apply {
        externalProjectPath = if (useProjectBasePath) {
            project.basePath
        } else {
            ExternalSystemApiUtil.getLocalSettings<GradleLocalSettings>(
                project,
                GradleConstants.SYSTEM_ID
            ).availableProjects.keys.firstOrNull()?.let { FileUtil.toCanonicalPath(it.path) }
        }
        taskNames = fullCommandLine.split(" ")
        externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }
}