package org.jetbrains.kotlin.test.helper

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.stacktrace.DiffHyperlink

fun AbstractTestProxy.collectDiffsRecursively(list: MutableList<DiffHyperlink>): List<DiffHyperlink> {
    if (isLeaf) {
        list.addAll(diffViewerProviders)
    } else {
        for (child in children) {
            child.collectDiffsRecursively(list)
        }
    }
    return list
}