# Media3 FFmpeg E-AC-3 fallback

This module vendors the AndroidX Media3 1.5.0 FFmpeg audio renderer and an FFmpeg 6.0 build containing only the `ac3` and `eac3` decoders. Media3 maps both `audio/eac3` and `audio/eac3-joc` to the E-AC-3 decoder. FFmpeg outputs decoded PCM; Atmos object metadata and device Atmos rendering are not guaranteed to survive software decoding.

The FFmpeg source snapshot is `ffmpeg-n6.0-source.tar.gz`, from upstream commit `ea3d24bbe3c58b171e55fe2151fc7ffaca3ab3d2`. Its LGPL 2.1-or-later license is in `FFMPEG-LICENSE.txt` and is also packaged in the app at `assets/licenses/FFmpeg-LGPL-2.1.txt`. The FFmpeg source archive is included to support rebuilding the native libraries. FFmpeg is built with `--disable-everything --enable-decoder=ac3 --enable-decoder=eac3`; no GPL components are enabled.

The Media3 Java renderer sources and JNI bridge are Apache-2.0 licensed and retain their upstream license headers. The AndroidX Media3 version is pinned to 1.5.0 to match the app.

`src/main/jni/ffmpeg/android-libs` contains the FFmpeg static libraries used to link `libffmpegJNI.so`; `src/main/jni/ffmpeg/include` contains per-ABI FFmpeg generated configuration and headers used for that link. The normal Android build uses these checked-in artifacts and does not run FFmpeg's configure/build process.
