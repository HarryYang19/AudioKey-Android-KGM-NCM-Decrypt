package com.harryyang.audiokey

import java.io.File
import java.io.FileInputStream

object AudioSniffer {
    fun extension(file: File): String {
        val header = ByteArray(16)
        val read = FileInputStream(file).use { input ->
            var total = 0
            while (total < header.size) {
                val count = input.read(header, total, header.size - total)
                if (count < 0) break
                total += count
            }
            total
        }
        if (read >= 4 && header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x66, 0x4C, 0x61, 0x43))) return "flac"
        if (read >= 3 && header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) return "mp3"
        if (read >= 2 && (header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0 && (header[1].toInt() and 0xF6) != 0xF0) return "mp3"
        if (read >= 2 && header[0] == 0xFF.toByte() && (header[1].toInt() and 0xF6) == 0xF0) return "aac"
        if (read >= 4 && header.copyOfRange(0, 4).contentEquals("OggS".toByteArray(Charsets.US_ASCII))) return "ogg"
        if (read >= 4 && header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII))) return "wav"
        if (read >= 8 && header.copyOfRange(4, 8).contentEquals("ftyp".toByteArray(Charsets.US_ASCII))) return "m4a"
        if (read >= 4 && header.copyOfRange(0, 4).contentEquals("FRM8".toByteArray(Charsets.US_ASCII))) return "dff"
        if (read >= 16 && header.copyOfRange(0, 16).contentEquals(byteArrayOf(
                0x30, 0x26, 0xB2.toByte(), 0x75, 0x8E.toByte(), 0x66, 0xCF.toByte(), 0x11,
                0xA6.toByte(), 0xD9.toByte(), 0x00, 0xAA.toByte(), 0x00, 0x62, 0xCE.toByte(), 0x6C
            ))) return "wma"
        throw IllegalArgumentException("解密完成，但无法识别输出音频格式")
    }
}
