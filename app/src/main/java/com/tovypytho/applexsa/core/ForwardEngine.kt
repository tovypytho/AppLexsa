package com.tovypytho.applexsa.core

/** No Android dependencies: all screen transitions, timeouts and single-send invariants live here. */
class ForwardEngine(private val clock: () -> Long) {
    var stage = Stage.READY; private set
    var code = "READY"; private set
    var startedAt = 0L; private set
    var sendAt: Long? = null; private set
    var attemptId = 0L; private set
    val timeline = mutableListOf<String>()
    private var deadline = 0L
    private var profile = Profile()
    private var target: Post? = null
    private var searchOpened = false
    private var searchEntered = false
    private var sent = false
    val busy get() = stage in setOf(Stage.WAIT_PICKER, Stage.WAIT_SELECTION, Stage.WAIT_RESULT, Stage.RETURNING)
    val withinDeadline get() = busy && clock() < deadline

    fun begin(screen: Screen, config: Profile): Command? {
        if (busy) return null
        attemptId++
        timeline.clear()
        profile = config
        target = null; sent = false; sendAt = null; searchOpened = false; searchEntered = false
        startedAt = clock()
        if (!config.enabled || config.channel.isBlank() || config.contact == null) return fail("NOT_CONFIGURED")
        if (screen.packageName != "com.whatsapp" || screen.kind != ScreenKind.SOURCE || screen.channel != config.channel) return fail("WRONG_SOURCE")
        if (!screen.atBottom) return fail("BOTTOM_UNCERTAIN")
        val post = screen.post ?: return fail("POST_UNCERTAIN")
        if (post.identity == config.lastAttempt) return fail("DUPLICATE_BLOCKED")
        val calibration = config.calibration?.takeIf {
            it.width == screen.width && it.height == screen.height && it.layout == post.layout && post.box.contains(Box(it.x, it.y, it.x, it.y))
        }
        if (post.forwardPath == null && calibration == null) return fail("FORWARD_NOT_FOUND")
        target = post
        transition(Stage.WAIT_PICKER, "TARGET_LOCKED")
        return Command.Forward(post, calibration)
    }

    fun observe(screen: Screen): Command? {
        if (!busy) return null
        if (clock() >= deadline) return fail(if (sent) "SEND_RESULT_UNKNOWN" else "STAGE_TIMEOUT")
        if (screen.packageName != "com.whatsapp") return fail("FOREGROUND_LOST")
        when (stage) {
            Stage.WAIT_PICKER -> {
                if (screen.kind == ScreenKind.SOURCE && screen.post?.identity != target?.identity) return fail("TARGET_CHANGED")
                if (screen.kind != ScreenKind.PICKER) return null
                if (screen.selectedCount == null) return null // Wait for the picker to finish loading.
                if (screen.selectedCount != 0) return fail("PRESELECTED_RECIPIENT")
                val matches = screen.recipients.filter { it.contact == profile.contact }
                if (matches.size > 1) return fail("AMBIGUOUS_CONTACT")
                if (matches.size == 1) {
                    transition(Stage.WAIT_SELECTION, "RECIPIENT_SELECTED")
                    return Command.Select(matches.single())
                }
                if (!searchOpened && screen.searchPath != null) {
                    searchOpened = true
                    return Command.Search(profile.contact!!.name, screen.searchPath, false)
                }
                if (!searchEntered && screen.searchFieldPath != null) {
                    searchEntered = true
                    return Command.Search(profile.contact!!.name, screen.searchFieldPath, true)
                }
            }
            Stage.WAIT_SELECTION -> {
                if (screen.kind != ScreenKind.PICKER) return fail("PICKER_LOST")
                val matches = screen.recipients.filter { it.contact == profile.contact }
                if (matches.size > 1 || screen.selectedCount?.let { it > 1 } == true || screen.recipients.count { it.checked } > 1) return fail("AMBIGUOUS_SELECTION")
                if (screen.selectedCount == 1 && matches.singleOrNull()?.checked == true) {
                    val path = screen.sendPath ?: return null
                    sent = true
                    sendAt = clock()
                    transition(Stage.WAIT_RESULT, "SEND_REQUESTED")
                    return Command.Send(path, target!!.identity)
                }
            }
            Stage.WAIT_RESULT -> {
                if (screen.kind == ScreenKind.SOURCE && screen.channel == profile.channel) {
                    transition(Stage.COMPLETE, "SEND_COMMAND_EXECUTED")
                } else if (screen.kind == ScreenKind.CHAT && normalized(screen.chatContact) == normalized(profile.contact!!.name)) {
                    transition(Stage.RETURNING, "RETURNING_TO_SOURCE")
                    return Command.Back
                }
            }
            Stage.RETURNING -> if (screen.kind == ScreenKind.SOURCE && screen.channel == profile.channel) {
                transition(Stage.COMPLETE, "SEND_COMMAND_EXECUTED")
            }
            else -> Unit
        }
        return null
    }

    fun cancel(reason: String = "CANCELLED") { if (busy) fail(reason) }
    fun actionFailed() { fail(if (sent) "SEND_RESULT_UNKNOWN" else "ACTION_FAILED") }
    private fun transition(next: Stage, reason: String) {
        stage = next; code = reason; deadline = clock() + 5_000
        timeline.add("$next $reason at=${clock() - startedAt}ms")
    }
    private fun fail(reason: String): Command? {
        stage = Stage.ERROR; code = reason
        timeline.add("ERROR $reason at=${clock() - startedAt}ms")
        return null
    }
}
