
// TODO unable to compile when offline ... why??
plugins {
    id("application")
    id("com.palantir.docker-run") version "0.25.0"

    kotlin("jvm") version "1.3.72"
    id("de.chrgroth.gradle.restcrud") version "0.1.0-SNAPSHOT"
}

repositories {
    mavenCentral()
    // mavenLocal()
    jcenter()
    // maven { url "https://kotlin.bintray.com/ktor" }
}

application {
    group = "de.chrgroth"
    mainClassName = "io.ktor.server.netty.EngineMain"
}

val versionKtor = "1.3.0"
val versionLogback = "1.2.1"
val versionSemVer = "1.1.1"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$versionKtor")
    implementation("io.ktor:ktor-server-netty:$versionKtor")
    implementation("io.ktor:ktor-server-core:$versionKtor")
    implementation("io.ktor:ktor-locations:$versionKtor")
    implementation("io.ktor:ktor-metrics:$versionKtor")
    implementation("io.ktor:ktor-server-host-common:$versionKtor")
    implementation("io.ktor:ktor-jackson:$versionKtor")

    implementation("ch.qos.logback:logback-classic:$versionLogback")
    implementation("net.swiftzer.semver:semver:$versionSemVer")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+")
    implementation("org.litote.kmongo:kmongo:3.12.+")

    testImplementation("io.ktor:ktor-server-tests:$versionKtor")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}

dockerRun {
    name = "james-api-test-mongodb"
    image = "mongo:4.2.3"
    ports("27017:27017")
    daemonize = true
    clean = true
    env(mapOf("MONGO_INITDB_ROOT_USERNAME" to "james", "MONGO_INITDB_ROOT_PASSWORD" to "semaj"))

    // volumes = "hostvolume": "/containervolume"
    // command 'sleep', '100'
    // arguments '--hostname=custom', '-P'

    // MONGO_INITDB_DATABASE
    // This variable allows you to specify the name of a database to be used for creation scripts in
    // /docker-entrypoint-initdb.d/*.js (see Initializing a fresh instance below). MongoDB is fundamentally designed for
    // "create on first use", so if you do not insert data with your JavaScript files, then no database is created.
}

tasks {
    //test.dependsOn("dockerRun")
    //test.finalizedBy("dockerStop")
}
