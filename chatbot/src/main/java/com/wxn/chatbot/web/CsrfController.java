package com.wxn.chatbot.web;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    @GetMapping("/csrf-token")
    public Map<String, String> token(CsrfToken csrf) {
        return Map.of("token", csrf.getToken(), "parameterName", csrf.getParameterName(), "headerName", csrf.getHeaderName());
    }
}
