package com.wxn.chatbot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.wxn.chatbot.model.Cliente;

/**
 * Acesso aos clientes, sempre filtrado por empresa nas buscas por telefone.
 * O telefone recebido deve estar normalizado com Cliente.normalizarTelefone.
 */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByEmpresaIdAndNumeroTelefone(Long empresaId, String numeroTelefone);

    boolean existsByEmpresaIdAndNumeroTelefone(Long empresaId, String numeroTelefone);

    @Query(value = "select nextval('clientes_id_seq')", nativeQuery = true)
    Long proximoId();
}
