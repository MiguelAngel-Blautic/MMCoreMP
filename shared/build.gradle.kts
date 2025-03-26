import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCocoapods)
    kotlin("plugin.serialization") version "1.9.0"
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("maven-publish")
}

kotlin {
    tasks.withType<Copy>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    tasks.register("copyResourcesToIOSBundle") {
        doLast {
            val resourcesDir = file("src/iosMain/resources")
            val outputDir = file("$buildDir/ios/iosMain/resources") // Esto es donde se debe copiar los recursos

            // Copia los archivos al directorio de recursos del bundle de iOS
            copy {
                from(resourcesDir)
                into(outputDir)
            }
        }
    }
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
        publishLibraryVariants("release")
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "16.0"
        framework {
            baseName = "shared"
            isStatic = true
        }
        pod("MediaPipeTasksVision")
    }
    sourceSets {
        iosMain {
            resources.srcDirs("resources")
            resources.srcDirs("./resources")
            resources.srcDirs("./iosMain/resources")
            resources.srcDirs("./src/iosMain/resources")
            resources.srcDirs("./shared/src/iosMain/resources")
            resources.srcDirs("iosMain/resources")
            resources.srcDirs("src/iosMain/resources")
            resources.srcDirs("shared/src/iosMain/resources")
        }
        commonMain.dependencies {
            implementation(libs.tasks.vision)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.cmp.bluetooth.manager)
            implementation(libs.kotlinx.serialization.json)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }
        androidMain.dependencies {
            implementation(libs.tensorflow.lite)
            implementation(libs.tensorflow.lite.gpu)
            implementation(libs.okhttp)
            implementation(libs.tasks.vision)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.camera.view)
            implementation(libs.junit)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
    dependencies {
        implementation(libs.androidx.core.ktx)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.datetime)
        implementation(libs.ktor.client.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.tasks.vision)
    }
}
android {
    namespace = "com.blautic.mmcore"
    compileSdk = 35
    defaultConfig {
        minSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
dependencies {
    implementation(libs.androidx.lifecycle.common.jvm)
    implementation(libs.androidx.runtime.android)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
}
tasks.register<Jar>("jarWithResources") {
    from("assets")  // Asegúrate de que la carpeta sea correcta

    archiveBaseName.set("shared")  // Nombre de tu archivo .jar
    archiveVersion.set("1.0.27")  // Versión del archivo .jar
    archiveClassifier.set("resources")  // Añadir un classifier para los recursos
}

publishing {
    repositories {
        mavenLocal() // Para publicar en local
        mavenCentral()
    }
    publications {
        create<MavenPublication>("release") {
            from(components["kotlin"])
            artifact(tasks["jarWithResources"])
            groupId = "com.blautic"      // ← Nombre del paquete (group)
            artifactId = "mmcore"      // ← Nombre de la librería
            version = "1.0.28"               // ← Versión actual
        }
    }
}