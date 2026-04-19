package com.yusufulgen.cuzdan.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class DateMaskWatcher(private val editText: EditText) : TextWatcher {
    private var isUpdating = false
    private val mask = "##.##.####"

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        if (isUpdating) {
            isUpdating = false
            return
        }

        var str = s.toString().replace("[^\\d]".toRegex(), "")
        if (str.length > 8) str = str.substring(0, 8)

        val sb = StringBuilder()
        var i = 0
        for (m in mask.toCharArray()) {
            if (m != '#') {
                if (str.length > i) {
                    sb.append(m)
                }
            } else {
                if (str.length > i) {
                    sb.append(str[i])
                    i++
                } else {
                    break
                }
            }
        }

        isUpdating = true
        editText.setText(sb.toString())
        editText.setSelection(sb.length)
    }

    override fun afterTextChanged(s: Editable?) {}
}
