package com.housing.billing;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Resident ID is required")
    @Positive(message = "Resident ID must be a positive number")
    private Long residentId;

    @NotBlank(message = "Bill month is required")
    private String billMonth;

    @NotNull(message = "Due date is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    @NotNull(message = "Maintenance amount is required")
    @PositiveOrZero(message = "Maintenance amount must be positive or zero")
    private Double maintenanceAmount;

    @NotNull(message = "Electricity amount is required")
    @PositiveOrZero(message = "Electricity amount must be positive or zero")
    private Double electricityAmount;

    private Double totalAmount;
    private String status;
    private String paymentDate;

    public Bill() {
    }

    public Long getId() {
        return id;
    }

    public Long getResidentId() {
        return residentId;
    }

    public void setResidentId(Long residentId) {
        this.residentId = residentId;
    }

    public String getBillMonth() {
        return billMonth;
    }

    public void setBillMonth(String billMonth) {
        this.billMonth = billMonth;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Double getMaintenanceAmount() {
        return maintenanceAmount;
    }

    public void setMaintenanceAmount(Double maintenanceAmount) {
        this.maintenanceAmount = maintenanceAmount;
    }

    public Double getElectricityAmount() {
        return electricityAmount;
    }

    public void setElectricityAmount(Double electricityAmount) {
        this.electricityAmount = electricityAmount;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        LocalDate pDate = getParsedPaymentDate();
        boolean hasPaidFlag = (this.status != null && this.status.toUpperCase().startsWith("PAID"));

        if (pDate != null || hasPaidFlag) {
            if (pDate != null && dueDate != null) {
                if (pDate.isAfter(dueDate)) {
                    return "PAID LATE";
                }
                return "PAID ON TIME";
            }
            if ("PAID LATE".equalsIgnoreCase(this.status)) {
                return "PAID LATE";
            }
            return "PAID ON TIME";
        }

        if (dueDate != null && LocalDate.now().isAfter(dueDate)) {
            return "OVERDUE";
        }
        return "PENDING";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(String paymentDate) {
        this.paymentDate = paymentDate;
    }

    @JsonIgnore
    public LocalDate getParsedPaymentDate() {
        if (paymentDate == null || paymentDate.isBlank()) {
            return null;
        }
        String trimmed = paymentDate.trim();
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e1) {
            try {
                return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            } catch (Exception e2) {
                try {
                    return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("d-M-yyyy"));
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }
}