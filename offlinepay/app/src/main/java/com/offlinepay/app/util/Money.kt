package com.offlinepay.app.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    fun format(minor: Long, code: String): String {
        val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
        return runCatching {
            nf.currency = Currency.getInstance(code)
            nf.format(minor / 100.0)
        }.getOrElse { "$code ${minor / 100.0}" }
    }

    fun parseToMinor(input: String): Long? =
        input.toDoubleOrNull()?.let { (it * 100).toLong() }
}
