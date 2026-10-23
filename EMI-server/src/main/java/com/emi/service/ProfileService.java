package com.emi.service;

import com.emi.dto.response.ProfileResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.UserJpaRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ProfileService {

    private final UserJpaRepository userJpaRepository;

    public ProfileResponse getUserProfile(String userID){
        Users user=userJpaRepository.findById(userID)
                .orElseThrow(()->new DomainException("User not found"));
        return toResponse(user);
    }

    private ProfileResponse toResponse(Users user) {
        return new ProfileResponse(
                user.getUserID(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getRole(),
                user.getStatus()
        );
    }

    private Users toUserEntity(ProfileResponse response) {
        return Users.builder()
                .userID(response.getUserId())
                .fullName(response.getFullName())
                .phoneNumber(response.getPhoneNumber())
                .email(response.getEmail())
                .role(response.getRole())
                .status(response.getStatus())
                .build();
    }
}
