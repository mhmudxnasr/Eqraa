import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.compose.compiler) apply false
}

subprojects {
    if (name != "test-app") {
        apply(plugin = "org.jetbrains.dokka")
    }
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    ktlint {
        android.set(true)
    }
}

tasks.register("cleanDocs", Delete::class).configure {
    delete("${project.rootDir}/docs/readium", "${project.rootDir}/docs/index.md", "${project.rootDir}/site")
}

tasks.withType<DokkaTaskPartial>().configureEach {
    dokkaSourceSets {
        configureEach {
            reportUndocumented.set(false)
            skipEmptyPackages.set(false)
            skipDeprecated.set(true)
        }
    }
}

tasks.named<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>("dokkaGfmMultiModule").configure {
    outputDirectory.set(file("${projectDir.path}/docs"))
}
