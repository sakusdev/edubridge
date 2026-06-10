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

tasks.test {
    useJUnitPlatform()
}
