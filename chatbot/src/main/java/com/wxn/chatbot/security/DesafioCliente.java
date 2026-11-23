package com.wxn.chatbot.security;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "desafios_cliente")
class DesafioCliente {
    @Id @Column(name = "conversa_id") Long conversaId;
    @Column(name = "cliente_id", nullable = false) Long clienteId;
    @Column(name = "empresa_id", nullable = false) Long empresaId;
    @Column(nullable = false, length = 254) String email;
    @Column(name = "codigo_hash", nullable = false, length = 100) String codigoHash;
    @Column(name = "expira_em", nullable = false) Instant expiraEm;
    @Column(nullable = false) int tentativas;
    @Column(nullable = false) boolean usado;
    @Column(name = "validado_ate") Instant validadoAte;

    protected DesafioCliente() {}
    DesafioCliente(Long conversaId) { this.conversaId = conversaId; }
}
