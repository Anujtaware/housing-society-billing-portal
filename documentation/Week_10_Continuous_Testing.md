# Week 10 — Continuous Testing in Jenkins

## 1. Objective
The objective of Week 10 is to implement **Continuous Testing in Jenkins** for the Housing Society Billing Portal. This integrates the automated UI end-to-end (E2E) Selenium test suite (developed in Week 9) directly into the automated Jenkins CI/CD deployment pipeline. 

Automated UI validation acts as a quality gate ensuring that the application deployed to Nginx and Spring Boot is fully functional before declaring the deployment successful. If any Selenium UI test fails, the pipeline halts immediately, returns a non-zero exit code, marks the build as `FAILURE`, captures diagnostic screenshots as Jenkins artifacts, and publishes standard JUnit/Surefire test reports.

---

## 2. Architecture & Pipeline Design

### 2.1 Pipeline Execution Flow
The pipeline follows a strict, sequential CI/CD progression:

```
Checkout
   │
   ▼
Build (mvn clean compile)
   │
   ▼
Unit & Integration Tests (mvn test -Dtest=!SeleniumE2ETests [9 tests])
   │
   ▼
Package (mvn package -DskipTests=true [Spring Boot JAR])
   │
   ▼
Deploy (scripts/deploy.ps1 [Spring Boot 8082 + Nginx Reverse Proxy 8081])
   │
   ▼
Health Check (scripts/healthcheck.ps1 [HTTP 200 on port 8081])
   │
   ▼
Selenium Tests (mvn test -Dtest=SeleniumE2ETests -Dapp.url=... [5 E2E tests])
   │
   ▼
Post Actions (Archive JAR, Archive Failure Screenshots, Publish JUnit/Surefire XML)
```

