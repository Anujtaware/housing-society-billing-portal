package com.housing.billing;

import java.util.List;

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

@RestController
@RequestMapping("/bills")
public class BillController {

    private final BillRepository billRepository;

    public BillController(BillRepository billRepository) {
        this.billRepository = billRepository;
    }

    @GetMapping
    public List<Bill> getAllBills() {
        return billRepository.findAll();
    }

    @PostMapping
    public Bill addBill(@RequestBody Bill bill) {

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
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        bill.setStatus("PAID");

        return billRepository.save(bill);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBill(@PathVariable Long id) {
        billRepository.deleteById(id);
    }
}
