package com.harryyang.audiokey

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KgmDecoderTest {
    @Test
    fun decodesSyntheticClassicKgmPayload() {
        val input = File.createTempFile("audiokey-test", ".kgm")
        val output = File.createTempFile("audiokey-out", ".tmp")
        try {
            val key = ByteArray(17) { (it * 7 + 3).toByte() }
            val plain = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0, 0, 0, 0, 0, 0, 0x11, 0x22)
            val getMask = KgmDecoder::class.java.getDeclaredMethod("getMask", java.lang.Long.TYPE).apply {
                isAccessible = true
            }
            val encrypted = ByteArray(plain.size) { index ->
                val position = index.toLong()
                val mask = getMask.invoke(KgmDecoder, position) as Int
                val mixedMask = nibbleMix(mask)
                val target = (plain[index].toInt() and 0xFF) xor mixedMask
                (key[(position % 17L).toInt()].toInt() xor nibbleMixInverse(target)).toByte()
            }

            val headerLength = 44
            val header = ByteArray(headerLength)
            val magic = byteArrayOf(
                0x7C, 0xD5.toByte(), 0x32, 0xEB.toByte(), 0x86.toByte(), 0x02,
                0x7F, 0x4B, 0xA8.toByte(), 0xAF.toByte(), 0xA6.toByte(), 0x8E.toByte(),
                0x0F, 0xFF.toByte(), 0x99.toByte(), 0x14
            )
            magic.copyInto(header)
            ByteBuffer.wrap(header, 0x10, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(headerLength)
            key.copyOfRange(0, 16).copyInto(header, 0x1C)
            input.writeBytes(header + encrypted)

            assertEquals("mp3", KgmDecoder.decode(input, output))
            assertArrayEquals(plain, output.readBytes())
        } finally {
            input.delete()
            output.delete()
        }
    }

    @Test
    fun rejectsTruncatedInput() {
        val input = File.createTempFile("audiokey-short", ".kgm")
        val output = File.createTempFile("audiokey-out", ".tmp")
        try {
            input.writeBytes(byteArrayOf(0x7C, 0xD5.toByte(), 0x32))
            val message = try {
                KgmDecoder.decode(input, output)
                ""
            } catch (error: IllegalArgumentException) {
                error.message.orEmpty()
            }
            assertTrue(message.contains("过短"))
        } finally {
            input.delete()
            output.delete()
        }
    }

    private fun nibbleMix(value: Int): Int = (value xor ((value and 0x0F) shl 4)) and 0xFF

    private fun nibbleMixInverse(value: Int): Int {
        val low = value and 0x0F
        val high = (value ushr 4) and 0x0F
        return low or ((high xor low) shl 4)
    }
}
