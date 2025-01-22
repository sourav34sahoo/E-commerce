package com.dailycodebuffer.OrderService.service;

import com.dailycodebuffer.OrderService.entity.Order;
import com.dailycodebuffer.OrderService.exception.CustomException;
import com.dailycodebuffer.OrderService.external.client.PaymentService;
import com.dailycodebuffer.OrderService.external.client.ProductService;
import com.dailycodebuffer.OrderService.external.request.PaymentRequest;
import com.dailycodebuffer.OrderService.external.response.PaymentResponse;
import com.dailycodebuffer.OrderService.model.OrderRequest;
import com.dailycodebuffer.OrderService.model.OrderResponse;
import com.dailycodebuffer.OrderService.repository.OrderRepository;
import com.dailycodebuffer.ProductService.model.ProductResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Service
@Log4j2
public class OrderServiceImpl implements OrderService{

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private PaymentService paymentService;

    @Override
    public long placeOrder(OrderRequest orderRequest) {
        // ORDER ENTITY -> Save the data with status order created
        // Product Service -> Block Products (Reduce the Quantity)
        // PAYMENT SERVICE -> Payments -> Success -> Complete, Else Cancelled

        log.info("Placing Order Request: {}" ,orderRequest);

        productService.reduceQuantity(
                orderRequest.getProductId(), orderRequest.getQuantity());

        log.info("Creating Order with Status CREATED");
        Order order = Order.builder()
                .productId(orderRequest.getProductId())
                .amount(orderRequest.getTotalAmount())
                .orderDate(Instant.now())
                .orderStatus("CREATED")
                .quantity(orderRequest.getQuantity())
                .build();

        order = orderRepository.save(order);

        log.info("Calling Payment Service to complete the payment");
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .orderId(order.getId())
                .amount(order.getAmount())
                .paymentMode(orderRequest.getPaymentMode())
                .build();

        String orderStatus = null;
        try{
            paymentService.doPayment(paymentRequest);
            log.info("Payment done Sucessfully. Changing the Order status");
            orderStatus = "PLACED";
        } catch (Exception e) {
            log.error("error occued in payment. Changing order status to PAYMENT_FAILED");
            orderStatus = "PAYMENT_FAILED";
        }

        order.setOrderStatus(orderStatus);
        orderRepository.save(order);

        log.info("Order Placed successfully with order id:{}", order.getId());
        return order.getId();
    }

    @Override
    public OrderResponse getOrderDetails(long orderId) {
        log.info("Get order details for  order id:{}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(()->new CustomException("Order not found for the order id:{}"+ orderId,
                "NOT_FOUND", 404));

        log.info("Invoking Product Service to fetch the Product for id: {}",order.getProductId());
        ProductResponse productResponse = restTemplate.getForObject("http://PRODUCT-SERVICE/product/"+order.getProductId(),
                ProductResponse.class
        );

        log.info("Getting Payment Information from the payment service");
        PaymentResponse paymentResponse
                = restTemplate.getForObject("http://PAYMENT-SERVICE/payment/order/" +order.getId(),
                PaymentResponse.class);

        OrderResponse.ProductDetails productDetails
                = OrderResponse.ProductDetails.builder()
                .productName(productResponse.getProductName())
                .productId(productResponse.getProductId())
                .build();

        OrderResponse.PaymentDetails paymentDetails
                = OrderResponse.PaymentDetails
                .builder()
                .PaymentId(paymentResponse.getPaymentId())
                .paymentStatus(paymentResponse.getStatus())
                .paymentDate(paymentResponse.getPaymentDate())
                .paymentMode(paymentResponse.getPaymentMode())
                .build();

        OrderResponse orderResponse = OrderResponse.builder()
                .orderId(order.getId())
                .amount(order.getAmount())
                .orderDate(order.getOrderDate())
                .orderStatus(order.getOrderStatus())
                .productDetails(productDetails)
                .paymentDetails(paymentDetails)
                .build();

        return orderResponse;
    }
}
