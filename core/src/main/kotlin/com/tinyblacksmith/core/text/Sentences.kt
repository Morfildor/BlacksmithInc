package com.tinyblacksmith.core.text

/** One clause as a sentence: a capital first letter and exactly one closing period. Blank stays blank. */
fun String.asSentence(): String = trim().trimEnd('.').trimEnd().replaceFirstChar { it.uppercase() }.let { if (it.isEmpty()) it else "$it." }

/** Short clauses as consecutive sentences; empty when there are none. The one place display lists are joined (no semicolon chains). */
fun List<String>.joinSentences(): String = map { it.asSentence() }.filter { it.isNotEmpty() }.joinToString(" ")
