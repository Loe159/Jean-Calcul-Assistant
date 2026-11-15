package io.github.mangi.eta.agent.terminal

import java.io.File
import java.security.MessageDigest

/** 文件操作在所选命名空间内执行，Linux 的链接不能交给 Android 宿主解析。 */
internal class ShellFileToolBackend(
    override val environment: String,
    override val identity: String,
    private val cwd: String,
    private val home: String,
    private val ensureActive: () -> Unit = {},
    private val execute: (command: String, stdin: ByteArray?, maxOutputBytes: Int) -> OneShotShellResult,
) : FileToolBackend {
    override fun checkActive() = ensureActive()

    override fun resolve(path: String): String {
        checkActive()
        if ('\u0000' in path || path.length > 4096) fail("INVALID_PATH", "File path is invalid or too long")
        val raw = path.ifEmpty { cwd }
        val absolute = when {
            raw == "~" -> home
            raw.startsWith("~/") -> "$home/${raw.removePrefix("~/")}"
            raw.startsWith('/') -> raw
            else -> "$cwd/$raw"
        }
        // 先让目标命名空间解析链接；提前折叠 link/.. 会改变内核实际访问的目录。
        val result = run("readlink -f -- ${shellQuote(absolute)}", maxOutputBytes = 8192, allowFailure = true)
        return if (result.exitCode == 0) {
            decode(result.output).removeSuffix("\n").takeIf { it.startsWith('/') }
                ?: fail("FILE_METADATA_INVALID", "System returned an invalid canonical path")
        } else absolute
    }

    override fun stat(path: String): FileToolStat {
        val result = run(statScript(shellQuote(path)), maxOutputBytes = MAX_STAT_BYTES)
        return parseStat(path, decode(result.output))
    }

    private fun statScript(quotedPath: String): String {
        val format = shellQuote("%s\n%d:%i:%s:%Y:%Z:%y:%z")
        return """
            if [ -L $quotedPath ]; then printf 'symlink\n'
            elif [ -f $quotedPath ]; then printf 'file\n'
            elif [ -d $quotedPath ]; then printf 'directory\n'
            elif [ -e $quotedPath ]; then printf 'other\n'
            else exit 41; fi
            LC_ALL=C stat -c $format -- $quotedPath
        """.trimIndent()
    }

    private fun parseStat(path: String, output: String): FileToolStat {
        val fields = output.split('\n', limit = 3)
        val kind = FileToolKind.entries.firstOrNull { it.wireName == fields.firstOrNull() }
            ?: fail("FILE_METADATA_INVALID", "Could not identify the returned file type")
        val size = fields.getOrNull(1)?.toLongOrNull()
            ?: fail("FILE_METADATA_INVALID", "Could not identify the returned file size")
        val metadata = fields.getOrNull(2)?.trimEnd('\n').orEmpty()
        val versionFields = metadata.split(':', limit = 6)
        if (size < 0 || versionFields.size != 6 ||
            versionFields.take(2).any { !it.matches(Regex("[0-9]+")) } ||
            versionFields[2].toLongOrNull() != size ||
            versionFields[3].toLongOrNull() == null || versionFields[4].toLongOrNull() == null ||
            versionFields[5].isBlank() || '?' in metadata || '%' in metadata || '\n' in metadata
        ) fail("FILE_METADATA_INVALID", "System returned invalid file version information")
        return FileToolStat(path, File(path).name.ifEmpty { "/" }, kind, size, digest(metadata.toByteArray()))
    }

    override fun readBytes(path: String, offset: Long, limit: Int): ByteArray {
        if (offset < 0 || limit !in 1..MAX_READ_BYTES) fail("INVALID_ARGUMENT", "Read range exceeds the limit")
        val blockOffset = offset / BLOCK_BYTES
        val prefix = (offset % BLOCK_BYTES).toInt()
        val blocks = (prefix + limit + BLOCK_BYTES - 1) / BLOCK_BYTES
        val quoted = shellQuote(path)
        val result = run(
            "[ -f $quoted ] || exit 42; [ -r $quoted ] || exit 43; " +
                "dd if=$quoted bs=$BLOCK_BYTES skip=$blockOffset count=$blocks",
            maxOutputBytes = blocks * BLOCK_BYTES,
        )
        val from = prefix.coerceAtMost(result.output.size)
        return result.output.copyOfRange(from, (from + limit).coerceAtMost(result.output.size))
    }

    override fun list(path: String, offset: Int, limit: Int, expectedRevision: String?): FileToolDirectoryPage {
        if (offset < 0 || limit !in 1..200) fail("INVALID_ARGUMENT", "Invalid directory pagination range")
        val before = stat(path)
        if (before.kind != FileToolKind.DIRECTORY) fail("NOT_DIRECTORY", "Target is not a directory")
        if (expectedRevision != null && expectedRevision != before.revision) {
            fail("STALE_CURSOR", "Directory changed. Start again from the first page.")
        }
        val quoted = shellQuote(path)
        val result = run(
            "[ -r $quoted ] && [ -x $quoted ] || exit 43; " +
                "find $quoted -mindepth 1 -maxdepth 1 -print0",
            maxOutputBytes = MAX_DIRECTORY_BYTES,
        )
        if (result.output.isNotEmpty() && result.output.last() != 0.toByte()) {
            fail("DIRECTORY_TOO_LARGE", "Directory enumeration exceeds the budget. Narrow the scope.")
        }
        val names = decode(result.output).split('\u0000').filter(String::isNotEmpty)
        if (names.size > MAX_DIRECTORY_ENTRIES) fail("DIRECTORY_TOO_LARGE", "Too many directory entries. Narrow the scope.")
        val sorted = names.sorted()
        val selected = statPage(sorted.drop(offset).take(limit))
        if (stat(path).revision != before.revision) fail("STALE_CURSOR", "Directory changed during reading. Read it again.")
        val next = (offset.toLong() + selected.size).coerceAtMost(sorted.size.toLong()).toInt()
        return FileToolDirectoryPage(selected, next, next < sorted.size, before.revision)
    }

    /** 一页条目的元数据共用一次命名空间；路径经 stdin 传入，避免撑满 exec 参数。 */
    private fun statPage(paths: List<String>): List<FileToolStat> {
        if (paths.isEmpty()) return emptyList()
        val script = buildString {
            append("eta_file_stat() {\n")
            append(statScript("\"${'$'}1\""))
            append("\n}\n")
            paths.forEach { path ->
                append("eta_file_stat ").append(shellQuote(path)).append(" || exit ${'$'}?\n")
                append("printf '\\000'\n")
            }
        }
        val result = run(
            "sh -s",
            stdin = script.toByteArray(Charsets.UTF_8),
            maxOutputBytes = minOf(paths.size * MAX_STAT_BYTES, MAX_DIRECTORY_BYTES),
        )
        val records = decode(result.output).split('\u0000')
        if (records.size != paths.size + 1 || records.last().isNotEmpty()) {
            fail("FILE_METADATA_INVALID", "Incomplete directory entry metadata. Read it again.")
        }
        return paths.mapIndexed { index, path -> parseStat(path, records[index]) }
    }

    override fun write(
        path: String,
        bytes: ByteArray,
        append: Boolean,
        expectedRevision: String?,
        expectedSha256: String?,
    ): FileToolWriteResult {
        if (bytes.size > MAX_READ_BYTES) fail("FILE_TOO_LARGE", "Write content exceeds the per-request limit")
        if (expectedSha256 != null && !expectedSha256.matches(Regex("[a-f0-9]{64}"))) {
            fail("INVALID_ARGUMENT", "Invalid content version format")
        }
        val quoted = shellQuote(path)
        val parent = shellQuote(File(path).parent ?: "/")
        expectedRevision?.let {
            if (stat(path).revision != it) fail("FILE_CHANGED", "File changed. Read it again before editing.")
        }
        val hashCheck = expectedSha256?.let {
            "eta_hash=${'$'}(sha256sum < $quoted) || exit 45; " +
                "[ \"${'$'}{eta_hash%% *}\" = ${shellQuote(it)} ] || exit 44; "
        }.orEmpty()
        // 保留既有 inode 的属主、模式和 SELinux 标签；如实报告非原子写入。
        run(
            "mkdir -p -- $parent || exit 43; " +
                "if [ -e $quoted ] && [ ! -f $quoted ]; then exit 42; fi; " +
                hashCheck + "cat ${if (append) ">>" else ">"} $quoted",
            stdin = bytes,
            maxOutputBytes = 8192,
        )
        return FileToolWriteResult(stat(path), atomic = false)
    }

    private fun run(
        command: String,
        stdin: ByteArray? = null,
        maxOutputBytes: Int,
        allowFailure: Boolean = false,
    ): OneShotShellResult {
        checkActive()
        val result = execute(command, stdin, maxOutputBytes)
        checkActive()
        when (result.exitCode) {
            SHELL_STDIN_FAILED_EXIT_CODE -> fail("FILE_INPUT_FAILED", "Write content was not fully transferred. Read and verify file state.")
            -3 -> fail("CANCELLED", "File operation canceled")
            -2 -> fail("FILE_TIMEOUT", "File operation timed out")
            -1 -> fail("FILE_PROCESS_FAILED", "Could not start file operation")
            41 -> fail("FILE_NOT_FOUND", "File not found or inaccessible to the current identity")
            42 -> fail("NOT_REGULAR_FILE", "Target is not a regular file")
            43 -> fail("FILE_ACCESS_DENIED", "Current identity lacks file access permission")
            44 -> fail("FILE_CHANGED", "File content changed. Read it again before editing.")
            45 -> fail("FILE_HASH_UNAVAILABLE", "Current environment cannot verify content versions")
            0 -> Unit
            else -> if (!allowFailure || result.exitCode != 1) fail("FILE_COMMAND_FAILED", "File operation failed (exit=${result.exitCode}）")
        }
        if (result.outputTruncated) fail("FILE_OUTPUT_LIMIT", "File operation result exceeds the budget. Narrow the scope.")
        return result
    }

    private fun decode(bytes: ByteArray): String = try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        fail("UNSUPPORTED_ENCODING", "File name or metadata is not valid UTF-8")
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fail(code: String, message: String): Nothing = throw FileToolException(code, message)

    private companion object {
        const val BLOCK_BYTES = 4096
        const val MAX_READ_BYTES = 512 * 1024
        const val MAX_DIRECTORY_BYTES = 1024 * 1024
        const val MAX_DIRECTORY_ENTRIES = 20_000
        const val MAX_STAT_BYTES = 8192
    }
}
