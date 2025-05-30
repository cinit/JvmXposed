plugins {
    `java-library`
}

group = "dev.tmpfs.jvmplant"
version = "1.0-SNAPSHOT"

dependencies {
    compileOnly(libs.jetbrains.annotations)
    implementation(libs.byte.buddy)
    implementation(libs.ow2.asm.asm)
    implementation(libs.ow2.asm.util)
}

// java 8
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
