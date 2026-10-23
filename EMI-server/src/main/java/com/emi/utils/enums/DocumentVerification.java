package com.emi.utils.enums;

// Admin uploads are VERIFIED straight away; staff uploads stay PENDING until an admin checks them
public enum DocumentVerification {
    VERIFIED, PENDING, REJECTED
}
