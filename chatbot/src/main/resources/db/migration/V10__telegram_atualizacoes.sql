CREATE TABLE telegram_atualizacoes (
 empresa_id BIGINT NOT NULL REFERENCES empresas(id),
 atualizacao_id BIGINT NOT NULL CHECK (atualizacao_id >= 0),
 conversa_id BIGINT NOT NULL REFERENCES conversas(id),
 PRIMARY KEY (empresa_id, atualizacao_id)
);
