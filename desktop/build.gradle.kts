import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipFile
import java.util.zip.ZipEntry

// 必须放文件顶层，dependencies 块里的局部 val 在 afterEvaluate 闭包内访问不到
val skikoVersion = "0.8.18"

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

            // Compose 插件的 createDistributable task 会预创建 app/xhsdn/ 空目录,
            // jpackage 拒绝写入非空(其实是任何已存在)的目标目录。
            // 先清掉,再让 jpackage 重建。
            val targetApp = appDir.get().asFile
            targetApp.deleteRecursively()

            jar.get().asFile.copyTo(File(s, jar.get().asFile.name))

            // skiko-awt-runtime 这个 jar 只在开发期把 skiko-windows-x64.dll 抽到 classpath,
            // createRuntimeImage 不会把 native 资源带进 jpackage runtime image。
            // 结果: 启动时 Skiko 找不到 dll → "Failed to launch JVM"。
            // 解决: 在 staging 里把 dll 摆好,jpackage 把它打进了 app/ 目录,
            //       Dskiko.library.path=$APPDIR 就能找到。
            // 绕开 kts 闭包类型推断陷阱 —— skiko jar 路径从 gradle 缓存里直接查。
            // 优点: 不依赖 ResolvedDependency / Configuration 的 lambda API,
            //       不会触发它"Cannot infer type"的怪报错。
            // 缺点: 跟 dependencies 里的 skikoVersion 手动保持一致。
            val gradleUserHome: File = gradle.gradleUserHomeDir
            val skikoCacheDir = File(
                gradleUserHome,
                "caches/modules-2/files-2.1/org.jetbrains.skiko/skiko-awt-runtime-windows-x64/${skikoVersion}/"
            )
            val skikoJarFile: File? = skikoCacheDir
                .walkTopDown()
                .firstOrNull { f -> f.isFile && f.name == "skiko-awt-runtime-windows-x64-${skikoVersion}.jar" }
            requireNotNull(skikoJarFile) {
                "未在 gradle 缓存找到 skiko-awt-runtime-windows-x64-${skikoVersion}.jar：${skikoCacheDir.absolutePath}"
            }

            val targetDll = File(s, "skiko-windows-x64.dll")
            val zip = ZipFile(skikoJarFile!!)
            val entry: ZipEntry? = zip.getEntry("skiko-windows-x64.dll")
            requireNotNull(entry) { "skiko-awt-runtime jar 内没有 skiko-windows-x64.dll" }
            zip.getInputStream(entry).use { input ->
                targetDll.outputStream().use { output -> input.copyTo(output) }
            }
            zip.close()
            require(targetDll.exists() && targetDll.length() > 0) { "skiko dll 抽不出来: ${targetDll.absolutePath}" }
            logger.lifecycle("xhsPackageExe: 已抽出 skiko-windows-x64.dll (${targetDll.length() / 1024} KB) 到 ${targetDll.absolutePath}")

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
