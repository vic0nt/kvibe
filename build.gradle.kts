plugins {
    `java-library`
    jacoco
    alias(libs.plugins.spotless)
}

group = "kvibe"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.jqwik)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("slow")
    }
}

val slowTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs crash and long-running concurrency tests tagged 'slow'."
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform {
        includeTags("slow")
    }
    shouldRunAfter(tasks.test)
}

val crashTestExtended by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the TR-3 crash test with 500+ iterations (Definition of Done, section 10). " +
        "Local only, not part of CI: too slow to run on every push."
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform {
        includeTags("slow")
    }
    filter {
        includeTestsMatching("kvibe.crash.KvibeStoreCrashTest")
    }
    systemProperty("kvibe.crashTest.iterations", "500")
    shouldRunAfter(tasks.test)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

spotless {
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat(libs.versions.palantirJavaFormat.get())
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
