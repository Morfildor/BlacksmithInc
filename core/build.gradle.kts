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
    filter { excludeTestsMatching("*ProductionSoakTest") }  // minutes long; it has its own task below
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

// Production-shaped long-save soak (plan 9.4): ./gradlew :core:soak   (tables and the day-1,000 / 2,000 saves land in core/build/soak/)
tasks.register<Test>("soak") {
    group = "tinyblacksmith"
    description = "Plays 2,000 forced-survival days for two smiths and reports save size and End Day time every 100 days."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnit()
    filter { includeTestsMatching("*ProductionSoakTest") }
    maxHeapSize = "4g"
    outputs.upToDateWhen { false }
    testLogging {
        showStandardStreams = true
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
