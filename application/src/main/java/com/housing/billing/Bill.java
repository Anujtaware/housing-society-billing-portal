package com.housing.billing;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long residentId;
    private String billMonth;
    private Double maintenanceAmount;
    private Double electricityAmount;
    private Double totalAmount;
    private String status;

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
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}