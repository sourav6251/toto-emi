package com.emi.controller;


import com.emi.annotation.CurrentUserId;
import com.emi.dto.response.ApiResponse;
import com.emi.dto.response.ProfileResponse;
import com.emi.service.ProfileService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile")
@AllArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping()
    public ResponseEntity<?> getProfileData(@CurrentUserId String userID){
        ProfileResponse profile=profileService.getUserProfile(userID);
        return ResponseEntity.ok(ApiResponse.success().add("data",profile));
    }
}
