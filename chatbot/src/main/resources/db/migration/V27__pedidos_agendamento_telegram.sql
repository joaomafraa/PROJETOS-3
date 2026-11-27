CREATE TABLE telegram_pedido_agendamento (
    conversa_id BIGINT PRIMARY KEY REFERENCES conversas(id),
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    tipo VARCHAR(32) NOT NULL,
    necessidade TEXT,
    etapa VARCHAR(32) NOT NULL,
    CONSTRAINT ck_agendamento_telegram_tipo CHECK (tipo IN ('DEMONSTRACAO', 'REUNIAO', 'VISITA_TECNICA')),
    CONSTRAINT ck_agendamento_telegram_etapa CHECK (etapa IN ('NECESSIDADE', 'HORARIO'))
);
