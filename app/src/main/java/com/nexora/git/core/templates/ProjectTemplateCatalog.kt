package com.nexora.git.core.templates

import javax.inject.Inject
import javax.inject.Singleton

data class ProjectTemplateSummary(
    val id: String,
    val name: String,
    val description: String,
)

data class RenderedProjectTemplate(
    val projectName: String,
    val files: Map<String, String>,
)

@Singleton
class ProjectTemplateCatalog @Inject constructor() {

    val templates: List<ProjectTemplateSummary> = DEFINITIONS.map {
        ProjectTemplateSummary(
            id = it.id,
            name = it.name,
            description = it.description,
        )
    }

    fun render(
        templateId: String,
        projectName: String,
    ): RenderedProjectTemplate {
        val definition = DEFINITIONS.firstOrNull {
            it.id == templateId
        } ?: throw IllegalArgumentException(
            "Unknown project template.",
        )

        val cleanName = sanitizeProjectName(projectName)
        val packageName = cleanName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "")
            .ifBlank { "nexoraapp" }
            .let {
                if (it.first().isDigit()) {
                    "app$it"
                } else {
                    it
                }
            }

        val values = mapOf(
            "{{PROJECT_NAME}}" to cleanName,
            "{{PACKAGE_NAME}}" to packageName,
        )

        fun substitute(input: String): String =
            values.entries.fold(input) { current, (key, value) ->
                current.replace(key, value)
            }

        val rendered = definition.files.entries.associate {
            (path, content) ->
            substitute(path) to substitute(content)
        }

