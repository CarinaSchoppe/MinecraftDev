/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2024 minecraft-dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, version 3.0 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.demonwav.mcdev.toml

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.lang.annotations.Language
import org.toml.lang.psi.TomlArrayTable
import org.toml.lang.psi.TomlElement
import org.toml.lang.psi.TomlFileType
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlTable

// Modified version of the intellij-rust file
// https://github.com/intellij-rust/intellij-rust/blob/42d0981d45e8830aa5efe82c45688bab8223201c/toml/src/main/kotlin/org/rust/toml/completion/RsTomlKeysCompletionProvider.kt
class TomlSchema private constructor(
    val topLevelEntries: Set<TomlSchemaEntry>,
    private val tables: List<TomlTableSchema>,
) {

    fun topLevelKeys(isArray: Boolean): Set<String> =
        tables.filter { it.isArray == isArray }.mapTo(mutableSetOf()) { it.name }

    fun topLevelEntries(isArray: Boolean): Set<TomlSchemaEntry> =
        tables.filter { it.isArray == isArray }.flatMapTo(mutableSetOf()) { it.entries }

    fun keysForTable(tableName: String): Set<String> =
        tableSchema(tableName)?.entries?.mapTo(mutableSetOf()) { it.key }.orEmpty()

    fun entriesForTable(tableName: String): Set<TomlSchemaEntry> =
        tableSchema(tableName)?.entries.orEmpty()

    fun tableEntry(tableName: String, key: String): TomlSchemaEntry? =
        tableSchema(tableName)?.entries?.find { it.key == key }

    fun tableSchema(tableName: String): TomlTableSchema? =
        tables.find { it.name == tableName }

    companion object {
        fun parse(project: Project, @Language("TOML") example: String): TomlSchema {
            val toml = PsiFileFactory.getInstance(project)
                .createFileFromText("dummy.toml", TomlFileType, example)

            val rootKeys = toml.children
                .filterIsInstance<TomlKeyValue>()
                .mapTo(mutableSetOf()) { it.schemaEntry }
            val tables = toml.children
                .filterIsInstance<TomlKeyValueOwner>()
                .mapNotNull { it.schema }

            return TomlSchema(rootKeys, tables)
        }
    }
}

private val TomlKeyValueOwner.schema: TomlTableSchema?
    get() {
        val (name, isArray) = when (this) {
            is TomlTable -> header.key?.segments?.firstOrNull()?.text to false
            is TomlArrayTable -> header.key?.segments?.firstOrNull()?.text to true
            else -> return null
        }
        if (name == null) return null

        val keys = entries.mapTo(mutableSetOf()) { it.schemaEntry }
        val description = getComments()
        return TomlTableSchema(name, isArray, keys, description)
    }

private val TomlKeyValue.schemaEntry: TomlSchemaEntry
    get() = TomlSchemaEntry(this.key.text, getComments(), value?.tomlType)

private fun TomlElement.getComments(): List<String> {
    val comments = mutableListOf<String>()
    var sibling = PsiTreeUtil.skipWhitespacesBackward(this)
    while (sibling is PsiComment) {
        comments += sibling.text.removePrefix("#").trim()
        sibling = PsiTreeUtil.skipWhitespacesBackward(sibling)
    }
    return comments.reversed()
}

data class TomlSchemaEntry(
    val key: String,
    val description: List<String>,
    val type: TomlValueType?,
)

class TomlTableSchema(
    val name: String,
    val isArray: Boolean,
    val entries: Set<TomlSchemaEntry>,
    val description: List<String>,
)
