package com.tovypytho.applexsa.automation

import com.tovypytho.applexsa.core.*
import org.junit.Assert.*
import org.junit.Test

class WhatsAppSelectorsTest {
    private fun node(path: String, text: String = "", id: String = "", box: Box = Box(0, 0, 500, 1000), children: List<UiNode> = emptyList()) =
        UiNode(path, id = id, text = text, box = box, children = children)
    private fun row(path: String, time: String, top: Int) = node(path, box = Box(0, top, 500, top + 250), children = listOf(
        node("$path/0", box = Box(0, top, 500, top + 210)).copy(className = "android.widget.ImageView"),
        node("$path/1", time, box = Box(350, top + 210, 480, top + 240)),
        node("$path/2", box = Box(0, top + 210, 60, top + 250)).copy(description = "Teruskan", clickable = true)
    ))
    private fun channel(rows: List<UiNode>, forward: Boolean = false) = node("0", children = listOf(
        node("0/0", "test", "com.whatsapp:id/toolbar_title", Box(50, 25, 200, 70)),
        node("0/1", "1 pengikut", box = Box(50, 70, 200, 90)),
        node("0/2", box = Box(0, 100, 500, 950), children = rows).copy(className = "androidx.recyclerview.widget.RecyclerView", scrollable = true, scrollForward = forward)
    ))
    @Test fun identicalImagesChooseLastPostByContainerNotImage() {
        val screen = WhatsAppSelectors.read(channel(listOf(row("0/2/0", "11.30", 100), row("0/2/1", "11.31", 400))), 500, 1000, "com.whatsapp")
        assertEquals("0/2/1/2", screen.post?.forwardPath)
        assertTrue(screen.atBottom)
    }
    @Test fun canScrollForwardMeansNotAtBottom() {
        assertFalse(WhatsAppSelectors.read(channel(listOf(row("0/2/0", "11.31", 100)), true), 500, 1000, "com.whatsapp").atBottom)
    }
    @Test fun singlePostWithoutScrollableFlagCanBeAtBottom() {
        val tree = channel(listOf(row("0/2/0", "11.31", 100)))
        val noScroll = tree.copy(children = tree.children.map { if (it.path == "0/2") it.copy(scrollable = false) else it })
        assertTrue(WhatsAppSelectors.read(noScroll, 500, 1000, "com.whatsapp").atBottom)
    }
    @Test fun newerTextUpdateNeverFallsBackToOlderImage() {
        val last = node("0/2/1", box = Box(0, 450, 500, 600), children = listOf(node("0/2/1/0", "barang habis 11.32")))
        val s = WhatsAppSelectors.read(channel(listOf(row("0/2/0", "11.31", 100), last)), 500, 1000, "com.whatsapp")
        assertFalse(s.atBottom); assertNull(s.post)
    }
    @Test fun viewCountDoesNotUnblockSamePost() {
        fun withCounter(value: String): Screen {
            val original = row("0/2/0", "11.31", 100)
            val changed = original.copy(children = original.children + node("0/2/0/3", value))
            return WhatsAppSelectors.read(channel(listOf(changed)), 500, 1000, "com.whatsapp")
        }
        assertEquals(withCounter("1").post?.identity, withCounter("2").post?.identity)
    }
    @Test fun addedReactionAndAggregatedViewCountDoNotUnblockSamePost() {
        val original = row("0/2/0", "11.31 1 tayangan", 100)
        val changed = row("0/2/0", "11.31 20 tayangan", 100).let {
            it.copy(children = it.children + node("0/2/0/4", "3 reaksi", "com.whatsapp:id/reaction"))
        }
        fun read(post: UiNode) = WhatsAppSelectors.read(channel(listOf(post)), 500, 1000, "com.whatsapp")
        assertEquals(read(original).post?.identity, read(changed).post?.identity)
    }
    @Test fun clippedLastPostIsRejected() {
        val screen = WhatsAppSelectors.read(channel(listOf(row("0/2/0", "11.31", 800))), 500, 1000, "com.whatsapp")
        assertFalse(screen.atBottom); assertNull(screen.post)
    }
    @Test fun samePostIdentitySurvivesPreviousRowDisappearing() {
        val a = WhatsAppSelectors.read(channel(listOf(row("0/2/0", "11.30", 100), row("0/2/1", "11.31", 400))), 500, 1000, "com.whatsapp")
        val b = WhatsAppSelectors.read(channel(listOf(row("0/2/0", "11.31", 400))), 500, 1000, "com.whatsapp")
        assertEquals(a.post?.identity, b.post?.identity)
    }
    @Test fun differentTimestampsDistinguishIdenticalImages() {
        fun identity(t: String) = WhatsAppSelectors.read(channel(listOf(row("0/2/0", t, 100))), 500, 1000, "com.whatsapp").post?.identity
        assertNotEquals(identity("11.30"), identity("11.31"))
    }
    @Test fun unsupportedPackageRejected() {
        assertEquals(ScreenKind.OTHER, WhatsAppSelectors.read(channel(emptyList()), 500, 1000, "com.whatsapp.w4b").kind)
    }
    @Test fun diagnosticsExcludeSensitiveValues() {
        val report = WhatsAppSelectors.structuralReport(node("0", "Secret seller +628123456789", children = listOf(node("0/1").copy(description = "private message"))))
        assertFalse(report.contains("Secret")); assertFalse(report.contains("628123")); assertFalse(report.contains("private message"))
    }
    @Test fun unknownScreenHasNoActionablePost() {
        val s = WhatsAppSelectors.read(node("0", "random"), 500, 1000, "com.whatsapp")
        assertEquals(ScreenKind.OTHER, s.kind); assertNull(s.sendPath)
    }
    private fun picker(checked: Boolean) = node("0", children = listOf(
        node("0/0", "Teruskan ke…"),
        node("0/1", children = listOf(
            node("0/1/0", "acc2", "com.whatsapp:id/contactpicker_row_name"),
            node("0/1/1", "", "com.whatsapp:id/contactpicker_row_checkbox").copy(checkable = true, checked = checked)
        )).copy(clickable = true),
        node("0/2").copy(description = "Kirim", clickable = true)
    ))
    @Test fun selectionDoesNotChangeContactIdentity() {
        val a = WhatsAppSelectors.read(picker(false), 500, 1000, "com.whatsapp")
        val b = WhatsAppSelectors.read(picker(true), 500, 1000, "com.whatsapp")
        assertEquals(a.recipients.single().contact, b.recipients.single().contact)
        assertEquals(0, a.selectedCount); assertEquals(1, b.selectedCount)
        assertTrue(b.recipients.single().checked); assertEquals("0/2", b.sendPath)
    }
}
