plugins {
    java
}

group = "burp"
version = "3.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // Burp Suite Montoya API
    compileOnly("net.portswigger.burp.extensions:montoya-api:2025.5")
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Site Map Extractor",
            "Implementation-Version" to version
        )
    }
    
    // Create a fat JAR with all dependencies (if any runtime deps added later)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
