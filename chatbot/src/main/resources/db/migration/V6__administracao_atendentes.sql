-- HU08.3: administracao de atendentes e responsaveis por notificacoes.

-- IDs de novos atendentes passam a vir de sequencia. Comeca em 1000 para nao colidir
-- com os IDs informados manualmente ate agora (atendente do painel = 1).
-- ATENCAO: antes de aplicar no Neon, conferir que SELECT MAX(id) FROM atendentes < 1000.
CREATE SEQUENCE atendentes_id_seq START WITH 1000 INCREMENT BY 1;

-- O e-mail ja e gravado normalizado (minusculo e sem espacos) pela entidade Atendente.
-- ATENCAO: conferir no banco antes de aplicar se ja existem e-mails repetidos na mesma empresa.
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
