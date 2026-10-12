# Week 12 — Jenkins-Docker Continuous Deployment

## 1. Objective
The objective of Week 12 is to achieve automated **Continuous Deployment (CD)** by integrating Docker container build, image versioning, deployment, and health verification directly into the Jenkins Declarative Pipeline for the **Housing Society Billing Portal**.

Key objectives accomplished:
- Enabled seamless Docker CLI execution for the Jenkins Windows service running under the `LocalSystem` security context.
- Extended the existing Jenkins Declarative Pipeline ([`Jenkinsfile`](file:///d:/Housing-Society-Billing-Portal/Jenkinsfile)) with automated Docker CD stages after all automated quality gates (JUnit unit tests and Selenium browser tests) pass.
- Implemented build-traceable Docker image version tagging (`housing-society-billing-portal:build-${BUILD_NUMBER}` alongside `:latest`).
- Automated container lifecycle management: safe cleanup of previous CD containers without impacting other workloads.
- Deployed a fresh container (`housing-billing-cd`) to an independent, non-conflicting host port (`8084` -> container `8080`).
- Configured container database connectivity using container environment variables (`DB_HOST=host.docker.internal`) while protecting host-level pipeline stages.
- Created a robust automated container health check script with retries that validates HTTP 200 dashboard status, database-backed REST endpoints (`/residents` and `/bills`), and container startup logs.
- Preserved all existing Week 7–11 functionality:
  - Jenkins CI on port `8080`.
  - Nginx reverse proxy deployment on port `8081` -> Spring Boot on port `8082`.
  - All 14 existing automated tests (9 JUnit unit/integration tests + 5 Selenium UI tests) passing 100%.
  - Git tag `v1.0` permanently preserved at commit `251962d`.

---

## 2. Docker Access from Jenkins Windows Service

### 2.1 Problem Diagnosis
Jenkins runs as a Windows Service under the `NT AUTHORITY\SYSTEM` (`LocalSystem`) account. Docker Desktop for Windows natively manages its named pipes under the interactive user session (`C:\Users\anujt\.docker\run\docker.sock` and `npipe:////./pipe/dockerDesktopLinuxEngine`). By default, non-interactive service accounts do not have the user's `DOCKER_CONFIG` environment variable or named pipe redirection, which causes `docker` commands executed from Jenkins service jobs to fail with `docker: command not found` or daemon connection errors.

### 2.2 Solution: Global Execution Shims
To provide system-wide access without altering Jenkins service credentials or reinstalling software, wrapper shims were placed in `C:\DevTools\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin` (which is globally accessible on the System `PATH`):
- `docker.cmd`:
  ```cmd
  @echo off
  set "DOCKER_CONFIG=C:\Users\anujt\.docker"
  set "DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine"
  "C:\Program Files\Docker\Docker\resources\bin\docker.exe" %*
  ```
- `docker-compose.cmd`: Wraps `docker-compose.exe`.
- `docker-credential-desktop.cmd`: Wraps Docker credential helper.

### 2.3 Verification from Jenkins
Jenkins execution verification confirmed:
- `docker version`: Client `29.8.2`, Server `29.8.2` (API version `1.56`)
- `docker info`: Server Version `29.8.2`, OS/Arch: `linux/amd64`
- `docker images`: Successfully enumerated local images.
- `docker ps`: Successfully enumerated running containers.

---

## 3. Pipeline Architecture & Stages

The pipeline extends the existing declarative workflow with three new Docker Continuous Deployment stages placed after the Selenium test quality gate.

```
+---------------------------------------------------------------------------------------------------+
|                                  Jenkins Declarative Pipeline                                     |
+---------------------------------------------------------------------------------------------------+
|  1. Checkout SCM                  (Polls GitHub repo on branch */main)                            |
|  2. Compile                       (mvn clean compile)                                             |
|  3. Unit & Integration Tests      (mvn test: 9/9 tests pass)                                      |
|  4. Package                       (mvn package -DskipTests=true)                                  |
|  5. Deploy (Local Dev)            (scripts/deploy.ps1 -> Spring Boot on 8082 + Nginx on 8081)     |
|  6. Health Check (Local Dev)      (scripts/healthcheck.ps1 -> validates port 8081)                |
|  7. Selenium UI Tests             (mvn test -Dtest=SeleniumE2ETests: 5/5 tests pass)              |
+---------------------------------------------------------------------------------------------------+
|                                 NEW CONTINUOUS DEPLOYMENT STAGES                                  |
+---------------------------------------------------------------------------------------------------+
|  8. Docker Build                  (docker build -t ...:build-${BUILD_NUMBER} -t ...:latest)       |
|  9. Docker Container Deploy       (scripts/docker-deploy.ps1 -> container on port 8084)          |
| 10. Docker Health Check           (scripts/docker-healthcheck.ps1 -> validates port 8084)         |
+---------------------------------------------------------------------------------------------------+
|  11. Post Actions                 (Archive JAR & screenshots, publish JUnit test reports)         |
+---------------------------------------------------------------------------------------------------+
```

---

## 4. Docker Continuous Deployment Implementation

### 4.1 Stage 8: Docker Build
The Docker image is built from the repository [`Dockerfile`](file:///d:/Housing-Society-Billing-Portal/Dockerfile) using the pre-packaged Maven executable JAR.
- Jenkins commands executed:
  ```groovy
  docker build -t housing-society-billing-portal:build-${BUILD_NUMBER} -t housing-society-billing-portal:latest .
  ```
- Image versioning strategy:
  - Every pipeline build generates an immutable, unique image tag tied directly to the Jenkins build number: `housing-society-billing-portal:build-${BUILD_NUMBER}`.
  - The `:latest` tag is updated to point to the most recently built artifact.
  - The existing Week 11 Docker image (`housing-society-billing-portal:v1.0`, Image ID `dcff5bbf2ca4`) is preserved and never overwritten.

### 4.2 Stage 9: Docker Container Deploy
Container deployment is managed by [`scripts/docker-deploy.ps1`](file:///d:/Housing-Society-Billing-Portal/scripts/docker-deploy.ps1):
- **Safe Cleanup:** Inspects whether container `housing-billing-cd` is running or stopped. If present, it executes `docker stop` and `docker rm` exclusively on that container name. Unrelated containers (such as Week 11 test containers) are completely untouched.
- **Port Mapping:** Host port `8084` -> Container port `8080`.
  - Non-conflicting with `8080` (Jenkins), `8081` (Nginx), `8082` (Local Spring Boot), and `8083` (Week 11 test port).
- **Database Networking:**
  - Container environment variables:
    - `-e DB_HOST=host.docker.internal`
    - `-e DB_PORT=3306`
    - `-e DB_NAME=society_billing`
    - `-e DB_USER=root`
    - `-e DB_PASSWORD=******` (injected securely from Jenkins credentials without echoing)
- **Deployment Command:**
  ```powershell
  docker run -d `
      --name housing-billing-cd `
      -p 8084:8080 `
      -e DB_HOST=host.docker.internal `
      -e DB_PORT=3306 `
      -e DB_NAME=society_billing `
      -e DB_USER=root `
      -e DB_PASSWORD=$DbPassword `
      housing-society-billing-portal:build-14
  ```

### 4.3 Stage 10: Docker Health Check
Container verification is automated via [`scripts/docker-healthcheck.ps1`](file:///d:/Housing-Society-Billing-Portal/scripts/docker-healthcheck.ps1):
- Polls `http://localhost:8084/index.html` with configurable retries (up to 30 attempts, 2-second interval).
- Validates database connectivity by invoking JSON REST endpoints:
  - `GET http://localhost:8084/residents` (verifies HTTP 200 and parses resident records).
  - `GET http://localhost:8084/bills` (verifies HTTP 200 and parses bill records).
- Extracts and prints recent container logs (`docker logs --tail 25 housing-billing-cd`) showing active Hibernate queries and Tomcat startup time.
- If health verification fails, the script exits with code 1, immediately halting and failing the Jenkins pipeline.

---

## 5. Jenkins Build Execution Evidence

### 5.1 Build Overview
- **Jenkins Job:** `Housing-Society-Billing-Portal`
- **Build Number:** `14`
- **Trigger:** SCM polling (`* * * * *`) on commit `6514199`
- **Result:** `SUCCESS`

### 5.2 Build Console Highlights
```text
=== Stage 8: Docker Image Build & Versioning ===
#8 naming to docker.io/library/housing-society-billing-portal:build-14 done
#8 naming to docker.io/library/housing-society-billing-portal:latest done

REPOSITORY                       TAG        IMAGE ID       CREATED AT                      SIZE
housing-society-billing-portal   build-14   4fdec1e318fe   2026-10-12 16:42:17 +0530 IST   588MB
housing-society-billing-portal   latest     4fdec1e318fe   2026-10-12 16:42:17 +0530 IST   588MB
housing-society-billing-portal   v1.0       dcff5bbf2ca4   2026-10-12 16:04:49 +0530 IST   588MB

=== Stage 9: Deploy Fresh Docker Container ===
Target Container : housing-billing-cd
Target Port      : 8084 -> 8080
Database Host    : host.docker.internal:3306 (society_billing)
Deploying fresh Docker container...
Container started: ab865147fb3a5ba85eb23b7a858ffaa163b49c717088b9dd632b781da53ff1c8

=== Stage 10: Verify Docker Container Health ===
Attempt 1 of 30: Checking http://localhost:8084/index.html...
Application is HEALTHY! HTTP Status: 200
GET /residents responded successfully. Count: 1
GET /bills responded successfully. Count: 1

Representative Container Logs (housing-billing-cd):
    select r1_0.id, r1_0.email, r1_0.flat_number, r1_0.name, r1_0.phone from resident r1_0
    select b1_0.id, b1_0.bill_month, b1_0.electricity_amount, b1_0.maintenance_amount, b1_0.payment_date, b1_0.resident_id, b1_0.status, b1_0.total_amount from bill b1_0

Docker CD Deployment Verification Complete!
Status       : HEALTHY
Web Portal   : http://localhost:8084/index.html

Recording test results: 14 tests run, 0 failures, 0 errors, 0 skipped.
Finished: SUCCESS
```

---

## 6. Verification Matrix

| Verification Item | Requirement | Measured Result | Status |
|---|---|---|---|
| **Jenkins Docker Access** | Jenkins service executes Docker commands | `docker version`, `docker info`, `docker run` succeed | ✅ PASS |
| **Jenkins Docker Build** | Image built automatically during pipeline | Built in Stage 8 via `Dockerfile` | ✅ PASS |
| **Image Versioning** | Build-traceable tag generated | `housing-society-billing-portal:build-14` | ✅ PASS |
| **Image Traceability** | Docker image maps to Jenkins BUILD_NUMBER | Image ID `4fdec1e318fe` tied to Build #14 | ✅ PASS |
| **Fresh Container Deployment** | Jenkins launches fresh container | Container `housing-billing-cd` (`ab865147fb3a`) running | ✅ PASS |
| **Container Replacement** | Previous CD container safely replaced | Clean stop & remove logic in `scripts/docker-deploy.ps1` | ✅ PASS |
| **Port Non-Conflict** | Dedicated host port | `8084:8080` (coexists with 8080, 8081, 8082, 8083) | ✅ PASS |
| **Database Connectivity** | Connects to existing MySQL via host gateway | `host.docker.internal:3306` with queries executing | ✅ PASS |
| **Credential Safety** | MySQL password never logged in console | Injected via masked environment variable | ✅ PASS |
| **Container Web Dashboard** | HTTP 200 on `http://localhost:8084/index.html` | HTTP 200 OK | ✅ PASS |
| **Container REST APIs** | `/residents` and `/bills` functional | Responding with HTTP 200 & active DB records | ✅ PASS |
| **Container Health Check** | Scripted health verification with retries | `scripts/docker-healthcheck.ps1` passed | ✅ PASS |
| **Existing Automated Tests** | All 14 tests pass | 14/14 PASS (9 unit + 5 Selenium) | ✅ PASS |
| **Week 8 Nginx Reverse Proxy** | Port 8081 remains operational | `http://localhost:8081/index.html` returns HTTP 200 | ✅ PASS |
| **Jenkins CI Server** | Port 8080 remains operational | `http://localhost:8080/` responsive | ✅ PASS |
| **Git Release Tag v1.0** | Untouched at commit `251962d` | `git cat-file -p v1.0` -> `251962dc90e...` | ✅ PASS |

---

## 7. Traceability Record

- **Jenkins Build Number:** `14`
- **Docker Image Name:** `housing-society-billing-portal`
- **Docker Image Tag:** `build-14` (also tagged `:latest`)
- **Docker Image ID:** `4fdec1e318fe`
- **Container Name:** `housing-billing-cd`
- **Container ID:** `ab865147fb3a`
- **Host Port Mapping:** `0.0.0.0:8084 -> 8080/tcp`
- **Container Health Check URL:** `http://localhost:8084/index.html`
- **Git Branch:** `feature/week12-jenkins-docker-cd`
- **Commit Hash:** `6514199`
- **Git Tag `v1.0`:** Points to commit `251962d` (untouched)
