package org.jetbrains.kotlin.test.helper

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.runTask
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNameIdentifierOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.kotlin.test.helper.actions.collectTestMethodsIfTestData
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.resume
import kotlin.io.path.Path
import kotlin.io.path.pathString

private val DIGIT_REGEX = """\d+""".toRegex()

val VirtualFile.simpleNameUntilFirstDot: String
    get() {
        var processingFirst = true
        val parts = buildList {
            for (part in name.split(".")) {
                val isNumber = DIGIT_REGEX.matches(part)
                if (processingFirst) {
                    add(part)
                    processingFirst = false
                    continue
                }
                if (!isNumber) {
                    break
                }
                add(part)
            }
        }
        return parts.joinToString(".")
    }

private val wildcardPattern = Regex("[{?*\\[]")

fun glob(searchPattern: String, run: (Path) -> Unit) {
    var prefixWithoutWildcards = Path(searchPattern)
    var suffixWithWildcards = Path("")
    while (prefixWithoutWildcards.pathString.contains(wildcardPattern)) {
        suffixWithWildcards = prefixWithoutWildcards.fileName.resolve(suffixWithWildcards)
        prefixWithoutWildcards = prefixWithoutWildcards.parent
    }
    val pathMatcher = prefixWithoutWildcards.fileSystem.getPathMatcher("glob:$searchPattern")

    Files.walkFileTree(
        prefixWithoutWildcards,
        emptySet(),
        suffixWithWildcards.nameCount,
        object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (pathMatcher.matches(file))
                    run(file)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                return FileVisitResult.CONTINUE
            }
        }
    )
}

private val supportedExtensions = listOf("kt", "kts", "args", "can-freeze-ide")

enum class TestDataType {
    File,
    Directory,
    DirectoryOfFiles,
}

fun VirtualFile.getTestDataType(project: Project): TestDataType? {
    val configuration = TestDataPathsConfiguration.getInstance(project)
    if (configuration.testDataDirectories.any { path.startsWith(it) }) return TestDataType.Directory
    if (configuration.testDataFiles.any { path.startsWith(it) }) {
        return when {
            extension in supportedExtensions -> TestDataType.File
            isDirectory -> TestDataType.DirectoryOfFiles
            else -> null
        }
    }
    return null
}

val String.asPathWithoutAllExtensions: String
    get() {
        val separatorLastIndex = lastIndexOf(File.separatorChar)
        var dotPreviousIndex: Int
        var dotIndex = length

        do {
            dotPreviousIndex = dotIndex
            dotIndex = lastIndexOf('.', dotPreviousIndex - 1)
        } while (
            dotIndex > separatorLastIndex && // it also handles `-1`
            !subSequence(dotIndex + 1, dotPreviousIndex).let { it.isNotEmpty() && it.all { c -> c.isDigit() } }
        )

        return substring(0, dotPreviousIndex)
    }

val VirtualFile.nameWithoutAllExtensions get() = name.asPathWithoutAllExtensions

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

fun AnActionEvent.toFileNamesString(): String? {
    val project = project ?: return null
    return getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        ?.filter { it.getTestDataType(project) != null }
        ?.map { it.nameWithoutAllExtensions }
        ?.distinct()
        ?.joinToString(separator = ", ")
}

fun PsiClass.buildRunnerLabel(allTags: Map<String, Array<String>>): String {
    val runnerName = this.name!!
    val tags = allTags.firstNotNullOfOrNull { (pattern, tags) ->
        val patternRegex = pattern.toRegex()
        if (patternRegex.matches(runnerName)) {
            tags.toList()
        } else null
    }.orEmpty()
    return buildString {
        if (tags.isNotEmpty()) {
            this.append(tags.joinToString(prefix = "[", postfix = "] ", separator = ", "))
        }
        this.append(runnerName)
    }
}

fun Project.isGradleEnabled(): Boolean = ExternalSystemApiUtil
    .getLocalSettings<GradleLocalSettings>(this, GradleConstants.SYSTEM_ID)
    .availableProjects
    .isNotEmpty()

context(scope: CoroutineScope)
suspend fun awaitTestRun(project: Project): SMTestProxy.SMRootTestProxy {
    return suspendCancellableCoroutine {
        val connection = project.messageBus.connect(scope)
        connection
            .subscribe(SMTRunnerEventsListener.TEST_STATUS, object : SMTRunnerEventsAdapter() {
                override fun onTestingFinished(testsRoot: SMTestProxy.SMRootTestProxy) {
                    connection.disconnect()
                    it.resume(testsRoot)
                }
            })

        it.invokeOnCancellation { connection.disconnect() }
    }
}

suspend fun generateTestsAndWait(project: Project, files: List<VirtualFile>) {
    val (commandLine, _) = generateTestsCommandLine(project, files)

    suspendCancellableCoroutine {
        runTask(
            TaskExecutionSpec.create(
                project = project,
                systemId = SYSTEM_ID,
                executorId = getRunExecutorInstance().id,
                settings = createGradleExternalSystemTaskExecutionSettings(
                    project, commandLine, useProjectBasePath = true
                )
            )
                .withActivateToolWindowBeforeRun(true)
                .withCallback(object : TaskCallback {
                    override fun onSuccess() = it.resume(Unit)
                    override fun onFailure() = it.resume(Unit)
                }).build()
        )
    }

    for (i in 1..10) {
        val tests = smartReadAction(project) {
            filterAndCollectTestDeclarations(files, project)
        }
        if (tests.isNotEmpty()) break
        delay(500)
    }
}

fun generateTestsCommandLine(project: Project, files: List<VirtualFile>): Pair<String, String> {
    fun isAncestor(basePath: String, vararg strings: String): Boolean {
        val file = VfsUtil.findFile(Paths.get(basePath, *strings), false) ?: return false
        return files.all{  VfsUtil.isAncestor(file, it, false) }
    }

    val basePath = project.basePath
    return if (basePath != null &&
        (isAncestor(basePath, "compiler", "testData", "diagnostics") ||
                isAncestor(basePath, "compiler", "fir", "analysis-tests", "testData"))
    ) {
        "generateFrontendApiTests compiler:tests-for-compiler-generator:generateTests" to "Generate Diagnostic Tests"
    } else {
        "generateTests" to "Generate Tests"
    }
}

fun filterAndCollectTestDeclarations(files: List<VirtualFile>?, project: Project): List<PsiNameIdentifierOwner> {
    if (files == null) return emptyList()
    return files.flatMap { it.collectTestMethodsIfTestData(project) }
}