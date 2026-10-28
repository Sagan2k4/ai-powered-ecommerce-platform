package com.aishop.repository;

import com.aishop.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    List<Invoice> findByStoreNameIsNullOrStoreNameNot(String storeName);
}
