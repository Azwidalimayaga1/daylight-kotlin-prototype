plugins { kotlin("jvm"); application }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
application { mainClass.set("com.daylight.server.ServerKt") }
dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
