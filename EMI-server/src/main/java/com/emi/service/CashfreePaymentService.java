package com.emi.service;

import com.cashfree.pg.ApiException;
import com.cashfree.pg.Cashfree;
import com.cashfree.pg.model.CreateOrderRequest;
import com.cashfree.pg.model.CreateSubscriptionPaymentRequest;
import com.cashfree.pg.model.CreateSubscriptionPaymentResponse;
import com.cashfree.pg.model.CreateSubscriptionRequest;
import com.cashfree.pg.model.CreateSubscriptionRequestPlanDetails;
import com.cashfree.pg.model.CreateSubscriptionRequestSubscriptionMeta;
import com.cashfree.pg.model.CustomerDetails;
import com.cashfree.pg.model.ManageSubscriptionRequest;
import com.cashfree.pg.model.OrderEntity;
import com.cashfree.pg.model.OrderMeta;
import com.cashfree.pg.model.SubscriptionCustomerDetails;
import com.cashfree.pg.model.SubscriptionEntity;
import com.emi.config.properties.CashfreeProperties;
import com.emi.dto.request.OnlinePaymentRequest;
import com.emi.dto.response.PaymentOrderResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.Customer;
import com.emi.model.entity.Finance;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class CashfreePaymentService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    public static final String FINANCE_TAG = "financeId";
    public static final String CREATED_BY_TAG = "createdBy";
    public static final String PAY_MONTHS_TAG = "payMonths";
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    private final Cashfree cashfree;
    private final CashfreeProperties cashfreeProperties;

    public PaymentOrderResponse generateCashfreeOrder(Finance finance, Customer customer, BigDecimal amount, OnlinePaymentRequest paymentRequest) {
        ensureConfigured();
        String orderId = finance.getFinanceNumber() + "-"
                + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String expiry = OffsetDateTime.now(INDIA)
                .plusMinutes(Math.max(cashfreeProperties.getOrderExpiryMinutes(), 15))
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        Map<String, String> tags = new HashMap<>();
        tags.put(FINANCE_TAG, finance.getFinanceId());
        tags.put(CREATED_BY_TAG, paymentRequest.getUserID());
        tags.put(PAY_MONTHS_TAG, paymentRequest.getMonths().toString());
        String notifyUrl = blankToNull(cashfreeProperties.getNotifyUrl());

        CreateOrderRequest request = new CreateOrderRequest()
                .orderId(orderId)
                .orderAmount(amount)
                .orderCurrency("INR")
                .customerDetails(new CustomerDetails()
                        .customerId(customer.getCustomerId())
                        .customerPhone(tenDigitPhone(customer.getPhoneNumber()))
                        .customerName(customer.getFullName())
                        .customerEmail(blankToNull(customer.getEmail())))
                .orderMeta(notifyUrl == null ? null : new OrderMeta().notifyUrl(notifyUrl))
                .orderExpiryTime(expiry)
                .orderNote(finance.getFinanceNumber() + " EMI payment")
                .orderTags(tags);

        OrderEntity order;
        try {
            order = cashfree.PGCreateOrder(request, null, null, null).getData();
        } catch (ApiException e) {
            throw translate("create order", e);
        }

        return PaymentOrderResponse.builder()
                .orderId(order.getOrderId())
                .financeId(finance.getFinanceNumber())
                .amount(order.getOrderAmount())
                .currency(order.getOrderCurrency())
                .status(lower(order.getOrderStatus()))
                .paymentSessionId(order.getPaymentSessionId())
                .expiresAt(order.getOrderExpiryTime())
                .build();
    }

    public OrderEntity getOrderDetails(String orderId) {
        ensureConfigured();
        try {
            return cashfree.PGFetchOrder(orderId.trim(), null, null, null).getData();
        } catch (ApiException e) {
            throw translate("fetch order " + orderId, e);
        }
    }

    public SubscriptionEntity createAutoPaySubscription(Finance finance, Customer customer, String firstChargeTime, String returnUrl) {
        ensureConfigured();
        String subscriptionId = "SUB-" + finance.getFinanceNumber() + "-"
                + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String planId = "PLAN-" + finance.getFinanceNumber();

        // Set mandate cap to 2x monthly EMI so overdue late fees can be debited without exceeding bank mandate limits
        BigDecimal maxAmount = finance.getEmiAmount().multiply(BigDecimal.valueOf(2));

        CreateSubscriptionRequestPlanDetails planDetails = new CreateSubscriptionRequestPlanDetails()
                .planId(planId)
                .planName(finance.getFinanceNumber() + " EMI Plan")
                .planType("PERIODIC")
                .planIntervalType("MONTH")
                .planIntervals(1)
                .planAmount(finance.getEmiAmount())
                .planMaxAmount(maxAmount)
                .planMaxCycles(finance.getTenureMonths())
                .planCurrency("INR")
                .planNote(finance.getFinanceNumber() + " monthly EMI");

        SubscriptionCustomerDetails customerDetails = new SubscriptionCustomerDetails()
                .customerPhone(tenDigitPhone(customer.getPhoneNumber()))
                .customerName(customer.getFullName())
                .customerEmail(blankToNull(customer.getEmail()));

        CreateSubscriptionRequest request = new CreateSubscriptionRequest()
                .subscriptionId(subscriptionId)
                .planDetails(planDetails)
                .customerDetails(customerDetails)
                .subscriptionFirstChargeTime(firstChargeTime)
                .subscriptionNote(finance.getFinanceNumber() + " AutoPay Mandate");

        if (returnUrl != null && !returnUrl.isBlank()) {
            request.subscriptionMeta(new CreateSubscriptionRequestSubscriptionMeta().returnUrl(returnUrl.trim()));
        }

        try {
            var response = cashfree.SubsCreateSubscription(request, null, null, null);
            return response.getData();
        } catch (ApiException e) {
            throw translate("create AutoPay subscription", e);
        }
    }

    public CreateSubscriptionPaymentResponse chargeSubscription(String subscriptionId, BigDecimal amount, String remarks) {
        ensureConfigured();
        String paymentId = "CHG-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();

        CreateSubscriptionPaymentRequest request = new CreateSubscriptionPaymentRequest()
                .subscriptionId(subscriptionId)
                .paymentId(paymentId)
                .paymentAmount(amount)
                .paymentType("RECURRING")
                .paymentRemarks(remarks != null ? remarks : "EMI and overdue charge");

        try {
            var response = cashfree.SubsCreatePayment(request, null, null, null);
            return response.getData();
        } catch (ApiException e) {
            throw translate("charge subscription " + subscriptionId, e);
        }
    }

    public SubscriptionEntity cancelSubscription(String subscriptionId) {
        ensureConfigured();
        ManageSubscriptionRequest request = new ManageSubscriptionRequest()
                .subscriptionId(subscriptionId)
                .action("CANCEL");

        try {
            var response = cashfree.SubsManageSubscription(subscriptionId.trim(), request, null, null, null);
            log.info("AutoPay subscription {} successfully cancelled in Cashfree", subscriptionId);
            return response.getData();
        } catch (ApiException e) {
            log.warn("Failed to cancel AutoPay subscription {} in Cashfree: {}", subscriptionId, e.getMessage());
            return null;
        }
    }

    private void ensureConfigured() {
        if (!cashfreeProperties.isConfigured()) {
            throw new DomainException("Online payment is not configured");
        }
    }

    private DomainException translate(String action, ApiException e) {
        String message = null;
        if (e.getResponseBody() != null) {
            Matcher m = ERROR_MESSAGE.matcher(e.getResponseBody());
            if (m.find()) {
                message = m.group(1);
            }
        }
        log.warn("Cashfree {} failed with {}: {}", action, e.getCode(), message != null ? message : e.getMessage());

        int status = e.getCode();
        if (status == 0) {
            return new DomainException("Could not reach the payment gateway, please try again");
        }
        if (status == 404) {
            return new DomainException("Order not found", HttpStatus.NOT_FOUND);
        }
        if (status == 401 || status == 403) {
            return new DomainException("Online payment is not available right now");
        }
        return new DomainException(message != null && !message.isBlank()
                ? "Payment gateway: " + message : "Payment gateway request failed");
    }

    private String tenDigitPhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() < 10) {
            throw new DomainException("The customer's mobile number is not valid for online payment");
        }
        return digits.substring(digits.length() - 10);
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
