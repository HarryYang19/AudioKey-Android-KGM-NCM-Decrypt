package com.harryyang.audiokey

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Offline decoder for the classic Kugou KGM/KGMA container.
 * The mask tables and byte transform are ported from MyKgmWasm (MIT),
 * Copyright (c) 2025 huangbao. See THIRD_PARTY_NOTICES.md.
 */
object KgmDecoder {
    private val KGM_MAGIC = hex("7C D5 32 EB 86 02 7F 4B A8 AF A6 8E 0F FF 99 14")

    // Tables from MyKgmWasm/KgmUtils.h (MIT).
    private val TABLE1 = hex("""
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 01 21 01 61 01 21 01 E1 01 21 01 61 01 21 01
        D2 23 02 02 42 42 02 02 C2 C2 02 02 42 42 02 02
        D3 D3 02 03 63 43 63 03 E3 C3 E3 03 63 43 63 03
        94 B4 94 65 04 04 04 04 84 84 84 84 04 04 04 04
        95 95 95 95 04 05 25 05 E5 85 A5 85 E5 05 25 05
        D6 B6 96 B6 D6 27 06 06 C6 C6 86 86 C6 C6 06 06
        D7 D7 97 97 D7 D7 06 07 E7 C7 E7 87 E7 C7 E7 07
        18 38 18 78 18 38 18 E9 08 08 08 08 08 08 08 08
        19 19 19 19 19 19 19 19 08 09 29 09 69 09 29 09
        DA 3A 1A 3A 5A 3A 1A 3A DA 2B 0A 0A 4A 4A 0A 0A
        DB DB 1B 1B 5B 5B 1B 1B DB DB 0A 0B 6B 4B 6B 0B
        9C BC 9C 7C 1C 3C 1C 7C 9C BC 9C 6D 0C 0C 0C 0C
        9D 9D 9D 9D 1D 1D 1D 1D 9D 9D 9D 9D 0C 0D 2D 0D
        DE BE 9E BE DE 3E 1E 3E DE BE 9E BE DE 2F 0E 0E
        DF DF 9F 9F DF DF 1F 1F DF DF 9F 9F DF DF 0E 0F
        00 20 00 60 00 20 00 E0 00 20 00 60 00 20 00 F1
    """.trimIndent())

    private val TABLE2 = hex("""
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 01 23 01 67 01 23 01 EF 01 23 01 67 01 23 01
        DF 21 02 02 46 46 02 02 CE CE 02 02 46 46 02 02
        DE DE 02 03 65 47 65 03 ED CF ED 03 65 47 65 03
        9D BF 9D 63 04 04 04 04 8C 8C 8C 8C 04 04 04 04
        9C 9C 9C 9C 04 05 27 05 EB 8D AF 8D EB 05 27 05
        DB BD 9F BD DB 25 06 06 CA CA 8E 8E CA CA 06 06
        DA DA 9E 9E DA DA 06 07 E9 CB E9 8F E9 CB E9 07
        19 3B 19 7F 19 3B 19 E7 08 08 08 08 08 08 08 08
        18 18 18 18 18 18 18 18 08 09 2B 09 6F 09 2B 09
        D7 39 1B 39 5F 39 1B 39 D7 29 0A 0A 4E 4E 0A 0A
        D6 D6 1A 1A 5E 5E 1A 1A D6 D6 0A 0B 6D 4F 6D 0B
        95 B7 95 7B 1D 3F 1D 7B 95 B7 95 6B 0C 0C 0C 0C
        94 94 94 94 1C 1C 1C 1C 94 94 94 94 0C 0D 2F 0D
        D3 B5 97 B5 D3 3D 1F 3D D3 B5 97 B5 D3 2D 0E 0E
        D2 D2 96 96 D2 D2 1E 1E D2 D2 96 96 D2 D2 0E 0F
        00 22 00 66 00 22 00 EE 00 22 00 66 00 22 00 FE
    """.trimIndent())

