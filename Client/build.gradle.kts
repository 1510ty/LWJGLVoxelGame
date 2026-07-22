import org.gradle.internal.os.OperatingSystem;

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}


tasks{
    shadowJar {
        archiveFileName.set("LWJGLVoxelGame-Client-${project.version}.jar")
        manifest {
            attributes["Main-Class"] = "com.mc1510ty.LWJGLVoxelGame.Client.Main"
        }
    }
}




val lwjglVersion = "3.4.3-SNAPSHOT"
val jomlVersion = "1.10.9"
val `joml-primitivesVersion` = "1.10.0"

val platforms = listOf(
    "natives-windows",
    "natives-windows-arm64",
    "natives-windows-x86",
    "natives-linux",
    "natives-linux-arm64",
    "natives-linux-arm32",
    "natives-linux-ppc64le",
    "natives-linux-riscv64",
    "natives-macos",
    "natives-macos-arm64",
    "natives-freebsd"
)

val lwjglModules = listOf(
    "lwjgl", "lwjgl-assimp", "lwjgl-bgfx", "lwjgl-freetype", "lwjgl-glfw",
    "lwjgl-harfbuzz", "lwjgl-hwloc", "lwjgl-jemalloc", "lwjgl-ktx",
    "lwjgl-llvm", "lwjgl-lmdb", "lwjgl-lz4", "lwjgl-meshoptimizer",
    "lwjgl-mimalloc", "lwjgl-msdfgen", "lwjgl-nanovg", "lwjgl-nfd",
    "lwjgl-nuklear", "lwjgl-openal", "lwjgl-opengl", "lwjgl-opengles",
    "lwjgl-openxr", "lwjgl-opus", "lwjgl-par", "lwjgl-remotery",
    "lwjgl-rpmalloc", "lwjgl-sdl", "lwjgl-shaderc", "lwjgl-spng",
    "lwjgl-spvc", "lwjgl-stb", "lwjgl-tinyexr", "lwjgl-tinyfd",
    "lwjgl-vma", "lwjgl-vulkan", "lwjgl-xxhash", "lwjgl-yoga", "lwjgl-zstd"
)

repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots")
}

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-assimp")
    implementation("org.lwjgl:lwjgl-bgfx")
    implementation("org.lwjgl:lwjgl-egl")
    implementation("org.lwjgl:lwjgl-fmod")
    implementation("org.lwjgl:lwjgl-freetype")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-harfbuzz")
    implementation("org.lwjgl:lwjgl-hwloc")
    implementation("org.lwjgl:lwjgl-jawt")
    implementation("org.lwjgl:lwjgl-jemalloc")
    implementation("org.lwjgl:lwjgl-ktx")
    implementation("org.lwjgl:lwjgl-llvm")
    implementation("org.lwjgl:lwjgl-lmdb")
    implementation("org.lwjgl:lwjgl-lz4")
    implementation("org.lwjgl:lwjgl-meshoptimizer")
    implementation("org.lwjgl:lwjgl-mimalloc")
    implementation("org.lwjgl:lwjgl-msdfgen")
    implementation("org.lwjgl:lwjgl-nanovg")
    implementation("org.lwjgl:lwjgl-nfd")
    implementation("org.lwjgl:lwjgl-nuklear")
    implementation("org.lwjgl:lwjgl-odbc")
    implementation("org.lwjgl:lwjgl-openal")
    implementation("org.lwjgl:lwjgl-opencl")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-opengles")
    implementation("org.lwjgl:lwjgl-openxr")
    implementation("org.lwjgl:lwjgl-opus")
    implementation("org.lwjgl:lwjgl-par")
    implementation("org.lwjgl:lwjgl-remotery")
    implementation("org.lwjgl:lwjgl-renderdoc")
    implementation("org.lwjgl:lwjgl-rpmalloc")
    implementation("org.lwjgl:lwjgl-sdl")
    implementation("org.lwjgl:lwjgl-shaderc")
    implementation("org.lwjgl:lwjgl-spng")
    implementation("org.lwjgl:lwjgl-spvc")
    implementation("org.lwjgl:lwjgl-stb")
    implementation("org.lwjgl:lwjgl-tinyexr")
    implementation("org.lwjgl:lwjgl-tinyfd")
    implementation("org.lwjgl:lwjgl-vma")
    implementation("org.lwjgl:lwjgl-vulkan")
    implementation("org.lwjgl:lwjgl-xxhash")
    implementation("org.lwjgl:lwjgl-yoga")
    implementation("org.lwjgl:lwjgl-zstd")

    platforms.forEach { platform ->
        lwjglModules.forEach { module ->
            // 1. lwjgl-vulkan は macOS系 のみにする（もともとの設定を再現）
            if (module == "lwjgl-vulkan" && platform != "natives-macos" && platform != "natives-macos-arm64") {
                return@forEach
            }

            // 2. エラーログで弾かれた存在しない組み合わせを個別にスキップする
            if (module == "lwjgl-bgfx" && platform == "natives-windows-arm64") return@forEach
            if (module == "lwjgl-ktx" && platform == "natives-windows-x86") return@forEach
            if (module == "lwjgl-openxr" && (platform == "natives-macos" || platform == "natives-macos-arm64")) return@forEach
            if (module == "lwjgl-remotery" && platform == "natives-windows-arm64") return@forEach

            // 問題ない組み合わせだけを安全に適用
            implementation("org.lwjgl:$module::$platform")
        }
    }

    implementation("org.joml:joml:$jomlVersion")
    implementation("org.joml:joml-primitives:${`joml-primitivesVersion`}")
}
