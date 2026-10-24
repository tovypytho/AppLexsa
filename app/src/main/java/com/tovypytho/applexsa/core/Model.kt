package com.tovypytho.applexsa.core

import java.security.MessageDigest

fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
fun normalized(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val x get() = (left + right) / 2
    val y get() = (top + bottom) / 2
    val width get() = right - left
    val height get() = bottom - top
    fun contains(other: Box) = other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
}

/** An immutable accessibility snapshot. Node paths are valid ONLY within this snapshot. */
data class UiNode(
    val path: String, val id: String = "", val text: String = "", val description: String = "",
    val className: String = "", val box: Box = Box(0, 0, 0, 0),
    val clickable: Boolean = false, val enabled: Boolean = true, val visible: Boolean = true,
    val checkable: Boolean = false, val checked: Boolean = false, val selected: Boolean = false,
    val scrollable: Boolean = false, val scrollForward: Boolean = false,
    val children: List<UiNode> = emptyList()
) {
    fun all(): List<UiNode> = listOf(this) + children.flatMap { it.all() }
    val label get() = listOf(text, description).filter { it.isNotBlank() }.distinct().joinToString(" ")
}

data class Contact(val name: String, val signature: String)
data class Calibration(val x: Int, val y: Int, val width: Int, val height: Int, val layout: String)
data class Profile(
    val channel: String = "", val contact: Contact? = null, val enabled: Boolean = false,
    val lastAttempt: String = "", val calibration: Calibration? = null
)
data class Post(val identity: String, val forwardPath: String?, val box: Box, val layout: String)
data class Recipient(val contact: Contact, val path: String, val checked: Boolean)
enum class ScreenKind { SOURCE, PICKER, CHAT, OTHER }
data class Screen(
    val kind: ScreenKind = ScreenKind.OTHER, val channel: String = "",
    val post: Post? = null, val atBottom: Boolean = false, val recipients: List<Recipient> = emptyList(),
    val selectedCount: Int? = null, val sendPath: String? = null, val searchPath: String? = null,
    val searchFieldPath: String? = null, val chatContact: String = "",
    val width: Int = 0, val height: Int = 0, val packageName: String = "com.whatsapp"
)

enum class Stage { READY, WAIT_PICKER, WAIT_SELECTION, WAIT_RESULT, RETURNING, COMPLETE, ERROR }
sealed interface Command {
    data class Forward(val post: Post, val calibration: Calibration?) : Command
    data class Select(val recipient: Recipient) : Command
    data class Search(val query: String, val path: String, val edit: Boolean) : Command
    data class Send(val path: String, val identity: String) : Command
    data object Back : Command
}
