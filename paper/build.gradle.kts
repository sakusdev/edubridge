plugins {
    java
}

base {
    archivesName.set("geyser-edu-gate-paper")
}

dependencies {
    implementation(project(":common"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}
