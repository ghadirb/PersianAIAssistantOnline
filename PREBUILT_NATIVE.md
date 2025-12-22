Prebuilt native libraries (liblocal_llama.so)

This project normally builds `local_llama` from `llama.cpp` via CMake/NDK. In CI or quick builds you can provide prebuilt native libraries instead.

Place prebuilt libraries under a folder named `prebuilt_libs` at the repository root using this layout:

prebuilt_libs/
  arm64-v8a/
    liblocal_llama.so
  armeabi-v7a/
    liblocal_llama.so

When present, the Gradle configuration will include `prebuilt_libs` as an additional `jniLibs` source directory and the bundled `liblocal_llama.so` will be packaged into the APK.

Notes:
- Prebuilt libraries must match the JNI symbol names expected by the Java wrapper. The wrapper expects the `Java_com_persianai_assistant_offline_LocalLlamaRunner_native*` symbols.
- If you're building locally and want to compile from source, set the environment variable `LLAMA_CPP_DIR` to point to your local `llama.cpp` checkout, or place `llama.cpp` under `Compressed/llama.cpp-master` relative to the project root.
- If you need a hosted place to store prebuilt `.so` files, add them into `prebuilt_libs` in CI before running Gradle assemble.

Example CI snippet (bash):

```bash
# Download prebuilt libs into repository
mkdir -p prebuilt_libs/arm64-v8a
curl -L -o prebuilt_libs/arm64-v8a/liblocal_llama.so https://example.com/prebuilt/arm64-v8a/liblocal_llama.so
# Then run gradle assemble
./gradlew assembleRelease
```
