package com.wxn.chatbot.web;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.wxn.chatbot.dto.LeadResumo;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.LeadService;
@RestController
@RequestMapping("/api/leads")
public class PainelLeadsController {
    private final LeadService leads;
    private final ContextoAcesso contexto;
    public PainelLeadsController(LeadService leads, ContextoAcesso contexto) { this.leads = leads; this.contexto = contexto; }
    @GetMapping public List<LeadResumo> listar() { return leads.listar(contexto.empresaIdAtual()); }
}
