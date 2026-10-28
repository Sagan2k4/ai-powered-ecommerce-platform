package com.aishop.repository;

import com.aishop.entity.Order;
import com.aishop.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"items", "user"})
    Optional<Order> findWithItemsById(Long id);

    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    @EntityGraph(attributePaths = {"items", "user"})
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Product ids the user has paid for, most recent first — the main personalization signal. */
    @Query("""
            select i.product.id from OrderItem i
            where i.order.user.id = :userId and i.order.status in :statuses and i.product is not null
            order by i.order.createdAt desc
            """)
    List<Long> findPurchasedProductIds(@Param("userId") Long userId, @Param("statuses") Collection<OrderStatus> statuses);

    long countByStatusIn(Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.status in :statuses")
    BigDecimal sumRevenue(@Param("statuses") Collection<OrderStatus> statuses);
}
