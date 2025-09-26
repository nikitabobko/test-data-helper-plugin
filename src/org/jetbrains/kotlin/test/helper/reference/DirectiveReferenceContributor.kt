package org.jetbrains.kotlin.test.helper.reference

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.test.helper.getTestDataType

class DirectiveReferenceContributor : PsiReferenceContributor() {
    val regex = Regex("// ([A-Z_]+)(:.*)?")
    val regexLanguageFeature = Regex("[+-]([A-Za-z0-9_]+)")

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiComment::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val file =
                        element.containingFile?.virtualFile ?: return PsiReference.EMPTY_ARRAY
                    if (file.getTestDataType(element.project) == null) return PsiReference.EMPTY_ARRAY

                    val text = element.text
                    val refs = mutableListOf<PsiReference>()

                    val regex = regex
                    regex.matchAt(text, 0)?.let { match ->
                        val (name, matchRange) = match.groups[1] ?: return@let
                        val range = TextRange(matchRange.first, matchRange.last + 1)
                        refs.add(TestDirectiveReference(element, range, name))

                        if (name == "LANGUAGE") {
                            val (features, featuresRange) = match.groups[2] ?: return@let
                            regexLanguageFeature.findAll(features).forEach {
                                val (feature, featureRange) = it.groups[1] ?: return@forEach
                                refs.add(
                                    LanguageFeatureReference(
                                        element,
                                        TextRange(
                                            featuresRange.first + featureRange.first,
                                            featuresRange.first + featureRange.last + 1,
                                        ),
                                        feature
                                    )
                                )
                            }
                        }
                    }

                    return refs.toTypedArray()
                }
            }
        )
    }
}
