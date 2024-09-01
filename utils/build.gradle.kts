import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("maven-publish")
}

group = "com.jason.selector"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.okhttp)
    api(libs.disklrucache)
    implementation("org.json:json:20220320")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val libVersion = "1.1.9"

tasks.withType<Jar> {
    archiveBaseName.set("selector") // 设置构件的基本名称
    archiveVersion.set(libVersion) // 设置构件的版本
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8) // 设置 JVM 目标版本
    }
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                url = uri("https://packages.aliyun.com/605d421bb209386279b71e16/maven/2089042-release-AXyFkY")
                credentials {
                    username = "605d41f54639bfa6eb015d4d"
                    password = "Sl2lfKXdde83"
                }
            }
        }
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                groupId = "com.jason"
                artifactId = "network"
                version = libVersion
                System.out.println("implementation(\"$groupId:$artifactId:$version\")")
            }
        }
    }
}