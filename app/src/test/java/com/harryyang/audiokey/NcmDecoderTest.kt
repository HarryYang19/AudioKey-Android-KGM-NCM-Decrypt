package com.harryyang.audiokey

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class NcmDecoderTest {
    @Test
    fun decryptsSyntheticNcmContainerToOriginalAudioBytes() {
        val musicKey = "AudioKey-test-stream-key".toByteArray(Charsets.US_ASCII)
        val audio = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33)
        val ncm = makeNcm(musicKey, audio)
        val input = File.createTempFile("audiokey-test", ".ncm")
        val output = File.createTempFile("audiokey-out", ".tmp")
        try {
            input.writeBytes(ncm)
            assertEquals("mp3", NcmDecoder.decode(input, output))
            assertArrayEquals(audio, output.readBytes())
        } finally {
            input.delete()
            output.delete()
        }
    }

    private fun makeNcm(musicKey: ByteArray, audio: ByteArray): ByteArray {
        val coreKey = hex("687A4852416D736F356B496E62617857")
        val keyPlain = "neteasecloudmusic".toByteArray(Charsets.US_ASCII) + musicKey
        val pad = 16 - (keyPlain.size % 16)
        val padded = keyPlain + ByteArray(pad) { pad.toByte() }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(coreKey, "AES"))
        val encryptedKey = cipher.doFinal(padded).map { (it.toInt() xor 0x64).toByte() }.toByteArray()

        val box = IntArray(256) { it }
        var last = 0
        var keyOffset = 0
        for (i in 0 until 256) {
            val swap = box[i]
            val c = (swap + last + (musicKey[keyOffset].toInt() and 0xFF)) and 0xFF
            keyOffset = (keyOffset + 1) % musicKey.size
            box[i] = box[c]
            box[c] = swap
            last = c
        }
        val encryptedAudio = audio.copyOf()
        for (i in encryptedAudio.indices) {
            val j = (i + 1) and 0xFF
            val first = box[j]
            val keyIndex = (first + box[(first + j) and 0xFF]) and 0xFF
            encryptedAudio[i] = (encryptedAudio[i].toInt() xor box[keyIndex]).toByte()
        }

        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.write("CTENFDAM".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0, 0))
        writeIntLE(out, encryptedKey.size)
        out.write(encryptedKey)
        writeIntLE(out, 0) // metadata
        out.write(ByteArray(9)) // crc + reserved
        writeIntLE(out, 0) // cover length
        out.write(encryptedAudio)
        out.flush()
        return bytes.toByteArray()
    }

    private fun writeIntLE(out: DataOutputStream, value: Int) {
        out.writeByte(value and 0xFF)
        out.writeByte((value ushr 8) and 0xFF)
        out.writeByte((value ushr 16) and 0xFF)
        out.writeByte((value ushr 24) and 0xFF)
    }

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) {
        value.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}
