plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    // Project "app" depends on project "utils". (Project paths are separated with ":", so ":utils" refers to the top-level "utils" project.)
    implementation(project(":utils"))

    implementation("io.lettuce:lettuce-core:7.5.1.RELEASE")
    implementation("com.googlecode.lanterna:lanterna:3.1.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("com.typesafe:config:1.4.3")
    implementation("com.google.code.gson:gson:2.11.0")

    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.32")

    testImplementation(kotlin("test"))
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "io.github.eyuppastirmaci.dioptra.AppKt"
}
