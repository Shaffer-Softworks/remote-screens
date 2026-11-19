package tech.shaffersoftworks.remotescreens.detekt

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import dev.detekt.api.modifiedText
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression

/**
 * Requires a blank line between logical blocks inside a function, lambda, or other block.
 * Single-line declarations, calls, and assignments may stay grouped.
 */
class StatementSpacingRule(config: Config) : Rule(
    config,
    "Logical blocks inside a code block are separated by a blank line.",
) {
    override val ruleName: RuleName = RuleName("StatementSpacing")

    private val insertionOffsets = mutableListOf<Int>()

    override fun preVisit(root: KtFile) {
        insertionOffsets.clear()
    }

    override fun postVisit(root: KtFile) {
        if (!autoCorrect || insertionOffsets.isEmpty()) {
            return
        }

        val corrected = StringBuilder(root.text)

        insertionOffsets.sortedDescending().forEach { offset ->
            corrected.insert(offset, "\n")
        }

        root.modifiedText = corrected.toString()
    }

    override fun visitBlockExpression(expression: KtBlockExpression) {
        checkStatementSpacing(expression)
        super.visitBlockExpression(expression)
    }

    private fun checkStatementSpacing(expression: KtBlockExpression) {
        val statements = expression.statements
        val fileText = expression.containingKtFile.text

        for (index in 1 until statements.size) {
            val previous = statements[index - 1]
            val current = statements[index]

            if (requiresBlankLine(previous, current) && !hasBlankLine(
                    fileText = fileText,
                    previous = previous,
                    current = current,
                )
            ) {
                recordBlankLine(fileText, previous, current)
                report(Finding(Entity.from(current), MISSING_BLANK_LINE))
            }
        }
    }

    private fun requiresBlankLine(previous: KtExpression, current: KtExpression): Boolean =
        isControlFlow(previous) ||
            isControlFlow(current) ||
            spansMultipleLines(previous) ||
            spansMultipleLines(current) ||
            current is KtReturnExpression

    private fun isControlFlow(expression: KtExpression): Boolean =
        expression is KtIfExpression ||
            expression is KtWhenExpression ||
            expression is KtLoopExpression ||
            expression is KtTryExpression

    private fun spansMultipleLines(expression: KtExpression): Boolean = expression.text.contains('\n')

    private fun recordBlankLine(fileText: String, previous: KtExpression, current: KtExpression) {
        val gapStart = previous.textRange.endOffset

        val gapEnd = leadingCommentStart(
            fileText = fileText,
            previous = previous,
            current = current,
        )

        val gap = if (gapEnd > gapStart) fileText.substring(gapStart, gapEnd) else ""
        val missingNewlines = BLANK_LINE_NEWLINES - gap.count { it == '\n' }

        if (missingNewlines <= 0) {
            return
        }

        val lastNewline = gap.lastIndexOf('\n')
        val insertAt = if (lastNewline >= 0) gapStart + lastNewline else gapStart
        repeat(missingNewlines) { insertionOffsets += insertAt }
    }

    private fun hasBlankLine(fileText: String, previous: KtExpression, current: KtExpression): Boolean {
        val gapStart = previous.textRange.endOffset

        val gapEnd = leadingCommentStart(
            fileText = fileText,
            previous = previous,
            current = current,
        )

        if (gapEnd <= gapStart) {
            return false
        }

        val textBetween = fileText.substring(gapStart, gapEnd)

        return textBetween.count { it == '\n' } >= BLANK_LINE_NEWLINES
    }

    /**
     * Comments sitting on the lines immediately above [current] belong to that statement.
     * The blank line is required above those comments, not between the comment and the statement.
     * An end-of-line comment on [previous] stays with [previous].
     */
    private fun leadingCommentStart(fileText: String, previous: KtExpression, current: KtExpression): Int {
        var sibling = current.prevSibling
        var commentStart = current.textRange.startOffset

        while (sibling != null && sibling !== previous) {
            when (sibling) {
                is PsiWhiteSpace -> sibling = sibling.prevSibling
                is PsiComment, is KDoc -> {
                    if (!hasLineBreakBetween(
                            fileText = fileText,
                            previous = previous,
                            element = sibling,
                        )
                    ) {
                        break
                    }

                    commentStart = sibling.textRange.startOffset
                    sibling = sibling.prevSibling
                }
                else -> break
            }
        }

        return commentStart
    }

    private fun hasLineBreakBetween(fileText: String, previous: KtExpression, element: PsiElement): Boolean {
        val start = previous.textRange.endOffset
        val end = element.textRange.startOffset

        if (end <= start) {
            return false
        }

        return fileText.substring(start, end).contains('\n')
    }

    private companion object {
        const val MISSING_BLANK_LINE = "Expected a blank line before this statement."
        const val BLANK_LINE_NEWLINES = 2
    }
}
