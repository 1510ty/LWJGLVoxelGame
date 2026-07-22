import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

val gitCommit: String? by project
val buildTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))

version = if (!gitCommit.isNullOrBlank()) {
    "$buildTime-$gitCommit"
} else {
    "$buildTime"
}


group = "com.mc1510ty"

subprojects {
    version = rootProject.version

    apply(plugin = "java")
    apply(plugin = "java-library")

}