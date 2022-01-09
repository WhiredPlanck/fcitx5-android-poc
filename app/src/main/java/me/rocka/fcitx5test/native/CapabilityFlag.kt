package me.rocka.fcitx5test.native

/**
 * translated from
 * [fcitx-utils/capabilityflags.h](https://github.com/fcitx/fcitx5/blob/5.0.13/src/lib/fcitx-utils/capabilityflags.h)
 */
@Suppress("unused")
enum class CapabilityFlag(val flag: ULong) {
    NoFlag(0UL),

    /**
     * Deprecated, because this flag is not compatible with fcitx 4.
     */
    ClientSideUI(1UL shl 0),
    Preedit(1UL shl 1),
    ClientSideControlState(1UL shl 2),
    Password(1UL shl 3),
    FormattedPreedit(1UL shl 4),
    ClientUnfocusCommit(1UL shl 5),
    SurroundingText(1UL shl 6),
    Email(1UL shl 7),
    Digit(1UL shl 8),
    Uppercase(1UL shl 9),
    Lowercase(1UL shl 10),
    NoAutoUpperCase(1UL shl 11),
    Url(1UL shl 12),
    Dialable(1UL shl 13),
    Number(1UL shl 14),
    NoOnScreenKeyboard(1UL shl 15),
    SpellCheck(1UL shl 16),
    NoSpellCheck(1UL shl 17),
    WordCompletion(1UL shl 18),
    UppercaseWords(1UL shl 19),
    UppwercaseSentences(1UL shl 20),
    Alpha(1UL shl 21),
    Name(1UL shl 22),
    GetIMInfoOnFocus(1UL shl 23),
    RelativeRect(1UL shl 24),
    // 25 ~ 31 are reserved for fcitx 4 compatibility.

    // New addition in fcitx 5.
    Terminal(1UL shl 32),
    Date(1UL shl 33),
    Time(1UL shl 34),
    Multiline(1UL shl 35),
    Sensitive(1UL shl 36),
    KeyEventOrderFix(1UL shl 37),

    /**
     * Whether client will set KeyState::Repeat on the key event.
     */
    ReportKeyRepeat(1UL shl 38),

    /**
     * Whether client display input panel by itself.
     */
    ClientSideInputPanel(1UL shl 39),

    PasswordOrSensitive(Password.flag or Sensitive.flag);

}

class CapabilityFlags(val flags: ULong) {
    companion object {
        fun mergeFlags(list: Array<out CapabilityFlag>): ULong {
            var acc = CapabilityFlag.NoFlag.flag
            list.forEach { acc = acc or it.flag }
            return acc
        }

        val DefaultFlags: CapabilityFlags
            get() = CapabilityFlags(
                CapabilityFlag.Preedit,
                CapabilityFlag.ClientUnfocusCommit,
                CapabilityFlag.ClientSideInputPanel,
            )
    }

    constructor(vararg list: CapabilityFlag) : this(mergeFlags(list))

    fun toLong() = flags.toLong()
}