#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <chrono>
#include <mutex>
#include <thread>
#include <sched.h>
#include <unistd.h>
#include <android/log.h>
#include <ctranslate2/translator.h>
#include <ctranslate2/replica_pool.h>
#include <sentencepiece_processor.h>
#include <algorithm>

#define LOG_TAG "SIH_AI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static sentencepiece::SentencePieceProcessor* g_spm_source = nullptr;
static sentencepiece::SentencePieceProcessor* g_spm_target = nullptr;
static std::string g_model_dir = "";
static ctranslate2::Translator* g_translator = nullptr;
static std::mutex g_engine_mutex;

static unsigned int get_optimal_thread_count() {
    unsigned int total_cores = std::thread::hardware_concurrency();
    if (total_cores <= 2) return (total_cores > 0 ? total_cores : 1);

    std::vector<int> perf_core_ids;
    unsigned long long max_freq_found = 0;
    std::vector<unsigned long long> freqs;

    // Method 1: Sysfs frequency check (fast if permissions allow)
    for (unsigned int i = 0; i < total_cores; ++i) {
        std::string path = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/cpufreq/cpuinfo_max_freq";
        FILE* f = fopen(path.c_str(), "r");
        unsigned long long freq = 0;
        if (f) {
            if (fscanf(f, "%llu", &freq) == 1) {
                if (freq > max_freq_found) max_freq_found = freq;
            }
            fclose(f);
        }
        freqs.push_back(freq);
    }

    if (max_freq_found > 0) {
        for (unsigned int i = 0; i < total_cores; ++i) {
            if (freqs[i] >= max_freq_found * 0.85) {
                perf_core_ids.push_back(i);
            }
        }
    }

    // Method 2: Empirical 0.1ms micro-benchmark test if sysfs was restricted by SELinux
    if (perf_core_ids.empty()) {
        std::vector<std::pair<long long, int>> core_durations;
        for (unsigned int i = 0; i < total_cores; ++i) {
            cpu_set_t cpuset;
            CPU_ZERO(&cpuset);
            CPU_SET(i, &cpuset);
            if (sched_setaffinity(0, sizeof(cpu_set_t), &cpuset) == 0) {
                auto start = std::chrono::high_resolution_clock::now();
                volatile double dummy = 1.0;
                for (int j = 0; j < 30000; ++j) { dummy += j * 0.001; }
                auto elapsed = std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::high_resolution_clock::now() - start).count();
                core_durations.push_back({elapsed, static_cast<int>(i)});
            }
        }

        // Restore affinity to all cores
        cpu_set_t all_cores;
        CPU_ZERO(&all_cores);
        for (unsigned int i = 0; i < total_cores; ++i) CPU_SET(i, &all_cores);
        sched_setaffinity(0, sizeof(cpu_set_t), &all_cores);

        if (!core_durations.empty()) {
            std::sort(core_durations.begin(), core_durations.end());
            long long fastest_time = core_durations[0].first;
            for (const auto& entry : core_durations) {
                if (entry.first <= fastest_time * 1.5) {
                    perf_core_ids.push_back(entry.second);
                }
            }
        }
    }

    // Dynamic core calculation & safe fallback
    unsigned int count = static_cast<unsigned int>(perf_core_ids.size());
    if (count == 0) {
        LOGI("Thread Allocator: Core detection restricted. Using 2-thread safe fallback.");
        return 2;
    }

    unsigned int final_threads = std::max(2u, std::min(4u, count));
    LOGI("Thread Allocator: Detected %u Performance Cores (total=%u). Configured %u threads.", count, total_cores, final_threads);
    return final_threads;
}

