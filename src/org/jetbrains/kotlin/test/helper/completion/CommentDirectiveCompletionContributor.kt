package org.jetbrains.kotlin.test.helper.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.test.helper.reference.LANGUAGE_FEATURE_FQ_NAME
import org.jetbrains.kotlin.test.helper.reference.isDirective

class CommentDirectiveCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withElementType(KtTokens.EOL_COMMENT),
            CommentDirectiveCompletionProvider()
        )
    }
}

class CommentDirectiveCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        resultSet: CompletionResultSet
    ) {
        val project = parameters.position.project

        if ((parameters.position as? PsiComment)?.text?.startsWith("// LANGUAGE") == true) {
            completeLanguageFeatures(project, resultSet)
        } else {
            completeDirectives(project, resultSet)
        }
    }

    private fun completeLanguageFeatures(
        project: Project,
        resultSet: CompletionResultSet
    ) {
        val psiFacade = JavaPsiFacade.getInstance(project)
        val classes = psiFacade
            .findClasses(
                LANGUAGE_FEATURE_FQ_NAME,
                GlobalSearchScope.allScope(project)
            )

        classes.flatMap { it.fields.filterIsInstance<PsiEnumConstant>() }
            .mapTo(mutableSetOf()) { it.name }
            .forEach { resultSet.addElement(LookupElementBuilder.create(it)) }
    }

    private fun completeDirectives(
        project: Project,
        resultSet: CompletionResultSet
    ) {
        val scope = GlobalSearchScope.allScope(project)

        val directiveContainer = JavaPsiFacade.getInstance(project)
            .findClass("org.jetbrains.kotlin.test.directives.model.DirectivesContainer", scope)
            ?: return

        val inheritors = ClassInheritorsSearch.search(directiveContainer, scope, true).findAll()

        for (clazz in inheritors) {
            if (clazz !is KtLightClass) continue
            val properties = clazz.kotlinOrigin
                ?.declarations
                ?.filterIsInstance<KtProperty>()
                ?.filter { it.isDirective() }
                ?: continue

            properties.forEach { field ->
                resultSet.addElement(LookupElementBuilder.create(field.name ?: return@forEach))
            }
        }
    }
}
