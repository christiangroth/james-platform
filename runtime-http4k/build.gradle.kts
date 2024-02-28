plugins {
    id("kotlin-project")
    id("io.quarkus") version "3.7.3"
}

dependencies {

    // http4k
    implementation(platform("org.http4k:http4k-bom:5.13.8.0"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-undertow")
    implementation("org.http4k:http4k-client-apache")
    implementation("org.http4k:http4k-security-oauth")
    implementation("org.http4k:http4k-template-handlebars")

    // JWT handling
    implementation("com.auth0:java-jwt:3.2.0")
    implementation("com.lambdaworks:scrypt:1.4.0")

    // YAML parsing
    implementation("com.charleskorn.kaml:kaml:0.57.0")

    // james-platform modules
    implementation(project(":core"))
    implementation(project(":core-app"))
    implementation(project(":core-data"))
    implementation(project(":core-typesystem"))
    implementation(project(":core-user"))
    implementation(project(":core-workspace"))
}
