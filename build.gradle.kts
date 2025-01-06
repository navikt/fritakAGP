import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val mainClassFritakAgp = "no.nav.helse.fritakagp.AppKt"

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
    id("com.github.ben-manes.versions")
    id("com.autonomousapps.dependency-analysis")
    jacoco
}

application {
    mainClass.set(mainClassFritakAgp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    val githubPassword: String by project

    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io") {
        content {
            excludeGroup("no.nav.helsearbeidsgiver")
        }
    }
    maven {
        setUrl("https://maven.pkg.github.com/navikt/*")
        credentials {
            username = "x-access-token"
            password = githubPassword
        }
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("app")
    manifest {
        attributes["Main-Class"] = mainClassFritakAgp
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }
    doLast {
        configurations.runtimeClasspath.get().forEach {
            val file = File("$buildDir/libs/${it.name}")
            if (!file.exists()) {
                it.copyTo(file)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named<Test>("test") {
    include("no/nav/helse/**")
    exclude("no/nav/helse/slowtests/**")
}

task<Test>("slowTests") {
    include("no/nav/helse/slowtests/**")
    outputs.upToDateWhen { false }
    group = "verification"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.withType<Wrapper> {
    gradleVersion = "8.3"
}

dependencies {
    val altinnClientVersion: String by project
    val altinnCorrespondenceAgencyVersion: String by project
    val arbeidsgiverNotifikasjonKlientVersion: String by project
    val bakgrunnsjobbVersion: String by project
    val assertJVersion: String by project
    val brukernotifikasjonSchemasVersion: String by project
    val confluentVersion: String by project
    val coroutinesVersion: String by project
    val cxfVersion: String by project
    val dokarkivKlientVersion: String by project
    val fellesBackendVersion: String by project
    val flywayVersion: String by project
    val gcpStorageVersion: String by project
    val hikariVersion: String by project
    val jacksonModuleKotlinVersion: String by project
    val jacksonVersion: String by project
    val janinoVersion: String by project
    val javaxActivationVersion: String by project
    val javaxWsRsApiVersion: String by project
    val jaxwsToolsVersion: String by project
    val jaxwsVersion: String by project
    val junitJupiterVersion: String by project
    val kafkaClient: String by project
    val kformatVersion: String by project
    val koinVersion: String by project
    val ktorVersion: String by project
    val logbackEncoderVersion: String by project
    val logback_version: String by project
    val mockkVersion: String by project
    val mockOAuth2ServerVersion: String by project
    val pdfboxVersion: String by project
    val pdlClientVersion: String by project
    val aaregClientVersion: String by project
    val postgresqlVersion: String by project
    val prometheusVersion: String by project
    val slf4jVersion: String by project
    val tokenProviderVersion: String by project
    val tokenSupportVersion: String by project
    val utilsVersion: String by project
    val valiktorVersion: String by project
    val tmsVarselKotlinBuilderVersion: String by project

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlinVersion")
    implementation("com.github.navikt:brukernotifikasjon-schemas:$brukernotifikasjonSchemasVersion")
    implementation("com.google.cloud:google-cloud-storage:$gcpStorageVersion")
    implementation("com.sun.activation:javax.activation:$javaxActivationVersion")
    implementation("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("de.m3y.kformat:kformat:$kformatVersion")
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation("io.insert-koin:koin-core-jvm:$koinVersion")
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.ktor:ktor-server:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-locations:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("javax.ws.rs:javax.ws.rs-api:$javaxWsRsApiVersion")
    implementation("javax.xml.ws:jaxws-api:$jaxwsVersion")

    implementation("no.nav.helsearbeidsgiver:hag-bakgrunnsjobb:$bakgrunnsjobbVersion")
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")
    implementation("no.nav.helsearbeidsgiver:dokarkiv-client:$dokarkivKlientVersion")
    implementation("no.nav.helsearbeidsgiver:utils:$utilsVersion")
    implementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "io.netty", module = "netty-all")
    }
    implementation("no.nav.helsearbeidsgiver:pdl-client:$pdlClientVersion")
    implementation("no.nav.helsearbeidsgiver:aareg-client:$aaregClientVersion")
    implementation("no.nav.security:token-client-core:$tokenSupportVersion")
    implementation("no.nav.security:token-validation-ktor-v2:$tokenSupportVersion")
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")
    implementation("no.nav.helsearbeidsgiver:tokenprovider:$tokenProviderVersion")
    implementation("no.nav.tjenestespesifikasjoner:altinn-correspondence-agency-external-basic:$altinnCorrespondenceAgencyVersion")
    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaClient")
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
    implementation("org.codehaus.janino:janino:$janinoVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.valiktor:valiktor-core:$valiktorVersion")
    implementation("org.valiktor:valiktor-javatime:$valiktorVersion")
    implementation("io.mockk:mockk:$mockkVersion") // Brukes til å mocke eksterne avhengigheter under lokal kjøring
    implementation("no.nav.tms.varsel:kotlin-builder:$tmsVarselKotlinBuilderVersion")

    testImplementation("io.insert-koin:koin-test:$koinVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.assertj:assertj-core:$assertJVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}
