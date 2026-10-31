// Painel de contexto do atendimento.
// Mostra somente dados vindos da API da fila (ConversaResumo).
// Campos que a API nao envia aparecem como "Não disponível".
(() => {
  const NAO_DISPONIVEL = 'Não disponível';
  const painel = document.querySelector('#contexto');
  const resumo = document.querySelector('#contexto-resumo');
  if (!painel || !resumo) return;

  const TEXTO_INICIAL = resumo.textContent;
  const nomesStatus = {
    EM_ATENDIMENTO_AUTOMATICO: 'Atendimento automático',
    AGUARDANDO_ATENDENTE: 'Aguardando atendente',
    EM_ATENDIMENTO_HUMANO: 'Atendimento humano',
    ENCERRADA: 'Encerrada'
  };

  function campo(nome) { return painel.querySelector(`[data-campo="${nome}"]`); }

  function preencher(nome, valor) {
    const alvo = campo(nome);
    if (!alvo) return;
    const vazio = valor === null || valor === undefined || String(valor).trim() === '';
    alvo.textContent = vazio ? NAO_DISPONIVEL : String(valor);
    alvo.classList.toggle('indisponivel', vazio);
  }

  function preencherStatus(texto, status) {
    const selo = document.createElement('span');
    selo.className = 'status-badge';
    selo.textContent = texto;
    if (status) selo.dataset.status = status;
    campo('status')?.replaceChildren(selo);
  }

  // Formata telefones brasileiros no padrao E.164; outros ficam como vieram.
  function telefone(valor) {
    if (!valor) return null;
    const br = /^\+55(\d{2})(\d{4,5})(\d{4})$/.exec(valor);
    return br ? `+55 (${br[1]}) ${br[2]}-${br[3]}` : valor;
  }

  function dataHora(valor) {
    if (!valor) return null;
    const data = new Date(valor);
    return Number.isNaN(data.getTime()) ? null : data.toLocaleString('pt-BR');
  }

  // Volta ao estado inicial, igual ao layout sem conversa selecionada.
  function limpar() {
    resumo.textContent = TEXTO_INICIAL;
    preencher('cliente', 'Aguardando seleção');
    preencher('telefone', '—');
    preencher('chatbot', '—');
    preencherStatus('Não selecionado');
  }

  function mostrar(conversa) {
    if (!conversa) { limpar(); return; }
    const inicio = dataHora(conversa.dataInicio);
    // Resumo factual com dados do banco; nada e inventado.
    resumo.textContent = inicio
      ? `Conversa #${conversa.id}, iniciada em ${inicio}.`
      : `Conversa #${conversa.id}.`;
    preencher('cliente', conversa.clienteNome);
    preencher('telefone', telefone(conversa.numeroTelefone));
    preencher('chatbot', conversa.chatbotNome);
    preencherStatus(conversa.status ? (nomesStatus[conversa.status] || conversa.status) : NAO_DISPONIVEL,
      conversa.status);
  }

  window.contextoAtendimento = { mostrar, limpar };
})();
