import com.google.protobuf.gradle.id

plugins {
    java
    application
    id("com.google.protobuf") version "0.9.4"
    id("org.graalvm.buildtools.native") version "0.10.2"
}

repositories {
    mavenCentral()
}

dependencies {
    // Shaded high-performance gRPC Network Transport & Stub bindings
    implementation("io.grpc:grpc-netty-shaded:1.68.0")
    implementation("io.grpc:grpc-protobuf:1.68.0")
    implementation("io.grpc:grpc-stub:1.68.0")
    implementation("io.grpc:grpc-services:1.68.0")

    // Zero-Syscall User-Space OpenTelemetry Tracing Engine
    implementation("io.opentelemetry:opentelemetry-api:1.43.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.43.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.43.0")

    // Lightweight connection pooling and low-level PostgreSQL driver limits
    implementation("com.zaxxer:HikariCP:6.0.0")
    implementation("org.postgresql:postgresql:42.7.4")

    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

application {
    mainClass.set("com.mistergamarra.MicroserviceApplication")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("java25-production-ready")
            buildArgs.add("--enable-preview")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+UnlockExperimentalVMOptions")
            buildArgs.add("-H:IncludeResources=db/migration/.*\\.sql")

            // FIX: Dynamic dynamic linking optimized for isolated Distroless execution grids
            buildArgs.add("-H:+StaticExecutableWithDynamicLibC")

            // CLEANUP: Removed the unnecessary com.fasterxml.jackson build-time initialization flag
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.68.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc") {
                    option("jakarta_omit=true")
                }
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDirs(
                "build/generated/source/proto/main/java",
                "build/generated/source/proto/main/grpc"
            )
        }
    }
}
