# Authentication API Documentation

Base URL: `http://localhost:4000/auth`

---

## 1. Send / Resend OTP

### URL
`POST /auth/otp/send`

### Request Body
```json
{
  "phoneNumber": "9876543210"
}
```

#### Field Specifications:
| Field | Type | Required | Validation Rules | Description |
|---|---|---|---|---|
| `phoneNumber` | String | Yes | Must not be blank. Regex: `^[6-9]\d{9}$` | 10-digit Indian mobile number |

---

### Response Body Structure

#### Success Response (200 OK)
```json
{
  "message": "353768"
}
```

#### Error Responses

##### 1. Validation Error (400 Bad Request)
```json
{
  "message": "Validation failed",
  "data": {
    "status": 400,
    "error": "Bad Request",
    "path": "/auth/otp/send",
    "timestamp": "2026-10-15T19:02:16.52990552+05:30",
    "fieldErrors": {
      "phoneNumber": "Phone number must be a valid 10-digit Indian mobile number"
    }
  }
}
```

##### 2. Method Not Allowed (405 Method Not Allowed)
```json
{
  "message": "Request method 'GET' is not supported",
  "data": {
    "status": 405,
    "error": "Method Not Allowed",
    "path": "/auth/otp/send",
    "timestamp": "2026-10-15T19:32:19.060402814+05:30"
  }
}
```

##### 3. Internal Server Error (500 Internal Server Error)
```json
{
  "message": "Something went wrong",
  "data": {
    "status": 500,
    "error": "Internal Server Error",
    "path": "/auth/otp/send",
    "timestamp": "2026-10-15T19:20:43.136617717+05:30"
  }
}
```

---

### Expected Status Codes
| Status Code | Meaning | Description |
|---|---|---|
| `200 OK` | Success | OTP has been generated successfully and returned/sent. |
| `400 Bad Request` | Validation Error | Phone number is missing or does not match the 10-digit Indian number format. |
| `405 Method Not Allowed` | Method Not Allowed | An HTTP method other than `POST` was called on this endpoint. |
| `500 Internal Server Error` | Server Error | An unhandled server-side error occurred. |

---

## 2. Verify OTP

### URL
`POST /auth/otp/verify`

### Request Body
```json
{
  "phoneNumber": "9876543210",
  "otp": "353768"
}
```

#### Field Specifications:
| Field | Type | Required | Validation Rules | Description |
|---|---|---|---|---|
| `phoneNumber` | String | Yes | Must not be blank. Regex: `^[6-9]\d{9}$` | 10-digit Indian mobile number |
| `otp` | String | Yes | Must not be blank. Regex: `^\d{6}$` | 6-digit numeric OTP |

---

### Response Body Structure

#### Success Response (200 OK)
**Response Headers:**
```http
Set-Cookie: registerToken=<JWT_TOKEN>; Path=/; Max-Age=3600; Secure; HttpOnly; SameSite=Strict
```
**Response Body:**
```json
{
  "message": "success",
  "data": {
    "message": "OTP verified successfully",
    "registerToken": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxMDE2OGU5Yy1hZWRiLTQxNmEtODY4Mi05OTFlMTVmYWQ0NjkiLCJpYXQiOjE3OTIwNzMxNDUsImV4cCI6MTc5MjA3Njc0NX0.R46L_HtVYZariZ1Cr8uoSWxJ-TAyHxbs21Bh2qvtpVQMKP4Cspfl7ADQ2wn00fL5"
  }
}
```

#### Error Responses

##### 1. Invalid OTP / Expired OTP (400 Bad Request)
```json
{
  "message": "Invalid otp",
  "data": {
    "status": 400,
    "error": "Bad Request",
    "path": "/auth/otp/verify",
    "timestamp": "2026-10-15T19:02:16.52990552+05:30"
  }
}
```
*(Also returns `400 Bad Request` with `"message": "Otp Expired"` if OTP exceeded the 5-minute validity window, or `"message": "No OTP found. Please request a new OTP"`)*

##### 2. User Not Found (404 Not Found)
```json
{
  "message": "User not found",
  "data": {
    "status": 404,
    "error": "Not Found",
    "path": "/auth/otp/verify",
    "timestamp": "2026-10-15T19:02:16.52990552+05:30"
  }
}
```

