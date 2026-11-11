CREATE TABLE telegram_revisoes_envio (
 envio_id BIGINT PRIMARY KEY REFERENCES telegram_envios_atendente(id),
 atendente_id BIGINT NOT NULL REFERENCES atendentes(id),
 data_confirmacao TIMESTAMP WITH TIME ZONE NOT NULL
);
