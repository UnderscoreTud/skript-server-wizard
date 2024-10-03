plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.0"
}

group = "me.tud"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jline:jline:3.27.0")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "me.tud.Main"
    }
}

tasks.test {
    useJUnitPlatform()
}