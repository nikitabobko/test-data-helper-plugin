package org.jetbrains.kotlin.test.helper.services

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.parentsOfType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.test.helper.TestDataPathsConfiguration
import org.jetbrains.kotlin.test.helper.actions.filterAndCollectTestDeclarations
import org.jetbrains.kotlin.test.helper.buildRunnerLabel
import org.jetbrains.kotlin.test.helper.gradle.GradleRunConfig
import org.jetbrains.kotlin.test.helper.gradle.computeGradleCommandLine
import org.jetbrains.kotlin.test.helper.gradle.runGradleCommandLine
import org.jetbrains.kotlin.test.helper.toFileNamesString
import javax.swing.ListSelectionModel

@Service(Service.Level.PROJECT)
class TestDataRunnerService(
    val project: Project,
    val scope: CoroutineScope
) {
    fun collectAndRunAllTests(e: AnActionEvent, files: List<VirtualFile>?, debug: Boolean) {
        if (files == null) return

        scope.launch(Dispatchers.Default) {
            doCollectAndRunAllTests(e, files, debug)
        }
    }

    suspend fun doCollectAndRunAllTests(e: AnActionEvent, files: List<VirtualFile>, debug: Boolean, filterByClass: String? = null) {
        val commandLine = withBackgroundProgress(project, title = "Running all tests") {
            reportSequentialProgress { reporter ->
                reporter.indeterminateStep("Collecting tests")

                smartReadAction(project) {
                    val testDeclarations = filterAndCollectTestDeclarations(files, project)

                    val filtered = if (!filterByClass.isNullOrEmpty()) {
                        groupTests(testDeclarations)[filterByClass] ?: testDeclarations
                    } else {
                        testDeclarations
                    }

                    computeGradleCommandLine(filtered)
                }
            }
        }

        val config = GradleRunConfig(
            commandLine,
            title = e.toFileNamesString()?.let { "$filterByClass: $it" },
            debug = debug,
            useProjectBasePath = false,
            runAsTest = true
        )
        runGradleCommandLine(e, config)
    }

    fun collectAndRunSpecificTests(e: AnActionEvent, files: List<VirtualFile>?, debug: Boolean) {
        if (files == null) return

        scope.launch(Dispatchers.Default) {
            val byClass = withBackgroundProgress(project, title = "Running specific tests") {
                reportSequentialProgress { reporter ->
                    reporter.indeterminateStep("Collecting tests")

                    smartReadAction(project) {
                        val testDeclarations = filterAndCollectTestDeclarations(files, project)
                        groupTests(testDeclarations)
                    }
                }
            }

            withContext(Dispatchers.EDT) {
                JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(byClass.keys.sortedBy { it })
                    .setTitle("Select Test Class")
                    .setItemsChosenCallback { selected ->
                        val testMethods = selected
                            .flatMap { byClass[it].orEmpty() }
                            .ifEmpty { return@setItemsChosenCallback }

                        scope.launch(Dispatchers.Default) {
                            val commandLine = smartReadAction(project) {
                                computeGradleCommandLine(testMethods)
                            }

                            withContext(Dispatchers.EDT) {
                                val config = GradleRunConfig(
                                    commandLine,
                                    title = e.toFileNamesString()?.let { "$selected: $it" },
                                    debug = debug,
                                    useProjectBasePath = false,
                                    runAsTest = true,
                                )
                                runGradleCommandLine(e, config)
                            }
                        }

                    }
                    .setNamerForFiltering { it }
                    .setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
                    .createPopup()
                    .showInBestPositionFor(e.dataContext)
            }
        }
    }

    private fun groupTests(testDeclarations: List<PsiNameIdentifierOwner>): Map<String, List<PsiNameIdentifierOwner>> {
        val testTags = TestDataPathsConfiguration.getInstance(project).testTags
        return testDeclarations
            .groupBy { it.parentsOfType<PsiClass>().last() }
            .mapKeys { it.key.buildRunnerLabel(testTags) }
    }
}