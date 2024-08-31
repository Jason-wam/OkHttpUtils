plugins {
    kotlin("jvm") version "2.0.0"
}

group = "com.jason.selector"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(project(":utils"))
    implementation("org.json:json:20220320")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}