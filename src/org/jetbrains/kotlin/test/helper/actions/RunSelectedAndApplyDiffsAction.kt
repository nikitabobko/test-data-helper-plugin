package org.jetbrains.kotlin.test.helper.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.test.helper.services.TestDataRunnerService

class RunSelectedAndApplyDiffsAction : GradleOnlyAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList() ?: return

        val service = project.service<TestDataRunnerService>()
        service.scope.launch {
            withBackgroundProgress(project, "Running Specific & Applying Diffs") {
                val result = service.selectTestMethods(files, e) ?: return@withBackgroundProgress

                reportSequentialProgress { reporter ->
                    reporter.indeterminateStep("Running Specific Tests")

                    runTestAndApplyDiffLoop(project) {
                        service.doRunTests(result, e)
                    }
                }
            }
        }
    }
}