    private val MASK = hex("""
        B8 D5 3D B2 E9 AF 78 8C 83 33 71 51 76 A0 CD 37
        2F 3E 35 8D A9 BE 98 B7 E7 8C 22 CE 5A 61 DF 68
        69 89 FE A5 B6 DE A9 77 FC C8 BD BD E5 6D 3E 5A
        36 EF 69 4E BE E1 E9 66 1C F3 D9 02 B6 F2 12 9B
        44 D0 6F B9 35 89 B6 46 6D 73 82 06 69 C1 ED D7
        85 C2 30 DF A2 62 BE 79 2D 62 62 3D 0D 7E BE 48
        89 23 02 A0 E4 D5 75 51 32 02 53 FD 16 3A 21 3B
        16 0F C3 B2 BB B3 E2 BA 3A 3D 13 EC F6 01 45 84
        A5 70 0F 93 49 0C 64 CD 31 D5 CC 4C 07 01 9E 00
        1A 23 90 BF 88 1E 3B AB A6 3E C4 73 47 10 7E 3B
        5E BC E3 00 84 FF 09 D4 E0 89 0F 5B 58 70 4F FB
        65 D8 5C 53 1B D3 C8 C6 BF EF 98 B0 50 4F 0F EA
        E5 83 58 8C 28 2C 84 67 CD D0 9E 47 DB 27 50 CA
        F4 63 63 E8 97 7F 1B 4B 0C C2 C1 21 4C CC 58 F5
        94 52 A3 F3 D3 E0 68 F4 00 23 F3 5E 0A 7B 93 DD
        AB 12 B2 13 E8 84 D7 A7 9F 0F 32 4C 55 1D 04 36
        52 DC 03 F3 F9 4E 42 E9 3D 61 EF 7C B6 B3 93 50
    """.trimIndent())

    init {
        check(TABLE1.size == 272 && TABLE2.size == 272 && MASK.size == 272) {
            "KGM lookup table length mismatch"
        }
    }

    fun decode(input: File, output: File): String {
        RandomAccessFile(input, "r").use { source ->
            require(source.length() >= 0x2C) { "KGM 文件过短" }
            val magic = ByteArray(16)
            source.readFully(magic)
            val magicInts = IntArray(magic.size) { magic[it].toInt() and 0xFF }
            require(magicInts.contentEquals(KGM_MAGIC)) { "不支持的 KGM 文件头" }

            source.seek(0x10)
            val headerLength = readUInt32LE(source)
            require(headerLength >= 0x2C && headerLength <= source.length()) { "KGM 文件头长度无效" }
            source.seek(0x1C)
            val key16 = ByteArray(16)
            source.readFully(key16)
            val key = ByteArray(17)
            System.arraycopy(key16, 0, key, 0, 16)
            key[16] = 0
            source.seek(headerLength)

            BufferedOutputStream(FileOutputStream(output), 64 * 1024).use { out ->
                val buffer = ByteArray(64 * 1024)
                var offset = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    for (i in 0 until count) {
                        val pos = offset + i
                        var medium = (key[(pos % 17L).toInt()].toInt() xor (buffer[i].toInt() and 0xFF)) and 0xFF
                        medium = (medium xor ((medium and 0x0F) shl 4)) and 0xFF

                        var mask = getMask(pos)
                        mask = (mask xor ((mask and 0x0F) shl 4)) and 0xFF
                        buffer[i] = (medium xor mask).toByte()
                    }
                    out.write(buffer, 0, count)
                    offset += count
                }
            }
        }
        return AudioSniffer.extension(output)
    }

    private fun getMask(position: Long): Int {
        var offset = position ushr 4
        var value = 0
        while (offset >= 0x11L) {
            value = value xor TABLE1[(offset % 272L).toInt()]
            offset = offset ushr 4
            value = value xor TABLE2[(offset % 272L).toInt()]
            offset = offset ushr 4
        }
        return MASK[(position % 272L).toInt()] xor value
    }

    private fun readUInt32LE(file: RandomAccessFile): Long {
        val b0 = file.readUnsignedByte().toLong()
        val b1 = file.readUnsignedByte().toLong()
        val b2 = file.readUnsignedByte().toLong()
        val b3 = file.readUnsignedByte().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun hex(value: String): IntArray = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .map { it.toInt(16) }.toIntArray()
}
