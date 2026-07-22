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

val lwjglNatives = Pair(
    System.getProperty("os.name")!!,
    System.getProperty("os.arch")!!
).let { (name, arch) ->
    when {
        "FreeBSD".equals(name)                                    ->
            "natives-freebsd"
        arrayOf("Linux", "SunOS", "Unit").any { name.startsWith(it) } ->
            if (arrayOf("arm", "aarch64").any { arch.startsWith(it) })
                "natives-linux${if (arch.contains("64") || arch.startsWith("armv8")) "-arm64" else "-arm32"}"
            else if (arch.startsWith("ppc"))
                "natives-linux-ppc64le"
            else if (arch.startsWith("riscv"))
                "natives-linux-riscv64"
            else
                "natives-linux"
        arrayOf("Mac OS X", "Darwin").any { name.startsWith(it) }     ->
            "natives-macos${if (arch.startsWith("aarch64")) "-arm64" else ""}"
        arrayOf("Windows").any { name.startsWith(it) }                ->
            if (arch.contains("64"))
                "natives-windows${if (arch.startsWith("aarch64")) "-arm64" else ""}"
            else
                "natives-windows-x86"
        else                                                                            ->
            throw Error("Unrecognized or unsupported platform. Please set \"lwjglNatives\" manually")
    }
}


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
    implementation("org.lwjgl:lwjgl::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-assimp::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-bgfx::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-freetype::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-harfbuzz::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-hwloc::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-jemalloc::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-ktx::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-llvm::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-lmdb::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-lz4::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-meshoptimizer::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-mimalloc::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-msdfgen::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-nanovg::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-nfd::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-nuklear::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-openal::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-opengles::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-openxr::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-opus::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-par::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-remotery::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-rpmalloc::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-sdl::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-shaderc::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-spng::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-spvc::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-stb::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-tinyexr::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-tinyfd::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-vma::$lwjglNatives")
    if (lwjglNatives == "natives-macos" || lwjglNatives == "natives-macos-arm64") implementation("org.lwjgl:lwjgl-vulkan::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-xxhash::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-yoga::$lwjglNatives")
    implementation("org.lwjgl:lwjgl-zstd::$lwjglNatives")
    implementation("org.joml:joml:$jomlVersion")
    implementation("org.joml:joml-primitives:${`joml-primitivesVersion`}")
}
