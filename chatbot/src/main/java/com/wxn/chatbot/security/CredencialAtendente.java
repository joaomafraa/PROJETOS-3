
package com.wxn.chatbot.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "atendente_credenciais")
public class CredencialAtendente {

    @Id
    @Column(name = "atendente_id")
    private Long atendenteId;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    protected CredencialAtendente() {
    }

    public CredencialAtendente(Long atendenteId, String senhaHash) {
        if (atendenteId == null || atendenteId <= 0) {
            throw new IllegalArgumentException("ID invalido.");
        }

        if (senhaHash == null || senhaHash.isBlank()) {
            throw new IllegalArgumentException("Hash obrigatorio.");
        }

        this.atendenteId = atendenteId;
        this.senhaHash = senhaHash;
    }

    public Long getAtendenteId() {
        return atendenteId;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void alterarSenhaHash(String novoHash) {
        if (novoHash == null || novoHash.isBlank()) {
            throw new IllegalArgumentException("Hash obrigatorio.");
        }

        this.senhaHash = novoHash;
    }
}
