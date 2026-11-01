plugins {
    id("com.android.library")
}

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 31
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++11"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.media3:media3-decoder:1.5.0")
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.annotation:annotation:1.9.1")
    compileOnly("org.checkerframework:checker-qual:3.13.0")
}
