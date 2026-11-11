package com.wxn.chatbot.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaginasController {
    @GetMapping("/")
    public String inicio() { return "forward:/abertura/index.html"; }
    @GetMapping("/entrar")
    public String entrar() { return "forward:/login.html"; }
    @GetMapping("/painel")
    public String painel() { return "forward:/painel.html"; }
}
