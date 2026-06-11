plugins {
    id("fabric-loom")
}

base {
    archivesName.set("geyser-edu-gate-fabric")
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.4")
    mappings("net.fabricmc:yarn:1.21.4+build.8:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.10")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.119.3+1.21.4")

    implementation(project(":common"))
    implementation(project(":auth-service"))
    include(project(":common"))
    include(project(":auth-service"))
    include("org.postgresql:postgresql:42.7.7")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
