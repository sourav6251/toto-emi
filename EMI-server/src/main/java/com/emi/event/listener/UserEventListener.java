package com.emi.event.listener;

import com.emi.event.user.OtpRequestedEvent;
import com.emi.event.user.UserRegisteredEvent;
import com.emi.service.OtpService;
import com.emi.utils.enums.OtpPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs after the publishing transaction commits, on the AsyncConfig pool,
 * so a slow or failing side effect never delays or rolls back the request.
 * Failures are logged by AsyncConfig's uncaught exception handler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

//    private final OtpService otpService;
//
//    @Async
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    public void onUserRegistered(UserRegisteredEvent event) {
//        log.info("User registered: userId={}, role={}, eventId={}",
//                event.userId(), event.role(), event.eventId());
//
//        otpService.issue(event.userId(), OtpPurpose.REGISTRATION);
//    }
//
//    @Async
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    public void onOtpRequested(OtpRequestedEvent event) {
//        otpService.issue(event.userId(), event.purpose());
//    }
}
