CREATE TABLE telegram_abertura_chamado (
    conversa_id BIGINT PRIMARY KEY REFERENCES conversas(id),
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    aguardando_descricao BOOLEAN NOT NULL DEFAULT FALSE
);
