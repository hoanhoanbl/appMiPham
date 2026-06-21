package com.example.appbanmypham.util

fun normalizePhone10(input: String): String =
    input.filter { it.isDigit() }.take(10)

fun isValidPhone10(input: String): Boolean =
    input.filter { it.isDigit() }.length == 10
