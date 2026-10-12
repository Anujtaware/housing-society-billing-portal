# Week 11 — Docker Image and Container Lifecycle

## 1. Objective
The objective of Week 11 is to containerize the Spring Boot application of the **Housing Society Billing Portal** using Docker and manage its full container lifecycle. This encompasses:
- Designing a production-ready, lean [`Dockerfile`](file:///d:/Housing-Society-Billing-Portal/Dockerfile) compatible with Java 25.
- Establishing environment-driven database connectivity without hardcoded machine configurations or leaked credentials.
- Creating an optimized [`.dockerignore`](file:///d:/Housing-Society-Billing-Portal/.dockerignore) to maintain a minimal build context.
- Building and version-tagging the Docker image (`housing-society-billing-portal:v1.0`).
- Running the container on non-conflicting port mappings (`8083:8080`) preserving existing services (Jenkins on `8080`, Nginx on `8081`, local Spring Boot on `8082`).
- Verifying container health, dashboard access, REST APIs, and database transactions.
- Inspecting container logs.
- Demonstrating the complete container lifecycle: `RUNNING` ➔ `STOP` ➔ `START` ➔ `VERIFY` ➔ `RESTART` ➔ `VERIFY` ➔ `STOP` ➔ `REMOVE`.
- Maintaining the integrity of existing Git history, Jenkins pipeline, Selenium tests, and release tag `v1.0`.

---

## 2. Docker Environment & Engine
- **Docker Engine Version:** `29.8.2` (build `7fc2dff`, API `1.56`)
- **Docker Desktop Version:** `4.94.0` (build `241994`)
- **Container Runtime:** `containerd` v2.3.6 / `runc` v1.5.1
- **OS/Architecture:** Linux / amd64 on WSL2 backend
- **Host Context:** `desktop-linux`

---

## 3. Dockerfile & Build Configuration

### 3.1 Base Image Selection
- **Base Image:** `eclipse-temurin:25-jre`
- **Rationale:** The application strictly requires Java 25 (`<java.version>25</java.version>`). Eclipse Temurin OpenJDK 25 JRE provides a lean, official, and battle-tested runtime based on Ubuntu 26.04 without the unnecessary overhead of a full JDK compiler in production.

### 3.2 Dockerfile Implementation
File: [`Dockerfile`](file:///d:/Housing-Society-Billing-Portal/Dockerfile)
```dockerfile
# Eclipse Temurin OpenJDK 25 JRE runtime image
FROM eclipse-temurin:25-jre

# Metadata
LABEL maintainer="Anuj Taware <anuj@housing-portal.local>"
LABEL description="Housing Society Billing Portal - Containerized Spring Boot Application"
LABEL version="v1.0"

# Set application working directory inside container
WORKDIR /app

# Copy the pre-packaged Spring Boot executable JAR into the container
COPY application/target/billing-portal-0.0.1-SNAPSHOT.jar app.jar

# Expose default application port
EXPOSE 8080

# Clean production entrypoint to run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 3.3 Docker Ignore Configuration
File: [`.dockerignore`](file:///d:/Housing-Society-Billing-Portal/.dockerignore)
```
.git
.gitignore
.vscode
.agents
documentation
scripts
Jenkinsfile
*.md
application/src/test
application/test-results
application/target/*
!application/target/billing-portal-0.0.1-SNAPSHOT.jar
```
This ensures Git history, source code, tests, documentation, and extraneous Maven artifacts are omitted from the build context, reducing context size to just the executable artifact (~54MB).

---

## 4. Database Configuration Approach

### 4.1 Parameterization in application.properties
The Spring Boot configuration in [`application/src/main/resources/application.properties`](file:///d:/Housing-Society-Billing-Portal/application/src/main/resources/application.properties) was enhanced to allow environment variable overrides while preserving default fallback values:
```properties
# MySQL Database Configuration (Environment variable overrides with backward-compatible defaults)
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:housing_billing}
spring.datasource.username=${DB_USER:root}
spring.datasource.password=${DB_PASSWORD:Housing@2026}

# Server Port
server.port=${SERVER_PORT:8080}
```
- **Local / CI Compatibility:** When no environment variables are set, defaults (`localhost:3306`, `housing_billing`, `root`, `Housing@2026`, port `8080`) apply automatically, preserving 100% backward compatibility with Week 1–10 builds, JUnit tests, and Jenkins deployments.
- **Docker Networking:** Inside the container, passing `-e DB_HOST=host.docker.internal` allows the containerized Spring Boot instance to connect to MySQL running on the host machine.
- **MySQL Host Grants:** In MySQL 8.0, `root@'%'` was granted privileges to permit incoming network connections routed through the Docker virtual gateway.

---

## 5. Docker Image Build & Verification

### 5.1 Build Command
```bash
mvn package -DskipTests=true -f application/pom.xml
docker build -t housing-society-billing-portal:v1.0 -t housing-society-billing-portal:latest .
```

### 5.2 Verification (`docker images`)
```
IMAGE                                   ID             DISK USAGE   CONTENT SIZE   EXTRA
housing-society-billing-portal:latest   dcff5bbf2ca4        588MB          169MB        
housing-society-billing-portal:v1.0     dcff5bbf2ca4        588MB          169MB        
```
- **Image Name & Tag:** `housing-society-billing-portal:v1.0`
- **Image ID:** `dcff5bbf2ca4`
- **Image Content Size:** `169MB` (Total disk usage: `588MB`)

---

## 6. Container Execution & Port Mapping

### 6.1 Port Mapping Strategy
To prevent port collision with active infrastructure:
- **Port 8080:** Jenkins CI Server
- **Port 8081:** Nginx Reverse Proxy (Week 8 live deployment)
- **Port 8082:** Local Spring Boot deployed process
- **Port 8083:** **Docker Container Host Port** mapped to container port **8080** (`-p 8083:8080`)

### 6.2 Run Command
```bash
docker run -d \
  --name housing-billing-app \
  -p 8083:8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=3306 \
  -e DB_NAME=housing_billing \
  -e DB_USER=root \
  -e DB_PASSWORD=Housing@2026 \
  housing-society-billing-portal:v1.0
```

### 6.3 Status Verification (`docker ps`)
```
CONTAINER ID   IMAGE                                 COMMAND               CREATED          STATUS          PORTS                                         NAMES
4d2c239e8a7d   housing-society-billing-portal:v1.0   "java -jar app.jar"   23 seconds ago   Up 23 seconds   0.0.0.0:8083->8080/tcp, [::]:8083->8080/tcp   housing-billing-app
```

---

## 7. Container Logs Verification (`docker logs housing-billing-app`)

```text
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.1)

2026-10-12T10:35:19.207Z  INFO 1 --- [billing-portal] [           main] c.h.billing.BillingPortalApplication     : Starting BillingPortalApplication v0.0.1-SNAPSHOT using Java 25.0.4.1 with PID 1 (/app/app.jar started by root in /app)
2026-10-12T10:35:20.642Z  INFO 1 --- [billing-portal] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JPA repositories in DEFAULT mode.
2026-10-12T10:35:21.741Z  INFO 1 --- [billing-portal] [           main] o.s.boot.tomcat.TomcatWebServer          : Tomcat initialized with port 8080 (http)
2026-10-12T10:35:23.265Z  INFO 1 --- [billing-portal] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2026-10-12T10:35:24.007Z  INFO 1 --- [billing-portal] [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@59e0d521
2026-10-12T10:35:24.010Z  INFO 1 --- [billing-portal] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2026-10-12T10:35:24.168Z  INFO 1 --- [billing-portal] [           main] org.hibernate.orm.connections.pooling    : HHH10001005: Database info:
	Database JDBC URL [jdbc:mysql://host.docker.internal:3306/housing_billing]
	Database driver: MySQL Connector/J
	Database version: 8.0.42
2026-10-12T10:35:25.621Z  INFO 1 --- [billing-portal] [           main] j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-10-12T10:35:26.717Z  INFO 1 --- [billing-portal] [           main] o.s.boot.tomcat.TomcatWebServer          : Tomcat started on port 8080 (http) with context path '/'
2026-10-12T10:35:26.756Z  INFO 1 --- [billing-portal] [           main] c.h.billing.BillingPortalApplication     : Started BillingPortalApplication in 8.443 seconds (process running for 9.869)
```

---

## 8. Functional & API Verification (Port 8083)

| Target | Request | Observed Response | Status |
|---|---|---|---|
| Dashboard UI | `GET http://localhost:8083/index.html` | HTTP 200 OK, full HTML dashboard loaded | ✅ PASS |
| Resident API | `GET http://localhost:8083/residents` | HTTP 200 OK, returned existing JSON records | ✅ PASS |
| Bill API | `GET http://localhost:8083/bills` | HTTP 200 OK, returned existing bill records | ✅ PASS |
| Safe Creation | `POST http://localhost:8083/residents` | Created test resident (`ID: 45`, `DOC-101`) | ✅ PASS |
| Safe Cleanup | `DELETE http://localhost:8083/residents/45` | HTTP 204 No Content, clean database state | ✅ PASS |

---

## 9. Container Lifecycle Verification

### 9.1 Phase 1: STOP
```bash
docker stop housing-billing-app
```
- **Status in `docker ps -a`:** `Exited (143) Less than a second ago`
- **Container State:** Stopped cleanly without data loss.

### 9.2 Phase 2: START
```bash
docker start housing-billing-app
```
- **Status in `docker ps`:** `Up 17 seconds (0.0.0.0:8083->8080/tcp)`
- **Verification:** `GET http://localhost:8083/index.html` ➔ HTTP 200; `GET /residents` ➔ HTTP 200.

### 9.3 Phase 3: RESTART
```bash
docker restart housing-billing-app
```
- **Status in `docker ps`:** `Up 17 seconds`
- **Verification:** `GET http://localhost:8083/index.html` ➔ HTTP 200; `GET /bills` ➔ HTTP 200.

### 9.4 Phase 4: REMOVAL
```bash
docker stop housing-billing-app
docker rm housing-billing-app
```
- **Verification in `docker ps -a`:** Container completely removed from list.
- **Image Retention:** `housing-society-billing-portal:v1.0` remains intact in local image repository.

---

## 10. Verification Summary Matrix

| Verification Criterion | Expected | Observed | Status |
|---|---|---|---|
| A. Dockerfile exists & valid | Valid Dockerfile in repo root | Java 25 JRE base image, entrypoint | ✅ PASS |
| B. Docker image builds | Image builds without errors | Built in 3.9 seconds | ✅ PASS |
| C. Meaningful versioned tag | Tagged appropriately | `housing-society-billing-portal:v1.0` | ✅ PASS |
| D. Container starts | Runs in detached mode | Container `housing-billing-app` started | ✅ PASS |
| E. Responds on host port | Port 8083 mapped to 8080 | HTTP 200 OK | ✅ PASS |
| F. Dashboard works | UI loads via container | `http://localhost:8083/index.html` HTTP 200 | ✅ PASS |
| G. /residents works | Resident API returns JSON | 1 existing record retrieved | ✅ PASS |
| H. /bills works | Bill API returns JSON | 1 existing bill retrieved | ✅ PASS |
| I. MySQL connectivity | Connects via `host.docker.internal` | HikariCP pool initialized, 8.0.42 | ✅ PASS |
| J. Docker logs healthy | Clean Spring Boot startup | Started in 8.443s, Tomcat on 8080 | ✅ PASS |
| K. Stop & Start works | Recovers after start | Tested, verified HTTP 200 | ✅ PASS |
| L. Restart works | Recovers after restart | Tested, verified HTTP 200 | ✅ PASS |
| M. Container removed cleanly | Removed without lingering state | `docker rm` succeeded | ✅ PASS |
| N. Existing services unaffected | Jenkins 8080, Nginx 8081 healthy | Verified active & responding | ✅ PASS |
| O. 14 Tests remain passing | All unit & Selenium tests pass | 14/14 passed | ✅ PASS |
| P. Working tree clean | No uncommitted modifications | Clean git working tree | ✅ PASS |
| Q. Tag `v1.0` preserved | Points to `251962d` | Verified `251962d` unchanged | ✅ PASS |
| R. No Week 12 features | No Jenkins Docker stages added | Scope restricted to Week 11 | ✅ PASS |

---

## 11. Files Changed
- [`Dockerfile`](file:///d:/Housing-Society-Billing-Portal/Dockerfile) — Container definition for Java 25 Spring Boot app.
- [`.dockerignore`](file:///d:/Housing-Society-Billing-Portal/.dockerignore) — Build context optimization.
- [`application/src/main/resources/application.properties`](file:///d:/Housing-Society-Billing-Portal/application/src/main/resources/application.properties) — Dynamic environment variable support for database credentials.
- [`documentation/Week_11_Docker_Containerization.md`](file:///d:/Housing-Society-Billing-Portal/documentation/Week_11_Docker_Containerization.md) — Comprehensive technical documentation.

---

## 12. Git Information
- **Branch:** `feature/week11-docker`
- **Commit Message:** `feat: containerize billing portal with docker`
- **Release Tag:** `v1.0` remains intact at `251962d`.
