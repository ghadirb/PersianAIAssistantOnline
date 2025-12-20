#include <jni.h>
#include <string>
#include <vector>
#include <memory>
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
    auto *model = llama_load_model_from_file(path.c_str(), mparams);
    if (!model) return 0;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.seed = 1234;

    auto *ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        llama_free_model(model);
        return 0;
    }

    auto *handle = new LlamaHandle();
    handle->model = model;
    handle->ctx = ctx;
    handle->n_ctx = cparams.n_ctx;
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

    if (prompt.empty()) return nullptr;

    // Tokenize prompt
    std::vector<llama_token> tokens(prompt.size() + 128);
    int n_tokens = llama_tokenize(model, prompt.c_str(), (int)prompt.size(), tokens.data(), (int)tokens.size(), true, false);
    if (n_tokens < 1) return nullptr;
    tokens.resize(n_tokens);

    // Prime the context
    if (llama_eval(ctx, tokens.data(), (int)tokens.size(), 0, 4) != 0) {
        return nullptr;
    }

    std::vector<llama_token> output;
    output.reserve(maxTokens);

    int n_past = (int)tokens.size();
    llama_token current = tokens.back();
    const int eos = llama_token_eos(model);
    const int vocab = llama_n_vocab(model);

    for (int i = 0; i < maxTokens; ++i) {
        if (llama_eval(ctx, &current, 1, n_past, 4) != 0) break;
        n_past += 1;
        const float *logits = llama_get_logits(ctx);
        int next = pick_greedy(logits, vocab);
        if (next == eos) break;
        output.push_back(next);
        current = next;
    }

    std::string result;
    result.reserve(output.size() * 4);
    for (auto t : output) {
        result += llama_token_to_piece(model, t);
    }

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_persianai_assistant_offline_LocalLlamaRunner_nativeUnload(JNIEnv *env, jobject /*thiz*/, jlong h) {
    if (h == 0) return;
    auto *handle = reinterpret_cast<LlamaHandle *>(h);
    if (handle->ctx) llama_free(handle->ctx);
    if (handle->model) llama_free_model(handle->model);
    delete handle;
    // آزادسازی منابع سراسری بک‌اند
    llama_backend_free();
}
