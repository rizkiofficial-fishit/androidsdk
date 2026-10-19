#include <jni.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

// Fungsi ini diekspor oleh libnode.so (Node.js for Mobile).
namespace node {
__attribute__((visibility("default"))) int Start(int argc, char* argv[]);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ryzfinity_live_NodeService_startNode(JNIEnv* env, jobject /* this */, jobjectArray arguments, jstring logPath) {
    const char* lp = env->GetStringUTFChars(logPath, nullptr);
    if (lp && *lp) {
        freopen(lp, "a", stdout);
        freopen(lp, "a", stderr);
        setvbuf(stdout, nullptr, _IOLBF, 0);
        setvbuf(stderr, nullptr, _IONBF, 0);
    }
    env->ReleaseStringUTFChars(logPath, lp);

    jsize n = env->GetArrayLength(arguments);
    std::vector<std::string> args;
    size_t total = 0;
    for (jsize i = 0; i < n; i++) {
        jstring js = (jstring) env->GetObjectArrayElement(arguments, i);
        const char* c = env->GetStringUTFChars(js, nullptr);
        args.emplace_back(c);
        env->ReleaseStringUTFChars(js, c);
        total += args.back().size() + 1;
    }
    // libuv butuh argv di satu blok memori yang berurutan.
    char* buf = (char*) calloc(total + 1, 1);
    std::vector<char*> argv(n + 1, nullptr);
    char* p = buf;
    for (jsize i = 0; i < n; i++) {
        memcpy(p, args[i].c_str(), args[i].size() + 1);
        argv[i] = p;
        p += args[i].size() + 1;
    }
    return node::Start((int) n, argv.data());
}
