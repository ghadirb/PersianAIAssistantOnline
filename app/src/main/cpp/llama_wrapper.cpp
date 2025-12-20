#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <cstring>
#include <android/log.h>
#include "llama.h"

struct LlamaHandle {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    int n_ctx = 2048;
};

static int pick_greedy(const float *logits, int vocab) {
    int best_id = 0;
    float best_logit = logits[0];
    for (int i = 1; i < vocab; ++i) {
        if (logits[i] > best_logit) {
            best_logit = logits[i];
            best_id = i;
        }
    }
    return best_id;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_persianai_assistant_offline_LocalLlamaRunner_nativeLoad(JNIEnv *env, jobject /*thiz*/, jstring jpath) {
    const char *cpath = env->GetStringUTFChars(jpath, nullptr);
    std::string path(cpath ? cpath : "");
    env->ReleaseStringUTFChars(jpath, cpath);

    if (path.empty()) return 0;

    // llama.cpp جدید دیگر آرگومان نمی‌گیرد
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    auto *model = llama_model_load_from_file(path.c_str(), mparams);
    if (!model) {
        __android_log_print(ANDROID_LOG_ERROR, "local_llama", "Failed to load model: %s", path.c_str());
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;

    auto *ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        __android_log_print(ANDROID_LOG_ERROR, "local_llama", "Failed to init context for model: %s", path.c_str());
        llama_model_free(model);
        return 0;
    }

    auto *handle = new LlamaHandle();
    handle->model = model;
    handle->ctx = ctx;
    handle->n_ctx = cparams.n_ctx;
    __android_log_print(ANDROID_LOG_INFO, "local_llama", "Model loaded ok: %s", path.c_str());
    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_persianai_assistant_offline_LocalLlamaRunner_nativeInfer(JNIEnv *env, jobject /*thiz*/, jlong h, jstring jprompt, jint maxTokens) {
    if (h == 0) return nullptr;
    auto *handle = reinterpret_cast<LlamaHandle *>(h);
    auto *model = handle->model;
    auto *ctx = handle->ctx;
    if (!model || !ctx) return nullptr;

    const char *cprompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(cprompt ? cprompt : "");
    env->ReleaseStringUTFChars(jprompt, cprompt);

    if (prompt.empty()) {
        __android_log_print(ANDROID_LOG_WARN, "local_llama", "Prompt empty");
        return nullptr;
    }

    // Tokenize prompt
    std::vector<llama_token> tokens(prompt.size() + 128);
    const llama_vocab * vocab = llama_model_get_vocab(model);
    int n_tokens = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(), tokens.data(), (int)tokens.size(), true, false);
    if (n_tokens < 1) {
        __android_log_print(ANDROID_LOG_ERROR, "local_llama", "Tokenize failed");
        return nullptr;
    }
    tokens.resize(n_tokens);

    // Prime the context using batch API
    {
        llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
        // ensure logits for last token
        batch.logits[(int)tokens.size() - 1] = 1;
        if (llama_decode(ctx, batch) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, "local_llama", "llama_decode failed on prompt");
            return nullptr;
        }
    }

    std::vector<llama_token> output;
    output.reserve(maxTokens);

    int n_past = (int)tokens.size();
    llama_token current = tokens.back();
    const int eos = llama_vocab_eos(vocab);
    const int vocab_size = llama_vocab_n_tokens(vocab);

    for (int i = 0; i < maxTokens; ++i) {
        llama_batch batch = llama_batch_get_one(&current, 1);
        batch.pos[0] = n_past;
        batch.logits[0] = 1;
        if (llama_decode(ctx, batch) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, "local_llama", "llama_decode failed at step %d", i);
            break;
        }
        n_past += 1;
        const float *logits = llama_get_logits(ctx);
        int next = pick_greedy(logits, vocab_size);
        if (next == eos) break;
        output.push_back(next);
        current = next;
    }

    std::string result;
    result.reserve(output.size() * 4);
    for (auto t : output) {
        char buf[512];
        int32_t written = llama_token_to_piece(vocab, t, buf, sizeof(buf), 0, true);
        if (written > 0) {
            result.append(buf, buf + written);
        }
    }

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_persianai_assistant_offline_LocalLlamaRunner_nativeUnload(JNIEnv *env, jobject /*thiz*/, jlong h) {
    if (h == 0) return;
    auto *handle = reinterpret_cast<LlamaHandle *>(h);
    if (handle->ctx) llama_free(handle->ctx);
    if (handle->model) llama_model_free(handle->model);
    delete handle;
    // آزادسازی منابع سراسری بک‌اند
    llama_backend_free();
}
