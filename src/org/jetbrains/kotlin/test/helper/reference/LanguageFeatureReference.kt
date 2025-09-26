package org.jetbrains.kotlin.test.helper.reference

import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope

internal const val LANGUAGE_FEATURE_FQ_NAME = "org.jetbrains.kotlin.config.LanguageFeature"

class LanguageFeatureReference(
    element: PsiElement,
    range: TextRange,
    private val key: String
) : PsiReferenceBase<PsiElement>(element, range), PsiPolyVariantReference {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val project = myElement.project
        val psiFacade = JavaPsiFacade.getInstance(project)
        val classes = psiFacade
            .findClasses(
                LANGUAGE_FEATURE_FQ_NAME,
                GlobalSearchScope.allScope(project)
            )

        return classes.mapNotNull { clazz ->
            clazz.fields
                .filterIsInstance<PsiEnumConstant>()
                .firstOrNull { it.name == key }
                ?.let { PsiElementResolveResult(it) }
        }.toTypedArray()
    }

    override fun resolve(): PsiElement? {
        return multiResolve(false).singleOrNull()?.element
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
