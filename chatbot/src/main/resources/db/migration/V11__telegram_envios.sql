CREATE TABLE telegram_envios (
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    atualizacao_id BIGINT NOT NULL,
    chat_id BIGINT NOT NULL,
    conteudo TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE', 'ENVIADO', 'INCERTO')),
    PRIMARY KEY (empresa_id, atualizacao_id),
    FOREIGN KEY (empresa_id, atualizacao_id) REFERENCES telegram_atualizacoes(empresa_id, atualizacao_id)
);
