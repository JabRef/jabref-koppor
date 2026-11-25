package org.jabref.gradle

import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import javax.inject.Inject

/**
 * Builds JavaFX from an [openjdk/jfx](https://github.com/openjdk/jfx) pull request, publishes it to `~/.m2`,
 * and returns the published version. Enabled by `-PjavafxPr=<PR link or number>` (see `versions/build.gradle.kts`).
 *
 * The PR head is fetched on every build; JavaFX is only rebuilt when the head commit changed.
 */
abstract class JavaFxFromPr : ValueSource<String, JavaFxFromPr.Params> {
    interface Params : ValueSourceParameters {
        /** PR link (`https://github.com/openjdk/jfx/pull/2369`) or number (`2369`). */
        val pr: Property<String>

        /** JDK used to run the JavaFX build. */
        val javaHome: Property<String>
    }

    @get:Inject
    abstract val exec: ExecOperations

    override fun obtain(): String {
        val pr = parameters.pr.get().trim()
        val number = Regex("""^(?:https://github\.com/openjdk/jfx/pull/)?(\d+)(?:/.*)?$""").find(pr)?.groupValues?.get(1)
            ?: throw GradleException("Invalid value '$pr' for property 'javafxPr'. Expected a link like https://github.com/openjdk/jfx/pull/2369 or a PR number.")
        val dir = File(System.getProperty("java.io.tmpdir"), "jabref-jfx-pr-$number")
        if (!File(dir, ".git").isDirectory) {
            dir.mkdirs()
            run(dir, "git", "init", "-q")
        }
        run(dir, "git", "fetch", "--depth", "1", "https://github.com/openjdk/jfx.git", "pull/$number/head")
        run(dir, "git", "checkout", "-q", "--force", "FETCH_HEAD")
        val sha = run(dir, "git", "rev-parse", "--short", "HEAD")
        val major = Properties().apply { File(dir, "build.properties").reader().use { load(it) } }
            .getProperty("jfx.release.major.version")
        val version = "$major-pr$number-$sha"

        val marker = File(dir, "build/jabref-published-$version")
        val published = File(System.getProperty("user.home"), ".m2/repository/org/openjfx/javafx-controls/$version")
        if (!marker.exists() || !published.isDirectory) {
            val gradlew = if (System.getProperty("os.name").startsWith("Windows")) listOf("cmd", "/c", "gradlew.bat") else listOf("sh", "gradlew")
            exec.exec {
                workingDir = dir
                environment("JAVA_HOME", parameters.javaHome.get())
                commandLine(gradlew + listOf("-PMAVEN_PUBLISH=true", "-PMAVEN_VERSION=$version", "publishToMavenLocal"))
            }
            marker.createNewFile()
        }
        return version
    }

    private fun run(dir: File, vararg command: String): String {
        val out = ByteArrayOutputStream()
        exec.exec {
            workingDir = dir
            commandLine(*command)
            standardOutput = out
        }
        return out.toString().trim()
    }
}
