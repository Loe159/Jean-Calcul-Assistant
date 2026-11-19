package io.github.mangi.eta.agent.device.notifications

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.LongBuffer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** No notification text is ever sent over a network. Model downloads are user-initiated. */
internal object LocalNotificationModel {
    private val commit = NotificationNliScores.MODEL_REVISION
    private const val MODEL_SHA = "0ac394942a086fb1c02beb0fde0357bf7f3bd739ca07af7fa6c9fa11c9c05512"
    private const val TOKENIZER_SHA = "aebee76d0312011b0d73b08a255d4d11d25c83e9639324870c7b916ea102e13e"
    private const val MODEL_SIZE = 268409234L
    private const val TOKENIZER_SIZE = 34363287L
    private const val BASE = "https://huggingface.co/Horizon-Labs/multilingual-zeroshot-small/resolve/"
    private val files = listOf(
        Asset("model_quantized.onnx", "onnx/model_quantized.onnx", MODEL_SHA, MODEL_SIZE),
        Asset("tokenizer.json", "tokenizer.json", TOKENIZER_SHA, TOKENIZER_SIZE),
    )
    private data class Asset(val name: String, val remote: String, val sha: String, val size: Long)
    private fun folder(context: Context) = File(context.filesDir, "notification-model")
    private fun file(context: Context, name: String) = File(folder(context), name)

    fun installed(context: Context): Boolean = files.all { file(context, it.name).length() == it.size }
    fun delete(context: Context) { files.forEach { file(context, it.name).delete() } }
    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    suspend fun install(context: Context, progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        folder(context).mkdirs()
        for (asset in files) {
            val destination = file(context, asset.name)
            if (destination.length() == asset.size && digest(destination) == asset.sha) continue
            val staging = File(folder(context), asset.name + ".part")
            staging.delete()
            try {
                progress("Téléchargement : ${asset.name}")
                val url = URL(BASE + commit + "/" + asset.remote)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 45000
                    instanceFollowRedirects = true
                }
                try {
                    check(conn.responseCode == 200) { "Téléchargement HTTP ${conn.responseCode}" }
                    // HTTP redirects may use Xet CDN; they only deliver model weights.
                    conn.inputStream.use { input ->
                        FileOutputStream(staging).use { output ->
                            val buffer = ByteArray(1024 * 1024)
                            var count = 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                count += n
                                require(count <= asset.size) { "Taille invalide" }
                                output.write(buffer, 0, n)
                            }
                        }
                    }
                } finally { conn.disconnect() }
                progress("Vérification SHA-256 : ${asset.name}")
                require(staging.length() == asset.size && digest(staging) == asset.sha) {
                    "Fichier modèle invalide"
                }
                check(staging.renameTo(destination)) { "Impossible d'installer le modèle" }
            } finally { staging.delete() }
        }
        progress("Modèle vérifié et installé localement")
    }

    /** Synchronous work: call only on IO dispatcher, never on the UI or listener callback. */
    @Synchronized
    fun classify(
        context: Context, text: String,
        labels: List<String> = NotificationNliScores.validatedLabels,
    ): List<Pair<String, Double>> {
        require(installed(context)) { "Installer le modèle avant de commencer" }
        require(text.isNotBlank()) { "Saisir une notification à tester" }
        require(NotificationLabelRules.valid(labels.map { NotificationLabelRule(it) })) { "Labels invalides" }
        // Session per test: no inference threads or ~300 MiB session retained while idle.
        // A build without the native tokenizer (local builds: only CI compiles the Rust library)
        // must fail this analysis, not kill the process from the inference thread.
        val tokenizer = try {
            AndroidHfTokenizer()
        } catch (missing: UnsatisfiedLinkError) {
            throw IllegalStateException("Tokenizer natif absent de cette version", missing)
        }
        check(tokenizer.initialize(file(context, "tokenizer.json").absolutePath)) { "Tokenizer indisponible" }
        try {
        val batches = labels.map { label ->
            tokenizer.encodePair(text.take(512), NotificationNliScores.hypothesis(label), 128)
                ?: error("Erreur d'encodage de paire")
        }
        val length = batches.maxOf { it.size }
        require(length in 1..128)
        val ids = LongArray(labels.size * length)
        val mask = LongArray(ids.size)
        batches.forEachIndexed { row, tokens ->
            tokens.forEachIndexed { col, id ->
                ids[row * length + col] = id
                mask[row * length + col] = 1L
            }
        }
        val dims = longArrayOf(labels.size.toLong(), length.toLong())
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            addConfigEntry("session.intra_op.allow_spinning", "0")
            addConfigEntry("session.inter_op.allow_spinning", "0")
        }
        try {
            val environment = OrtEnvironment.getEnvironment()
            environment.createSession(file(context, "model_quantized.onnx").absolutePath, options).use { session ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), dims).use { input ->
                    OnnxTensor.createTensor(environment, LongBuffer.wrap(mask), dims).use { attention ->
                        session.run(mapOf("input_ids" to input, "attention_mask" to attention)).use { result ->
                            val logits = (result[0].value as Array<*>).map { value ->
                                (value as FloatArray).toList()
                            }
                            return labels.zip(NotificationNliScores.scores(logits, multiLabel = false))
                                .sortedByDescending { it.second }
                        }
                    }
                }
            }
        } finally { options.close() }
        } finally { tokenizer.release() }
    }
}
