package com.aishop.service;

/** Published once an order's payment is confirmed; listeners run after the transaction commits. */
public record OrderPaidEvent(Long orderId, Long userId) {
}
