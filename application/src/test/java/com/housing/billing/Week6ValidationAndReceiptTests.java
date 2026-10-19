package com.housing.billing;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class Week6ValidationAndReceiptTests {

    @Autowired
    private ResidentController residentController;

    @Autowired
    private BillController billController;

    @Autowired
    private HomeController homeController;

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Verify HomeController redirects to /index.html")
    void testHomeControllerRedirect() {
        String destination = homeController.home();
        assertEquals("redirect:/index.html", destination);
    }

    @Test
    @DisplayName("Verify Resident validation passes for valid entity")
    void testValidResident() {
        Resident resident = new Resident("Rahul Sharma", "B-204", "9876543210", "rahul@example.com");
        Set<ConstraintViolation<Resident>> violations = validator.validate(resident);
        assertTrue(violations.isEmpty(), "Valid resident should have 0 violations");
    }

    @Test
    @DisplayName("Verify Resident validation fails for blank and malformed fields")
    void testInvalidResident() {
        Resident resident = new Resident("", "", "123", "invalid-email");
        Set<ConstraintViolation<Resident>> violations = validator.validate(resident);
        assertFalse(violations.isEmpty(), "Invalid resident must produce violations");

        boolean hasName = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        boolean hasFlat = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("flatNumber"));
        boolean hasPhone = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
        boolean hasEmail = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email"));

        assertTrue(hasName, "Should violate name constraint");
        assertTrue(hasFlat, "Should violate flatNumber constraint");
        assertTrue(hasPhone, "Should violate phone constraint");
        assertTrue(hasEmail, "Should violate email constraint");
    }

    @Test
    @DisplayName("Verify Bill validation passes for valid entity")
    void testValidBill() {
        Bill bill = new Bill();
        bill.setResidentId(1L);
        bill.setBillMonth("October 2026");
        bill.setDueDate(LocalDate.now().plusDays(15));
        bill.setMaintenanceAmount(2500.0);
        bill.setElectricityAmount(800.0);

        Set<ConstraintViolation<Bill>> violations = validator.validate(bill);
        assertTrue(violations.isEmpty(), "Valid bill should have 0 violations");
    }

    @Test
    @DisplayName("Verify Bill validation fails for negative amounts and missing required fields")
    void testInvalidBill() {
        Bill bill = new Bill();
        bill.setResidentId(null);
        bill.setBillMonth("");
        bill.setDueDate(null);
        bill.setMaintenanceAmount(-100.0);
        bill.setElectricityAmount(-50.0);

        Set<ConstraintViolation<Bill>> violations = validator.validate(bill);
        assertFalse(violations.isEmpty(), "Invalid bill must produce violations");

        boolean hasResidentId = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("residentId"));
        boolean hasBillMonth = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("billMonth"));
        boolean hasDueDate = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("dueDate"));
        boolean hasMaint = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("maintenanceAmount"));
        boolean hasElec = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("electricityAmount"));

        assertTrue(hasResidentId, "Should violate residentId constraint");
        assertTrue(hasBillMonth, "Should violate billMonth constraint");
        assertTrue(hasDueDate, "Should violate dueDate constraint");
        assertTrue(hasMaint, "Should violate maintenanceAmount constraint");
        assertTrue(hasElec, "Should violate electricityAmount constraint");
    }

    @Test
    @DisplayName("Verify Resident creation, retrieval, and deletion lifecycle")
    void testResidentLifecycle() {
        Resident resident = new Resident("Amit Verma", "C-302", "9811223344", "amit.verma@example.com");
        Resident created = residentController.addResident(resident);
        assertNotNull(created.getId());
        assertEquals("Amit Verma", created.getName());

        assertTrue(residentController.getAllResidents().stream().anyMatch(r -> r.getId().equals(created.getId())));

        residentController.deleteResident(created.getId());
        assertFalse(residentController.getAllResidents().stream().anyMatch(r -> r.getId().equals(created.getId())));
    }

    @Test
    @DisplayName("Verify Bill creation, total calculation, mark-as-paid, and receipt generation")
    void testBillLifecycleAndReceipt() {
        Resident resident = residentController.addResident(
                new Resident("Sneha Joshi", "A-501", "9822334455", "sneha.joshi@example.com"));

        LocalDate dueDate = LocalDate.now().plusDays(15);
        Bill bill = new Bill();
        bill.setResidentId(resident.getId());
        bill.setBillMonth("September 2026");
        bill.setDueDate(dueDate);
        bill.setMaintenanceAmount(2200.0);
        bill.setElectricityAmount(750.0);

        Bill createdBill = billController.addBill(bill);
        assertNotNull(createdBill.getId());
        assertEquals(2950.0, createdBill.getTotalAmount(), 0.001);
        assertEquals(dueDate, createdBill.getDueDate());
        assertEquals("PENDING", createdBill.getStatus());

        // Check receipt when PENDING
        BillReceipt unpaidReceipt = billController.getBillReceipt(createdBill.getId());
        assertNotNull(unpaidReceipt);
        assertEquals("PENDING", unpaidReceipt.getStatus());
        assertEquals("Pending Payment", unpaidReceipt.getPaymentDate());
        assertEquals(dueDate, unpaidReceipt.getDueDate());
        assertEquals("Sneha Joshi", unpaidReceipt.getResidentName());
        assertEquals("A-501", unpaidReceipt.getFlatNumber());
        assertEquals(2950.0, unpaidReceipt.getTotalAmount(), 0.001);
        assertTrue(unpaidReceipt.getReceiptNumber().startsWith("REC-"));

        // Mark as paid
        Bill paidBill = billController.markAsPaid(createdBill.getId());
        assertEquals("PAID ON TIME", paidBill.getStatus());
        assertNotNull(paidBill.getPaymentDate());

        // Check receipt when PAID ON TIME
        BillReceipt paidReceipt = billController.getBillReceipt(createdBill.getId());
        assertEquals("PAID ON TIME", paidReceipt.getStatus());
        assertNotEquals("Pending Payment", paidReceipt.getPaymentDate());
        assertNotNull(paidReceipt.getPaymentDate());
        assertEquals(paidBill.getPaymentDate(), paidReceipt.getPaymentDate());
        assertEquals(dueDate, paidReceipt.getDueDate());

        // Clean up
        billController.deleteBill(createdBill.getId());
        residentController.deleteResident(resident.getId());
    }

    @Test
    @DisplayName("Verify GlobalExceptionHandler formats ResponseStatusException as clean JSON")
    void testExceptionHandlerResponseStatus() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found with ID: 9999");
        ResponseEntity<?> response = exceptionHandler.handleResponseStatusException(ex);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Verify Bill creation persists and returns the specified due date")
    void testBillCreationWithDueDate() {
        Resident resident = residentController.addResident(
                new Resident("Kavita Rao", "D-101", "9833445566", "kavita.rao@example.com"));

        LocalDate dueDate = LocalDate.now().plusDays(20);
        Bill bill = new Bill();
        bill.setResidentId(resident.getId());
        bill.setBillMonth("November 2026");
        bill.setDueDate(dueDate);
        bill.setMaintenanceAmount(1800.0);
        bill.setElectricityAmount(450.0);

        Bill created = billController.addBill(bill);
        assertNotNull(created.getId());
        assertEquals(dueDate, created.getDueDate());
        assertEquals(2250.0, created.getTotalAmount(), 0.001);

        // Fetch back and assert persistence
        Bill fetched = billController.getBillById(created.getId());
        assertEquals(dueDate, fetched.getDueDate());

        billController.deleteBill(created.getId());
        residentController.deleteResident(resident.getId());
    }

    @Test
    @DisplayName("Verify Bill status is PENDING when payment is not made and due date has not passed")
    void testPendingBillWhenDueDateNotPassed() {
        Resident resident = residentController.addResident(
                new Resident("Rohan Patil", "D-102", "9844556677", "rohan.patil@example.com"));

        Bill bill = new Bill();
        bill.setResidentId(resident.getId());
        bill.setBillMonth("November 2026");
        bill.setDueDate(LocalDate.now().plusDays(10));
        bill.setMaintenanceAmount(1500.0);
        bill.setElectricityAmount(500.0);

        Bill created = billController.addBill(bill);
        assertEquals("PENDING", created.getStatus());

        billController.deleteBill(created.getId());
        residentController.deleteResident(resident.getId());
    }

    @Test
    @DisplayName("Verify Bill status is OVERDUE when payment is not made and due date has passed")
    void testOverdueBillWhenDueDatePassed() {
        Resident resident = residentController.addResident(
                new Resident("Vikram Mehta", "D-103", "9855667788", "vikram.mehta@example.com"));

        Bill bill = new Bill();
        bill.setResidentId(resident.getId());
        bill.setBillMonth("August 2026");
        bill.setDueDate(LocalDate.now().minusDays(5));
        bill.setMaintenanceAmount(2000.0);
        bill.setElectricityAmount(600.0);

        Bill created = billController.addBill(bill);
        assertEquals("OVERDUE", created.getStatus());

        billController.deleteBill(created.getId());
        residentController.deleteResident(resident.getId());
    }

    @Test
    @DisplayName("Verify Paid bill does not remain OVERDUE and becomes PAID LATE after payment")
    void testPaidBillRemainsPaidAfterDueDate() {
        Resident resident = residentController.addResident(
                new Resident("Ananya Deshmukh", "D-104", "9866778899", "ananya.d@example.com"));

        // Create overdue bill and mark as paid
        Bill bill = new Bill();
        bill.setResidentId(resident.getId());
        bill.setBillMonth("July 2026");
        bill.setDueDate(LocalDate.now().minusDays(10));
        bill.setMaintenanceAmount(2100.0);
        bill.setElectricityAmount(700.0);

        Bill created = billController.addBill(bill);
        assertEquals("OVERDUE", created.getStatus());

        Bill paid = billController.markAsPaid(created.getId());
        assertEquals("PAID LATE", paid.getStatus());

        // Re-fetch to ensure it remains PAID LATE and does not revert to OVERDUE
        Bill fetched = billController.getBillById(created.getId());
        assertEquals("PAID LATE", fetched.getStatus());
        assertNotNull(fetched.getPaymentDate());

        billController.deleteBill(created.getId());
        residentController.deleteResident(resident.getId());
    }

    @Test
    @DisplayName("Verify payment updates an overdue bill to PAID LATE and records payment date")
    void testPaymentUpdatesOverdueBillToPaid() {
        Resident resident = residentController.addResident(
                new Resident("Siddharth Nair", "D-105", "9877889900", "siddharth.n@example.com"));

        Bill bill = new Bill();
        bill.setResidentId(resident.getId());
        bill.setBillMonth("July 2026");
        bill.setDueDate(LocalDate.now().minusDays(2));
        bill.setMaintenanceAmount(1900.0);
        bill.setElectricityAmount(400.0);

        Bill created = billController.addBill(bill);
        assertEquals("OVERDUE", created.getStatus());
        assertNull(created.getPaymentDate());

        Bill paid = billController.markAsPaid(created.getId());
        assertEquals("PAID LATE", paid.getStatus());
        assertNotNull(paid.getPaymentDate());

        billController.deleteBill(created.getId());
        residentController.deleteResident(resident.getId());
    }

    @Test
    @DisplayName("Verify receipt functionality accurately reflects due date and status across states")
    void testReceiptForOverdueAndPaidBills() {
        Resident resident = residentController.addResident(
                new Resident("Divya Kulkarni", "D-106", "9888990011", "divya.k@example.com"));

        LocalDate overdueDate = LocalDate.now().minusDays(7);
        Bill bill = new Bill();
        bill.setResidentId(resident.getId());
        bill.setBillMonth("June 2026");
        bill.setDueDate(overdueDate);
        bill.setMaintenanceAmount(2300.0);
        bill.setElectricityAmount(650.0);

        Bill created = billController.addBill(bill);

        // Verify receipt in OVERDUE state
        BillReceipt overdueReceipt = billController.getBillReceipt(created.getId());
        assertEquals("OVERDUE", overdueReceipt.getStatus());
        assertEquals(overdueDate, overdueReceipt.getDueDate());
        assertEquals("Pending Payment", overdueReceipt.getPaymentDate());
        assertEquals(2950.0, overdueReceipt.getTotalAmount(), 0.001);

        // Mark as paid and verify receipt in PAID LATE state
        billController.markAsPaid(created.getId());
        BillReceipt paidReceipt = billController.getBillReceipt(created.getId());
        assertEquals("PAID LATE", paidReceipt.getStatus());
        assertEquals(overdueDate, paidReceipt.getDueDate());
        assertNotEquals("Pending Payment", paidReceipt.getPaymentDate());

        billController.deleteBill(created.getId());
        residentController.deleteResident(resident.getId());
    }

    @Test
    @DisplayName("Verify Case 3: Payment date <= due date results in PAID ON TIME")
    void testPaidOnTimeWhenPaymentDateOnOrBeforeDueDate() {
        Resident resident = residentController.addResident(
                new Resident("Tanvi Shinde", "E-201", "9811223344", "tanvi.s@example.com"));

        // Case 3A: paymentDate is strictly before dueDate
        Bill billBefore = new Bill();
        billBefore.setResidentId(resident.getId());
        billBefore.setBillMonth("October 2026");
        billBefore.setDueDate(LocalDate.parse("2026-10-15"));
        billBefore.setPaymentDate("2026-10-10");
        billBefore.setMaintenanceAmount(2000.0);
        billBefore.setElectricityAmount(500.0);

        Bill createdBefore = billController.addBill(billBefore);
        assertEquals("PAID ON TIME", createdBefore.getStatus());

        // Case 3B: paymentDate is exactly on dueDate
        Bill billEqual = new Bill();
        billEqual.setResidentId(resident.getId());
        billEqual.setBillMonth("October 2026");
        billEqual.setDueDate(LocalDate.parse("2026-10-10"));
        billEqual.setPaymentDate("2026-10-10");
        billEqual.setMaintenanceAmount(2000.0);
        billEqual.setElectricityAmount(500.0);

        Bill createdEqual = billController.addBill(billEqual);
        assertEquals("PAID ON TIME", createdEqual.getStatus());

        // Case 3C: Mark as paid on or before due date
        Bill billPending = new Bill();
        billPending.setResidentId(resident.getId());
        billPending.setBillMonth("December 2026");
        billPending.setDueDate(LocalDate.now().plusDays(10));
        billPending.setMaintenanceAmount(2200.0);
        billPending.setElectricityAmount(300.0);

        Bill createdPending = billController.addBill(billPending);
        assertEquals("PENDING", createdPending.getStatus());

        Bill paidOnTime = billController.markAsPaid(createdPending.getId());
        assertEquals("PAID ON TIME", paidOnTime.getStatus());

        BillReceipt receipt = billController.getBillReceipt(createdPending.getId());
        assertEquals("PAID ON TIME", receipt.getStatus());

        // Clean up
        billController.deleteBill(createdBefore.getId());
        billController.deleteBill(createdEqual.getId());
        billController.deleteBill(createdPending.getId());
        residentController.deleteResident(resident.getId());
    }

    @Test
    @DisplayName("Verify Case 4: Payment date > due date results in PAID LATE and not OVERDUE")
    void testPaidLateWhenPaymentDateAfterDueDate() {
        Resident resident = residentController.addResident(
                new Resident("Amit Verma", "E-202", "9822446688", "amit.v@example.com"));

        // Case 4A: paymentDate strictly after dueDate
        Bill billLate = new Bill();
        billLate.setResidentId(resident.getId());
        billLate.setBillMonth("October 2026");
        billLate.setDueDate(LocalDate.parse("2026-10-10"));
        billLate.setPaymentDate("2026-10-19");
        billLate.setMaintenanceAmount(2500.0);
        billLate.setElectricityAmount(400.0);

        Bill createdLate = billController.addBill(billLate);
        assertEquals("PAID LATE", createdLate.getStatus());

        // Case 4B: Mark an overdue bill as paid today -> must become PAID LATE, NOT OVERDUE
        Bill billOverdue = new Bill();
        billOverdue.setResidentId(resident.getId());
        billOverdue.setBillMonth("September 2026");
        billOverdue.setDueDate(LocalDate.now().minusDays(5));
        billOverdue.setMaintenanceAmount(1800.0);
        billOverdue.setElectricityAmount(350.0);

        Bill createdOverdue = billController.addBill(billOverdue);
        assertEquals("OVERDUE", createdOverdue.getStatus());

        Bill paidOverdue = billController.markAsPaid(createdOverdue.getId());
        assertEquals("PAID LATE", paidOverdue.getStatus());
        assertNotEquals("OVERDUE", paidOverdue.getStatus());

        BillReceipt lateReceipt = billController.getBillReceipt(createdOverdue.getId());
        assertEquals("PAID LATE", lateReceipt.getStatus());

        // Clean up
        billController.deleteBill(createdLate.getId());
        billController.deleteBill(createdOverdue.getId());
        residentController.deleteResident(resident.getId());
    }
}
