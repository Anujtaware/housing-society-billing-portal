package com.housing.billing;

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
        bill.setMaintenanceAmount(-100.0);
        bill.setElectricityAmount(-50.0);

        Set<ConstraintViolation<Bill>> violations = validator.validate(bill);
        assertFalse(violations.isEmpty(), "Invalid bill must produce violations");

        boolean hasResidentId = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("residentId"));
        boolean hasBillMonth = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("billMonth"));
        boolean hasMaint = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("maintenanceAmount"));
        boolean hasElec = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("electricityAmount"));

        assertTrue(hasResidentId, "Should violate residentId constraint");
        assertTrue(hasBillMonth, "Should violate billMonth constraint");
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

        Bill bill = new Bill();
        bill.setResidentId(resident.getId());
        bill.setBillMonth("September 2026");
        bill.setMaintenanceAmount(2200.0);
        bill.setElectricityAmount(750.0);

        Bill createdBill = billController.addBill(bill);
        assertNotNull(createdBill.getId());
        assertEquals(2950.0, createdBill.getTotalAmount(), 0.001);
        assertEquals("UNPAID", createdBill.getStatus());

        // Check receipt when UNPAID
        BillReceipt unpaidReceipt = billController.getBillReceipt(createdBill.getId());
        assertNotNull(unpaidReceipt);
        assertEquals("UNPAID", unpaidReceipt.getStatus());
        assertEquals("Pending Payment", unpaidReceipt.getPaymentDate());
        assertEquals("Sneha Joshi", unpaidReceipt.getResidentName());
        assertEquals("A-501", unpaidReceipt.getFlatNumber());
        assertEquals(2950.0, unpaidReceipt.getTotalAmount(), 0.001);
        assertTrue(unpaidReceipt.getReceiptNumber().startsWith("REC-"));

        // Mark as paid
        Bill paidBill = billController.markAsPaid(createdBill.getId());
        assertEquals("PAID", paidBill.getStatus());
        assertNotNull(paidBill.getPaymentDate());

        // Check receipt when PAID
        BillReceipt paidReceipt = billController.getBillReceipt(createdBill.getId());
        assertEquals("PAID", paidReceipt.getStatus());
        assertNotEquals("Pending Payment", paidReceipt.getPaymentDate());
        assertNotNull(paidReceipt.getPaymentDate());
        assertEquals(paidBill.getPaymentDate(), paidReceipt.getPaymentDate());

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
}
