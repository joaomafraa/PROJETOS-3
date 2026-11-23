package com.wxn.chatbot.web;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.wxn.chatbot.dto.LeadResumo;
import com.wxn.chatbot.dto.RegistrarLeadComando;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.LeadDuplicadoException;
import com.wxn.chatbot.service.LeadService;
@RestController
@RequestMapping("/api/leads")
public class PainelLeadsController {
    private final LeadService leads;
    private final ContextoAcesso contexto;
    public PainelLeadsController(LeadService leads, ContextoAcesso contexto) { this.leads = leads; this.contexto = contexto; }
    @GetMapping public List<LeadResumo> listar(@RequestParam(required = false) String contato) {
        Long empresaId = contexto.empresaIdAtual();
        return contato == null ? leads.listar(empresaId) : leads.buscarPorContato(empresaId, contato);
    }

    @PostMapping
    public ResponseEntity<LeadResumo> registrar(@RequestBody RegistrarLeadComando comando) {
        if (comando.origem() == null) throw new IllegalArgumentException("Informe a origem do lead.");
        return ResponseEntity.status(HttpStatus.CREATED).body(leads.registrar(contexto.empresaIdAtual(), comando));
    }

    @ExceptionHandler(LeadDuplicadoException.class)
    public ResponseEntity<Map<String, String>> duplicado(LeadDuplicadoException erro) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", erro.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException erro) {
        return ResponseEntity.badRequest().body(Map.of("erro", erro.getMessage()));
    }
}