##### 3. Validation Error (400 Bad Request)
```json
{
  "message": "Validation failed",
  "data": {
    "status": 400,
    "error": "Bad Request",
    "path": "/auth/otp/verify",
    "timestamp": "2026-10-15T19:02:16.52990552+05:30",
    "fieldErrors": {
      "otp": "OTP must be 6 digits"
    }
  }
}
```

---

### Expected Status Codes
| Status Code | Meaning | Description |
|---|---|---|
| `200 OK` | Success | OTP verified successfully. A signed `registerToken` JWT is returned and set as an HttpOnly cookie. |
| `400 Bad Request` | Invalid / Expired OTP | The provided OTP does not match, has expired, or payload validation failed. |
| `404 Not Found` | User Not Found | No user record found for the provided phone number. |
| `405 Method Not Allowed` | Method Not Allowed | An HTTP method other than `POST` was used. |
| `500 Internal Server Error` | Server Error | An unhandled server-side error occurred. |

---

## 3. Register User

### URL
`POST /auth/register`

### Request Headers / Cookies
| Name | Type | Required | Description |
|---|---|---|---|
| `registerToken` | Cookie or Header | Yes | The JWT token obtained from `POST /auth/otp/verify`. Can be supplied via cookie `registerToken=<JWT>` or header `registerToken: <JWT>`. |

### Request Body
```json
{
  "fullName": "Alice Smith",
  "phoneNumber": "9876543210",
  "email": "alice@example.com",
  "password": "Password@123"
}
```

#### Field Specifications:
| Field | Type | Required | Validation Rules | Description |
|---|---|---|---|---|
| `fullName` | String | Yes | Must not be blank. Max 150 characters | Full legal name |
| `phoneNumber` | String | Yes | Must not be blank. Regex: `^[6-9]\d{9}$` | 10-digit Indian mobile number |
| `email` | String | No | Valid email format. Max 255 characters | User email address |
| `password` | String | Yes | Must not be blank. Min 8, max 64 characters | User account password |

---

### Response Body Structure

#### Success Response (201 Created)
**Response Headers:**
```http
Set-Cookie: AccessToken=<JWT_TOKEN>; Path=/; Max-Age=3600; Secure; HttpOnly; SameSite=Strict
```
**Response Body:**
```json
{
  "message": "success",
  "data": {
    "data": {
      "userId": "10168e9c-aedb-416a-8682-991e15fad469",
      "phoneNumber": "9876543210",
      "role": "CUSTOMER",
      "status": "ACTIVE",
      "message": "Registration successful"
    },
    "accessToken": "eyJhbGciOiJIUzM4NCJ9.eyJyb2xlIjoiQ1VTVE9NRVIiLCJzdWIiOiIxMDE2OGU5Yy1hZWRiLTQxNmEtODY4Mi05OTFlMTVmYWQ0NjkiLCJpYXQiOjE3OTIwNzMyODcsImV4cCI6MTc5MjA3MzMzN30._xctJ4ihHzt7wTVCbWfkRkb4MhrN57NQgagwWxrUDH-tFDL6CUNjebHsG-fPVNw8"
  }
}
```

#### Error Responses

##### 1. Missing or Invalid Register Token (401 Unauthorized)
```json
{
  "message": "Missing 'registerToken' token in request headers or cookies",
  "data": {
    "status": 401,
    "error": "Unauthorized",
    "path": "/auth/register",
    "timestamp": "2026-10-15T18:55:07.316919159+05:30"
  }
}
```
*(Or `"message": "Invalid or expired token"` if an expired or tampered token was provided)*

##### 2. Phone Number / Email Already Registered (409 Conflict)
```json
{
  "message": "Phone number is already registered",
  "data": {
    "status": 409,
    "error": "Conflict",
    "path": "/auth/register",
    "timestamp": "2026-10-15T19:38:21.296501505+05:30"
  }
}
```
*(Or `"message": "Email is already registered"` if email is already taken)*

##### 3. Validation Failure (400 Bad Request)
```json
{
  "message": "Validation failed",
  "data": {
    "status": 400,
    "error": "Bad Request",
    "path": "/auth/register",
    "timestamp": "2026-10-15T19:02:16.52990552+05:30",
    "fieldErrors": {
      "password": "Password must be 8 to 64 characters",
      "email": "Email must be valid"
    }
  }
}
```

