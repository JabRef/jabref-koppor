import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("org.jabref.gradle.module")
    id("java-library")
}

dependencies {
    api(project(":jablib"))

    implementation("org.slf4j:slf4j-api")

    // LSP4J for LSP Server
    implementation("com.github.eclipse:lsp4j")

    // route all requests to java.util.logging to SLF4J (which in turn routes to tinylog)
    testImplementation("org.slf4j:jul-to-slf4j")
}

javaModuleTesting.whitebox(testing.suites["test"]) {
    requires.add("org.junit.jupiter.api")
}

tasks.test {
    testLogging {
        // set options for log level LIFECYCLE
        events("FAILED")
        exceptionFormat = TestExceptionFormat.FULL
    }
    maxParallelForks = 1
}
