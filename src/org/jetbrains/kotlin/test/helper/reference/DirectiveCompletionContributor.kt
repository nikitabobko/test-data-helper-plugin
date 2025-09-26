package org.jetbrains.kotlin.test.helper.reference

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.test.helper.getTestDataType

private val directiveRegex = Regex("""//\s*([A-Z0-9a-z_]+)""")

class DirectiveCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.editor.project ?: return
                    val vFile = parameters.originalFile.virtualFile ?: return
                    if (vFile.getTestDataType(project) == null) return

                    val document = parameters.editor.document
                    val caretOffset = parameters.offset
                    val lineNumber = document.getLineNumber(caretOffset)
                    val lineStart = document.getLineStartOffset(lineNumber)
                    val lineEnd = document.getLineEndOffset(lineNumber)
                    val lineText = document.getText(TextRange(lineStart, lineEnd))
                    val colonIndexInLine = lineText.indexOf(":").takeIf { it != -1 }
                    val caretOffsetInLine = caretOffset - lineStart

                    val match = directiveRegex.find(lineText) ?: return
                    val name = match.groups[1]?.value?.uppercase() ?: return

                    if (name == "LANGUAGE" && colonIndexInLine != null && caretOffsetInLine > colonIndexInLine) {
                        addLanguageFeatureVariants(parameters.position, result)
                    } else {
                        addDirectiveNameVariants(parameters.position, result)
                    }
                }
            }
        )
    }

    private fun addDirectiveNameVariants(contextElement: PsiElement, result: CompletionResultSet) {
        for (completionItem in getDirectivesCompletion(contextElement)) {
            result.addElement(completionItem)
        }
    }

    private fun addLanguageFeatureVariants(contextElement: PsiElement, result: CompletionResultSet) {
        for (completionItem in getLanguageFeaturesVariants(contextElement)) {
            result.addElement(completionItem)
        }
    }
}

private const val DIRECTIVES_CONTAINER_FQ_NAME = "org.jetbrains.kotlin.test.directives.model.DirectivesContainer"
private fun getDirectivesCompletion(contextElement: PsiElement): Sequence<LookupElement> {
    val project = contextElement.project
    val scope = GlobalSearchScope.allScope(project)
    val psiFacade = JavaPsiFacade.getInstance(project)

    val base = psiFacade.findClass(DIRECTIVES_CONTAINER_FQ_NAME, scope) ?: return emptySequence()

    return ClassInheritorsSearch.search(base, scope, true).findAll().asSequence()
        .mapNotNull { it.navigationElement as? org.jetbrains.kotlin.psi.KtClassOrObject }
        .filter { !it.hasModifier(KtTokens.OPEN_KEYWORD) && !it.hasModifier(KtTokens.ABSTRACT_KEYWORD) }
        .flatMap { container -> container.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtProperty>() }
        .mapNotNull { property ->
            val name = property.name ?: return@mapNotNull null
            val isDirective = analyze(property) { property.returnType.isSubtypeOf(DIRECTIVE_CLASS_ID) }
            name.takeIf { isDirective }
        }
        .map { LookupElementBuilder.create(it).withTypeText("Directive", /*grayed =*/ true) }
}

private fun getLanguageFeaturesVariants(contextElement: PsiElement): Sequence<LookupElement> {
    val project = contextElement.project
    val psiFacade = JavaPsiFacade.getInstance(project)
    val classes = psiFacade.findClasses(LANGUAGE_FEATURE_FQ_NAME, GlobalSearchScope.allScope(project))
    val constants = classes.map { clazz -> clazz.fields.filterIsInstance<PsiEnumConstant>() }.maxByOrNull { it.size }
    return constants.orEmpty().asSequence().map { enumConst ->
        LookupElementBuilder.create(enumConst.name).withTypeText("LanguageFeature", true)
    }
}
