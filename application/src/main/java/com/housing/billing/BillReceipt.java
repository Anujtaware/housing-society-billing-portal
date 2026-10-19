package com.housing.billing;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

public class BillReceipt {

    private String receiptNumber;
    private Long billId;
    private String billMonth;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    private Long residentId;
    private String residentName;
    private String flatNumber;
    private String phone;
    private String email;
    private Double maintenanceAmount;
    private Double electricityAmount;
    private Double totalAmount;
    private String status;
    private String paymentDate;
    private String issuedAt;

    public BillReceipt() {
    }

    public BillReceipt(String receiptNumber, Long billId, String billMonth, Long residentId,
                       String residentName, String flatNumber, String phone, String email,
                       Double maintenanceAmount, Double electricityAmount, Double totalAmount,
                       String status, String paymentDate, String issuedAt) {
        this(receiptNumber, billId, billMonth, null, residentId, residentName, flatNumber, phone, email,
             maintenanceAmount, electricityAmount, totalAmount, status, paymentDate, issuedAt);
    }

    public BillReceipt(String receiptNumber, Long billId, String billMonth, LocalDate dueDate, Long residentId,
                       String residentName, String flatNumber, String phone, String email,
                       Double maintenanceAmount, Double electricityAmount, Double totalAmount,
                       String status, String paymentDate, String issuedAt) {
        this.receiptNumber = receiptNumber;
        this.billId = billId;
        this.billMonth = billMonth;
        this.dueDate = dueDate;
        this.residentId = residentId;
        this.residentName = residentName;
        this.flatNumber = flatNumber;
        this.phone = phone;
        this.email = email;
        this.maintenanceAmount = maintenanceAmount;
        this.electricityAmount = electricityAmount;
        this.totalAmount = totalAmount;
        this.status = status;
        this.paymentDate = paymentDate;
        this.issuedAt = issuedAt;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
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

    public Long getResidentId() {
        return residentId;
    }

    public void setResidentId(Long residentId) {
        this.residentId = residentId;
    }

    public String getResidentName() {
        return residentName;
    }

    public void setResidentName(String residentName) {
        this.residentName = residentName;
    }

    public String getFlatNumber() {
        return flatNumber;
    }

    public void setFlatNumber(String flatNumber) {
        this.flatNumber = flatNumber;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(String paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(String issuedAt) {
        this.issuedAt = issuedAt;
    }
}
