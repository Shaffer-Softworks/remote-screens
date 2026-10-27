package tech.shaffersoftworks.remotescreens.player.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The composable-preview rule, plus two bans from the Kotlin and Compose rules:
 * no [kotlinx.coroutines.GlobalScope], and no Android XML layouts.
 */
class ComposablePreviewRuleTest {
    @Test
    fun everyUiComposableHasASameFilePreview() {
        val problems = uiSources().flatMap { file -> findings(file) }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun kotlinSourcesDoNotUseGlobalScopeOrXmlLayouts() {
        val scopeHits = kotlinSources().filter { source -> "GlobalScope" in source.readText() }.map { it.path }
        val layoutDir = File("src/main/res/layout")
        val layoutHit = layoutDir.isDirectory && layoutDir.listFiles().orEmpty().isNotEmpty()
        val problems = if (layoutHit) scopeHits + layoutDir.path else scopeHits
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun findings(file: File): List<String> {
        val functions = parseFunctions(file.readText())
        val missing = functions.filter { isUi(it) && !isCovered(it, functions, emptySet()) }
            .map { "${file.name} ${it.name} has no @Preview in this file that calls it" }
        val shape = functions.filter { isPreview(it) }.flatMap { previewProblems(file.name, it) }
        return missing + shape
    }

    private fun isCovered(function: SourceFunction, functions: List<SourceFunction>, seen: Set<String>): Boolean {
        val previews = functions.filter { isPreview(it) }
        val coveredByCall = previews.any { preview -> calls(preview.body, function.name) }
        val sideEffect = !callsLayout(function.body)
        val wiring = "hiltViewModel()" in function.signature && function.name !in seen
        val callees = functions.filter { isUi(it) && calls(function.body, it.name) }
        val calleesCovered = wiring && callees.isNotEmpty() &&
            callees.all { callee -> isCovered(callee, functions, seen + function.name) }
        return coveredByCall || sideEffect || calleesCovered
    }

    private fun previewProblems(fileName: String, function: SourceFunction): List<String> {
        val where = "$fileName ${function.name}"
        val width = WIDTH.find(function.annotations)?.groupValues?.get(1)
        val height = HEIGHT.find(function.annotations)?.groupValues?.get(1)
        val canvas = when {
            width == null || height == null -> "$where needs widthDp and heightDp"
            width != WALL_WIDTH_DP.toString() || height != WALL_HEIGHT_DP.toString() ->
                if (function.annotations.contains("name")) null else "$where uses a custom canvas and needs a name"
            else -> null
        }
        val privacy = if (function.modifiers.contains("private")) null else "$where must be private"
        val theme = if (function.body.contains("RemoteScreensTheme")) {
            null
        } else {
            "$where must wrap RemoteScreensTheme"
        }
        val named = if (function.name.endsWith("Preview")) null else "$where must end with Preview"
        return listOfNotNull(canvas, privacy, theme, named)
    }

    private fun uiSources(): List<File> =
        File("src/main/kotlin").walkTopDown().filter { file ->
            file.isFile && file.extension == "kt" && "/ui/" in file.invariantSeparatorsPath
        }.toList()

    private fun kotlinSources(): List<File> =
        listOf(File("src/main"), File("../server/src/main"), File("../shared/src/main")).flatMap { root ->
            if (root.isDirectory) {
                root.walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList()
            } else {
                emptyList()
            }
        }

    private data class SourceFunction(
        val name: String,
        val annotations: String,
        val modifiers: String,
        val signature: String,
        val returnType: String?,
        val body: String,
    )

    private companion object {
        const val WALL_WIDTH_DP = 800
        const val WALL_HEIGHT_DP = 480
        const val TRIPLE_QUOTE_LENGTH = 3

        val FUNCTION: Regex = Regex(
            """(?m)^((?:[ \t]*@.*\n)*)[ \t]*((?:(?:public|private|internal|protected|override)[ \t]+)*)""" +
                """fun[ \t]+([A-Za-z_][A-Za-z0-9_]*)""",
        )
        val WIDTH: Regex = Regex("""widthDp\s*=\s*(\d+)""")
        val HEIGHT: Regex = Regex("""heightDp\s*=\s*(\d+)""")
        val CALL: Regex = Regex("""(?<![\w.])([A-Z][A-Za-z0-9_]*)\s*[({\n]""")

        val NON_LAYOUT: Set<String> = setOf(
            "LaunchedEffect",
            "DisposableEffect",
            "SideEffect",
            "remember",
            "rememberLauncherForActivityResult",
            "hiltViewModel",
            "collectAsStateWithLifecycle",
            "produceState",
            "derivedStateOf",
            "LocalActivity",
            "LocalContext",
            "LocalLifecycleOwner",
            "LocalConfiguration",
            "stringResource",
            "painterResource",
            "colorResource",
            "PermissionPrompts",
        )

        fun isPreview(function: SourceFunction): Boolean = "@Preview" in function.annotations

        fun isUi(function: SourceFunction): Boolean {
            val returnsValue = function.returnType != null && function.returnType != "Unit"
            return "@Composable" in function.annotations && !isPreview(function) && !returnsValue
        }

        fun calls(body: String, name: String): Boolean =
            Regex("""(?<![\w.])${Regex.escape(name)}\s*[({\n]""").containsMatchIn(body)

        fun callsLayout(body: String): Boolean = CALL.findAll(body).any { match -> match.groupValues[1] !in NON_LAYOUT }

        fun parseFunctions(text: String): List<SourceFunction> =
            FUNCTION.findAll(text).map { match ->
                val paramsEnd = endOfParameters(text, match.range.last + 1)
                val (returnType, bodyStart) = splitReturn(text, paramsEnd)
                SourceFunction(
                    name = match.groupValues[3],
                    annotations = match.groupValues[1],
                    modifiers = match.groupValues[2],
                    signature = text.substring(match.range.first, bodyStart),
                    returnType = returnType,
                    body = readBlock(text, bodyStart),
                )
            }.toList()

        fun endOfParameters(text: String, start: Int): Int {
            var index = skipSpace(text, start)
            var paren = 0
            var brace = 0
            var end = index
            var closed = index >= text.length || text[index] != '('
            while (index < text.length && !closed) {
                if (text[index] == '"') {
                    index = readString(text, index)
                } else {
                    when (text[index]) {
                        '(' -> paren++
                        ')' -> {
                            paren--
                            if (paren == 0 && brace == 0) {
                                end = index + 1
                                closed = true
                            }
                        }
                        '{' -> brace++
                        '}' -> if (brace > 0) brace--
                    }
                    if (!closed) index++
                }
            }
            return end
        }

        fun splitReturn(text: String, paramsEnd: Int): Pair<String?, Int> {
            var index = skipSpace(text, paramsEnd)
            val typeStart = index + 1
            val hasType = index < text.length && text[index] == ':'
            val returnType = if (hasType) {
                var typeEnd = typeStart
                while (typeEnd < text.length && text[typeEnd] != '{' && text[typeEnd] != '=') typeEnd++
                index = typeEnd
                text.substring(typeStart, typeEnd).trim()
            } else {
                null
            }
            return returnType to index
        }

        fun readBlock(text: String, start: Int): String {
            val index = skipSpace(text, start)
            return when {
                index < text.length && text[index] == '{' -> text.substring(index, endOfBrace(text, index))
                index < text.length && text[index] == '=' -> text.substring(index, endOfExpression(text, index))
                else -> ""
            }
        }

        fun endOfBrace(text: String, open: Int): Int {
            var index = open
            var brace = 0
            var end = text.length
            var closed = false
            while (index < text.length && !closed) {
                if (text[index] == '"') {
                    index = readString(text, index)
                } else {
                    if (text[index] == '{') brace++
                    if (text[index] == '}') {
                        brace--
                        if (brace == 0) {
                            end = index + 1
                            closed = true
                        }
                    }
                    if (!closed) index++
                }
            }
            return end
        }

        fun endOfExpression(text: String, equals: Int): Int {
            var index = equals + 1
            var paren = 0
            var brace = 0
            var end = text.length
            var closed = false
            while (index < text.length && !closed) {
                if (text[index] == '"') {
                    index = readString(text, index)
                } else {
                    when (text[index]) {
                        '(' -> paren++
                        ')' -> if (paren > 0) paren--
                        '{' -> brace++
                        '}' -> if (brace > 0) brace--
                        '\n' -> if (paren == 0 && brace == 0) {
                            end = index
                            closed = true
                        }
                    }
                    if (!closed) index++
                }
            }
            return end
        }

        fun readString(text: String, start: Int): Int {
            val triple = text.startsWith("\"\"\"", start)
            var end = text.length
            if (triple) {
                val close = text.indexOf("\"\"\"", start + TRIPLE_QUOTE_LENGTH)
                if (close >= 0) end = close + TRIPLE_QUOTE_LENGTH
            } else {
                var index = start + 1
                var closed = false
                while (index < text.length && !closed) {
                    val escaped = text[index] == '\\'
                    val quote = !escaped && text[index] == '"'
                    end = if (quote) index + 1 else end
                    closed = quote
                    index = if (escaped) index + 2 else index + 1
                }
            }
            return end
        }

        fun skipSpace(text: String, start: Int): Int {
            var index = start
            while (index < text.length && text[index].isWhitespace()) index++
            return index
        }
    }
}
