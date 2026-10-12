package com.housing.billing;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week 9: Selenium WebDriver Automated End-to-End UI Tests
 * for Housing Society Billing Portal.
 */
@ExtendWith(SeleniumE2ETests.ScreenshotOnFailureWatcher.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SeleniumE2ETests {

    public static class ScreenshotOnFailureWatcher implements TestWatcher {
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            String testMethod = context.getRequiredTestMethod().getName();
            System.err.println("❌ Test Failed: " + testMethod + " -> Capturing screenshot...");
            captureScreenshot("FAILED_" + testMethod);
        }
    }

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static String baseUrl;

    // Test Data identifiers (clearly isolated to prevent database pollution)
    private static final String TEST_RESIDENT_NAME = "Selenium Test Resident";
    private static final String TEST_FLAT_NUMBER = "SEL-901";
    private static final String TEST_PHONE = "9988776655";
    private static final String TEST_EMAIL = "selenium.resident@society.com";
    private static final String TEST_BILL_MONTH = "SeleniumMonth 2026";
    private static final String TEST_MAINTENANCE = "3000";
    private static final String TEST_ELECTRICITY = "1500";
    private static String createdResidentId = null;
    private static String createdBillId = null;

    @BeforeAll
    public static void setUpAll() {
        baseUrl = System.getProperty("app.url", System.getenv().getOrDefault("APP_URL", "http://localhost:8081/index.html"));
        System.out.println("Selenium Tests targeting URL: " + baseUrl);

        driver = createWebDriver();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));
        driver.manage().window().setSize(new Dimension(1920, 1080));
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    private static WebDriver createWebDriver() {
        try {
            System.out.println("Initializing Chrome WebDriver in headless mode...");
            ChromeOptions chromeOptions = new ChromeOptions();
            chromeOptions.addArguments("--headless=new");
            chromeOptions.addArguments("--no-sandbox");
            chromeOptions.addArguments("--disable-dev-shm-usage");
            chromeOptions.addArguments("--disable-gpu");
            chromeOptions.addArguments("--remote-allow-origins=*");
            chromeOptions.addArguments("--window-size=1920,1080");
            return new ChromeDriver(chromeOptions);
        } catch (Exception e) {
            System.err.println("ChromeDriver failed, falling back to EdgeDriver: " + e.getMessage());
            EdgeOptions edgeOptions = new EdgeOptions();
            edgeOptions.addArguments("--headless=new");
            edgeOptions.addArguments("--no-sandbox");
            edgeOptions.addArguments("--disable-dev-shm-usage");
            edgeOptions.addArguments("--disable-gpu");
            edgeOptions.addArguments("--remote-allow-origins=*");
            edgeOptions.addArguments("--window-size=1920,1080");
            return new EdgeDriver(edgeOptions);
        }
    }

    @AfterAll
    public static void tearDownAll() {
        // Safe database cleanup for test records
        try {
            cleanUpTestData();
        } catch (Exception e) {
            System.err.println("Cleanup warning: " + e.getMessage());
        }

        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Scenario 1: Open Dashboard and verify all main UI elements load correctly.
     */
    @Test
    @Order(1)
    @DisplayName("Scenario 1: Dashboard UI and Summary Metrics Load Correctly")
    public void testDashboardLoadsSuccessfully() {
        driver.get(baseUrl);

        // 1. Verify Page Title (Deliberate test failure for Week 10 verification)
        assertEquals("DELIBERATE_TEST_FAILURE_INTENTIONAL_INCORRECT_TITLE", driver.getTitle(), "Deliberate test failure for Week 10 CI demonstration");

        // 2. Verify Header H1
        WebElement headerH1 = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("h1")));
        assertTrue(headerH1.getText().contains("Housing Society Billing Portal"), "Header should contain portal title");

        // 3. Verify Header Actions Buttons
        WebElement addResidentBtn = driver.findElement(By.id("openResidentModal"));
        WebElement addBillBtn = driver.findElement(By.id("openBillModal"));
        assertTrue(addResidentBtn.isDisplayed(), "+ Add Resident button must be visible");
        assertTrue(addBillBtn.isDisplayed(), "+ Add Bill button must be visible");

        // 4. Verify Summary Cards
        WebElement residentCount = driver.findElement(By.id("residentCount"));
        WebElement billCount = driver.findElement(By.id("billCount"));
        WebElement totalAmount = driver.findElement(By.id("totalAmount"));
        assertTrue(residentCount.isDisplayed(), "Resident count card must be displayed");
        assertTrue(billCount.isDisplayed(), "Bill count card must be displayed");
        assertTrue(totalAmount.isDisplayed(), "Total amount card must be displayed");

        // 5. Verify Tables
        WebElement residentTable = driver.findElement(By.id("residentTable"));
        WebElement billTable = driver.findElement(By.id("billTable"));
        assertNotNull(residentTable, "Resident table must be present in DOM");
        assertNotNull(billTable, "Bill table must be present in DOM");

        // 6. Capture baseline screenshot
        captureScreenshot("dashboard_loaded_baseline");
    }

    /**
     * Scenario 2: Add a new resident and verify that it appears in the resident table.
     */
    @Test
    @Order(2)
    @DisplayName("Scenario 2: Add New Resident via Modal and Verify Table Update")
    public void testAddResidentE2E() {
        driver.get(baseUrl);

        // 1. Open Resident Modal
        WebElement openBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("openResidentModal")));
        openBtn.click();

        // 2. Verify Modal is Open
        WebElement modal = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("residentModal")));
        assertTrue(modal.getAttribute("class").contains("open"), "Resident modal must be open");

        // 3. Fill Form Fields
        WebElement nameInput = driver.findElement(By.id("residentName"));
        WebElement flatInput = driver.findElement(By.id("residentFlat"));
        WebElement phoneInput = driver.findElement(By.id("residentPhone"));
        WebElement emailInput = driver.findElement(By.id("residentEmail"));

        nameInput.clear();
        nameInput.sendKeys(TEST_RESIDENT_NAME);
        flatInput.clear();
        flatInput.sendKeys(TEST_FLAT_NUMBER);
        phoneInput.clear();
        phoneInput.sendKeys(TEST_PHONE);
        emailInput.clear();
        emailInput.sendKeys(TEST_EMAIL);

        // 4. Submit Form
        WebElement submitBtn = driver.findElement(By.id("submitResidentBtn"));
        submitBtn.click();

        // 5. Verify Toast Notification
        WebElement toast = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast")));
        wait.until(ExpectedConditions.attributeContains(toast, "class", "show"));
        assertTrue(toast.getText().contains("added successfully"), "Toast should confirm resident added");

        // 6. Verify Resident Appears in Table
        wait.until(driver -> {
            List<WebElement> rows = driver.findElements(By.cssSelector("#residentTable tr"));
            for (WebElement row : rows) {
                if (row.getText().contains(TEST_FLAT_NUMBER)) {
                    // Extract ID from the first cell
                    List<WebElement> cells = row.findElements(By.tagName("td"));
                    if (!cells.isEmpty()) {
                        createdResidentId = cells.get(0).getText().trim();
                    }
                    return true;
                }
            }
            return false;
        });

        assertNotNull(createdResidentId, "Created resident ID should be captured from table");
        System.out.println("Verified resident created with ID: " + createdResidentId);
    }

    /**
     * Scenario 3: Create a monthly maintenance bill for the resident and verify in table.
     */
    @Test
    @Order(3)
    @DisplayName("Scenario 3: Create Bill for Resident and Verify in Bill Table")
    public void testAddBillE2E() {
        assertNotNull(createdResidentId, "Resident ID must be available from previous test");
        driver.get(baseUrl);

        // 1. Open Bill Modal
        WebElement openBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("openBillModal")));
        openBtn.click();

        // 2. Verify Bill Modal is Open
        WebElement modal = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("billModal")));
        assertTrue(modal.getAttribute("class").contains("open"), "Bill modal must be open");

        // 3. Fill Form Fields
        WebElement resIdInput = driver.findElement(By.id("billResidentId"));
        WebElement monthInput = driver.findElement(By.id("billMonth"));
        WebElement maintInput = driver.findElement(By.id("maintenanceAmount"));
        WebElement elecInput = driver.findElement(By.id("electricityAmount"));

        resIdInput.clear();
        resIdInput.sendKeys(createdResidentId);
        monthInput.clear();
        monthInput.sendKeys(TEST_BILL_MONTH);
        maintInput.clear();
        maintInput.sendKeys(TEST_MAINTENANCE);
        elecInput.clear();
        elecInput.sendKeys(TEST_ELECTRICITY);

        // 4. Submit Bill Form
        WebElement submitBtn = driver.findElement(By.id("submitBillBtn"));
        submitBtn.click();

        // 5. Verify Toast Notification
        WebElement toast = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast")));
        wait.until(ExpectedConditions.attributeContains(toast, "class", "show"));
        assertTrue(toast.getText().contains("added"), "Toast should confirm bill added");

        // 6. Verify Bill Appears in Bill Table
        wait.until(driver -> {
            List<WebElement> rows = driver.findElements(By.cssSelector("#billTable tr"));
            for (WebElement row : rows) {
                if (row.getText().contains(TEST_BILL_MONTH) && row.getText().contains(createdResidentId)) {
                    List<WebElement> cells = row.findElements(By.tagName("td"));
                    if (!cells.isEmpty()) {
                        createdBillId = cells.get(0).getText().trim();
                    }
                    return true;
                }
            }
            return false;
        });

        assertNotNull(createdBillId, "Created bill ID should be captured from table");
        System.out.println("Verified bill created with ID: " + createdBillId);
    }

    /**
     * Scenario 4: Pay the bill and verify status transition to PAID.
     */
    @Test
    @Order(4)
    @DisplayName("Scenario 4: Mark Bill as Paid and Verify Status Update")
    public void testPayBillE2E() {
        assertNotNull(createdBillId, "Bill ID must be available from previous test");
        driver.get(baseUrl);

        // 1. Locate the row with our bill ID
        WebElement billRow = wait.until(driver -> {
            List<WebElement> rows = driver.findElements(By.cssSelector("#billTable tr"));
            for (WebElement row : rows) {
                List<WebElement> cells = row.findElements(By.tagName("td"));
                if (!cells.isEmpty() && cells.get(0).getText().trim().equals(createdBillId)) {
                    return row;
                }
            }
            return null;
        });
        assertNotNull(billRow, "Target bill row must exist in table");

        // 2. Verify status is UNPAID before clicking pay
        assertTrue(billRow.getText().contains("UNPAID"), "Bill status must initially be UNPAID");

        // 3. Click the Pay button
        WebElement payBtn = billRow.findElement(By.className("btn-pay"));
        assertNotNull(payBtn, "Pay button must be present on UNPAID bill");
        payBtn.click();

        // 4. Verify Toast Notification
        WebElement toast = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast")));
        wait.until(ExpectedConditions.attributeContains(toast, "class", "show"));
        assertTrue(toast.getText().contains("marked as PAID"), "Toast should confirm bill paid");

        // 5. Verify the bill row status updates to PAID
        wait.until(driver -> {
            List<WebElement> rows = driver.findElements(By.cssSelector("#billTable tr"));
            for (WebElement row : rows) {
                List<WebElement> cells = row.findElements(By.tagName("td"));
                if (!cells.isEmpty() && cells.get(0).getText().trim().equals(createdBillId)) {
                    return row.getText().contains("PAID") && row.findElements(By.className("btn-pay")).isEmpty();
                }
            }
            return false;
        });

        System.out.println("Verified bill #" + createdBillId + " marked as PAID");
    }

    /**
     * Scenario 5: Open bill receipt modal and verify details (Resident name, flat, total amount, paid date).
     */
    @Test
    @Order(5)
    @DisplayName("Scenario 5: Open Bill Receipt Modal and Verify Receipt Details")
    public void testViewBillReceiptE2E() {
        assertNotNull(createdBillId, "Bill ID must be available from previous test");
        driver.get(baseUrl);

        // 1. Locate the bill row and click "Receipt" button
        WebElement billRow = wait.until(driver -> {
            List<WebElement> rows = driver.findElements(By.cssSelector("#billTable tr"));
            for (WebElement row : rows) {
                List<WebElement> cells = row.findElements(By.tagName("td"));
                if (!cells.isEmpty() && cells.get(0).getText().trim().equals(createdBillId)) {
                    return row;
                }
            }
            return null;
        });
        assertNotNull(billRow, "Target bill row must exist");

        WebElement receiptBtn = billRow.findElement(By.className("btn-receipt"));
        receiptBtn.click();

        // 2. Verify Receipt Modal is Open
        WebElement receiptModal = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("receiptModal")));
        assertTrue(receiptModal.getAttribute("class").contains("open"), "Receipt modal must open");

        // 3. Verify Receipt Fields
        WebElement badge = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("receiptStatusBadge")));
        assertEquals("PAID", badge.getText().trim(), "Receipt badge should show PAID");

        WebElement rResidentName = driver.findElement(By.id("rResidentName"));
        WebElement rFlatNumber = driver.findElement(By.id("rFlatNumber"));
        WebElement rBillMonth = driver.findElement(By.id("rBillMonth"));
        WebElement rTotal = driver.findElement(By.id("rTotal"));
        WebElement rPayDate = driver.findElement(By.id("rPayDate"));

        assertEquals(TEST_RESIDENT_NAME, rResidentName.getText().trim(), "Resident name should match");
        assertEquals(TEST_FLAT_NUMBER, rFlatNumber.getText().trim(), "Flat number should match");
        assertEquals(TEST_BILL_MONTH, rBillMonth.getText().trim(), "Bill month should match");
        assertTrue(rTotal.getText().contains("4500"), "Total amount should match maintenance + electricity (4500)");
        assertFalse(rPayDate.getText().contains("Pending"), "Payment date should be populated for paid bill");

        // 4. Verify Print and Download Buttons
        WebElement printBtn = driver.findElement(By.id("printReceiptBtn"));
        WebElement downloadBtn = driver.findElement(By.id("downloadReceiptBtn"));
        assertTrue(printBtn.isDisplayed(), "Print receipt button must be visible");
        assertTrue(downloadBtn.isDisplayed(), "Download receipt button must be visible");

        // 5. Close Modal by clicking Close button
        WebElement closeBtn = receiptModal.findElement(By.xpath(".//button[contains(text(), 'Close')]"));
        closeBtn.click();

        wait.until(ExpectedConditions.invisibilityOf(receiptModal));
        System.out.println("Verified receipt for bill #" + createdBillId + " successfully");
    }

    /**
     * Safely cleanup test records from the database via REST API to leave no residual test data.
     */
    private static void cleanUpTestData() {
        if (createdBillId != null) {
            try {
                System.out.println("Cleaning up test bill #" + createdBillId + "...");
                ((JavascriptExecutor) driver).executeScript(
                        "fetch('/bills/" + createdBillId + "', { method: 'DELETE' });"
                );
            } catch (Exception ignored) {
            }
        }
        if (createdResidentId != null) {
            try {
                System.out.println("Cleaning up test resident #" + createdResidentId + "...");
                ((JavascriptExecutor) driver).executeScript(
                        "fetch('/residents/" + createdResidentId + "', { method: 'DELETE' });"
                );
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Screenshot capture helper for test failure diagnostics.
     */
    public static void captureScreenshot(String testName) {
        if (driver instanceof TakesScreenshot) {
            try {
                File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path destDir = Paths.get("test-results", "screenshots");
                Files.createDirectories(destDir);
                Path destFile = destDir.resolve(testName + "_" + timestamp + ".png");
                Files.copy(srcFile.toPath(), destFile, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("📸 Screenshot saved to: " + destFile.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("Failed to capture screenshot: " + e.getMessage());
            }
        }
    }
}
