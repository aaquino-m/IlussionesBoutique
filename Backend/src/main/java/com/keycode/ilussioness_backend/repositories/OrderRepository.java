package com.keycode.ilussioness_backend.repositories;

import com.keycode.ilussioness_backend.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Orders live in the legacy {@code orders} table, whose primary key is text.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByCustomerId(UUID customerId);

    boolean existsByProducts_ProductId(UUID productId);

    @Modifying
    @Query("update Order o set o.customer = null where o.customer.id = :customerId")
    int detachCustomer(@Param("customerId") UUID customerId);
}