### 2.2 Jenkinsfile Configuration
The declarative pipeline in [`Jenkinsfile`](file:///d:/Housing-Society-Billing-Portal/Jenkinsfile) was updated as follows:

```groovy
pipeline {
    agent any

    parameters {
        choice(name: 'DEPLOY_ENV', choices: ['DEV', 'TEST', 'STAGING', 'PROD'], description: 'Deployment Target Environment')
        string(name: 'DEPLOY_PORT', defaultValue: '8081', description: 'Nginx Web Server / Reverse Proxy Port')
        string(name: 'APP_PORT', defaultValue: '8082', description: 'Spring Boot Application Port')
    }

    environment {
        JENKINS_NODE_COOKIE = 'dontKillMe'
    }

    options {
        disableConcurrentBuilds()
    }

    triggers {
        // Poll SCM every minute for changes pushed to GitHub
        pollSCM('* * * * *')
    }

    stages {
        stage('Checkout') {
            steps {
                echo '=== Stage 1: Checkout Housing Society Billing Portal ==='
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo '=== Stage 2: Clean & Compile Maven Project ==='
                bat 'mvn clean compile -f application/pom.xml'
            }
        }

        stage('Unit Tests') {
            steps {
                echo '=== Stage 3: Run Unit & Integration Tests ==='
                bat 'mvn test -Dtest=!SeleniumE2ETests -f application/pom.xml'
            }
        }

        stage('Package') {
            steps {
                echo '=== Stage 4: Package Spring Boot Executable JAR ==='
                bat 'mvn package -DskipTests=true -f application/pom.xml'
            }
        }

        stage('Deploy') {
            steps {
                echo "=== Stage 5: Deploy Application to Nginx Reverse Proxy (Env: ${params.DEPLOY_ENV}) ==="
                bat "powershell -ExecutionPolicy Bypass -File scripts/deploy.ps1 -EnvName ${params.DEPLOY_ENV} -DeployPort ${params.DEPLOY_PORT} -AppPort ${params.APP_PORT} -JarPath application/target/billing-portal-0.0.1-SNAPSHOT.jar"
            }
        }

        stage('Health Check') {
            steps {
                echo "=== Stage 6: Verify Deployed Application Health (Port: ${params.DEPLOY_PORT}) ==="
                bat "powershell -ExecutionPolicy Bypass -File scripts/healthcheck.ps1 -DeployPort ${params.DEPLOY_PORT} -MaxRetries 30 -DelaySeconds 2"
            }
        }

        stage('Selenium Tests') {
            steps {
                echo "=== Stage 7: Run Selenium Automated End-to-End UI Tests (Port: ${params.DEPLOY_PORT}) ==="
                bat "mvn test -Dtest=SeleniumE2ETests -Dapp.url=http://localhost:${params.DEPLOY_PORT}/index.html -f application/pom.xml"
            }
        }
    }

    post {
        always {
            echo '=== Archiving Build Artifact, Screenshots & Test Reports ==='
            archiveArtifacts artifacts: 'application/target/*.jar', fingerprint: true, allowEmptyArchive: false
            archiveArtifacts artifacts: 'application/test-results/screenshots/*.png', allowEmptyArchive: true
            junit testResults: 'application/target/surefire-reports/*.xml', allowEmptyResults: true
        }
        success {
            echo "✅ Week 10 Continuous Testing Pipeline completed successfully!"
            echo "🚀 Application is verified & live at: http://localhost:${params.DEPLOY_PORT}/index.html"
        }
        failure {
            echo "❌ Week 10 Continuous Testing Pipeline failed!"
        }
    }
}
```

---

## 3. Selenium & Browser Environment in Jenkins

### 3.1 Browser Provisioning
- **Target Browser:** Google Chrome (`154.0.8037.99`)
- **Driver Provisioning:** Automated by **Selenium Manager** (part of Selenium Java `4.28.1`), eliminating manual driver downloading or external third-party Jenkins plugins.
- **Service Account Compatibility:** When Jenkins runs as a Windows background service under the `LocalSystem` account, interactive desktop sessions are unavailable. Selenium was specifically configured with flags enabling reliable headless operation:
  - `--headless=new` (modern Chrome headless mode)
  - `--no-sandbox`
  - `--disable-dev-shm-usage`
  - `--disable-gpu`
  - `--remote-allow-origins=*`
  - `--window-size=1920,1080`
- **Fallback Support:** Fallback to Microsoft Edge headless is built into [`SeleniumE2ETests.java`](file:///d:/Housing-Society-Billing-Portal/application/src/test/java/com/housing/billing/SeleniumE2ETests.java) if Chrome is not detected.

### 3.2 Dynamic Application URL
The Selenium tests dynamically bind to the deployed port configured in the Jenkins build parameters via:
```bash
-Dapp.url=http://localhost:${params.DEPLOY_PORT}/index.html
```

---

## 4. Test Reporting & Artifact Management

### 4.1 Maven Surefire Report Aggregation
- **Unit & Integration Tests (Stage 3):**
  `mvn test -Dtest=!SeleniumE2ETests -f application/pom.xml` generates Surefire XML reports for:
  - `TEST-com.housing.billing.BillingPortalApplicationTests.xml` (1 test)
  - `TEST-com.housing.billing.Week6ValidationAndReceiptTests.xml` (8 tests)
- **Selenium UI Tests (Stage 7):**
  `mvn test -Dtest=SeleniumE2ETests -Dapp.url=... -f application/pom.xml` generates:
  - `TEST-com.housing.billing.SeleniumE2ETests.xml` (5 tests)
- Both sets of reports are preserved in `application/target/surefire-reports/`.
- In the `post` block, Jenkins publishes the combined test results using:
  ```groovy
  junit testResults: 'application/target/surefire-reports/*.xml', allowEmptyResults: true
  ```
- **Total Published Test Cases:** **14 tests (14 Passed, 0 Failed, 0 Skipped)**.

### 4.2 Screenshot Handling on Failure
- JUnit 5 `ScreenshotOnFailureWatcher` in [`SeleniumE2ETests.java`](file:///d:/Housing-Society-Billing-Portal/application/src/test/java/com/housing/billing/SeleniumE2ETests.java) captures a PNG screenshot if any test step fails:
  ```
  application/test-results/screenshots/FAILED_{testMethodName}_{timestamp}.png
  ```
- The Jenkins pipeline automatically archives these screenshots as build artifacts:
  ```groovy
  archiveArtifacts artifacts: 'application/test-results/screenshots/*.png', allowEmptyArchive: true
  ```
- This ensures test failures have immediate visual diagnostic evidence directly inside the Jenkins build UI.

---

## 5. Deliberate Failure Verification (Quality Gate Testing)

As mandated by Week 10 requirements, a deliberate assertion failure was introduced to verify that a failing UI test halts the Jenkins pipeline and prevents a false-positive build report.

### 5.1 Deliberate Defect Injection
In commit `82b5b5b`, an intentional incorrect expectation was introduced in [`SeleniumE2ETests.java`](file:///d:/Housing-Society-Billing-Portal/application/src/test/java/com/housing/billing/SeleniumE2ETests.java):
```java
// Deliberate test failure for Week 10 verification
assertEquals("DELIBERATE_TEST_FAILURE_INTENTIONAL_INCORRECT_TITLE", driver.getTitle(),
        "Page title should match portal title");
```

### 5.2 Jenkins Build #10 Results (FAILURE Verified)
- **Trigger:** SCM polling triggered Build #10 on commit `82b5b5b`.
- **Stages 1–6:** Checkout, Build, Unit Tests (9 passed), Package, Deploy, and Health Check completed successfully.
- **Stage 7 (Selenium Tests):**
  - Scenario 1 (`testDashboardLoadsSuccessfully`) failed with:
    `org.opentest4j.AssertionFailedError: Page title should match portal title ==> expected: <DELIBERATE_TEST_FAILURE_INTENTIONAL_INCORRECT_TITLE> but was: <Housing Society Billing Portal>`
  - Failure screenshot automatically created:
    `application/test-results/screenshots/FAILED_testDashboardLoadsSuccessfully_20261012_152238.png`
  - Maven Surefire exited with status code `1`.
  - Jenkins intercepted the non-zero exit code and marked Stage 7 as `FAILED`.
- **Post Action Execution:**
  - Build artifact JAR archived.
  - Failure screenshot `FAILED_testDashboardLoadsSuccessfully_20261012_152238.png` was archived into Jenkins build artifacts.
  - JUnit test report recorded 1 test failure.
- **Overall Build Status:** **FAILURE** (Pipeline safely halted).

---

## 6. Recovery & Final Verification

### 6.1 Reverting Intentional Defect
In commit `8aa9e24`, the deliberate failure was reverted:
```java
assertEquals("Housing Society Billing Portal", driver.getTitle(),
        "Page title should match portal title");
```

### 6.2 Jenkins Build #11 Results (SUCCESS Verified)
- **Trigger:** SCM polling automatically triggered Build #11 on commit `8aa9e24`.
- **Stage 1 (Checkout):** Revision `8aa9e24` checked out.
- **Stage 2 (Build):** Clean compilation succeeded.
- **Stage 3 (Unit Tests):** 9/9 tests passed (0 failures).
- **Stage 4 (Package):** `billing-portal-0.0.1-SNAPSHOT.jar` packaged.
- **Stage 5 (Deploy):** Process deployed to port 8082, Nginx reloaded on port 8081.
- **Stage 6 (Health Check):** HTTP 200 OK verified on `http://localhost:8081/index.html`.
- **Stage 7 (Selenium Tests):** All 5 Selenium E2E UI scenarios executed and passed in headless Chrome:
  - `testDashboardLoadsSuccessfully` — PASSED
  - `testAddResidentE2E` — PASSED
  - `testAddBillE2E` — PASSED
  - `testPayBillE2E` — PASSED
  - `testViewBillReceiptE2E` — PASSED
- **Post Actions:**
  - Artifacts archived (`billing-portal-0.0.1-SNAPSHOT.jar`).
  - Surefire results published: **14 tests run, 14 passed, 0 failures, 0 skipped**.
- **Overall Build Status:** **SUCCESS**.

---

## 7. Verification Summary Matrix

| Verification Criterion | Expected | Observed | Status |
|---|---|---|---|
| A. Existing Week 6/JUnit tests | 9 tests pass | 9 / 9 passed | ✅ PASS |
| B. Selenium E2E tests | 5 tests pass | 5 / 5 passed | ✅ PASS |
| C. Jenkins Integration Build | 14/14 tests pass | Build #9 SUCCESS (14/14 passed) | ✅ PASS |
| D. Deliberate Failure Build | Selenium test fails & halts pipeline | Build #10 FAILURE (Maven exit code 1) | ✅ PASS |
| E. Failure Screenshot Saved | Screenshot archived as build artifact | `FAILED_testDashboardLoadsSuccessfully_*.png` archived | ✅ PASS |
| F. Revert Deliberate Defect | Working assertions restored | Reverted in commit `8aa9e24` | ✅ PASS |
| G. Final Jenkins Build | 14/14 tests pass | Build #11 SUCCESS (14/14 passed) | ✅ PASS |
| H. Web UI Accessible | Accessible at port 8081 | `http://localhost:8081/index.html` HTTP 200 OK | ✅ PASS |
| I. Jenkins Surefire Publishing | Aggregates all test suites | 14 total tests displayed in Jenkins JUnit | ✅ PASS |
| J. Git Working Tree Clean | No uncommitted modifications | Clean tree on `feature/week10-continuous-testing` | ✅ PASS |
| K. Tag `v1.0` Integrity | Points to commit `251962d` | Verified `251962d` unchanged | ✅ PASS |

---

## 8. Files Changed in Week 10
- [`Jenkinsfile`](file:///d:/Housing-Society-Billing-Portal/Jenkinsfile) — Added `Selenium Tests` stage, test suite splitting, screenshot and Surefire XML artifact archiving.
- [`documentation/Week_10_Continuous_Testing.md`](file:///d:/Housing-Society-Billing-Portal/documentation/Week_10_Continuous_Testing.md) — Comprehensive Week 10 technical documentation and evidence report.

---

## 9. Git Information
- **Branch:** `feature/week10-continuous-testing`
- **Key Commits:**
  - `ff4d100` — `ci: integrate selenium continuous testing`
  - `82b5b5b` — `test: introduce deliberate assertion failure for Week 10 CI verification`
  - `8aa9e24` — `ci: revert deliberate failure and restore verified assertion`
- **Release Tag:** `v1.0` intact at `251962d`.
