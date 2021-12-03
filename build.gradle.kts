plugins {
    kotlin("jvm") version "1.6.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    application
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.0-RC")
    api("com.varabyte.konsole:konsole:0.9.0")
}

repositories {
    mavenCentral()
    maven(url = "https://us-central1-maven.pkg.dev/varabyte-repos/public")
}

application {
    mainClass.set("co.q64.cs6068.CS6068FinalProjectKt")
}