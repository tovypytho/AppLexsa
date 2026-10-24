package com.tovypytho.applexsa.automation

import com.tovypytho.applexsa.core.*

/** Conservative Indonesian WhatsApp adapter. These selectors require real-device validation. */
object WhatsAppSelectors {
    private fun UiNode.idIs(vararg names: String) = id.substringAfterLast('/') in names
    private fun UiNode.isForward() = normalized(description) in setOf("teruskan", "teruskan pembaruan", "forward", "forward update") ||
        idIs("forward", "forward_button", "newsletter_forward")
    private fun UiNode.isSend() = normalized(description) in setOf("kirim", "send") || idIs("send", "send_button")
    private val time = Regex("(?:^|\\s)\\d{1,2}[.:]\\d{2}(?:\\s|$)")
    private val selectedHeader = Regex("^(\\d+) (?:dipilih|selected)$")

    fun read(root: UiNode, width: Int, height: Int, packageName: String): Screen {
        if (packageName != "com.whatsapp") return Screen(packageName = packageName)
        val nodes = root.all().filter { it.visible && it.enabled }
        fun find(vararg names: String) = nodes.firstOrNull { it.idIs(*names) }
        val title = find("conversation_contact_name", "toolbar_title", "title")?.text?.takeIf { it.isNotBlank() }
            ?: nodes.filter { it.box.top in 1..(height / 7) && it.text.isNotBlank() && !it.clickable && it.children.isEmpty() }
                .map { it.text }.filterNot { it.contains(Regex("\\d+[.:]\\d+")) || it.contains("pengikut") || it.contains("dipilih") }
                .distinct().singleOrNull().orEmpty()
        val picker = nodes.any { normalized(it.text) in setOf("teruskan ke…", "teruskan ke...", "teruskan ke", "forward to…", "forward to...") } ||
            find("contactpicker_recycler_view", "contact_picker_list", "contactpicker_row_name") != null
        if (picker) {
            val recipients = recipientRows(nodes)
            val countFromHeader = nodes.mapNotNull { selectedHeader.matchEntire(normalized(it.text))?.groupValues?.get(1)?.toIntOrNull() }.distinct().singleOrNull()
            val checkables = nodes.filter { it.checkable }
            val checkedCount = checkables.count { it.checked }
            val count = countFromHeader?.let { maxOf(it, checkedCount) }
                ?: if (checkables.isNotEmpty()) checkedCount else null
            return Screen(ScreenKind.PICKER, recipients = recipients, selectedCount = count,
                sendPath = nodes.singleOrNull { it.isSend() && it.clickable }?.path,
                searchPath = nodes.firstOrNull { normalized(it.description) in setOf("cari", "search") && it.clickable }?.path,
                searchFieldPath = nodes.singleOrNull { it.className.endsWith("EditText") }?.path,
                width = width, height = height)
        }
        val channel = nodes.any { normalized(it.label).contains(Regex("\\d[\\d.,]* pengikut")) }
        if (channel && title.isNotBlank()) {
            val list = nodes.filter { it.className.endsWith("RecyclerView") || it.className.endsWith("ListView") }
                .maxByOrNull { it.box.height }
            val rows = list?.children.orEmpty().filter { row ->
                row.visible && row.all().any { time.containsMatchIn(it.label) }
            }
            val row = rows.lastOrNull()
            val complete = row != null && list != null && list.children.lastOrNull { it.visible } == row &&
                row.all().any { it.className.endsWith("ImageView") || it.isForward() } && list.box.contains(row.box) && row.all().filter { it.className.endsWith("ImageView") }
                .all { list.box.contains(it.box) }
            val atBottom = list != null && !list.scrollForward && complete
            val post = row?.takeIf { complete }?.let {
                // Never use counters, reaction nodes, tree shape, paths or coordinates as identity.
                // Without a WhatsApp message ID, indistinguishable same-minute posts must collide
                // conservatively rather than allowing a resend after the UI changes.
                val timestamps = it.all().filter { n -> n.children.isEmpty() }.flatMap { n ->
                    time.findAll(normalized(n.text)).map { match -> match.value.trim() }.toList()
                }.distinct().sorted()
                val captions = it.all().filter { n -> n.idIs("caption", "message_text", "message_caption", "text_message") }
                    .map { n -> normalized(n.text) }.filter { s -> s.isNotBlank() }.distinct()
                // Visible preceding rows change as the viewport moves. Do not use their count or
                // screen coordinates as identity: that would allow a duplicate on a changed viewport.
                // Indistinguishable same-minute posts collide conservatively and need manual reset.
                val identity = digest("$title|$timestamps|$captions")
                val hasIdentity = timestamps.isNotEmpty()
                val forward = it.all().filter { n -> n.isForward() }.singleOrNull()
                val layout = digest("$width|$height|${it.box}|${it.all().map { n -> n.id + n.className + n.box }}")
                if (!hasIdentity) null else Post(identity, forward?.path, it.box, layout)
            }
            return Screen(ScreenKind.SOURCE, channel = title, post = post, atBottom = atBottom, width = width, height = height)
        }
        val chat = find("entry", "conversation_entry") != null && title.isNotBlank()
        return Screen(if (chat) ScreenKind.CHAT else ScreenKind.OTHER, chatContact = if (chat) title else "", width = width, height = height)
    }

    private fun recipientRows(nodes: List<UiNode>): List<Recipient> {
        val names = nodes.filter { it.idIs("contactpicker_row_name", "contact_name", "display_name") && it.text.isNotBlank() }
        return names.mapNotNull { name ->
            val ancestors = nodes.filter { name.path.startsWith(it.path + "/") }.sortedByDescending { it.path.length }
            val row = ancestors.firstOrNull { it.clickable && it.all().any { n -> n.checkable || n.idIs("contactpicker_row_checkbox", "checkbox") } }
                ?: return@mapNotNull null
            val labels = row.all().filter { !it.checkable && !it.idIs("checkbox", "contactpicker_row_checkbox") }
                .flatMap { listOf(it.text, it.description) }.map(::normalized).filter { it.isNotBlank() }.distinct().sorted()
            Recipient(Contact(name.text.trim(), digest(labels.joinToString("|"))), row.path,
                row.all().any { it.checked || (it.checkable && it.selected) })
        }.distinctBy { it.path }
    }

    /** Export structure and capabilities, never message text or contact content. */
    fun structuralReport(root: UiNode): String = root.all().take(500).joinToString("\n") {
        "${it.path} id=${it.id} class=${it.className.substringAfterLast('.')} bounds=${it.box} " +
            "click=${it.clickable} checkable=${it.checkable} checked=${it.checked} scroll=${it.scrollable}/${it.scrollForward} " +
            "textLength=${it.text.length} descLength=${it.description.length}"
    }
}
