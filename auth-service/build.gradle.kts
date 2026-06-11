plugins {
    application
}

dependencies {
    implementation(project(":common"))
    implementation("org.postgresql:postgresql:42.7.7")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

application {
    mainClass.set("dev.sakus.geyseredu.authservice.AuthServiceMain")
    applicationName = "geyser-edu-auth-service"
}

base {
    archivesName.set("geyser-edu-auth-service")
}

val standaloneJar by tasks.registering(Jar::class) {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Builds a single executable auth-service jar with runtime dependencies."
    archiveClassifier.set("standalone")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
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

tasks.assemble {
    dependsOn(standaloneJar)
}

tasks.test {
    useJUnitPlatform()
}
