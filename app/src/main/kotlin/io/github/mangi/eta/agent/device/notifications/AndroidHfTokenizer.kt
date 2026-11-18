package io.github.mangi.eta.agent.device.notifications

/** Pinned Hugging Face BPE runtime. Library is compiled from source in the trusted build. */
internal class AndroidHfTokenizer {
    init { System.loadLibrary("jeancalcul_hf_tokenizer") }
    external fun initialize(tokenizerJsonPath: String): Boolean
    external fun encodePair(text: String, hypothesis: String, maxLength: Int): LongArray?
    external fun release()
}
