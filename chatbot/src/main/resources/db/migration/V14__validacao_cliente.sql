-- O endereco so pode ser cadastrado por processo confiavel, nunca pelo chat.
CREATE TABLE emails_confiaveis_cliente (
    cliente_id BIGINT PRIMARY KEY REFERENCES clientes(id),
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    email VARCHAR(254) NOT NULL
);

CREATE TABLE desafios_cliente (
    conversa_id BIGINT PRIMARY KEY REFERENCES conversas(id),
    cliente_id BIGINT NOT NULL REFERENCES clientes(id),
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    email VARCHAR(254) NOT NULL,
    codigo_hash VARCHAR(100) NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    tentativas INTEGER NOT NULL,
    usado BOOLEAN NOT NULL,
    validado_ate TIMESTAMP WITH TIME ZONE
);
