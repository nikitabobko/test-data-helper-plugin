package org.jetbrains.kotlin.test.helper.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.addRemoveModifier.setModifierList
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class CreateContextualOverloadIntention : PsiElementBaseIntentionAction() {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val function = element.findFunctionParent() ?: return false
        return function.nameIdentifier == element
                && function.valueParameters.singleOrNull()?.typeReference?.text == "FirSession"
                && function.contextReceiverList == null
                && function.hasBody()
    }

    @OptIn(KaIdeApi::class)
    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val factory = KtPsiFactory(project)
        val function = element.findFunctionParent() ?: return
        val parameter = function.valueParameters.singleOrNull() ?: return
        val functionName = function.name

        val copy1 = function.parent.addAfter((function.copy() as KtNamedFunction).apply {
            equalsToken?.delete()
            bodyExpression?.replace(
                factory.createBlock(
                    "return $functionName()"
                )
            )
            valueParameters.first().run {
                addAnnotationEntry(factory.createAnnotationEntry("""@Suppress("unused")"""))
                setName("s")
            }
            setModifierList(factory.createModifierList("context(sessionHolder: org.jetbrains.kotlin.fir.SessionHolder)\n"))
            addAnnotationEntry(factory.createAnnotationEntry("""@Deprecated("Use parameterless overload", replaceWith = ReplaceWith("$functionName()"))"""))
        }, function) as KtElement

        val copy2 = function.parent.addAfter((function.copy() as KtNamedFunction).apply {
            equalsToken?.delete()
            bodyExpression?.replace(
                factory.createBlock(
                    "return $functionName(${parameter.nameAsName} = sessionHolder.session)"
                )
            )
            valueParameters.first().delete()
            setModifierList(factory.createModifierList("context(sessionHolder: org.jetbrains.kotlin.fir.SessionHolder)\n"))
        }, function) as KtElement

        shortenReferences(listOf(copy1, copy2))
    }

    private fun PsiElement.findFunctionParent(): KtNamedFunction? {
        return parents(withSelf = true).firstIsInstanceOrNull<KtNamedFunction>()
    }

    override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }

    override fun startInWriteAction(): Boolean = true
    override fun getText(): @IntentionName String = familyName
    override fun getFamilyName(): @IntentionFamilyName String = "Add contextual overloads"
}