package io.github.mangi.eta.agent.device.notifications

/**
 * Native official Hugging Face BPE tokenizer (phase-A instrumentation only).
 * No production listener may depend on this before all parities are proven.
 */
internal class AndroidHfTokenizer {
    init {
        System.loadLibrary("jeancalcul_hf_tokenizer")
    }

    external fun initialize(tokenizerJsonPath: String): Boolean

    external fun encodePair(text: String, hypothesis: String, maxLength: Int): LongArray?
}
