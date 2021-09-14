package me.rocka.fcitx5test

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.Log
import android.view.View

class FcitxService : InputMethodService(), KeyboardView.OnKeyboardActionListener {
    lateinit var keybord: Keyboard
    override fun onInitializeInterface() {
        keybord = Keyboard(this, R.xml.qwerty)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.input, null) as KeyboardView
        view.keyboard = keybord
        view.setOnKeyboardActionListener(this)
        return view
    }

    override fun onPress(primaryCode: Int) {
        Log.d(javaClass.name, "onPress $primaryCode")
    }

    override fun onRelease(primaryCode: Int) {
        Log.d(javaClass.name, "onRelease $primaryCode")

    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        Log.d(javaClass.name, "onKey $primaryCode ${keyCodes?.joinToString()}")

    }

    override fun onText(text: CharSequence?) {
        Log.d(javaClass.name, "onText $text")

    }

    override fun swipeLeft() {
        Log.d(javaClass.name, "swipeLeft")
    }

    override fun swipeRight() {
        Log.d(javaClass.name, "swipeRight")

    }

    override fun swipeDown() {
        Log.d(javaClass.name, "swipeDown")

    }

    override fun swipeUp() {
        Log.d(javaClass.name, "swipeUp")
    }
}