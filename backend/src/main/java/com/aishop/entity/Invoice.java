package com.aishop.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", unique = true)
    private Order order;

    @Column(nullable = false, unique = true, length = 40)
    private String invoiceNumber;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = 5_000_000)
    private byte[] pdf;

    /** Store name printed on the PDF, so invoices can be re-rendered if the store is renamed. */
    @Column(length = 100)
    private String storeName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Invoice() {
    }

    public Invoice(Order order, String invoiceNumber, byte[] pdf, String storeName) {
        this.order = order;
        this.invoiceNumber = invoiceNumber;
        this.pdf = pdf;
        this.storeName = storeName;
    }

    /** Replaces the PDF content; the invoice number and date never change. */
    public void rerender(byte[] pdf, String storeName) {
        this.pdf = pdf;
        this.storeName = storeName;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public byte[] getPdf() { return pdf; }
    public String getStoreName() { return storeName; }
    public Instant getCreatedAt() { return createdAt; }
}
