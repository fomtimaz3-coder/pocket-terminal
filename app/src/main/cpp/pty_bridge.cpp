#define _GNU_SOURCE
#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string>
#include <cstring>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

#define LOG_TAG "PocketTerminal"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_com_pocketterminal_core_NativePtyBridge_createSubprocess(
        JNIEnv* env,
        jobject,
        jstring shell,
        jstring cwd,
        jobjectArray environment,
        jintArray process_id,
        jint rows,
        jint cols) {
    const char* native_shell = env->GetStringUTFChars(shell, nullptr);
    const char* native_cwd = env->GetStringUTFChars(cwd, nullptr);

    struct winsize window{};
    window.ws_row = static_cast<unsigned short>(rows);
    window.ws_col = static_cast<unsigned short>(cols);

    int master_fd = posix_openpt(O_RDWR | O_NOCTTY);
    if (master_fd < 0 || grantpt(master_fd) != 0 || unlockpt(master_fd) != 0) {
        LOGE("opening PTY failed: %s", strerror(errno));
        env->ReleaseStringUTFChars(shell, native_shell);
        env->ReleaseStringUTFChars(cwd, native_cwd);
        return -1;
    }

    const char* slave_name = ptsname(master_fd);
    if (slave_name == nullptr) {
        LOGE("ptsname failed: %s", strerror(errno));
        close(master_fd);
        env->ReleaseStringUTFChars(shell, native_shell);
        env->ReleaseStringUTFChars(cwd, native_cwd);
        return -1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        close(master_fd);
        env->ReleaseStringUTFChars(shell, native_shell);
        env->ReleaseStringUTFChars(cwd, native_cwd);
        return -1;
    }

    if (pid == 0) {
        setsid();
        const int slave_fd = open(slave_name, O_RDWR);
        if (slave_fd < 0) _exit(126);
        ioctl(slave_fd, TIOCSCTTY, 0);
        dup2(slave_fd, STDIN_FILENO);
        dup2(slave_fd, STDOUT_FILENO);
        dup2(slave_fd, STDERR_FILENO);
        if (slave_fd > STDERR_FILENO) close(slave_fd);
        close(master_fd);

        if (chdir(native_cwd) != 0) {
            LOGE("chdir failed: %s", strerror(errno));
        }

        if (environment != nullptr) {
            const jsize count = env->GetArrayLength(environment);
            for (jsize i = 0; i < count; ++i) {
                auto item = static_cast<jstring>(env->GetObjectArrayElement(environment, i));
                const char* value = env->GetStringUTFChars(item, nullptr);
                const std::string pair(value);
                const auto separator = pair.find('=');
                if (separator != std::string::npos) {
                    setenv(pair.substr(0, separator).c_str(),
                           pair.substr(separator + 1).c_str(), 1);
                }
                env->ReleaseStringUTFChars(item, value);
                env->DeleteLocalRef(item);
            }
        }

        execl(native_shell, native_shell, "-i", static_cast<char*>(nullptr));
        _exit(127);
    }

    env->ReleaseStringUTFChars(shell, native_shell);
    env->ReleaseStringUTFChars(cwd, native_cwd);

    if (process_id != nullptr) {
        jint* output = env->GetIntArrayElements(process_id, nullptr);
        output[0] = static_cast<jint>(pid);
        env->ReleaseIntArrayElements(process_id, output, 0);
    }

    return master_fd;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pocketterminal_core_NativePtyBridge_read(
        JNIEnv* env, jobject, jint fd, jbyteArray buffer, jint length) {
    jbyte* bytes = env->GetByteArrayElements(buffer, nullptr);
    ssize_t result;
    do {
        result = ::read(fd, bytes, static_cast<size_t>(length));
    } while (result < 0 && errno == EINTR);
    env->ReleaseByteArrayElements(buffer, bytes, result > 0 ? 0 : JNI_ABORT);
    return static_cast<jint>(result);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pocketterminal_core_NativePtyBridge_write(
        JNIEnv* env, jobject, jint fd, jbyteArray buffer, jint length) {
    jbyte* bytes = env->GetByteArrayElements(buffer, nullptr);
    ssize_t result = ::write(fd, bytes, static_cast<size_t>(length));
    env->ReleaseByteArrayElements(buffer, bytes, JNI_ABORT);
    return static_cast<jint>(result);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocketterminal_core_NativePtyBridge_setWindowSize(
        JNIEnv*, jobject, jint fd, jint rows, jint cols) {
    struct winsize window{};
    window.ws_row = static_cast<unsigned short>(rows);
    window.ws_col = static_cast<unsigned short>(cols);
    ioctl(fd, TIOCSWINSZ, &window);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocketterminal_core_NativePtyBridge_sendSignal(
        JNIEnv*, jobject, jint pid, jint signal_number) {
    if (pid > 0) {
        kill(static_cast<pid_t>(pid), signal_number);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocketterminal_core_NativePtyBridge_closePty(
        JNIEnv*, jobject, jint fd) {
    if (fd >= 0) {
        close(fd);
    }
}