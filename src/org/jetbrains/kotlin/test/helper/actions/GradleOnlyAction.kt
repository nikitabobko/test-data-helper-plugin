package org.jetbrains.kotlin.test.helper.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.NlsActions
import org.jetbrains.kotlin.test.helper.isGradleEnabled
import javax.swing.Icon

abstract class GradleOnlyAction : AnAction {
    constructor() : super()
    constructor(text: @NlsActions.ActionText String?) : super(text)

    constructor(
        text: @NlsActions.ActionText String?,
        description: @NlsActions.ActionDescription String?,
        icon: Icon?,
    ) : super(text, description, icon)

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.isGradleEnabled() == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}