package com.housing.billing;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/bills")
public class BillController {

    private final BillRepository billRepository;
    private final ResidentRepository residentRepository;

    public BillController(BillRepository billRepository, ResidentRepository residentRepository) {
        this.billRepository = billRepository;
        this.residentRepository = residentRepository;
    }

    @GetMapping
    public List<Bill> getAllBills() {
        return billRepository.findAll();
    }

    @PostMapping
    public Bill addBill(@Valid @RequestBody Bill bill) {

        if (bill.getMaintenanceAmount() == null) {
            bill.setMaintenanceAmount(0.0);
        }

        if (bill.getElectricityAmount() == null) {
            bill.setElectricityAmount(0.0);
        }

        bill.setTotalAmount(
            bill.getMaintenanceAmount() + bill.getElectricityAmount()
        );

        if (bill.getStatus() == null || bill.getStatus().isBlank()) {
            bill.setStatus("UNPAID");
        }

        return billRepository.save(bill);
    }

    @PutMapping("/{id}/pay")
    public Bill markAsPaid(@PathVariable Long id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found with ID: " + id));

        bill.setStatus("PAID");
        bill.setPaymentDate(LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));

        return billRepository.save(bill);
    }

    @GetMapping("/{id}/receipt")
    public BillReceipt getBillReceipt(@PathVariable Long id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found with ID: " + id));

        Resident resident = null;
        if (bill.getResidentId() != null) {
            resident = residentRepository.findById(bill.getResidentId()).orElse(null);
        }

        String receiptNumber = "REC-" + String.format("%05d", bill.getId());
        String resName = (resident != null && resident.getName() != null) ? resident.getName() : "Resident #" + bill.getResidentId();
        String flatNum = (resident != null && resident.getFlatNumber() != null) ? resident.getFlatNumber() : "N/A";
        String phone = (resident != null && resident.getPhone() != null) ? resident.getPhone() : "N/A";
        String email = (resident != null && resident.getEmail() != null) ? resident.getEmail() : "N/A";

        String payDate;
        if ("PAID".equalsIgnoreCase(bill.getStatus())) {
            payDate = (bill.getPaymentDate() != null && !bill.getPaymentDate().isBlank())
                    ? bill.getPaymentDate()
                    : LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } else {
            payDate = "Pending Payment";
        }

        String issuedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));

        return new BillReceipt(
                receiptNumber,
                bill.getId(),
                bill.getBillMonth(),
                bill.getResidentId(),
                resName,
                flatNum,
                phone,
                email,
                bill.getMaintenanceAmount(),
                bill.getElectricityAmount(),
                bill.getTotalAmount(),
                bill.getStatus(),
                payDate,
                issuedAt
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBill(@PathVariable Long id) {
        billRepository.deleteById(id);
    }
}
