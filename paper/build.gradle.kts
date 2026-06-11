plugins {
    java
}

base {
    archivesName.set("geyser-edu-gate-paper")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":auth-service"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) {
                dependency
            } else {
                zipTree(dependency)
            }
        }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}
