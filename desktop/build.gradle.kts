import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        kotlin.srcDirs("src/jvmMain/kotlin", "src/main/kotlin")
        resources.srcDirs("src/jvmMain/resources", "src/main/resources")
    }
}

dependencies {
    implementation(project(":core"))

    val composeVersion = "1.7.3"
    implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
    implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
    implementation("org.jetbrains.compose.ui:ui:$composeVersion")
    implementation("org.jetbrains.compose.ui:ui-tooling-preview:$composeVersion")
    implementation("org.jetbrains.compose.material3:material3:$composeVersion")
    implementation("org.jetbrains.compose.material:material-icons-extended:$composeVersion")
    implementation("org.jetbrains.compose.desktop:desktop:$composeVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
}

compose.desktop {
    application {
        mainClass = "com.xhsdn.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "xhsdn"
            packageVersion = "1.0.0"
            modules("jdk.crypto.ec", "jdk.httpserver")
            description = "XHS Downloader - Windows 桌面版"
        }
    }
}
