package org.jetbrains.kotlin.test.helper.gradle

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

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