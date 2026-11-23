package com.wxn.chatbot.web;

import com.wxn.chatbot.service.DocumentosConhecimentoService;
import com.wxn.chatbot.service.DocumentosConhecimentoService.Documento;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/documentos")
public class DocumentosConhecimentoController {
    private final DocumentosConhecimentoService servico;
    public DocumentosConhecimentoController(DocumentosConhecimentoService servico) { this.servico = servico; }
    public record Conteudo(String titulo, String conteudo) { }
    public record Estado(Boolean ativo) { }
    @GetMapping public List<Documento> listar() { return servico.listar(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Documento criar(@RequestBody Conteudo dados) { return servico.criar(dados.titulo(), dados.conteudo()); }
    @PutMapping("/{id}") public Documento atualizar(@PathVariable Long id, @RequestBody Conteudo dados) { return servico.atualizar(id, dados.titulo(), dados.conteudo()); }
    @PatchMapping("/{id}/estado") public Documento estado(@PathVariable Long id, @RequestBody Estado dados) {
        if (dados.ativo() == null) throw new IllegalArgumentException("Informe o estado do documento.");
        return servico.ativar(id, dados.ativo());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage())); }
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> ausente(NoSuchElementException e) { return ResponseEntity.status(404).body(Map.of("mensagem", e.getMessage())); }
}
