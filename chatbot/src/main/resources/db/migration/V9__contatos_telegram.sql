ALTER TABLE clientes ALTER COLUMN numero_telefone DROP NOT NULL;
CREATE SEQUENCE telegram_conversas_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE telegram_contatos (
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    usuario_id BIGINT NOT NULL CHECK (usuario_id > 0),
    cliente_id BIGINT NOT NULL REFERENCES clientes(id),
    conversa_id BIGINT NOT NULL REFERENCES conversas(id),
    PRIMARY KEY (empresa_id, usuario_id),
    UNIQUE (conversa_id)
);
