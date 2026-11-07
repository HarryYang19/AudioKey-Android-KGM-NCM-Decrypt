package com.harryyang.audiokey

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Pure local NCM decoder. No network, account token, or remote key service is used. */
object NcmDecoder {
    private val MAGIC = "CTENFDAM".toByteArray(Charsets.US_ASCII)
    private val CORE_KEY = hexBytes("687A4852416D736F356B496E62617857")

    fun decode(input: File, output: File): String {
        DataInputStream(BufferedInputStream(FileInputStream(input), 64 * 1024)).use { source ->
            val magic = ByteArray(8)
            source.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "不是有效的 NCM 文件（文件头不匹配）" }
            skipFully(source, 2)

            val keyLength = readIntLE(source)
            require(keyLength in 16..(16 * 1024 * 1024)) { "NCM 密钥区长度异常" }
            val encryptedKey = ByteArray(keyLength)
            source.readFully(encryptedKey)
            for (i in encryptedKey.indices) encryptedKey[i] = (encryptedKey[i].toInt() xor 0x64).toByte()

            val decryptedKey = aesEcbDecrypt(encryptedKey, CORE_KEY)
            val unpaddedKey = pkcs7Unpad(decryptedKey)
            val prefix = "neteasecloudmusic".toByteArray(Charsets.US_ASCII)
            require(unpaddedKey.size > prefix.size && unpaddedKey.copyOfRange(0, prefix.size).contentEquals(prefix)) {
                "NCM 密钥解密失败或文件版本不受支持"
            }
            val musicKey = unpaddedKey.copyOfRange(prefix.size, unpaddedKey.size)

            val metadataLength = readIntLE(source)
            require(metadataLength in 0..(32 * 1024 * 1024)) { "NCM 元数据长度异常" }
            skipFully(source, metadataLength.toLong())

            // CRC32 (4 bytes) + reserved area (5 bytes), followed by cover size and image.
            skipFully(source, 9)
            val coverLength = readIntLE(source)
            require(coverLength in 0..(64 * 1024 * 1024)) { "NCM 封面长度异常" }
            skipFully(source, coverLength.toLong())

            val box = buildKeyBox(musicKey)
            BufferedOutputStream(FileOutputStream(output), 64 * 1024).use { target ->
                val buffer = ByteArray(64 * 1024)
                var audioOffset = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    for (i in 0 until count) {
                        // NCM's PRGA is a custom RC4-like lookup, not standard RC4 PRGA.
                        val j = ((audioOffset + i + 1L) and 0xFF).toInt()
                        val first = box[j]
                        val secondIndex = (first + j) and 0xFF
                        val keyIndex = (first + box[secondIndex]) and 0xFF
                        buffer[i] = (buffer[i].toInt() xor box[keyIndex]).toByte()
                    }
                    target.write(buffer, 0, count)
                    audioOffset += count
                }
            }
        }
        return AudioSniffer.extension(output)
    }

    private fun buildKeyBox(key: ByteArray): IntArray {
        require(key.isNotEmpty()) { "NCM 音频密钥为空" }
        val box = IntArray(256) { it }
        var lastByte = 0
        var keyOffset = 0
        for (i in 0 until 256) {
            val swap = box[i]
            val c = (swap + lastByte + (key[keyOffset].toInt() and 0xFF)) and 0xFF
            keyOffset++
            if (keyOffset >= key.size) keyOffset = 0
            box[i] = box[c]
            box[c] = swap
            lastByte = c
        }
        return box
    }

    private fun aesEcbDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        require(data.isNotEmpty() && data.size % 16 == 0) { "NCM AES 数据长度无效" }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun pkcs7Unpad(data: ByteArray): ByteArray {
        require(data.isNotEmpty()) { "NCM 密钥数据为空" }
        val padding = data.last().toInt() and 0xFF
        require(padding in 1..16 && padding <= data.size) { "NCM AES 填充无效" }
        for (i in data.size - padding until data.size) {
            require((data[i].toInt() and 0xFF) == padding) { "NCM AES 填充校验失败" }
        }
        return data.copyOfRange(0, data.size - padding)
    }

    private fun readIntLE(input: DataInputStream): Int {
        val b0 = input.readUnsignedByte()
        val b1 = input.readUnsignedByte()
        val b2 = input.readUnsignedByte()
        val b3 = input.readUnsignedByte()
        val value = b0.toLong() or (b1.toLong() shl 8) or (b2.toLong() shl 16) or (b3.toLong() shl 24)
        require(value <= Int.MAX_VALUE) { "NCM 文件字段超出支持范围" }
        return value.toInt()
    }

    private fun skipFully(input: DataInputStream, count: Long) {
        require(count >= 0) { "无效的 NCM 文件偏移" }
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                if (input.read() == -1) throw IllegalArgumentException("NCM 文件被截断")
                remaining--
            }
        }
    }

    private fun hexBytes(value: String): ByteArray = ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
