plugins {
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.apache.guacamole"
version = "1.6.0"
description = "guacamole-kubernetes"
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

dependencies {
    compileOnly("com.google.inject:guice:5.1.0")
    compileOnly("org.apache.commons:commons-collections4:4.1")
    api("io.kubernetes:client-java:25.0.0-legacy")
    compileOnly("org.apache.guacamole:guacamole-ext:1.6.0")
    compileOnly("javax.ws.rs:jsr311-api:1.1.1")
    compileOnly("org.slf4j:slf4j-api:1.7.7")
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}
