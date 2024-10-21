plugins {
    `java-library`
}

group = "dev.tmpfs.jvmplant"
version = "1.0-SNAPSHOT"

dependencies {
    api(projects.jvm.jvmplantShared)
    compileOnly(libs.jetbrains.annotations)
}

// java 8
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
