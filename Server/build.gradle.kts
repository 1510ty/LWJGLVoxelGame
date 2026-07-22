//        LWJGLVoxelGame
//        Copyright (C) 2026  1510ty
//
//        This program is free software: you can redistribute it and/or modify
//        it under the terms of the GNU General Public License as published by
//        the Free Software Foundation, either version 3 of the License, or
//        (at your option) any later version.
//
//        This program is distributed in the hope that it will be useful,
//        but WITHOUT ANY WARRANTY; without even the implied warranty of
//        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//        GNU General Public License for more details.
//
//        You should have received a copy of the GNU General Public License
//        along with this program.  If not, see <https://www.gnu.org/licenses/>.
plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.mc1510ty"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
}

tasks{
    shadowJar {
        archiveFileName.set("LWJGLVoxelGame-Server-${project.version}.jar")
        manifest {
            attributes["Main-Class"] = "com.mc1510ty.LWJGLVoxelGame.Server.Main"
        }
    }
}