# Week 9 — Selenium Test Design and Local Execution

## 1. Objective
Design and implement automated end-to-end (E2E) UI test scenarios using **Selenium WebDriver** for the Housing Society Billing Portal. Verify critical end-to-end user journeys against the deployed application, ensure assertions validate expected functional behavior, implement automated failure screenshot capture, ensure database safety and isolation, and enable smooth execution through Maven.

---

## 2. Test Environment & Architecture
- **Web Application URL:** `http://localhost:8081/index.html` (Nginx Reverse Proxy on port 8081 proxying to Spring Boot on port 8082)
- **Backend Service:** Spring Boot 4.1.1 (Java 25) with MySQL 8.0
- **Selenium Version:** Selenium WebDriver `4.28.1` (`org.seleniumhq.selenium:selenium-java:4.28.1`)
- **Browser & Driver:**
  - Google Chrome (Headless mode via `--headless=new`, version 154.0.8037.99)
  - Driver management handled automatically by built-in **Selenium Manager** (with Microsoft Edge Headless fallback)
  - Options: `--headless=new`, `--no-sandbox`, `--disable-dev-shm-usage`, `--disable-gpu`, `--window-size=1920,1080`
- **Test Framework:** JUnit 5 (`org.junit.jupiter`) with Surefire test runner

---

## 3. End-to-End Test Scenarios

### Scenario 1: Dashboard UI and Summary Metrics Load Correctly
- **Goal:** Verify that the main portal page loads with all expected header elements, action buttons, summary cards, and tables.
- **Steps:**
  1. Navigate to `http://localhost:8081/index.html`.
  2. Wait for page header `<h1>` to become visible.
- **Assertions:**
  - Page title equals `"Housing Society Billing Portal"`.
  - Header `h1` contains `"Housing Society Billing Portal"`.
  - Action buttons `+ Add Resident` (`#openResidentModal`) and `+ Add Bill` (`#openBillModal`) are displayed and clickable.
  - Summary cards (`#residentCount`, `#billCount`, `#totalAmount`) are rendered.
  - `#residentTable` and `#billTable` exist in the DOM.

### Scenario 2: Add New Resident via Modal
- **Goal:** Verify resident creation workflow through the modal form and subsequent table update.
- **Steps:**
  1. Click `#openResidentModal`.
  2. Verify `#residentModal` has class `open`.
  3. Enter Name (`"Selenium Test Resident"`), Flat (`"SEL-901"`), Phone (`"9988776655"`), Email (`"selenium.resident@society.com"`).
  4. Click `#submitResidentBtn`.
  5. Wait for toast notification (`#toast.show`).
- **Assertions:**
  - Toast text contains `"added successfully"`.
  - `#residentTable` contains row with flat number `"SEL-901"`.
  - Captured resident ID is valid and non-null.

### Scenario 3: Create Monthly Bill for Resident
- **Goal:** Verify bill creation linked to the newly created resident.
- **Steps:**
  1. Click `#openBillModal`.
  2. Verify `#billModal` has class `open`.
  3. Enter Resident ID, Bill Month (`"SeleniumMonth 2026"`), Maintenance (`3000`), Electricity (`1500`).
  4. Click `#submitBillBtn`.
  5. Wait for toast notification.
- **Assertions:**
  - Toast text contains `"added"`.
  - `#billTable` contains a new row for `"SeleniumMonth 2026"` matching the resident ID.
  - Captured bill ID is valid and non-null.

### Scenario 4: Bill Payment & Status Transition
- **Goal:** Verify marking an unpaid bill as paid updates its status and UI controls.
- **Steps:**
  1. Locate bill row for the created bill ID in `#billTable`.
  2. Verify initial status badge displays `"UNPAID"`.
  3. Click `.btn-pay` ("Mark Paid") button on that row.
  4. Wait for toast notification.
- **Assertions:**
  - Toast confirms bill marked as `"PAID"`.
  - Bill row status text updates to `"PAID"`.
  - `.btn-pay` button is removed/hidden for the paid bill.

### Scenario 5: View Bill Receipt Modal
- **Goal:** Verify official receipt modal displays all expected billing and resident data.
- **Steps:**
  1. Click `.btn-receipt` ("Receipt") button on the paid bill row.
  2. Wait for `#receiptModal` to open.
- **Assertions:**
  - `#receiptStatusBadge` displays `"PAID"`.
  - `#rResidentName` equals `"Selenium Test Resident"`.
  - `#rFlatNumber` equals `"SEL-901"`.
  - `#rBillMonth` equals `"SeleniumMonth 2026"`.
  - `#rTotal` contains `"4500"`.
  - `#rPayDate` contains valid payment date (not `"Pending"`).
  - Action buttons `#printReceiptBtn` and `#downloadReceiptBtn` are visible.
  - Close button dismisses the modal.

---

## 4. Database Safety and Cleanup
- **Isolation:** Test records use distinct prefixes (`SEL-901`, `Selenium Test Resident`, `SeleniumMonth 2026`) preventing confusion with production data.
- **Automated Cleanup:** In `@AfterAll`, the test suite executes REST API delete calls (`DELETE /bills/{id}` and `DELETE /residents/{id}`) to remove the test bill and resident. Existing society data remains completely intact.

---

## 5. Failure Screenshot Strategy
- Registered JUnit 5 `TestWatcher` extension: `ScreenshotOnFailureWatcher`.
- On test failure (`testFailed`), automatically takes a screenshot via `((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE)` and writes it to `application/test-results/screenshots/FAILED_{testMethod}_{timestamp}.png`.
- Baseline demonstration screenshot captured in Scenario 1: `application/test-results/screenshots/dashboard_loaded_baseline_*.png`.

---

## 6. Execution Commands & Verification Results

### Run Selenium Tests Alone:
```bash
mvn test -Dtest=SeleniumE2ETests -f application/pom.xml
```
**Result:** 5 / 5 tests passed (0 failures, 0 errors, time: ~8.9s).

### Run Complete Test Suite (Existing Week 6 + Selenium Week 9):
```bash
mvn test -f application/pom.xml
```
**Result:**
- `BillingPortalApplicationTests`: 1 passed
- `Week6ValidationAndReceiptTests`: 8 passed
- `SeleniumE2ETests`: 5 passed
- **Total:** **14 / 14 tests passed, 0 failures, 0 errors, 0 skipped.**
- **Surefire Reports:** Generated in `application/target/surefire-reports/`.

---

## 7. Limitations & Recommendations
- Headless execution requires Chrome or Edge browser installed on the host system.
- Application must be running and accessible at `http://localhost:8081/index.html` (or configured via `-Dapp.url=...`).