---

### Expected Status Codes
| Status Code | Meaning | Description |
|---|---|---|
| `201 Created` | Created | User successfully registered and activated. Session `AccessToken` issued. |
| `400 Bad Request` | Validation Error | Missing required fields or payload failed validation. |
| `401 Unauthorized` | Unauthorized | `registerToken` is missing, expired, or invalid. |
| `404 Not Found` | Not Found | User ID encoded in `registerToken` was not found. |
| `409 Conflict` | Conflict | The mobile number or email is already registered to another active user. |
| `405 Method Not Allowed` | Method Not Allowed | An HTTP method other than `POST` was used. |
| `500 Internal Server Error` | Server Error | An unhandled server-side error occurred. |

---

## 4. User Login

### URL
`POST /auth/login`

### Request Body
```json
{
  "phoneNumber": "9876543210",
  "password": "Password@123"
}
```

#### Field Specifications:
| Field | Type | Required | Validation Rules | Description |
|---|---|---|---|---|
| `phoneNumber` | String | Yes | Must not be blank. Regex: `^[6-9]\d{9}$` | Registered 10-digit mobile number |
| `password` | String | Yes | Must not be blank | Account password |

---

### Response Body Structure

#### Success Response (200 OK)
**Response Headers:**
```http
Set-Cookie: AccessToken=<JWT_TOKEN>; Path=/; Max-Age=3600; Secure; HttpOnly; SameSite=Strict
```
**Response Body:**
```json
{
  "message": "success",
  "data": {
    "data": {
      "userId": "10168e9c-aedb-416a-8682-991e15fad469",
      "fullName": "Alice Smith",
      "phoneNumber": "9876543210",
      "email": "alice@example.com",
      "role": "CUSTOMER",
      "status": "ACTIVE"
    },
    "accessToken": "eyJhbGciOiJIUzM4NCJ9.eyJyb2xlIjoiQ1VTVE9NRVIiLCJzdWIiOiIxMDE2OGU5Yy1hZWRiLTQxNmEtODY4Mi05OTFlMTVmYWQ0NjkiLCJpYXQiOjE3OTIwNzMzMDksImV4cCI6MTc5MjA3MzM1OX0.fKyrH2EW93UKPpUcp79oqZlP-iKWdh9w9GJWMblpTLjoV_r4J-5F89akuji656iC"
  }
}
```

#### Error Responses

##### 1. Invalid Credentials (401 Unauthorized)
```json
{
  "message": "Invalid phone number or password",
  "data": {
    "status": 401,
    "error": "Unauthorized",
    "path": "/auth/login",
    "timestamp": "2026-10-15T19:38:42.353349898+05:30"
  }
}
```

##### 2. Account Not Verified / Suspended (403 Forbidden)
```json
{
  "message": "Account verification pending. Please verify OTP first.",
  "data": {
    "status": 403,
    "error": "Forbidden",
    "path": "/auth/login",
    "timestamp": "2026-10-15T19:38:42.353349898+05:30"
  }
}
```
*(Or `"message": "Account is suspended. Please contact support."`)*

##### 3. Validation Error (400 Bad Request)
```json
{
  "message": "Validation failed",
  "data": {
    "status": 400,
    "error": "Bad Request",
    "path": "/auth/login",
    "timestamp": "2026-10-15T19:02:16.52990552+05:30",
    "fieldErrors": {
      "password": "Password is required"
    }
  }
}
```

---

### Expected Status Codes
| Status Code | Meaning | Description |
|---|---|---|
| `200 OK` | Success | Login successful. Session `AccessToken` returned and set as cookie. |
| `400 Bad Request` | Bad Request | Phone number or password is missing or improperly formatted. |
| `401 Unauthorized` | Unauthorized | Phone number not found or incorrect password. |
| `403 Forbidden` | Forbidden | Account is suspended or pending verification. |
| `405 Method Not Allowed` | Method Not Allowed | An HTTP method other than `POST` was used. |
| `500 Internal Server Error` | Server Error | An unhandled server-side error occurred. |

