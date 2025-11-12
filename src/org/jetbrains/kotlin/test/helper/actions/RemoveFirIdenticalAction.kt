package org.jetbrains.kotlin.test.helper.actions

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.rd.util.lifetime
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.test.helper.collectDiffsRecursively
import java.nio.file.Paths
import kotlin.collections.orEmpty

class RemoveFirIdenticalAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        val context = e.dataContext
        val tests = AbstractTestProxy.DATA_KEYS.getData(context).orEmpty()
        presentation.isEnabledAndVisible = tests
            .flatMap { it.collectDiffsRecursively(mutableListOf()) }
            .any { it.left.contains("FIR_IDENTICAL") }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val tests = AbstractTestProxy.DATA_KEYS.getData(e.dataContext) ?: return
        val project = e.project ?: return
        project.lifetime.coroutineScope.launch {
            val files = tests
                .flatMap { it.collectDiffsRecursively(mutableListOf()) }
                .filter { it.left.contains("FIR_IDENTICAL") }
                .mapNotNull { it.filePath }
                .distinct()
                .ifEmpty { return@launch }

            @Suppress("UnstableApiUsage")
            writeCommandAction(project, "Removing FIR_IDENTICAL") {
                for (file in files) {
                    val document = VfsUtil.findFile(Paths.get(file), true)?.findDocument() ?: continue

                    val newText = document.text.lines().filterNot { it == "// FIR_IDENTICAL" }.joinToString("\n")
                    document.replaceString(0, document.textLength, newText)

                    PsiDocumentManager.getInstance(project).commitDocument(document)
                }
            }
        }
    }
}