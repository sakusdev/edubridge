plugins {
    java
    id("fabric-loom") version "1.10-SNAPSHOT" apply false
}

group = "dev.sakus.geyseredu"
version = "0.1.4"

subprojects {
    group = rootProject.group
    version = rootProject.version

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(21)
        }
    }
}
