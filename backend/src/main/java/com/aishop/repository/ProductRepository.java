package com.aishop.repository;

import com.aishop.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p from Product p
            where p.active = true
              and (:category is null or p.category = :category)
              and (:q is null
                   or lower(p.name) like lower(concat('%', :q, '%'))
                   or lower(p.description) like lower(concat('%', :q, '%'))
                   or lower(p.tags) like lower(concat('%', :q, '%')))
            """)
    Page<Product> search(@Param("q") String q, @Param("category") String category, Pageable pageable);

    @Query("select distinct p.category from Product p where p.active = true order by p.category")
    List<String> findCategories();

    List<Product> findByActiveTrueAndStockGreaterThan(int stock);

    /** Atomically reserves stock; returns 0 if there is not enough left. */
    @Modifying
    @Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);
}
