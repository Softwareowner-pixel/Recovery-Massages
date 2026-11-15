package com.example.messagevault

/**
 * Recognises the text WhatsApp (and similar apps) show instead of a message that the sender
 * removed with "Delete for everyone". Add more languages to the lists below if you need them.
 */
object DeletedMessages {

    enum class Kind { NORMAL, DELETED_BY_SENDER, DELETED_BY_ME }

    // All lowercase. Compared against the whole text (after removing the little "no entry" emoji).
    private val DELETED_BY_SENDER = listOf(
        "this message was deleted",
        "se eliminó este mensaje",
        "este mensaje fue eliminado",
        "esta mensagem foi apagada",
        "esta mensagem foi eliminada",
        "ce message a été supprimé",
        "diese nachricht wurde gelöscht",
        "questo messaggio è stato eliminato",
        "это сообщение было удалено",
        "bu mesaj silindi",
        "pesan ini telah dihapus",
        "تم حذف هذه الرسالة",
        "यह संदेश हटा दिया गया था",
        "इस संदेश को हटा दिया गया था",
        "یہ پیغام حذف کر دیا گیا"
    )

    private val DELETED_BY_ME = listOf(
        "you deleted this message",
        "has eliminado este mensaje",
        "você apagou esta mensagem",
        "vous avez supprimé ce message",
        "sie haben diese nachricht gelöscht",
        "hai eliminato questo messaggio"
    )

    fun classify(text: String): Kind {
        val t = text
            .filterNot { it == '‎' || it == '‏' || it == '‪' || it == '‬' }
            .replace("🚫", "") // the "no entry" emoji
            .trim()
            .trimEnd('.', '!', '。')
            .trim()
            .lowercase()
        if (t.isEmpty()) return Kind.NORMAL
        // "Ali: this message was deleted" is how some notifications prefix the group member.
        if (matches(t, DELETED_BY_SENDER)) return Kind.DELETED_BY_SENDER
        if (matches(t, DELETED_BY_ME)) return Kind.DELETED_BY_ME
        return Kind.NORMAL
    }

    private fun matches(t: String, markers: List<String>): Boolean =
        markers.any { t == it || t.endsWith(": $it") }
}
