package com.saastracker.util

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.Reader

fun parseCsv(reader: Reader): CSVParser = CSVParser(
    reader,
    CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setTrim(true)
        .setIgnoreHeaderCase(true)
        .build()
)

fun String.csvEscape(): String =
    if (contains(',') || contains('"') || contains('\n'))
        "\"${replace("\"", "\"\"")}\"" else this

