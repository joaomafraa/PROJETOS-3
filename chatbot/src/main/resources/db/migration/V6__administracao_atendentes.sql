CREATE SEQUENCE atendentes_id_seq START WITH 1000 INCREMENT BY 1;

ALTER TABLE atendentes
    ADD CONSTRAINT uk_atendentes_empresa_email UNIQUE (empresa_id, email);

CREATE SEQUENCE responsaveis_notificacao_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE responsaveis_notificacao (
    id BIGINT PRIMARY KEY,
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    atendente_id BIGINT NOT NULL REFERENCES atendentes(id),
    tipo VARCHAR(40) NOT NULL,
    data_configuracao TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_responsavel_notificacao_tipo CHECK (tipo IN
        ('NOVO_TICKET', 'ATENDIMENTO_HUMANO_SOLICITADO', 'NOVO_LEAD')),
    CONSTRAINT uk_responsavel_notificacao UNIQUE (empresa_id, tipo, atendente_id)
);

CREATE INDEX idx_responsaveis_notificacao_empresa_tipo
    ON responsaveis_notificacao (empresa_id, tipo, atendente_id);