        return RenderedProjectTemplate(
            projectName = cleanName,
            files = rendered,
        )
    }

    private fun sanitizeProjectName(value: String): String {
        val name = value.trim()
        require(name.isNotBlank()) {
            "Project name is required."
        }
        require(name.length <= 64) {
            "Project name must be 64 characters or fewer."
        }
        require(name.none { it == '/' || it == '\\' }) {
            "Project name cannot contain path separators."
        }
        require(name.first().isLetterOrDigit()) {
            "Project name must start with a letter or number."
        }
        require(
            name.all {
                it.isLetterOrDigit() ||
                    it == ' ' ||
                    it == '-' ||
                    it == '_' ||
                    it == '.'
            },
        ) {
            "Project name contains unsupported characters."
        }
        require(name != "." && name != "..") {
            "Invalid project name."
        }
        return name
    }

    private data class Definition(
        val id: String,
        val name: String,
        val description: String,
        val files: Map<String, String>,
    )

    private companion object {
        val DEFINITIONS = listOf(
            Definition(
                id = "kotlin-cli",
                name = "Kotlin CLI",
                description = "Small Kotlin/JVM Gradle application.",
                files = mapOf(
                    "settings.gradle.kts" to
                        "rootProject.name = \"{{PROJECT_NAME}}\"\n",
                    "build.gradle.kts" to """
                        plugins {
                            kotlin("jvm") version "2.2.10"
                            application
                        }

                        repositories {
                            mavenCentral()
                        }

                        application {
                            mainClass.set("MainKt")
                        }
                    """.trimIndent() + "\n",
                    "src/main/kotlin/Main.kt" to """
                        fun main() {
                            println("Hello from {{PROJECT_NAME}}")
                        }
                    """.trimIndent() + "\n",
                    ".gitignore" to ".gradle/\nbuild/\n.idea/\n",
                    "README.md" to
                        "# {{PROJECT_NAME}}\n\nKotlin CLI project created with Nexora Git.\n",
                ),
            ),
            Definition(
                id = "android-compose",
                name = "Android Compose",
                description = "Minimal Android app with Kotlin and Compose.",
                files = mapOf(
                    "settings.gradle.kts" to """
                        pluginManagement {
                            repositories {
                                google()
                                mavenCentral()
                                gradlePluginPortal()
                            }
                        }
                        dependencyResolutionManagement {
                            repositoriesMode.set(
                                RepositoriesMode.FAIL_ON_PROJECT_REPOS,
                            )
                            repositories {
                                google()
                                mavenCentral()
                            }
                        }
                        rootProject.name = "{{PROJECT_NAME}}"
                        include(":app")
                    """.trimIndent() + "\n",
                    "build.gradle.kts" to """
                        plugins {
                            id("com.android.application") version "9.4.0" apply false
                            id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
                        }
                    """.trimIndent() + "\n",
                    "app/build.gradle.kts" to """
                        plugins {
                            id("com.android.application")
                            id("org.jetbrains.kotlin.plugin.compose")
                        }

                        android {
                            namespace = "com.nexora.{{PACKAGE_NAME}}"
                            compileSdk = 37
                            defaultConfig {
                                applicationId = "com.nexora.{{PACKAGE_NAME}}"
                                minSdk = 26
                                targetSdk = 36
                                versionCode = 1
                                versionName = "1.0.0"
                            }
                            buildFeatures {
                                compose = true
                            }
                        }

                        dependencies {
                            implementation(platform("androidx.compose:compose-bom:2026.09.00"))
                            implementation("androidx.activity:activity-compose:1.12.0")
                            implementation("androidx.compose.material3:material3")
                        }
                    """.trimIndent() + "\n",
                    "app/src/main/AndroidManifest.xml" to """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                            <application
                                android:label="{{PROJECT_NAME}}"
                                android:theme="@style/Theme.NexoraGenerated">
                                <activity
                                    android:name=".MainActivity"
                                    android:exported="true">
                                    <intent-filter>
                                        <action android:name="android.intent.action.MAIN" />
                                        <category android:name="android.intent.category.LAUNCHER" />
                                    </intent-filter>
                                </activity>
                            </application>
                        </manifest>
                    """.trimIndent() + "\n",
                    "app/src/main/res/values/themes.xml" to """
                        <resources>
                            <style
                                name="Theme.NexoraGenerated"
                                parent="android:style/Theme.Material.Light.NoActionBar" />
                        </resources>
                    """.trimIndent() + "\n",
                    "app/src/main/java/com/nexora/{{PACKAGE_NAME}}/MainActivity.kt" to """
                        package com.nexora.{{PACKAGE_NAME}}

                        import android.os.Bundle
                        import androidx.activity.ComponentActivity
                        import androidx.activity.compose.setContent
                        import androidx.compose.material3.Text

                        class MainActivity : ComponentActivity() {
                            override fun onCreate(savedInstanceState: Bundle?) {
                                super.onCreate(savedInstanceState)
                                setContent {
                                    Text("{{PROJECT_NAME}}")
                                }
                            }
                        }
                    """.trimIndent() + "\n",
                    ".gitignore" to
                        ".gradle/\n.idea/\nlocal.properties\n**/build/\n",
                    "README.md" to
                        "# {{PROJECT_NAME}}\n\nAndroid Compose project created with Nexora Git.\n",
                ),
            ),
            Definition(
                id = "typescript-node",
                name = "Node + TypeScript",
                description = "TypeScript CLI project for Node.js.",
                files = mapOf(
                    "package.json" to """
                        {
                          "name": "{{PACKAGE_NAME}}",
                          "private": true,
                          "version": "0.1.0",
                          "type": "module",
                          "scripts": {
                            "build": "tsc",
                            "start": "node dist/index.js"
                          },
                          "devDependencies": {
                            "typescript": "^5.9.0"
                          }
                        }
                    """.trimIndent() + "\n",
                    "tsconfig.json" to """
                        {
                          "compilerOptions": {
                            "target": "ES2022",
                            "module": "NodeNext",
                            "moduleResolution": "NodeNext",
                            "outDir": "dist",
                            "strict": true
                          },
                          "include": ["src/**/*.ts"]
                        }
                    """.trimIndent() + "\n",
                    "src/index.ts" to
                        "console.log(\"Hello from {{PROJECT_NAME}}\");\n",
                    ".gitignore" to "node_modules/\ndist/\n.env\n",
                    "README.md" to
                        "# {{PROJECT_NAME}}\n\nNode + TypeScript project created with Nexora Git.\n",
                ),
            ),
            Definition(
                id = "python-cli",
                name = "Python CLI",
                description = "Modern Python package with a CLI entry point.",
                files = mapOf(
                    "pyproject.toml" to """
                        [project]
                        name = "{{PACKAGE_NAME}}"
                        version = "0.1.0"
                        requires-python = ">=3.11"

                        [project.scripts]
                        {{PACKAGE_NAME}} = "{{PACKAGE_NAME}}.__main__:main"
                    """.trimIndent() + "\n",
                    "src/{{PACKAGE_NAME}}/__init__.py" to
                        "__version__ = \"0.1.0\"\n",
                    "src/{{PACKAGE_NAME}}/__main__.py" to """
                        def main() -> None:
                            print("Hello from {{PROJECT_NAME}}")


                        if __name__ == "__main__":
                            main()
                    """.trimIndent() + "\n",
                    ".gitignore" to
                        ".venv/\n__pycache__/\n*.pyc\n.env\n",
                    "README.md" to
                        "# {{PROJECT_NAME}}\n\nPython CLI project created with Nexora Git.\n",
                ),
            ),
        )
    }
}
