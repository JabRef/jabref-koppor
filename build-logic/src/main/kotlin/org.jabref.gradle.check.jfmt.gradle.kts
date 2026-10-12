import org.gradle.jvm.toolchain.JavaLauncher

// Formats Java sources with jfmt (https://github.com/Netflix/jfmt).
//
// jfmt has a single built-in style (4-space blocks, 8-space continuation, structural line breaks, Javadoc
// wrapped at 80 columns) and does not read any configuration. Its import normalization (unused import
// removal, wildcard expansion, shortening of qualified type names) is type-aware: javac resolves the
// declarations, so the compile class path of every source set is passed along.
//
// Tasks:
// - jfmt      rewrites the sources in place
// - jfmtCheck fails if any source would be rewritten (wired into `check`)

plugins {
    id("java")
}

val jfmtVersion = "0.8.4"

val jfmt: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    jfmt("com.netflix:com.netflix.tools.jfmt:$jfmtVersion")
    // jfmt relocates commonmark-java to com.netflix.tools.jfmt.internal.org.commonmark, but the relocated
    // Html5Entities still loads "/org/commonmark/internal/util/entities.txt" by its original absolute path.
    // Any `&lt;`-like entity in a Markdown Javadoc (///) comment then crashes jfmt with an
    // ExceptionInInitializerError. The original commonmark jar provides the resource at that path.
    jfmt("org.commonmark:commonmark:0.28.0")
}

// jfmt uses javac's internal packages. When launched on the class path, they have to be exported to the unnamed module.
val javacExports = listOf("api", "code", "file", "model", "parser", "tree", "util")
    .map { "--add-exports=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED" }

val launcher: Provider<JavaLauncher> = javaToolchains.launcherFor(java.toolchain)

fun registerJfmtTask(name: String, check: Boolean) = tasks.register<JavaExec>(name) {
    group = if (check) "verification" else "formatting"
    description = if (check) "Checks Java formatting with jfmt." else "Formats Java sources with jfmt."

    javaLauncher = launcher
    classpath = jfmt
    mainClass = "com.netflix.tools.jfmt.Jfmt"
    jvmArgs(javacExports)

    // Generated sources (e.g., ANTLR output below the build directory) are not formatted.
    val buildDirectory = layout.buildDirectory.get().asFile
    val javaSources = sourceSets.flatMap { sourceSet ->
        sourceSet.allJava.srcDirs
            .filter { !it.startsWith(buildDirectory) }
            .map { srcDir ->
                fileTree(srcDir) {
                    include("**/*.java")
                    // Submodule content is checked in its own repository.
                    exclude("**/themes.jabref.org/**")
                }
            }
    }
    // The compile class paths resolve the types referenced in declarations (needed for import normalization).
    val compileClasspaths = sourceSets.map { it.compileClasspath }

    inputs.files(javaSources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(compileClasspaths).withNormalizer(ClasspathNormalizer::class)
    if (check) {
        // Nothing is written; declaring an output makes the task cacheable and up-to-date aware.
        outputs.file(layout.buildDirectory.file("jfmt/check.marker"))
    }
    outputs.cacheIf { check }

    val argumentFile = layout.buildDirectory.file("jfmt/$name.args")
    val checkMarker = layout.buildDirectory.file("jfmt/check.marker")
    val pathSeparator = File.pathSeparator
    val version = jfmtVersion

    // javac-style argument files: quote arguments containing whitespace, backslashes, or quotes.
    val quote: (String) -> String = { argument ->
        if (argument.any { it.isWhitespace() || it == '\\' || it == '"' }) {
            "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            argument
        }
    }

    doFirst {
        val files = javaSources.flatMap { it.files }.sorted()
        val classpath = compileClasspaths.flatMap { it.files }.distinct().joinToString(pathSeparator)
        val lines = mutableListOf<String>()
        if (check) {
            lines += "--check"
        }
        if (classpath.isNotEmpty()) {
            lines += "--class-path"
            lines += quote(classpath)
        }
        files.forEach { lines += quote(it.absolutePath) }
        val file = argumentFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(lines.joinToString("\n"))
        args("@${file.absolutePath}")
        logger.info("jfmt: ${files.size} files")
    }
    if (check) {
        doLast {
            checkMarker.get().asFile.writeText(version)
        }
    }
}

registerJfmtTask("jfmt", check = false)
val jfmtCheck = registerJfmtTask("jfmtCheck", check = true)

tasks.named("check") {
    dependsOn(jfmtCheck)
}
