CREATE TABLE tickets (
    id BIGINT PRIMARY KEY CHECK (id > 0),
    protocolo TEXT NOT NULL,
    descricao TEXT NOT NULL,
    tipo VARCHAR(32) NOT NULL,
    data_abertura TIMESTAMP WITH TIME ZONE NOT NULL,
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    cliente_id BIGINT NOT NULL REFERENCES clientes(id),
    conversa_origem_id BIGINT NOT NULL REFERENCES conversas(id),
    status VARCHAR(32) NOT NULL,
    data_conclusao TIMESTAMP WITH TIME ZONE,
    responsavel_id BIGINT REFERENCES atendentes(id),
    CONSTRAINT ck_ticket_tipo CHECK (tipo IN
        ('SUPORTE', 'ORCAMENTO', 'DEMONSTRACAO', 'REUNIAO', 'VISITA_TECNICA')),
    CONSTRAINT ck_ticket_estado CHECK (
        (status = 'PENDENTE' AND data_conclusao IS NULL)
        OR (status = 'EM_ATENDIMENTO' AND responsavel_id IS NOT NULL AND data_conclusao IS NULL)
        OR (status = 'CONCLUIDO' AND responsavel_id IS NOT NULL AND data_conclusao IS NOT NULL)
    )
);

CREATE INDEX idx_tickets_empresa_abertura ON tickets (empresa_id, data_abertura, id);
