package com.example.sapcimonitor.controller;

import com.example.sapcimonitor.service.SapCiClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final SapCiClient sapCiClient;

    public AuthController(SapCiClient sapCiClient) {
        this.sapCiClient = sapCiClient;
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateCredentials(@RequestBody(required = false) Map<String, String> creds) {
        Map<String, Object> resp = new HashMap<>();
        String username = creds != null ? creds.get("username") : null;
        String password = creds != null ? creds.get("password") : null;

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            resp.put("valid", false);
            resp.put("error", "Username and password are required.");
            return ResponseEntity.badRequest().body(resp);
        }

        String token = sapCiClient.fetchUserAccessToken(username, password);
        if (token != null && !token.isBlank()) {
            resp.put("valid", true);
            resp.put("username", username);
            resp.put("system", "SAP_CPI_PROD");
            resp.put("client", "100");
            resp.put("message", "Authenticated successfully with SAP BTP XSUAA");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("valid", false);
            resp.put("error", "Authentication failed: invalid BTP credentials or MFA required.");
            return ResponseEntity.status(401).body(resp);
        }
    }
}
