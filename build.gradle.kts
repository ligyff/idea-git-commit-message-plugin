import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = "com.github.aicommit"
version = "1.1.0"

kotlin {
    jvmToolchain(25)
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1.1")
        bundledPlugin("Git4Idea")
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "261.*"
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaUltimate, "2026.1.1")
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "9.1.0"
        distributionType = Wrapper.DistributionType.BIN
    }
}
