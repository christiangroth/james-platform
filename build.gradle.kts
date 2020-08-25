plugins {
    id("de.chrgroth.gradle.restcrud") version "0.1.4-SNAPSHOT"
    id("com.palantir.docker-run") version "0.25.0"
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
