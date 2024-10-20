plugins {
    `java-library`
}

dependencies {
    implementation(projects.jvm.jvmplantShared)
    implementation(projects.jvm.jvmplantNative)
    implementation(projects.jvm.xposedJvm)
    compileOnly(libs.jetbrains.annotations)
}

// java 8
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
