CREATE TABLE configuracoes_atendimento (
    empresa_id BIGINT PRIMARY KEY REFERENCES empresas(id),
    fuso VARCHAR(60) NOT NULL,
    mensagem_fora_expediente VARCHAR(500) NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_por BIGINT REFERENCES atendentes(id)
);

CREATE TABLE horarios_atendimento (
    empresa_id BIGINT NOT NULL REFERENCES configuracoes_atendimento(empresa_id) ON DELETE CASCADE,
    posicao INTEGER NOT NULL,
    dia_semana VARCHAR(10) NOT NULL,
    abertura TIME NOT NULL,
    fechamento TIME NOT NULL,
    PRIMARY KEY (empresa_id, posicao),
    CONSTRAINT ck_horario_dia CHECK (dia_semana IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT ck_horario_intervalo CHECK (abertura < fechamento)
);

CREATE TABLE assuntos_atendimento_automatico (
    empresa_id BIGINT NOT NULL REFERENCES configuracoes_atendimento(empresa_id) ON DELETE CASCADE,
    posicao INTEGER NOT NULL,
    assunto VARCHAR(80) NOT NULL,
    PRIMARY KEY (empresa_id, posicao)
);
