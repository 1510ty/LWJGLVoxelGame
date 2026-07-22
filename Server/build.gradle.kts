plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.mc1510ty"

repositories {
    mavenCentral()
}

tasks{
    shadowJar {
        archiveFileName.set("LWJGLVoxelGame-Server-${project.version}.jar")
        manifest {
            attributes["Main-Class"] = "com.mc1510ty.LWJGLVoxelGame.Server.Main"
        }
    }
}