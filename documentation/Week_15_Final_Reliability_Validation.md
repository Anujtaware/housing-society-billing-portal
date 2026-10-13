# Week 15 — Final End-to-End Reliability Validation

## 1. Objective
The objective of Week 15 is to perform the **Final End-to-End Reliability Validation** of the complete 15-week DevOps pipeline and infrastructure for the **Housing Society Billing Portal**.

As the culmination of the entire project:
- Weeks 1–14 remain completely preserved without redos, duplicates, or breaking changes.
- Validated all operational infrastructure components: Jenkins CI/CD (`8080`), Nginx Reverse Proxy (`8081`), Local Spring Boot (`8082`), Docker CD Application (`8084`), and MySQL Database (`3306`).
- Verified the complete automated test suite: 14/14 tests passing (9 unit/integration + 5 Selenium UI end-to-end tests).
- Validated the Jenkins Declarative Pipeline as Code (`Jenkinsfile`) through all 10 stages and post actions.
- Conducted Ansible configuration and automated self-healing provisioning validation with Check Mode, initial execution, and idempotency verification.
- Performed a safe, reversible failure simulation (container stoppage), demonstrated automated self-healing remediation via Ansible, and verified post-recovery idempotency (`changed=0`).
- Validated the 5-scenario Selenium UI test suite directly against the containerized Docker CD deployment on port `8084`.
- Preserved historical Git release tag `v1.0` permanently pointing to commit `251962dc90e85b789d8676cc34f6587e6109b601`.
- Verified a clean working tree and pushed `feature/week15-final-validation` to GitHub without force pushes.

---

## 2. Complete System Architecture

The following diagram illustrates the complete, integrated DevOps lifecycle architecture established across the 15 weeks:

```
+---------------------------------------------------------------------------------------------------+
|                                  Housing Society Billing Portal                                   |
|                                     End-to-End DevOps Pipeline                                    |
+---------------------------------------------------------------------------------------------------+

   Developer Workstation / Feature Branch (feature/week15-final-validation)
                        │
                        ▼ (git push / SCM Poll)
              GitHub Repository
                        │
                        ▼ (Trigger: pollSCM '* * * * *')
              Jenkins CI/CD Server (Port 8080)
                        │
                        ├─ Stage 1: Checkout SCM
                        ├─ Stage 2: Compile (mvn clean compile)
                        ├─ Stage 3: Unit & Integration Tests (mvn test: 9/9 PASS)
                        ├─ Stage 4: Package (mvn package: Spring Boot JAR)
                        ├─ Stage 5: Local Deploy (scripts/deploy.ps1 -> Spring Boot on 8082)
                        ├─ Stage 6: Local Health Check (scripts/healthcheck.ps1 -> Nginx 8081)
                        ├─ Stage 7: Selenium UI Tests (mvn test: 5/5 PASS via Port 8081)
                        ├─ Stage 8: Docker Build (Image: housing-society-billing-portal:build-14)
                        ├─ Stage 9: Docker Deploy (scripts/docker-deploy.ps1 -> Container on 8084)
                        ├─ Stage 10: Docker Health Check (scripts/docker-healthcheck.ps1 -> Port 8084)
                        └─ Post Actions: Archive Artifacts, Screenshots & JUnit XML Reports
                        │
                        ▼
   +─────────────────────────────────────────────────────────────+
   |                  Live Infrastructure Topology               |
   +─────────────────────────────────────────────────────────────+
   |                                                             |
   |   [Port 8080] Jenkins CI Server (Pipeline Orchestrator)     |
   |                                                             |
   |   [Port 8081] Nginx Reverse Proxy (Production Web Entry)    |
   |          │                                                  |
   |          ▼                                                  |
   |   [Port 8082] Local Spring Boot Server (JAR Runtime)        |
   |          │                                                  |
   |   [Port 8084] Docker CD Deployment (housing-billing-cd)     |
   |          │                                                  |
   |          ▼ (Networking: host.docker.internal:3306)          |
   |   [Port 3306] MySQL Database (housing_billing schema)       |
   |                                                             |
   +─────────────────────────────────────────────────────────────+
                        ▲
                        │ (Audits, Provisions, Heals)
   +─────────────────────────────────────────────────────────────+
   |   Ansible Automation Controller (WSL2 Ubuntu 26.04)         |
   |   - Playbook: ansible/provision.yml                         |
   |   - Pre-flight assertions (Java 25, Docker 29.8.2)          |
   |   - Configuration management (/opt/housing-billing-portal)  |
   |   - Self-healing container remediation                      |
   |   - Continuous health auditing across all 6 endpoints       |
   +─────────────────────────────────────────────────────────────+
```

