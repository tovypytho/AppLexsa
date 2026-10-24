package com.tovypytho.applexsa.core

import org.junit.Assert.*
import org.junit.Test

class ForwardEngineTest {
    private var now = 100L
    private val engine = ForwardEngine { now }
    private val contact = Contact("acc2", "identity-a")
    private val profile = Profile("test", contact, true)
    private val post = Post("post-1", "0/3", Box(0, 100, 500, 800), "layout")
    private fun source(p: Post = post) = Screen(ScreenKind.SOURCE, "test", p, true, width = 500, height = 1000)
    private fun picker(selected: Boolean = false, contacts: List<Recipient>? = null, count: Int? = if (selected) 1 else 0) =
        Screen(ScreenKind.PICKER, recipients = contacts ?: listOf(Recipient(contact, "0/1", selected)), selectedCount = count, sendPath = "0/2")
    private fun reachSend(): Command.Send {
        assertTrue(engine.begin(source(), profile) is Command.Forward)
        assertTrue(engine.observe(picker()) is Command.Select)
        now += 200
        return engine.observe(picker(true)) as Command.Send
    }
    @Test fun oneTapOneSendAndReturnWithoutBack() {
        val send = reachSend()
        assertEquals("post-1", send.identity)
        assertNull(engine.observe(picker(true)))
        assertNull(engine.observe(source(post.copy(identity = "new"))))
        assertEquals(Stage.COMPLETE, engine.stage)
        assertEquals("SEND_COMMAND_EXECUTED", engine.code)
    }
    @Test fun repeatedTapDuringTransactionIsIgnored() {
        engine.begin(source(), profile)
        assertNull(engine.begin(source(post.copy(identity = "new")), profile))
        assertEquals(Stage.WAIT_PICKER, engine.stage)
    }
    @Test fun incomingPostBeforeForwardCancelsInsteadOfSwitchingTarget() {
        engine.begin(source(), profile)
        assertNull(engine.observe(source(post.copy(identity = "new"))))
        assertEquals("TARGET_CHANGED", engine.code)
    }
    @Test fun incomingPostAfterPickerDoesNotChangeLockedIdentity() {
        val command = reachSend()
        assertEquals(post.identity, command.identity)
        engine.observe(source(post.copy(identity = "new")))
        assertEquals(Stage.COMPLETE, engine.stage)
    }
    @Test fun duplicateDurableAttemptBlocksEvenAfterNewEngine() {
        val send = reachSend()
        val restarted = ForwardEngine { now }
        assertNull(restarted.begin(source(), profile.copy(lastAttempt = send.identity)))
        assertEquals("DUPLICATE_BLOCKED", restarted.code)
    }
    @Test fun newPostCanBeSent() {
        assertTrue(engine.begin(source(post.copy(identity = "new")), profile.copy(lastAttempt = "old")) is Command.Forward)
    }
    @Test fun wrongSourceNeverStarts() {
        assertNull(engine.begin(source().copy(channel = "other"), profile))
        assertEquals("WRONG_SOURCE", engine.code)
    }
    @Test fun unknownBottomNeverStarts() {
        assertNull(engine.begin(source().copy(atBottom = false), profile))
        assertEquals("BOTTOM_UNCERTAIN", engine.code)
    }
    @Test fun duplicateContactsNeverSelect() {
        engine.begin(source(), profile)
        engine.observe(picker(contacts = listOf(Recipient(contact, "a", false), Recipient(contact, "b", false))))
        assertEquals("AMBIGUOUS_CONTACT", engine.code)
    }
    @Test fun preselectedContactNeverSends() {
        engine.begin(source(), profile)
        engine.observe(picker(true))
        assertEquals("PRESELECTED_RECIPIENT", engine.code)
    }
    @Test fun loadingPickerWaitsUntilSelectionEvidenceAppears() {
        engine.begin(source(), profile)
        assertNull(engine.observe(picker(count = null)))
        assertEquals(Stage.WAIT_PICKER, engine.stage)
        assertTrue(engine.observe(picker()) is Command.Select)
    }
    @Test fun uncheckedOrUnknownSelectionNeverSends() {
        engine.begin(source(), profile)
        engine.observe(picker())
        assertNull(engine.observe(picker(false, count = 1)))
        assertNull(engine.observe(picker(true, count = null)))
        assertEquals(Stage.WAIT_SELECTION, engine.stage)
    }
    @Test fun multipleSelectedNeverSends() {
        engine.begin(source(), profile); engine.observe(picker())
        assertNull(engine.observe(picker(true, count = 2)))
        assertEquals("AMBIGUOUS_SELECTION", engine.code)
    }
    @Test fun sameNameDifferentIdentityIsNotSelected() {
        engine.begin(source(), profile)
        assertNull(engine.observe(picker(contacts = listOf(Recipient(contact.copy(signature = "other"), "a", false)))))
        assertEquals(Stage.WAIT_PICKER, engine.stage)
    }
    @Test fun timeoutNeverRetriesSend() {
        reachSend(); now += 5000
        assertNull(engine.observe(picker(true)))
        assertEquals("SEND_RESULT_UNKNOWN", engine.code)
        assertNull(engine.observe(picker(true)))
    }
    @Test fun stageTimeoutBeforeSend() {
        engine.begin(source(), profile); now += 5000
        engine.observe(picker())
        assertEquals("STAGE_TIMEOUT", engine.code)
    }
    @Test fun foregroundLossCancels() {
        engine.begin(source(), profile)
        engine.observe(Screen(packageName = "locked"))
        assertEquals("FOREGROUND_LOST", engine.code)
    }
    @Test fun backOnlyOnceFromVerifiedChat() {
        reachSend()
        val chat = Screen(ScreenKind.CHAT, chatContact = "acc2")
        assertEquals(Command.Back, engine.observe(chat))
        assertNull(engine.observe(chat))
        engine.observe(source())
        assertEquals(Stage.COMPLETE, engine.stage)
    }
    @Test fun unrelatedChatDoesNotTriggerBack() {
        reachSend()
        assertNull(engine.observe(Screen(ScreenKind.CHAT, chatContact = "other")))
    }
    @Test fun calibrationRequiresMatchingGeometryAndLayout() {
        val calibrated = profile.copy(calibration = Calibration(30, 700, 500, 1000, "layout"))
        assertTrue(engine.begin(source(post.copy(forwardPath = null)), calibrated) is Command.Forward)
        engine.cancel()
        assertNull(engine.begin(source(post.copy(forwardPath = null)).copy(width = 600), calibrated))
        assertEquals("FORWARD_NOT_FOUND", engine.code)
        assertNull(engine.begin(source(post.copy(forwardPath = null, layout = "moved")), calibrated))
    }
    @Test fun calibrationCannotClickOutsideTarget() {
        assertNull(engine.begin(source(post.copy(forwardPath = null)), profile.copy(calibration = Calibration(30, 900, 500, 1000, "layout"))))
    }
    @Test fun searchDoesNotLoop() {
        engine.begin(source(), profile)
        val empty = picker(contacts = emptyList()).copy(searchPath = "s")
        assertTrue(engine.observe(empty) is Command.Search)
        assertNull(engine.observe(empty))
        assertTrue(engine.observe(empty.copy(searchFieldPath = "e")) is Command.Search)
        assertNull(engine.observe(empty.copy(searchFieldPath = "e")))
    }
}
