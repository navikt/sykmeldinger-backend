group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.8.0"
val ktorVersion = "2.3.8"
val logbackVersion = "1.5.0"
val logstashEncoderVersion = "7.4"
val prometheusVersion = "0.16.0"
val jacksonVersion = "2.16.1"
val kluentVersion = "1.73"
val mockkVersion = "1.13.9"
val nimbusdsVersion = "9.37.3"
val kotestVersion = "5.8.0"
val testcontainersVersion = "1.19.6"
val swaggerUiVersion = "5.11.8"
val kotlinVersion = "1.9.22"
val flywayVersion = "10.8.1"
val postgresVersion = "42.7.2"
val koinVersion = "3.5.3"
val hikariVersion = "5.1.0"
val commonsCodecVersion = "1.16.1"
val ktfmtVersion = "0.44"
val snakeYamlVersion = "2.2"
val snappyJavaVersion = "1.1.10.5"
val kafkaVersion = "3.7.0"
plugins {
    id("application")
    kotlin("jvm") version "1.9.22"
    id("com.diffplug.spotless") version "6.25.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.hidetake.swagger.generator") version "2.19.2" apply true
}

application {
    mainClass.set("no.nav.syfo.ApplicationKt")
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")

    constraints {
        implementation("commons-codec:commons-codec:$commonsCodecVersion") {
            because("override transient version 1.13 from io.ktor:ktor-client-apache")
        }
    }
    constraints {
        implementation("org.yaml:snakeyaml:$snakeYamlVersion") {
            because("due to https://github.com/advisories/GHSA-mjmj-j48q-9wg2")
        }
    }

    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    constraints {
        implementation("org.xerial.snappy:snappy-java:$snappyJavaVersion") {
            because("override transient from org.apache.kafka:kafka_2.12")
        }
    }
    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    compileOnly("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    swaggerUI("org.webjars:swagger-ui:$swaggerUiVersion")

    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")

    testImplementation("io.insert-koin:koin-test:$koinVersion")
    testImplementation("io.insert-koin:koin-test-junit5:$koinVersion")
}

swaggerSources {
    create("sykmeldinger-backend").apply {
        setInputFile(file("api/oas3/sykmeldinger-backend-api.yaml"))
    }
}

tasks {

    generateSwaggerUI {
        val output: Provider<Directory> = layout.buildDirectory.dir("/resources/main/api")
        outputDir = output.get().asFile
    }

    shadowJar {
        mergeServiceFiles {
            setPath("META-INF/services/org.flywaydb.core.extensibility.Plugin")
        }
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.syfo.BootstrapKt",
                ),
            )
        }
    }


    test {
        useJUnitPlatform {
        }
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
        }
    }
}
