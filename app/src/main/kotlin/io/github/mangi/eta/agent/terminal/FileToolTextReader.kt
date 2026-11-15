package io.github.mangi.eta.agent.terminal

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

internal data class FileToolTextChunk(
    val text: String,
    val bytesConsumed: Int,
    val eof: Boolean,
)

/** 字节游标只能越过已完整解码的字符，不把半个 UTF-8 字符记为已读。 */
internal class FileToolTextReader(private val backend: FileToolBackend) {
    fun read(stat: FileToolStat, offset: Long, maxBytes: Int, maxChars: Int = 16_000): FileToolTextChunk {
        val bytes = backend.readBytes(stat.path, offset, maxBytes.coerceIn(1, 16_000))
        if (bytes.any { it == 0.toByte() }) throw FileToolException("BINARY_FILE", "File contains binary data. Use file export or terminal tools.")
        val eof = offset + bytes.size >= stat.sizeBytes
        val input = ByteBuffer.wrap(bytes)
        val output = CharBuffer.allocate(maxChars.coerceIn(2, 16_000))
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val result = decoder.decode(input, output, eof)
        if (result.isError) throw FileToolException("NOT_UTF8_TEXT", "Content is not valid UTF-8 or the byte cursor is inside a character")
        if (bytes.isNotEmpty() && input.position() == 0) {
            throw FileToolException("READ_LIMIT_TOO_SMALL", "Byte limit cannot fit a complete character. Use at least 4 bytes.")
        }
        if (bytes.isEmpty() && offset < stat.sizeBytes) throw FileToolException("FILE_CHANGED", "File changed while being read")
        output.flip()
        return FileToolTextChunk(output.toString(), input.position(), eof && input.position() == bytes.size)
    }
}