---

## 3. Infrastructure & Runtime Environment

| Infrastructure Component | Host / Port | Version / Runtime | Operational Role |
|---|---|---|---|
| **Jenkins CI Server** | `http://localhost:8080` | Jenkins `2.568.3` | CI/CD Declarative Pipeline Automation |
| **Nginx Web Server** | `http://localhost:8081` | Nginx `1.26.2` | Production Web Entry & Reverse Proxy |
| **Local Spring Boot** | `http://localhost:8082` | Spring Boot `4.1.1` (Java 25) | Native Host Application Deployment |
| **Docker CD Container** | `http://localhost:8084` | Container `housing-billing-cd` | Containerized Continuous Deployment |
| **MySQL Database** | `localhost:3306` | MySQL `8.0` | Relational Persistence (`housing_billing`) |
| **Docker Engine** | Windows Named Pipe / WSL2 | Docker `29.8.2`, API `1.56` | Container Runtime & Image Management |
| **Ansible Controller** | WSL2 Ubuntu 26.04 | Ansible Core `2.20.1`, Python `3.14.4`| Automated Provisioning & Self-Healing |
| **Browser Testing** | Headless Chrome | Chrome `154.0.8037.99` / Selenium `4.28.1` | Automated UI Quality Gates |

---

## 4. Automated Test Suite Validation

The full test suite consists of 14 automated tests executed via Maven Surefire:
- **Command:** `mvn test -f application/pom.xml`
- **Result:** `BUILD SUCCESS` (Total time: 27.129 s)

### 4.1 Test Execution Breakdown

| Test Suite Class | Suite Category | Total Tests | Passed | Failed | Errors | Skipped | Time |
|---|---|---|---|---|---|---|---|
| `BillingPortalApplicationTests` | Spring Context / Integration | 1 | 1 | 0 | 0 | 0 | 8.38 s |
| `Week6ValidationAndReceiptTests`| Unit / Validation / Lifecycle | 8 | 8 | 0 | 0 | 0 | 0.77 s |
| `SeleniumE2ETests` | Selenium UI End-to-End | 5 | 5 | 0 | 0 | 0 | 12.10 s |
| **Total Test Suite** | **Comprehensive Regression** | **14** | **14** | **0** | **0** | **0** | **21.25 s** |

### 4.2 Individual Test Scenarios Verified

1. `testHomeControllerRedirect` — Redirects `/` to `/index.html` (PASS)
2. `testValidResident` — Valid resident model satisfies all Bean Validation constraints (PASS)
3. `testInvalidResident` — Blank and malformed resident fields trigger validation violations (PASS)
4. `testValidBill` — Valid bill model passes validation (PASS)
5. `testInvalidBill` — Missing foreign key and negative amounts fail validation (PASS)
6. `testResidentLifecycle` — Resident creation, retrieval, and deletion lifecycle (PASS)
7. `testBillLifecycleAndReceipt` — Bill creation, total calculation, mark-as-paid, and receipt generation (PASS)
8. `testExceptionHandlerResponseStatus` — Global exception handling emits structured JSON (PASS)
9. `contextLoads` — Spring Boot application context initializes cleanly (PASS)
10. `testDashboardLoadsSuccessfully` (Selenium Scenario 1) — UI loads with metrics, forms, and tables (PASS)
11. `testAddResidentE2E` (Selenium Scenario 2) — Modal submission adds resident dynamically (PASS)
12. `testAddBillE2E` (Selenium Scenario 3) — Bill modal adds bill associated with resident (PASS)
13. `testPayBillE2E` (Selenium Scenario 4) — Payment action transitions bill UNPAID -> PAID (PASS)
14. `testViewBillReceiptE2E` (Selenium Scenario 5) — Receipt modal displays accurate receipt details (PASS)

---

## 5. Jenkins Declarative Pipeline Validation

