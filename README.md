# ⚡ Java 25 Production-Ready Native Microservice Scaffold

An ultra-low-footprint, **100% Framework-Free**, production-grade gRPC microservice template built on **Java 25 (Preview Features Enabled)**. Compiled Ahead-of-Time (AOT) down to a bare metal native binary and distributed inside a microscopic **Google Distroless** runtime container.

Built to demonstrate how to achieve **elite cloud performance** while completely eliminating framework ballast (no Spring Boot, no Quarkus, no Micronaut).

---

## 📊 Hardcore Benchmarks (0.5 CPU / 64MB RAM Limit)

To prove this scaffold is ready for brutal enterprise traffic workloads, we stress-tested the container grid under a massive **300,000 total mixed CRUD transactions running at 1,000 concurrent network sockets** simultaneously.

The service was capped at **half a CPU core** and a **hard 64MB RAM ceiling** (via Docker production limits).

### 📈 Throughput & Latency Breakdown

| Operation Vector | Throughput (RPS) | Fastest Response | Median Latency (`p50`) | Tail Latency (`p99`) |
| :--- | :--- | :--- | :--- | :--- |
| **READ** (Master Index Scans) | **~4,970 Requests/sec** | 39.66 ms | **197.95 ms** | 305.26 ms |
| **UPDATE** (Complex Row Mutations) | **~997 Requests/sec** | 10.03 ms | **995.41 ms** | 1.28 sec |
| **WRITE** (With HTTP Chaos Retries) | **~742 Requests/sec** | 15.35 ms | **1.33 sec** | 2.29 sec |

### 🧠 Critical Resource Observation
*   **Memory Efficiency:** The active RAM footprint remained completely flat **between 35MB and 55MB total RAM**, entirely immune to Stop-The-World garbage collection leaks or memory spikes.
*   **Queuing Resilience:** Under 1,000 concurrent socket storms, Project Loom safely managed thousands of suspended threads in memory waiting on our 16-connection HikariCP pool without throwing network timeouts.

---

## 💎 Why It Is Framework-Free (No Magic, Just Raw JDK)

Modern frameworks make local prototyping easy, but they introduce hidden resource costs: heavy reflection caches, dynamic class-loaders, slow startup loops, and large memory footprints. This scaffolding blueprint returns control to the developer by leveraging raw, modern JDK engineering:

*   **Project Loom Concurrency Engine:** Virtual threads route context-switches inside user space. When an operation blocks on database sockets or external HTTP calls, Loom unpins the underlying OS thread automatically, maximizing hardware use.
*   **Zero-Syscall Asynchronous Logger:** Logging can easily bottleneck network performance. Our custom structured JSON logger queues entries inside an in-memory `ArrayBlockingQueue` ring-buffer, using a single platform daemon thread to handle `System.out.flush()` out-of-band.
*   **Resilient Exponential Backoff Client:** Built using the native Java 25 `HttpClient` API paired with a shared virtual thread executor, performing **3x exponential backoff retries** (`100ms -> 200ms -> 400ms`) to gracefully absorb external network failures (HTTP 500s, congestion lag, TCP resets).
*   **Isolated Database Migrations:** Database migrations are extracted out of your application binary space and run via an ephemeral Alpine Flyway container at boot time. This keeps your core runtime binary entirely free of reflection code and under 50MB in total container size.

---

## 🛠️ Developer Getting Started Journey

This repository is an optimized **GitHub Repository Template**. Simply click the **"Use this template"** button to instantly spin up a clean, high-performance workspace.

### Prerequisites
Make sure your development machine has **Docker Desktop** (with Buildx active) and the **ghz** gRPC benchmarking utility installed.

### 1. Execute AOT Native Compilation
Compile your code down to a self-contained static Linux binary inside the isolated multi-stage container build pipeline:
```bash
make docker-build
```

### 2. Launch the Microservices Grid Infrastructure
Spins up your performance-tuned PostgreSQL container, applies migrations out-of-band, provisions a WireMock server to simulate external HTTP failure paths, and boots your application:
```bash
docker compose up -d
```

### 3. Track Live Diagnostics & Statistics
```bash
# Verify the ready state of your structured JSON log loop streams
docker compose logs -f app

# Watch the strict 64MB memory allocation limit live under traffic pressure
docker stats java25-production-ready-app
```

### 4. Re-run the Extreme CRUD Stress Test Suite
```bash
make benchmark-extreme
```

---

## 📁 Scaffold Architecture Layout

```text
├── .github/workflows/ci.yml       # Ultra-fast container-free template release automation
├── .env                          # Local environment variable configuration mappings
├── build.gradle.kts              # Simplified, dependency-lean build script
├── docker-compose.yml            # Production resource constrained cluster mesh setup
├── Dockerfile                    # Multi-stage GraalVM 25 to Distroless image blueprint
├── Makefile                      # Engineering lifecycle shortcut scripts
├── load-test/
│   ├── config-create.json        # Traffic parameters for the 100k requests test
│   └── wiremock/mappings/        # Dynamic fault injection templates (Chaos engineering)
└── src/main/
    ├── proto/order.proto         # Expanded 10-field Restaurant CRUD protobuf definitions
    └── java/com/mistergamarra/
        ├── MicroserviceApplication.java  # Main entry loop and Loom gRPC services setup
        ├── client/PromoClient.java        # Resilient exponential backoff HTTP client
        └── config/Logger.java             # Non-blocking async structured JSON logger thread
```

## ⚖️ License
Distributed under the terms of the open-source **MIT License**. See `LICENSE` for details.
