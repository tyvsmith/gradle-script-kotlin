package org.gradle.script.lang.kotlin.integration

import org.gradle.script.lang.kotlin.integration.fixture.DeepThought
import org.gradle.script.lang.kotlin.support.KotlinBuildScriptModel
import org.gradle.script.lang.kotlin.support.zipTo

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import org.gradle.tooling.GradleConnector

import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.io.File

class GradleScriptKotlinIntegrationTest {

    @JvmField
    @Rule val projectDir = TemporaryFolder()

    @Test
    fun `given a script with SAM conversions, it can run it`() {
        withBuildScript("""
            import org.gradle.api.tasks.bundling.Zip
            import java.util.concurrent.Callable

            configurations.create("compile")

            dependencies { compile(gradleApi()) }

            task<Zip>("repackage") {
                baseName = "gradle-api"
                from(Callable {
                    val files = configurations.getByName("compile").files
                    zipTree(files.single { it.name.startsWith(baseName) })
                })
            }
        """)
        assert(
            build("tasks").output.contains("repackage"))
    }

    @Test
    fun `given a buildscript block, it will be used to compute the runtime classpath`() {

        withClassJar("fixture.jar", DeepThought::class.java)

        withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("fixture.jar"))
                }
            }

            task("answer") {
                doLast {
                    // resources.jar should be in the classpath
                    val computer = ${DeepThought::class.qualifiedName}()
                    val answer = computer.compute()
                    println("*" + answer + "*")
                }
            }
        """)

        assert(
            build("answer").output.contains("*42*"))

        assertThat(
            kotlinBuildScriptModel().classPath.map { it.name },
            hasItem("fixture.jar"))
    }

    private fun withBuildScript(script: String) {
        withFile("settings.gradle", "rootProject.buildFileName = 'build.gradle.kts'")
        withFile("build.gradle.kts", script)
    }

    private fun withFile(fileName: String, text: String) {
        file(fileName).writeText(text)
    }

    private fun withClassJar(fileName: String, vararg classes: Class<*>) =
        zipTo(
            file(fileName),
            classes.asSequence().map {
                val classFilePath = it.name.replace('.', '/') + ".class"
                classFilePath to it.getResource("/$classFilePath").readBytes()
            })

    private fun file(fileName: String) =
        projectDir.newFile(fileName)

    private fun build(vararg arguments: String): BuildResult =
        gradleRunner()
            .withArguments(*arguments, "--stacktrace")
            .build()

    private fun gradleRunner() =
        GradleRunner
            .create()
            .withDebug(false)
            .withGradleInstallation(customInstallation())
            .withProjectDir(projectDir.root)

    private fun kotlinBuildScriptModel(): KotlinBuildScriptModel =
        GradleConnector
            .newConnector()
            .useInstallation(customInstallation())
            .forProjectDirectory(projectDir.root)
            .connect()
            .getModel(KotlinBuildScriptModel::class.java)

    private fun customInstallation() =
        File("build/custom").listFiles()?.let {
            it.singleOrNull() ?:
                throw IllegalStateException(
                    "Expected 1 custom installation but found ${it.size}. Run `./gradlew clean customInstallation`.")
        } ?: throw IllegalStateException("Custom installation not found. Run `./gradlew customInstallation`.")

}
