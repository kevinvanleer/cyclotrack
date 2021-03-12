package com.kvl.cyclotrack

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.DatePicker
import androidx.fragment.app.DialogFragment
import java.util.*

class DatePickerFragment(
    private val date: Date?,
    private val callback: (Date) -> Unit,
) : DialogFragment(),
    DatePickerDialog.OnDateSetListener {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use the current date as the default date in the picker
        val default = GregorianCalendar.getInstance()
        default.set(1990, 6, 22)
        val c = GregorianCalendar()
        c.time = date ?: default.time
        val year = c.get(GregorianCalendar.YEAR)
        val month = c.get(GregorianCalendar.MONTH)
        val day = c.get(GregorianCalendar.DAY_OF_MONTH)

        // Create a new instance of DatePickerDialog and return it
        return DatePickerDialog(requireContext(),
            this,
            year,
            month,
            day)
    }

    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        val chosen = GregorianCalendar.getInstance()
        chosen.set(year, month, dayOfMonth)
        callback(chosen.time)
    }

}