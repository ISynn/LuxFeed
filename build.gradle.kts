plugins {
    java
}

group = "io.github.isynn"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Matches the Paper server build this plugin is developed and tested against (Paper 26.2 build 135).
val paperApiVersion = "26.2.build.135-stable"
val mockitoVersion = "5.24.0"

// Mockito is attached as a Java agent: dynamic agent loading is restricted on recent JDKs.
val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    mockitoAgent("org.mockito:mockito-core:$mockitoVersion") {
        isTransitive = false
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val properties = mapOf("version" to project.version)
    inputs.properties(properties)
    filesMatching("plugin.yml") {
        expand(properties)
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${mockitoAgent.singleFile}") })
}
