# --- Configuration Variables ---
BINARY_NAME=java25-production-ready
GRAALVM_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home
BINARY_PATH=build/native/nativeCompile/$(BINARY_NAME)

# --- Environment Enforcements ---
export JAVA_HOME := $(GRAALVM_HOME)
export GRAALVM_HOME := $(GRAALVM_HOME)

.PHONY: all clean compile run restart db-up db-down help
# Default target when typing just 'make'
all: compile

## compile: Clears caches and builds the standalone AOT native machine binary
compile: clean
	@echo "========================================="
	@echo " Starting GraalVM AOT Compilation...     "
	@echo " Using JDK: $(GRAALVM_HOME)              "
	@echo "========================================="
	./gradlew nativeCompile

## run: Executes the generated framework-free binary natively
run:
	@if [ -f $(BINARY_PATH) ]; then \
		echo "========================================="; \
		echo " Booting $(BINARY_NAME) Natively...      "; \
		echo "========================================="; \
		./$(BINARY_PATH); \
	else \
		echo "Error: Binary not found at $(BINARY_PATH). Run 'make compile' first."; \
		exit 1; \
	fi

## restart: Re-compiles code changes and boots the native instance immediately
restart: compile run

## db-up: Spins up the tuned PostgreSQL container and waits for it to be completely healthy
db-up:
	@echo "Spanning up PostgreSQL database container..."
	docker compose up -d
	@echo "Waiting for database to pass internal healthchecks..."
	@until [ "$$(docker inspect --format='{{.State.Health.Status}}' java25-production-ready-db)" = "healthy" ]; do \
		printf "."; \
		sleep 1; \
	done
	@echo "\nPostgreSQL database is ready and accepting socket connections!"

## db-down: Stops and tears down the PostgreSQL container layer, retaining volume data
db-down:
	@echo "Tearing down database container infrastructure..."
	docker compose down

## clean: Wipes out generated protobuf structures and temporary build caches
clean:
	@echo "Cleaning previous build artifacts..."
	@rm -rf build/generated
	@./gradlew clean

## help: Shows the available make command execution shortcuts
help:
	@echo "Available commands:"
	@sed -n 's/^##//p' $(MAKEFILE_LIST)


# ... (Keep all your existing db-up, db-down, and compile targets exactly as they are)

.PHONY: docker-build docker-run docker-clean

## docker-build: Builds the ultra-small static container image via multi-stage scratch pipeline
docker-build:
	@echo "========================================="
	@echo " Building microscopic scratch container... "
	@echo "========================================="
	docker build -t java25-production-ready:latest .
	@echo "\nContainer build complete! Check the size using: docker images java25-production-ready"

## docker-run: Launches the microscopic container mapping it to your localized network mesh
docker-run:
	@echo "Booting service inside scratch container..."
	docker run --rm -p 8080:8080 --name java25-production-ready-app --network=host java25-production-ready:latest

## docker-clean: Removes local image dangling layers from the system cache
docker-clean:
	docker rmi java25-production-ready:latest || true

# ... (Keep all your existing configuration variables at the top of the file)

.PHONY: proto

## proto: Generates only the Protobuf and gRPC Java scaffolding stub classes
proto:
	@echo "========================================="
	@echo " Generating Protobuf & gRPC Stubs...     "
	@echo "========================================="
	@rm -rf build/generated
	./gradlew generateProto
	@echo "\nStubs generated successfully! Check: build/generated/source/proto/main/"


# ... (Keep all your existing targets like db-up, compile, run, docker-build exactly as they are)

# ... (Keep all your existing metrics, compilation, and db targets exactly as they are)

# ... (Keep all your existing targets like db-up, compile, and docker-build exactly as they are)

.PHONY: benchmark-realistic

## benchmark-realistic: Runs 10,000 unique template writes, extracts a live ID, and profiles realistic lookups
benchmark-realistic:
	@echo "================================================================="
	@echo " Running Realistic WRITE Benchmark: 10,000 Unique Dynamic Payloads"
	@echo "================================================================="
	ghz --config=load-test/config-create.json
	@echo "\nExtracting a real generated Order ID from the live PostgreSQL cluster..."
	$(eval LIVE_ID=$(shell docker exec -i java25-production-ready-db psql -U postgres -d java25-production-ready-db -t -A -c "SELECT id FROM client_orders ORDER BY created_at DESC LIMIT 1;"))
	@echo "Found active database record reference ID: $(LIVE_ID)"
	@if [ -z "$(LIVE_ID)" ]; then \
		echo "Error: No data found in database."; \
		exit 1; \
	fi
	@echo "\n================================================================="
	@echo " Running Realistic READ Benchmark: 10,000 Live Index Queries    "
	@echo " Target ID: $(LIVE_ID) (Concurrency: 100)                        "
	@echo "================================================================="
	ghz --insecure \
		--proto=src/main/proto/order.proto \
		--call=order.OrderService.GetOrder \
		--data='{"order_id": "$(LIVE_ID)"}' \
		--total=10000 \
		--concurrency=100 \
		--cpus=4 \
		localhost:8080


# ... (Keep all your existing build and compile targets exactly as they are)

.PHONY: benchmark-extreme

## benchmark-extreme: Stress-tests the container with 100,000 records at 1,000 concurrency across CRUD operations
benchmark-extreme:
	@echo "================================================================="
	@echo " RUNNING EXTREME WRITE TEST: 100,000 Records (Concurrency: 1000)"
	@echo "================================================================="
	ghz --config=load-test/config-create.json
	@echo "\nExtracting an active Order ID from the PostgreSQL database..."
	$(eval LIVE_ID=$(shell docker exec -i java25-production-ready-db psql -U postgres -d java25-production-ready-db -t -A -c "SELECT id FROM client_orders ORDER BY created_at DESC LIMIT 1;"))
	@echo "Found active database record reference ID: $(LIVE_ID)"
	@if [ -z "$(LIVE_ID)" ]; then \
		echo "Error: No data found in database."; \
		exit 1; \
	fi
	@echo "\n================================================================="
	@echo " RUNNING EXTREME READ TEST: 100,000 Live Index Queries (Conc: 1000)"
	@echo " Target ID: $(LIVE_ID)                                           "
	@echo "================================================================="
	ghz --insecure \
		--proto=src/main/proto/order.proto \
		--call=order.OrderService.GetOrder \
		--data='{"order_id": "$(LIVE_ID)"}' \
		--total=100000 \
		--concurrency=1000 \
		--cpus=4 \
		localhost:8080
	@echo "\n================================================================="
	@echo " RUNNING EXTREME UPDATE TEST: 100,000 Records (Concurrency: 1000)"
	@echo "================================================================="
	ghz --insecure \
		--proto=src/main/proto/order.proto \
		--call=order.OrderService.UpdateOrder \
		--data='{"order_id": "$(LIVE_ID)", "status": "COMPLETED", "order_type": "PICKUP", "items_total": 52.20, "delivery_fee": 0.0, "promo_code": "PROMO_COMP"}' \
		--total=100000 \
		--concurrency=1000 \
		localhost:8080

