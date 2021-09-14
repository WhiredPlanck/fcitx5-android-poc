/*
 * Copyright (C) 2008-2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.rocka.fcitx5test

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.text.method.MetaKeyKeyListener
import android.view.KeyCharacterMap
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.IBinder
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.*
import android.widget.Toast
import androidx.lifecycle.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.rocka.fcitx5test.native.Fcitx
import me.rocka.fcitx5test.native.FcitxEvent
import java.util.ArrayList

/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
class SoftKeyboard : InputMethodService(), OnKeyboardActionListener, LifecycleOwner {
    private var mInputView: KeyboardView? = null
    private lateinit var mCandidateView: CandidateView
    private var mLastDisplayWidth = 0
    private var mCapsLock = false
    private var mLastShiftTime: Long = 0
    private var mMetaState: Long = 0
    private lateinit var mQwertyKeyboard: Keyboard
    private lateinit var fcitx: Fcitx

    private val dispatcher = ServiceLifecycleDispatcher(this)

    override fun onCreate() {
        fcitx = Fcitx(this)
        lifecycle.addObserver(fcitx)
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onServicePreSuperOnStart()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        dispatcher.onServicePreSuperOnDestroy()
        super.onDestroy()
    }


    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    override fun onInitializeInterface() {
        // Configuration changes can happen after the keyboard gets recreated,
        // so we need to be able to re-build the keyboards if the available
        // space has changed.
        val displayWidth = maxWidth
        if (displayWidth == mLastDisplayWidth) return
        mLastDisplayWidth = displayWidth
        mQwertyKeyboard = Keyboard(this, R.xml.qwerty)
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    override fun onCreateInputView(): View {
        mInputView = layoutInflater.inflate(
            R.layout.input, null
        ) as KeyboardView
        mInputView?.setOnKeyboardActionListener(this)
        mInputView?.keyboard = mQwertyKeyboard
        fcitx.eventFlow.onEach {
            when (it) {
                is FcitxEvent.CandidateListEvent -> {
                    setSuggestions(it.data.take(30))
                }
                is FcitxEvent.CommitStringEvent -> {
                    currentInputConnection.commitText(it.data, 1)
                }
                is FcitxEvent.InputPanelAuxEvent -> {
                }
                is FcitxEvent.PreeditEvent -> {
                    currentInputConnection.setComposingText(it.data.preedit, 1)
                }
                is FcitxEvent.ReadyEvent -> {
                    Toast.makeText(this, "fcitx5 is ready", Toast.LENGTH_SHORT).show()
                }
                is FcitxEvent.UnknownEvent -> {
                }
            }
        }.launchIn(lifecycle.coroutineScope)
        return mInputView!!
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like [.onCreateInputView].
     */
    override fun onCreateCandidatesView(): View {
        mCandidateView = CandidateView(this)
        mCandidateView.setService(this)
        return mCandidateView
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
//        updateCandidates()
        if (!restarting) {
            // Clear shift states.
            mMetaState = 0
        }
        updateShiftKeyState(attribute)

        // Update the label on the enter key, depending on what the application
        // says it will do.
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    override fun onFinishInput() {
        super.onFinishInput()

        fcitx.reset()

        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false)
        mInputView?.closing()
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        // Apply the selected keyboard to the input view.
        mInputView?.closing()
    }


    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private fun translateKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        mMetaState = MetaKeyKeyListener.handleKeyDown(
            mMetaState,
            keyCode, event
        )
        var c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState))
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState)
        val ic = currentInputConnection
        if (c == 0 || ic == null) {
            return false
        }
        var dead = false
        if (c and KeyCharacterMap.COMBINING_ACCENT != 0) {
            dead = true
            c = c and KeyCharacterMap.COMBINING_ACCENT_MASK
        }
        onKey(c, null)
        return true
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK ->                 // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.repeatCount == 0) {
                    if (mInputView!!.handleBack()) {
                        return true
                    }
                }
            KeyEvent.KEYCODE_DEL -> {                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                onKey(Keyboard.KEYCODE_DELETE, null)
                return true
            }
            KeyEvent.KEYCODE_ENTER ->                 // Let the underlying text editor always handle these.
                return false
            else ->                 // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
                if (PROCESS_HARD_KEYS) {
                    if (keyCode == KeyEvent.KEYCODE_SPACE
                        && event.metaState and KeyEvent.META_ALT_ON != 0
                    ) {
                        // A silly example: in our input method, Alt+Space
                        // is a shortcut for 'android' in lower case.
                        val ic = currentInputConnection
                        if (ic != null) {
                            // First, tell the editor that it is no longer in the
                            // shift state, since we are consuming this.
                            ic.clearMetaKeyStates(KeyEvent.META_ALT_ON)
                            keyDownUp(KeyEvent.KEYCODE_A)
                            keyDownUp(KeyEvent.KEYCODE_N)
                            keyDownUp(KeyEvent.KEYCODE_D)
                            keyDownUp(KeyEvent.KEYCODE_R)
                            keyDownUp(KeyEvent.KEYCODE_O)
                            keyDownUp(KeyEvent.KEYCODE_I)
                            keyDownUp(KeyEvent.KEYCODE_D)
                            // And we consume this event.
                            return true
                        }
                    }
                    if (translateKeyDown(keyCode, event)) {
                        return true
                    }
                }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        if (PROCESS_HARD_KEYS) {
            mMetaState = MetaKeyKeyListener.handleKeyUp(
                mMetaState,
                keyCode, event
            )
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private fun updateShiftKeyState(attr: EditorInfo?) {
        if (mInputView == null) {
            return;
        }
        if (attr != null && mQwertyKeyboard === mInputView?.keyboard) {
            var caps = 0
            val ei = currentInputEditorInfo
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = currentInputConnection.getCursorCapsMode(attr.inputType)
            }
            mInputView?.isShifted = mCapsLock || caps != 0
        }
    }

    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private fun isAlphabet(code: Int): Boolean {
        return Character.isLetter(code)
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private fun keyDownUp(keyEventCode: Int) {
        currentInputConnection.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode)
        )
        currentInputConnection.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, keyEventCode)
        )
    }

    /**
     * Helper to send a character to the editor as raw key events.
     */
    private fun sendKey(keyCode: Int) {
        when (keyCode) {
            '\n'.code -> keyDownUp(KeyEvent.KEYCODE_ENTER)
            else -> if (keyCode >= '0'.code && keyCode <= '9'.code) {
                keyDownUp(keyCode - '0'.code + KeyEvent.KEYCODE_0)
            } else {
                currentInputConnection.commitText(keyCode.toChar().toString(), 1)
            }
        }
    }

    // Implementation of KeyboardViewListener
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                handleBackspace()
            }
            Keyboard.KEYCODE_SHIFT -> {
                handleShift()
            }
            Keyboard.KEYCODE_CANCEL -> {
                handleClose()
            }
            else -> {
                mQwertyKeyboard.keys.find { it.codes[0] == primaryCode }?.label?.let {
                    if (it.length == 1)
                        fcitx.sendKey(it[0])
                    else
                        fcitx.sendKey(it.toString())
                }
            }
        }
    }

    override fun onText(text: CharSequence) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        ic.commitText(text, 0)
        ic.endBatchEdit()
        updateShiftKeyState(currentInputEditorInfo)
    }


    fun setSuggestions(
        suggestions: List<String>?
    ) {
        if (suggestions != null && suggestions.isNotEmpty()) {
            setCandidatesViewShown(true)
        } else if (isExtractViewShown) {
            setCandidatesViewShown(true)
        }
        mCandidateView.setSuggestions(suggestions)
    }

    private fun handleBackspace() {
//        val length = mComposing.length
//        if (length > 1) {
//            mComposing.delete(length - 1, length)
//            currentInputConnection.setComposingText(mComposing, 1)
//            updateCandidates()
//        } else if (length > 0) {
//            mComposing.setLength(0)
//            currentInputConnection.commitText("", 0)
//            updateCandidates()
//        } else {
//            keyDownUp(KeyEvent.KEYCODE_DEL)
//        }
        if (!fcitx.isEmpty())
            fcitx.sendKey("BackSpace")
        else
            keyDownUp(KeyEvent.KEYCODE_DEL)

        updateShiftKeyState(currentInputEditorInfo)
    }

    private fun handleShift() {
        if (mInputView == null) {
            return;
        }
        val currentKeyboard: Keyboard = mInputView!!.getKeyboard()
        // TODO
        when {
            mQwertyKeyboard === currentKeyboard -> {
                // Alphabet keyboard
                checkToggleCapsLock()
                mInputView!!.setShifted(mCapsLock || !mInputView!!.isShifted())
            }
        }
    }

    private fun handleClose() {
//        commitTyped(currentInputConnection)
        requestHideSelf(0)
        mInputView?.closing()
    }

    private val token: IBinder?
        private get() {
            val dialog = window ?: return null
            val window = dialog.window ?: return null
            return window.attributes.token
        }


    private fun checkToggleCapsLock() {
        val now = System.currentTimeMillis()
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock
            mLastShiftTime = 0
        } else {
            mLastShiftTime = now
        }
    }

    fun pickDefaultCandidate() {
        pickSuggestionManually(0)
    }

    fun pickSuggestionManually(index: Int) {
        fcitx.select(index)
    }

    override fun swipeRight() {
    }

    override fun swipeLeft() {
        handleBackspace()
    }

    override fun swipeDown() {
        handleClose()
    }

    override fun swipeUp() {}
    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}

    companion object {
        const val DEBUG = false

        /**
         * This boolean indicates the optional example code for performing
         * processing of hard keys in addition to regular text generation
         * from on-screen interaction.  It would be used for input methods that
         * perform language translations (such as converting text entered on
         * a QWERTY keyboard to Chinese), but may not be used for input methods
         * that are primarily intended to be used for on-screen text entry.
         */
        const val PROCESS_HARD_KEYS = true
    }

    override fun getLifecycle(): Lifecycle {
        return dispatcher.lifecycle
    }
}