static std::vector<std::string> split_into_chunks(const std::string& text) {
    std::vector<std::string> chunks;
    std::stringstream ss(text);
    std::string chunk;
    while (std::getline(ss, chunk, '.')) {
        if (!chunk.empty()) {
            chunks.push_back(chunk + ".");
        }
    }
    if (chunks.empty()) {
        chunks.push_back(text);
    }
    return chunks;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_indicT_MainActivity_initNativeIndicToEng(
        JNIEnv* env, jobject /* this */, jstring model_dir, jstring /* unused_spm_path */) {

    std::lock_guard<std::mutex> lock(g_engine_mutex);

    const char* native_model = env->GetStringUTFChars(model_dir, nullptr);
    std::string target_model_dir = std::string(native_model);
    env->ReleaseStringUTFChars(model_dir, native_model);

    // If already active in memory with the exact same directory, reuse it
    if (g_translator != nullptr && g_spm_source != nullptr && g_spm_target != nullptr && g_model_dir == target_model_dir) {
        LOGI("Engine 2 (Indic->Eng) already loaded in RAM. Skipping re-init.");
        return 0;
    }

    // Purge any stale instances before loading fresh model
    if (g_translator != nullptr) { delete g_translator; g_translator = nullptr; }
    if (g_spm_source != nullptr) { delete g_spm_source; g_spm_source = nullptr; }
    if (g_spm_target != nullptr) { delete g_spm_target; g_spm_target = nullptr; }

    g_model_dir = target_model_dir;
    std::string src_path = g_model_dir + "/model.SRC";
    std::string tgt_path = g_model_dir + "/model.TGT";

    g_spm_source = new sentencepiece::SentencePieceProcessor();
    g_spm_target = new sentencepiece::SentencePieceProcessor();

    LOGI("Loading Source Dictionary: %s", src_path.c_str());
    if (!g_spm_source->Load(src_path).ok()) {
        LOGE("Failed to load source dictionary at %s", src_path.c_str());
        delete g_spm_source; g_spm_source = nullptr;
        delete g_spm_target; g_spm_target = nullptr;
        g_model_dir = "";
        return 1;
    }

    LOGI("Loading Target Dictionary: %s", tgt_path.c_str());
    if (!g_spm_target->Load(tgt_path).ok()) {
        LOGE("Failed to load target dictionary at %s", tgt_path.c_str());
        delete g_spm_source; g_spm_source = nullptr;
        delete g_spm_target; g_spm_target = nullptr;
        g_model_dir = "";
        return 2;
    }

    ctranslate2::ReplicaPoolConfig pool_config;
    pool_config.num_threads_per_replica = get_optimal_thread_count();

    try {
        ctranslate2::models::ModelLoader loader(g_model_dir);
        loader.compute_type = ctranslate2::ComputeType::AUTO;
        g_translator = new ctranslate2::Translator(loader, pool_config);
        LOGI("Engine 2 (Indic->Eng) successfully initialized in RAM with %zu threads (Quantized INT8/AUTO).", pool_config.num_threads_per_replica);
    } catch (const std::exception& e) {
        LOGE("Failed to boot CTranslate2 Engine 2: %s", e.what());
        delete g_spm_source; g_spm_source = nullptr;
        delete g_spm_target; g_spm_target = nullptr;
        g_model_dir = "";
        return 3;
    }

    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_indicT_MainActivity_unloadNativeIndicToEng(JNIEnv* env, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);

    if (g_spm_source != nullptr) {
        delete g_spm_source;
        g_spm_source = nullptr;
    }
    if (g_spm_target != nullptr) {
        delete g_spm_target;
        g_spm_target = nullptr;
    }
    if (g_translator != nullptr) {
        delete g_translator;
        g_translator = nullptr;
    }

    g_model_dir = "";
    LOGI("Engine 2 (Indic->Eng) Native RAM wiped successfully.");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_indicT_MainActivity_translateIndicToEng(
        JNIEnv* env, jobject /* this */, jstring text, jstring src_lang, jint beam_size) {

    std::lock_guard<std::mutex> lock(g_engine_mutex);

    const char* native_text = env->GetStringUTFChars(text, nullptr);
    const char* native_src = env->GetStringUTFChars(src_lang, nullptr);

    if (g_translator == nullptr || g_model_dir.empty() || g_spm_source == nullptr || g_spm_target == nullptr) {
        LOGE("translateIndicToEng called but Engine 2 is NULL in RAM.");
        env->ReleaseStringUTFChars(text, native_text);
        env->ReleaseStringUTFChars(src_lang, native_src);
        return env->NewStringUTF("Error: AI Engine is NULL. The models failed to load.");
    }

    std::string input_text(native_text);
    if (!input_text.empty() && input_text.back() != '.' && input_text.back() != '?' && input_text.back() != '!') {
        input_text += ".";
    }

    std::vector<std::string> sentences = split_into_chunks(input_text);
    std::vector<std::vector<std::string>> batch;
    std::vector<std::vector<std::string>> target_prefixes;

    size_t max_raw_token_len = 0;
    for (const auto& sentence : sentences) {
        std::vector<std::string> raw_tokens;
        g_spm_source->Encode(sentence, &raw_tokens);
        max_raw_token_len = std::max(max_raw_token_len, raw_tokens.size());

        std::vector<std::string> source_tokens = {std::string(native_src)};
        for (const auto& t : raw_tokens) {
            source_tokens.push_back(t);
        }
        source_tokens.push_back("</s>");
        source_tokens.push_back("eng_Latn");

        batch.push_back(source_tokens);
        target_prefixes.push_back({"eng_Latn"});
    }

    std::string final_stitched_text = "";

    try {
        ctranslate2::TranslationOptions options;
        options.beam_size = (beam_size > 0 && beam_size <= 5) ? static_cast<size_t>(beam_size) : 1;
        options.max_decoding_length = std::min(static_cast<size_t>(128), std::max(static_cast<size_t>(15), max_raw_token_len * 2 + 5));
        options.end_token = "</s>";
        options.repetition_penalty = 1.3;
        options.replace_unknowns = true;

        auto results = g_translator->translate_batch(batch, target_prefixes, options);

        for (const auto& result : results) {
            std::vector<std::string> output_tokens = result.hypotheses[0];

            while (!output_tokens.empty() &&
                   (output_tokens.front() == "eng_Latn" ||
                    output_tokens.front() == std::string(native_src) ||
                    output_tokens.front().find("Latn") != std::string::npos ||
                    output_tokens.front().find("Deva") != std::string::npos ||
                    output_tokens.front().find("Olck") != std::string::npos ||
                    output_tokens.front() == "</s>" ||
                    output_tokens.front() == "<pad>")) {
                output_tokens.erase(output_tokens.begin());
            }

            std::string decoded_chunk;
            g_spm_target->Decode(output_tokens, &decoded_chunk);

            // Strip unicode unknown symbols and control chars
            std::string utf8_unk = "\xE2\x81\x87";
            if (decoded_chunk.find(utf8_unk) == 0) {
                decoded_chunk.erase(0, 3);
            }
            std::string android_unk = "\xEF\xBF\xBD";
            while (decoded_chunk.find(android_unk) != std::string::npos) {
                decoded_chunk.replace(decoded_chunk.find(android_unk), 3, "");
            }

            while (!decoded_chunk.empty() && (decoded_chunk[0] == '?' || decoded_chunk[0] == ' ' || decoded_chunk[0] == ',')) {
                decoded_chunk.erase(0, 1);
            }

            final_stitched_text += decoded_chunk + " ";
        }
    } catch (const std::exception& e) {
        LOGE("Translation Exception in Engine 2: %s", e.what());
        final_stitched_text = std::string("Error: ") + e.what();
    }

    env->ReleaseStringUTFChars(text, native_text);
    env->ReleaseStringUTFChars(src_lang, native_src);

    if (!final_stitched_text.empty() && final_stitched_text.back() == ' ') {
        final_stitched_text.pop_back();
    }

    return env->NewStringUTF(final_stitched_text.c_str());
}