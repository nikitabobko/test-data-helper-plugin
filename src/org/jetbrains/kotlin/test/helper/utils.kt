package org.jetbrains.kotlin.test.helper

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiClass
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.Path
import kotlin.io.path.pathString

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

