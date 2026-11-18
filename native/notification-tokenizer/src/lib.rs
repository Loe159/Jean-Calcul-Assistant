//! POC JNI adapter for the unmodified Hugging Face tokenizers runtime.
//! No network calls, no logging of notification contents, no Android permission.
use jni::{
    objects::{JObject, JString},
    sys::{jboolean, jint, jlongArray},
    JNIEnv,
};
use std::sync::{Mutex, OnceLock};
use tokenizers::{Tokenizer, TruncationParams, TruncationStrategy};

static TOKENIZER: OnceLock<Mutex<Option<Tokenizer>>> = OnceLock::new();

fn current() -> &'static Mutex<Option<Tokenizer>> {
    TOKENIZER.get_or_init(|| Mutex::new(None))
}

// Always call from a bounded background worker, never Android's main thread.
#[no_mangle]
pub extern "system" fn Java_io_github_mangi_eta_agent_device_notifications_AndroidHfTokenizer_initialize(
    mut env: JNIEnv,
    _this: JObject,
    path: JString,
) -> jboolean {
    let Ok(path) = env.get_string(&path) else { return 0 };
    let path = path.to_string_lossy().into_owned();
    let Ok(tokenizer) = Tokenizer::from_file(path) else { return 0 };
    let Ok(mut guard) = current().lock() else { return 0 };
    *guard = Some(tokenizer);
    1
}

#[no_mangle]
pub extern "system" fn Java_io_github_mangi_eta_agent_device_notifications_AndroidHfTokenizer_encodePair(
    mut env: JNIEnv,
    _this: JObject,
    text: JString,
    hypothesis: JString,
    max_length: jint,
) -> jlongArray {
    if !(16..=512).contains(&max_length) { return std::ptr::null_mut(); }
    let Ok(text) = env.get_string(&text) else { return std::ptr::null_mut() };
    let text = text.to_string_lossy().into_owned();
    let Ok(hypothesis) = env.get_string(&hypothesis) else { return std::ptr::null_mut() };
    let hypothesis = hypothesis.to_string_lossy().into_owned();

    let Ok(mut guard) = current().lock() else { return std::ptr::null_mut() };
    let Some(tokenizer) = guard.as_mut() else { return std::ptr::null_mut() };
    if tokenizer.with_truncation(Some(TruncationParams {
        max_length: max_length as usize,
        strategy: TruncationStrategy::LongestFirst,
        ..Default::default()
    })).is_err() {
        return std::ptr::null_mut();
    }
    let Ok(encoding) = tokenizer.encode((text.as_str(), hypothesis.as_str()), true)
        else { return std::ptr::null_mut() };
    let ids: Vec<i64> = encoding.get_ids().iter().map(|id| i64::from(*id)).collect();
    let Ok(array) = env.new_long_array(ids.len() as i32)
        else { return std::ptr::null_mut() };
    if env.set_long_array_region(&array, 0, &ids).is_err() {
        return std::ptr::null_mut();
    }
    array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_io_github_mangi_eta_agent_device_notifications_AndroidHfTokenizer_release(
    _env: JNIEnv,
    _this: JObject,
) {
    if let Ok(mut guard) = current().lock() {
        *guard = None;
    }
}
