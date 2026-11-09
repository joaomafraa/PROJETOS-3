
CREATE TABLE atendente_credenciais (
    atendente_id BIGINT PRIMARY KEY,
    senha_hash VARCHAR(100) NOT NULL,

    CONSTRAINT fk_credencial_atendente
        FOREIGN KEY (atendente_id)
        REFERENCES atendentes(id)
        ON DELETE CASCADE
);
