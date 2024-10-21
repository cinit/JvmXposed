plugins {
    `java-library`
}

dependencies {
    implementation(projects.jvm.jvmplantShared)
    implementation(projects.jvm.jvmplantNative)
    implementation(projects.jvm.xposedJvm)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// java 8
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
