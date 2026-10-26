CREATE TABLE empresas (
    id BIGINT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL
);

CREATE TABLE clientes (
    id BIGINT PRIMARY KEY,
    nome VARCHAR(255),
    email VARCHAR(254),
    numero_telefone VARCHAR(32) NOT NULL,
    identidade_confirmada BOOLEAN NOT NULL,
    empresa_id BIGINT,
    empresa_informada VARCHAR(255),
    CONSTRAINT fk_clientes_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id)
);

CREATE TABLE chatbots (
    id BIGINT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    nome VARCHAR(255) NOT NULL,
    mensagem_boas_vindas VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL,
    CONSTRAINT fk_chatbots_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id)
);

CREATE TABLE atendentes (
    id BIGINT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    perfil VARCHAR(32) NOT NULL,
    ativo BOOLEAN NOT NULL,
    CONSTRAINT fk_atendentes_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id)
);

CREATE TABLE conversas (
    id BIGINT PRIMARY KEY,
    cliente_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    chatbot_id BIGINT NOT NULL,
    responsavel_id BIGINT,
    data_inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    data_encerramento TIMESTAMP WITH TIME ZONE,
    status VARCHAR(40) NOT NULL,
    CONSTRAINT fk_conversas_cliente FOREIGN KEY (cliente_id) REFERENCES clientes (id),
    CONSTRAINT fk_conversas_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id),
    CONSTRAINT fk_conversas_chatbot FOREIGN KEY (chatbot_id) REFERENCES chatbots (id),
    CONSTRAINT fk_conversas_responsavel FOREIGN KEY (responsavel_id) REFERENCES atendentes (id)
);

CREATE INDEX idx_conversas_fila
    ON conversas (empresa_id, status, data_inicio, id);