The pipeline defined in [`Jenkinsfile`](file:///d:/Housing-Society-Billing-Portal/Jenkinsfile) orchestrates the entire continuous delivery process.

### 5.1 Pipeline Verification Details

- **Jenkins Job:** `Housing-Society-Billing-Portal`
- **Build Number:** `14`
- **Trigger:** SCM polling (`* * * * *`)
- **Commit Used:** `6514199935fcbd2721aa72bbb74fcbe0fc6aede6` (`6514199`)
- **Docker Image Produced:** `housing-society-billing-portal:build-14` (and `housing-society-billing-portal:latest`)
- **Docker Image ID:** `4fdec1e318fe`
- **Docker Container:** `housing-billing-cd`
- **Deployment Ports:**
  - Nginx Reverse Proxy: `http://localhost:8081/index.html`
  - Spring Boot Host App: `http://localhost:8082/index.html`
  - Docker CD App: `http://localhost:8084/index.html`
- **Final Result:** `SUCCESS`

### 5.2 Verified Stages Flow

```
1. Checkout             ──> Clones Git repo at targeted commit
2. Compile              ──> Executes 'mvn clean compile -f application/pom.xml'
3. Unit Tests           ──> Executes 'mvn test -Dtest=!SeleniumE2ETests' (9 tests pass)
4. Package              ──> Packages executable JAR 'billing-portal-0.0.1-SNAPSHOT.jar'
5. Deploy               ──> Executes 'scripts/deploy.ps1' (Spring Boot 8082 + Nginx 8081)
6. Health Check         ──> Executes 'scripts/healthcheck.ps1' (Port 8081 HTTP 200 OK)
7. Selenium Tests       ──> Executes 'mvn test -Dtest=SeleniumE2ETests' (5 tests pass)
8. Docker Build         ──> Executes 'docker build' -> ':build-14' & ':latest'
9. Docker Deploy        ──> Executes 'scripts/docker-deploy.ps1' -> Port 8084
10. Docker Health Check ──> Executes 'scripts/docker-healthcheck.ps1' (Port 8084 HTTP 200 OK)
11. Post Actions        ──> Archives JAR, screenshots, and publishes JUnit reports (14/14)
```

---

## 6. Ansible Provisioning & Reliability Validation

Configuration management and self-healing reliability are automated via [`ansible/provision.yml`](file:///d:/Housing-Society-Billing-Portal/ansible/provision.yml) and executed via [`scripts/run-ansible.ps1`](file:///d:/Housing-Society-Billing-Portal/scripts/run-ansible.ps1).

### 6.1 Check Mode Validation (`--check`)
- **Command:**
  ```powershell
  powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1 -Playbook provision.yml -CheckMode
  ```
- **Execution Log Highlights:**
  - Directory structure assertions passed for `/opt/housing-billing-portal/{config,deployments,logs,backups}`.
  - Java runtime verified: `java version "25" 2025-09-16 LTS`.
  - Docker runtime verified: Docker Engine `29.8.2`.
  - Templates verified matching target state (`application-env.properties`, `portal-environment.json`).
  - Container inspection: `Container 'housing-billing-cd' status: Exists=True, Running=True`. Remediation skipped.
  - Health checks verified all 6 endpoints returning HTTP 200.
- **PLAY RECAP:**
  ```text
  PLAY RECAP *********************************************************************
  localhost                  : ok=21   changed=0    unreachable=0    failed=0    skipped=3    rescued=0    ignored=0
  ```

### 6.2 Normal Execution (Baseline)
- **Command:**
  ```powershell
  powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1 -Playbook provision.yml
  ```
- **PLAY RECAP:**
  ```text
  PLAY RECAP *********************************************************************
  localhost                  : ok=21   changed=0    unreachable=0    failed=0    skipped=3    rescued=0    ignored=0
  ```

### 6.3 Pre-Failure Idempotency Verification
- **Command:**
  ```powershell
  powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1 -Playbook provision.yml
  ```
- **PLAY RECAP:**
  ```text
  PLAY RECAP *********************************************************************
  localhost                  : ok=21   changed=0    unreachable=0    failed=0    skipped=3    rescued=0    ignored=0
  ```
- **Idempotency Proof:** `changed=0`. No unintended state drift or resource re-creation.

---

## 7. Failure Simulation & Self-Healing Recovery Demonstration

To demonstrate pipeline and infrastructure resilience, an actual, safe, and reversible failure was simulated against the containerized CD deployment.

### 7.1 Pre-Failure Status Confirmation
```powershell
curl.exe -I http://localhost:8084/index.html
# Output: HTTP/1.1 200 OK
```

### 7.2 Safe Failure Injection
The CD container was stopped:
```powershell
docker stop housing-billing-cd
```

### 7.3 Outage Verification
```powershell
curl.exe -I --connect-timeout 2 http://localhost:8084/index.html
# Output: curl: (28) Connection timed out after 2014 milliseconds
```
Port `8084` confirmed offline and unresponsive.

### 7.4 Automated Self-Healing Execution
The Ansible reliability playbook was executed:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1 -Playbook provision.yml
```

**Actual Execution Evidence:**
```text
TASK [Inspect Docker CD container status] **************************************
ok: [localhost]

TASK [Set container operational facts] *****************************************
ok: [localhost]

TASK [Display detected container status] ***************************************
ok: [localhost] => {
    "msg": "Container 'housing-billing-cd' status: Exists=True, Running=False"
}

TASK [Remove stopped/stale container if present] *******************************
changed: [localhost]

TASK [Launch recovered Docker CD container] ************************************
changed: [localhost]

TASK [Wait for recovered container application endpoint to become ready] *******
FAILED - RETRYING: [localhost]: Wait for recovered container application endpoint to become ready (30 retries left).
FAILED - RETRYING: [localhost]: Wait for recovered container application endpoint to become ready (29 retries left).
FAILED - RETRYING: [localhost]: Wait for recovered container application endpoint to become ready (28 retries left).
FAILED - RETRYING: [localhost]: Wait for recovered container application endpoint to become ready (27 retries left).
ok: [localhost]

TASK [Reliability Check - Verify Docker CD deployment endpoint (Port 8084)] ****
ok: [localhost]

TASK [Reliability Check - Verify Docker CD /residents database REST API] *******
ok: [localhost]

TASK [Reliability Check - Verify Docker CD /bills database REST API] ***********
ok: [localhost]

TASK [Reliability Check - Verify Local Spring Boot deployment endpoint (Port 8082)] ***
ok: [localhost]

TASK [Reliability Check - Verify Nginx reverse proxy endpoint (Port 8081)] *****
ok: [localhost]

TASK [Reliability Check - Verify Jenkins CI server endpoint (Port 8080)] *******
ok: [localhost]

PLAY RECAP *********************************************************************
localhost                  : ok=24   changed=2    unreachable=0    failed=0    skipped=0    rescued=0    ignored=0
```

### 7.5 Post-Recovery Health & Data Verification
```powershell
docker ps --filter "name=housing-billing-cd"
# CONTAINER ID   IMAGE                                     STATUS         PORTS
# b78a1674bb3c   housing-society-billing-portal:build-14   Up 30 seconds  0.0.0.0:8084->8080/tcp

curl.exe -I http://localhost:8084/index.html
# HTTP/1.1 200 OK

curl.exe http://localhost:8084/residents
# [{"name":"Anuj Taware","flatNumber":"A-101","phone":"9876543210","email":"anuj@gmail.com","id":1}]

curl.exe http://localhost:8084/bills
# [{"billMonth":"September 2026","electricityAmount":0.0,"id":1,"maintenanceAmount":3000.0,"paymentDate":null,"residentId":1,"status":"PAID","totalAmount":3000.0}]
```
- Container was recreated (`b78a1674bb3c`) and reached healthy status within 4 polling retries (8 seconds).
- Full database persistence confirmed: Resident `Anuj Taware` (Flat `A-101`) and paid bill records intact.

### 7.6 Post-Recovery Idempotency Proof
The playbook was re-run immediately on the recovered infrastructure:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1 -Playbook provision.yml
```
**Actual Output:**
```text
TASK [Display detected container status] ***************************************
ok: [localhost] => {
    "msg": "Container 'housing-billing-cd' status: Exists=True, Running=True"
}

TASK [Remove stopped/stale container if present] *******************************
skipping: [localhost]

TASK [Launch recovered Docker CD container] ************************************
skipping: [localhost]

TASK [Wait for recovered container application endpoint to become ready] *******
skipping: [localhost]

PLAY RECAP *********************************************************************
localhost                  : ok=21   changed=0    unreachable=0    failed=0    skipped=3    rescued=0    ignored=0
```
- **Post-Recovery Idempotency Result:** `changed=0`. The self-healing workflow returns to zero-change equilibrium once health is restored.

---

## 8. Selenium E2E UI Testing on Docker CD Deployment

To prove that containerization did not introduce any regressions into user interaction workflows, the 5 Selenium scenarios were executed directly against the live Docker container:
- **Command:**
  ```powershell
  mvn test -Dtest=SeleniumE2ETests "-Dapp.url=http://localhost:8084/index.html" -f application/pom.xml
  ```
- **Result:** `BUILD SUCCESS` (Total time: 16.516 s)

### 8.1 Scenario Results on Docker Port 8084

| Scenario | Objective | Observed Result | Status |
|---|---|---|---|
| **Scenario 1** | Dashboard loads with metrics, forms, and table containers | Title matches, cards visible, baseline screenshot saved | ✅ PASS |
| **Scenario 2** | Add resident via modal form (`Selenium Test Resident`, `SEL-901`) | Toast confirmed, resident added with ID: `63` | ✅ PASS |
| **Scenario 3** | Create bill for resident (`SeleniumMonth 2026`, Maint: 3000, Elec: 1500) | Toast confirmed, bill added with ID: `41` | ✅ PASS |
| **Scenario 4** | Pay bill and verify status transition | Button clicked, toast confirmed, status transitions to `PAID` | ✅ PASS |
| **Scenario 5** | View receipt modal and verify details | Receipt `REC-` opens, badge shows `PAID`, amount shows `4500.0`, print/download buttons visible | ✅ PASS |
| **Cleanup** | Safe API cleanup of test records | Test bill `#41` and resident `#63` removed via DELETE API | ✅ PASS |

---

## 9. Final End-to-End Service Status Matrix

All services were verified simultaneously at the end of Week 15:

| Service / Layer | Verification Endpoint | Expected | Observed Response | Status |
|---|---|---|---|---|
| **Jenkins CI Server** | `http://localhost:8080/login` | HTTP 200 | `HTTP/1.1 200 OK` | ✅ PASS |
| **Nginx Reverse Proxy** | `http://localhost:8081/index.html` | HTTP 200 | `HTTP/1.1 200 OK` | ✅ PASS |
| **Local Spring Boot** | `http://localhost:8082/index.html` | HTTP 200 | `HTTP/1.1 200 OK` | ✅ PASS |
| **Docker CD Application** | `http://localhost:8084/index.html` | HTTP 200 | `HTTP/1.1 200 OK` | ✅ PASS |
| **REST API: Residents** | `http://localhost:8084/residents` | HTTP 200 + JSON | `HTTP/1.1 200 OK` (1 resident) | ✅ PASS |
| **REST API: Bills** | `http://localhost:8084/bills` | HTTP 200 + JSON | `HTTP/1.1 200 OK` (1 bill) | ✅ PASS |
| **Database Server** | `localhost:3306` (`housing_billing`) | Port open, queries run | Active queries, records returned | ✅ PASS |
| **Docker Container** | `housing-billing-cd` | Up / Running | `running` (`Up 30+ seconds`) | ✅ PASS |
| **Automated Tests** | Maven Surefire Test Suite | 14/14 Pass | `14 passed, 0 failed, 0 skipped` | ✅ PASS |
| **Selenium UI Tests** | Docker Port 8084 UI Suite | 5/5 Pass | `5 passed, 0 failed, 0 skipped` | ✅ PASS |
| **Ansible Check Mode** | `scripts/run-ansible.ps1 -CheckMode` | 0 changes | `ok=21 changed=0 skipped=3` | ✅ PASS |
| **Ansible Self-Healing** | Container stop -> Auto-recovery | Recovery + 200 | `ok=24 changed=2 skipped=0` | ✅ PASS |
| **Ansible Idempotency** | Repeat execution | 0 changes | `ok=21 changed=0 skipped=3` | ✅ PASS |
| **Git Release Tag v1.0** | Commit `251962d` | Untouched | `251962dc90e85b789d8676cc34f6587e6109b601` | ✅ PASS |

---

## 10. Git Repository, Branch & Release Integrity

- **Branch Created / Used:** `feature/week15-final-validation`
- **Working Tree:** Clean (zero untracked or dirty modified files).
- **Git Release Tag `v1.0`:** Permanently intact, pointing to historical commit:
  ```text
  251962dc90e85b789d8676cc34f6587e6109b601
  ```
- **Force Push Used:** No (`--force` was never invoked).
- **Previous Weeks' Branches:** Completely preserved without modification.
- **GitHub Push Status:** Branch `feature/week15-final-validation` pushed to `origin`.

---

## 11. Final Project Status: 15 WEEKS COMPLETE

With all end-to-end reliability tests passing, self-healing remediation validated, full regression testing clean (14/14 tests), containerized CD operating on port `8084`, and zero drift across the entire 15-week infrastructure, the **Housing Society Billing Portal DevOps Project is 100% COMPLETE**.
