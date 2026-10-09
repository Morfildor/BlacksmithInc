plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        allWarningsAsErrors.set(false)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    systemProperty("golden.record", System.getProperty("golden.record") ?: "false")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Headless simulator: ./gradlew :core:simulate --args="--runs 1000 --seed 1"
tasks.register<JavaExec>("simulate") {
    group = "tinyblacksmith"
    description = "Runs the headless seeded vertical-slice simulator."
    mainClass.set("com.tinyblacksmith.core.sim.SimulatorKt")
    classpath = sourceSets["main"].runtimeClasspath
}
