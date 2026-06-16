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
    // skiko-awt-runtime 负责把 skiko-windows-x64.dll 注入到 classpath；
    // 否则 Compose 启动时找不到 native 库会抛 LibraryLoadException
    val skikoVersion = "0.8.18"
    implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:$skikoVersion")

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

// Compose Desktop 1.7.3 在打包前会清空 build/compose/tmp/createDistributable/libs/
// 但后续 jpackage 又从这个目录读 jar，导致 "Input length = 1" 失败。
// 直接接管：用我们自己的 Exec task 调 jpackage，绕开 Compose 插件的 staging。
afterEvaluate {
    val packageExe = tasks.register<Exec>("xhsPackageExe") {
        group = "compose desktop"
        description = "使用 jpackage 直接打包 Windows .exe（绕过 Compose Desktop 1.7.3 staging bug）"
        dependsOn("jar", "createRuntimeImage")

        val stagingDir = layout.buildDirectory.dir("compose/staging/xhsdn")
        val appDir = layout.buildDirectory.dir("compose/binaries/main/app/xhsdn")
        val runtimeDir = layout.buildDirectory.dir("compose/tmp/main/runtime")
        val jar = layout.buildDirectory.file("libs/desktop.jar")

        inputs.dir(runtimeDir).withPropertyName("runtimeDir")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(jar).withPropertyName("jar")
            .withPathSensitivity(PathSensitivity.NONE)
        outputs.dir(appDir).withPropertyName("appDir")

        doFirst {
            val s = stagingDir.get().asFile
            s.deleteRecursively()
            s.mkdirs()
            jar.get().asFile.copyTo(File(s, jar.get().asFile.name))

            val javaHome = System.getenv("JAVA_HOME")
                ?: error("JAVA_HOME must be set before running this task")
            val jpackage = File(javaHome, "bin/jpackage.exe")
            require(jpackage.exists()) { "jpackage.exe not found at $jpackage" }

            setExecutable(jpackage.absolutePath as Any)
            setArgs(listOf(
                "--input", s.absolutePath,
                "--runtime-image", runtimeDir.get().asFile.absolutePath,
                "--main-jar", jar.get().asFile.name,
                "--main-class", "com.xhsdn.desktop.MainKt",
                "--type", "app-image",
                "--dest", layout.buildDirectory.dir("compose/binaries/main/app").get().asFile.absolutePath,
                "--name", "xhsdn",
                "--description", "XHS Downloader - Windows 桌面版",
                "--app-version", "1.0.0",
                "--java-options", "-Dcompose.application.resources.dir=\$APPDIR\\resources",
                "--java-options", "-Dcompose.application.configure.swing.globals=true",
                "--java-options", "-Dskiko.library.path=\$APPDIR",
                "--vendor", "hd-pyy",
            ))
        }
    }

    // 替换默认 createDistributable：把它指向我们自己的 task
    tasks.named("createDistributable") { dependsOn(packageExe) }
}
