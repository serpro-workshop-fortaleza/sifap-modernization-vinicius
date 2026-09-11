package br.gov.sifap.payment.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentDiscountRepository extends JpaRepository<PaymentDiscount, Long> {

    List<PaymentDiscount> findByPaymentId(Long paymentId);

    void deleteByPaymentId(Long paymentId);
}
