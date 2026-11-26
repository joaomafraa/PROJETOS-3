-- HU04.1: planos e servicos FICTICIOS para demonstracao.
-- Rode manualmente, apenas em banco de teste ou com um cliente de teste combinado com o grupo.
-- Troque :empresa_id e :cliente_id pelos IDs do cliente de teste (o cliente precisa ser dessa empresa).
-- Para descobrir os IDs: select id, nome, empresa_id from clientes where numero_telefone = '+55...';

insert into servicos_contratados (empresa_id, cliente_id, tipo, nome, detalhe, situacao, inicio, fim, demonstracao)
select c.empresa_id, c.id, v.tipo, v.nome, v.detalhe, v.situacao, v.inicio, v.fim, true
from clientes c
cross join (values
    ('PLANO',   'Plano Empresarial',   'Portal corporativo para até 50 usuários (demonstração).', 'ATIVO',     DATE '2026-03-01', cast(null as date)),
    ('SERVICO', 'Backup em nuvem',     'Cópia diária com retenção de 30 dias (demonstração).',     'ATIVO',     DATE '2026-03-01', cast(null as date)),
    ('SERVICO', 'Suporte prioritário', 'Atendimento em até 4 horas úteis (demonstração).',         'ENCERRADO', DATE '2025-01-10', DATE '2025-12-31')
) as v(tipo, nome, detalhe, situacao, inicio, fim)
where c.id = :cliente_id and c.empresa_id = :empresa_id;

-- Para remover os dados de demonstracao desse cliente:
-- delete from servicos_contratados where cliente_id = :cliente_id and empresa_id = :empresa_id and demonstracao = true;
