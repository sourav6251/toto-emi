package com.emi.service;

import com.emi.dto.request.CustomerProfileRequest;
import com.emi.dto.response.CustomerResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.Customer;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.AssetJpaRepository;
import com.emi.model.reposetory.CustomerJpaRepository;
import com.emi.model.reposetory.EmiInstallmentJpaRepository;
import com.emi.model.reposetory.FinanceJpaRepository;
import com.emi.model.reposetory.UserJpaRepository;
import com.emi.utils.enums.CustomerStatus;
import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomerServiceTest {

    @Mock
    private CustomerJpaRepository customerJpaRepository;

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private FinanceJpaRepository financeJpaRepository;

    @Mock
    private AssetJpaRepository assetJpaRepository;

    @Mock
    private EmiInstallmentJpaRepository emiInstallmentJpaRepository;

    @Mock
    private FinanceSummaryAssembler financeSummaryAssembler;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void createWalkIn_whenUserDoesNotExist_createsUserAndCustomer() {
        CustomerProfileRequest request = CustomerProfileRequest.builder()
                .fullName("John Doe")
                .phoneNumber("9876543210")
                .email("john@example.com")
                .addressLine("123 Street")
                .district("Kolkata")
                .state("WB")
                .pinCode("700001")
                .build();

        when(customerJpaRepository.existsByPhoneNumber("9876543210"))
                .thenReturn(false);
        when(userJpaRepository.findByPhoneNumber("9876543210"))
                .thenReturn(Optional.empty());

        when(userJpaRepository.save(any(Users.class))).thenAnswer(invocation -> {
            Users u = invocation.getArgument(0);
            u.setUserID("user-uuid-123");
            return u;
        });

        when(customerJpaRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer c = invocation.getArgument(0);
            c.setCustomerId("cust-uuid-456");
            return c;
        });

        CustomerResponse response = customerService.createWalkIn("staff-001", true, request);

        assertNotNull(response);
        assertEquals("cust-uuid-456", response.getCustomerId());
        assertEquals("John Doe", response.getFullName());
        assertEquals("9876543210", response.getPhoneNumber());

        ArgumentCaptor<Users> userCaptor = ArgumentCaptor.forClass(Users.class);
        verify(userJpaRepository).save(userCaptor.capture());
        Users savedUser = userCaptor.getValue();
        assertEquals("9876543210", savedUser.getPhoneNumber());
        assertEquals("John Doe", savedUser.getFullName());
        assertEquals("john@example.com", savedUser.getEmail());
        assertEquals(Role.CUSTOMER, savedUser.getRole());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerJpaRepository).save(customerCaptor.capture());
        Customer savedCustomer = customerCaptor.getValue();
        assertEquals("user-uuid-123", savedCustomer.getUserId());
        assertEquals("staff-001", savedCustomer.getAssignedStaffId());
        assertEquals("staff-001", savedCustomer.getCreatedBy());
        assertEquals(CustomerStatus.ACTIVE, savedCustomer.getStatus());
    }

    @Test
    void createWalkIn_whenUserExistsWithoutCustomer_updatesUserAndLinksCustomer() {
        CustomerProfileRequest request = CustomerProfileRequest.builder()
                .fullName("Jane Doe")
                .phoneNumber("9876543211")
                .email("jane@example.com")
                .build();

        Users existingUser = Users.builder()
                .userID("existing-user-uuid")
                .phoneNumber("9876543211")
                .status(UserStatus.PENDING_VERIFICATION)
                .build();

        when(customerJpaRepository.existsByPhoneNumber("9876543211"))
                .thenReturn(false);
        when(userJpaRepository.findByPhoneNumber("9876543211"))
                .thenReturn(Optional.of(existingUser));
        when(customerJpaRepository.findByUserId("existing-user-uuid"))
                .thenReturn(Optional.empty());
        when(userJpaRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerJpaRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer c = invocation.getArgument(0);
            c.setCustomerId("cust-uuid-789");
            return c;
        });

        CustomerResponse response = customerService.createWalkIn("staff-002", false, request);

        assertNotNull(response);
        assertEquals("Jane Doe", existingUser.getFullName());
        assertEquals("jane@example.com", existingUser.getEmail());
        assertEquals(Role.CUSTOMER, existingUser.getRole());
        assertEquals(UserStatus.ACTIVE, existingUser.getStatus());

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerJpaRepository).save(customerCaptor.capture());
        Customer savedCustomer = customerCaptor.getValue();
        assertEquals("existing-user-uuid", savedCustomer.getUserId());
        assertNull(savedCustomer.getAssignedStaffId());
    }

    @Test
    void createWalkIn_whenCustomerAlreadyExistsForUser_throwsConflict() {
        CustomerProfileRequest request = CustomerProfileRequest.builder()
                .fullName("Jane Doe")
                .phoneNumber("9876543211")
                .build();

        Users existingUser = Users.builder()
                .userID("existing-user-uuid")
                .phoneNumber("9876543211")
                .build();

        when(customerJpaRepository.existsByPhoneNumber("9876543211"))
                .thenReturn(false);
        when(userJpaRepository.findByPhoneNumber("9876543211"))
                .thenReturn(Optional.of(existingUser));
        when(customerJpaRepository.findByUserId("existing-user-uuid"))
                .thenReturn(Optional.of(Customer.builder().customerId("cust-1").build()));

        DomainException exception = assertThrows(DomainException.class, () ->
                customerService.createWalkIn("staff-001", true, request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertTrue(exception.getMessage().contains("already exists for this phone number"));
        verify(customerJpaRepository, never()).save(any(Customer.class));
    }

    @Test
    void createWalkIn_whenPhoneExistsWithDifferentName_throwsConflict() {
        CustomerProfileRequest request = CustomerProfileRequest.builder()
                .fullName("Different Name")
                .phoneNumber("9876543212")
                .build();

        when(customerJpaRepository.existsByPhoneNumber("9876543212"))
                .thenReturn(true);

        DomainException exception = assertThrows(DomainException.class, () ->
                customerService.createWalkIn("staff-001", true, request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertTrue(exception.getMessage().contains("already exists for this phone number"));
        verify(userJpaRepository, never()).save(any(Users.class));
        verify(customerJpaRepository, never()).save(any(Customer.class));
    }

    @Test
    void getCustomerDetail_byUserId_returnsOwnCustomerData() {
        String loggedInUserId = "user-123";
        Customer customer = Customer.builder()
                .customerId("cust-123")
                .userId(loggedInUserId)
                .fullName("My Own Name")
                .phoneNumber("9876543210")
                .status(CustomerStatus.ACTIVE)
                .build();

        when(customerJpaRepository.findByUserId(loggedInUserId))
                .thenReturn(Optional.of(customer));
        when(financeJpaRepository.findAllByCustomerId("cust-123"))
                .thenReturn(java.util.List.of());

        var detail = customerService.getCustomerDetail(loggedInUserId);

        assertNotNull(detail);
        assertEquals("cust-123", detail.getCustomerId());
        assertEquals("user-123", detail.getUserId());
        assertEquals("My Own Name", detail.getFullName());
        assertEquals("9876543210", detail.getPhoneNumber());
        verify(customerJpaRepository).findByUserId(loggedInUserId);
    }

    @Test
    void getCustomerDetail_byUserId_whenNotFound_throwsNotFound() {
        when(customerJpaRepository.findByUserId("unknown-user"))
                .thenReturn(Optional.empty());

        DomainException exception = assertThrows(DomainException.class, () ->
                customerService.getCustomerDetail("unknown-user")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void searchCustomerByMobileNo_whenQueryLessThan3Digits_throwsBadRequest() {
        DomainException exception = assertThrows(DomainException.class, () ->
                customerService.searchCustomerByMobileNo("98")
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void searchCustomerByMobileNo_whenQueryHas3Digits_returnsMatchingCustomers() {
        Users user = Users.builder()
                .userID("user-987")
                .fullName("Priya Sharma")
                .phoneNumber("9876543210")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userJpaRepository.searchByPhoneOrName("987"))
                .thenReturn(java.util.List.of(user));

        java.util.List<Users> results = customerService.searchCustomerByMobileNo("987");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("user-987", results.get(0).getUserID());
        assertEquals("9876543210", results.get(0).getPhoneNumber());
        verify(userJpaRepository).searchByPhoneOrName("987");
    }

    @Test
    void createOrUpdateWalkIn_whenCustomerAlreadyExists_updatesProfileFields() {
        CustomerProfileRequest request = CustomerProfileRequest.builder()
                .fullName("Updated Name")
                .phoneNumber("9876543210")
                .email("updated@example.com")
                .addressLine("New Address")
                .build();

        Customer existing = Customer.builder()
                .customerId("cust-123")
                .fullName("Old Name")
                .phoneNumber("9876543210")
                .status(CustomerStatus.ACTIVE)
                .build();

        when(customerJpaRepository.findByPhoneNumber("9876543210"))
                .thenReturn(Optional.of(existing));
        when(customerJpaRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.createOrUpdateWalkIn("staff-1", false, request);

        assertNotNull(response);
        assertEquals("Updated Name", response.getFullName());
        assertEquals("9876543210", response.getPhoneNumber());
        verify(customerJpaRepository).save(existing);
    }
}

