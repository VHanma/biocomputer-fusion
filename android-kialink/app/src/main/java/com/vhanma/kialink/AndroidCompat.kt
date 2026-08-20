package com.vhanma.kialink

import android.widget.EditText

/** Keeps the dependency-free UI DSL concise across current Kotlin/Android toolchains. */
var EditText.singleLine: Boolean
    get() = isSingleLine
    set(value) {
        setSingleLine(value)
    }
