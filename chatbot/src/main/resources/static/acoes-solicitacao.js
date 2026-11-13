// Acoes da solicitacao selecionada (HU10.3): assumir e concluir.
// Usa os pontos de encaixe de solicitacoes.js: o evento "solicitacao-selecionada",
// o container #acoes-solicitacao e window.solicitacoes.recarregar().
// A permissao real e conferida no servidor; aqui os botoes apenas refletem o estado.
(() => {
  const acoes = document.querySelector('#acoes-solicitacao');
  const detalhe = document.querySelector('#detalhe-solicitacao');
  const aviso = document.querySelector('#aviso-acoes-solicitacao');
  if (!acoes || !detalhe || !aviso) return;

  let ticketAtual = null;
  let avisoDoTicket = null;
  let usuarioId = null;
  let usuarioCarregado = null;
  let confirmandoConclusao = false;
  let enviando = false;

  function botao(texto, classe, aoClicar) {
    const item = document.createElement('button');
    item.type = 'button';
    item.className = `button ${classe}`.trim();
    item.textContent = texto;
    item.addEventListener('click', aoClicar);
    return item;
  }

  function informar(texto, erro = false) {
    aviso.textContent = texto;
    aviso.className = erro ? 'system-message erro' : 'system-message';
    aviso.hidden = !texto;
  }

  // O filtro por responsavel ja informa quem e o usuario logado ("voce").
  function carregarUsuario() {
    if (!usuarioCarregado) {
      usuarioCarregado = fetch('/api/tickets/responsaveis', { cache: 'no-store', headers: { Accept: 'application/json' } })
        .then(resposta => (resposta.ok ? resposta.json() : []))
        .then(opcoes => { usuarioId = (opcoes.find(opcao => opcao.voce) || {}).id ?? null; })
        .catch(() => { usuarioCarregado = null; });
    }
    return usuarioCarregado;
  }

  async function tokenCsrf() {
    const resposta = await fetch('/csrf-token', { cache: 'no-store' });
    if (!resposta.ok) throw new Error('csrf');
    return resposta.json();
  }

  function mensagemDeErro(status, acao) {
    if (status === 401) return 'Sua sessão expirou. Entre novamente para continuar.';
    if (status === 403) return acao === 'concluir'
      ? 'Somente o responsável pela solicitação pode concluí-la.'
      : 'Seu usuário não pode assumir esta solicitação.';
    if (status === 404) return 'Solicitação não encontrada. Ela pode pertencer a outra empresa.';
    if (status === 409) return acao === 'concluir'
      ? 'Esta solicitação não está mais em atendimento. Os dados foram atualizados.'
      : 'Esta solicitação já foi assumida por outro atendente. Os dados foram atualizados.';
    return 'Não foi possível atualizar a solicitação. Tente novamente.';
  }

  async function executar(acao) {
    if (!ticketAtual || enviando) return;
    const ticket = ticketAtual;
    enviando = true;
    confirmandoConclusao = false;
    desenhar();
    informar(acao === 'concluir' ? 'Concluindo solicitação…' : 'Assumindo solicitação…');
    let status = 0;
    try {
      const csrf = await tokenCsrf();
      const resposta = await fetch(`/api/tickets/${encodeURIComponent(ticket.id)}/${acao}`, {
        method: 'POST',
        cache: 'no-store',
        headers: { Accept: 'application/json', [csrf.headerName]: csrf.token }
      });
      status = resposta.status;
    } catch {
      informar('Não foi possível conectar. Confira sua conexão e tente novamente.', true);
      enviando = false;
      desenhar();
      return;
    }
    enviando = false;
    const ok = status >= 200 && status < 300;
    // Em 409 o estado mudou no servidor: recarrega para mostrar a situacao real.
    avisoDoTicket = ticket.id;
    if (ok) informar(acao === 'concluir' ? `Solicitação ${ticket.protocolo} concluída.` : `Você assumiu a solicitação ${ticket.protocolo}.`);
    else informar(mensagemDeErro(status, acao), true);
    if (ok || status === 409 || status === 404) {
      if (window.solicitacoes) await window.solicitacoes.recarregar();
    } else {
      desenhar();
    }
  }

  function desenhar() {
    acoes.replaceChildren();
    if (!ticketAtual) return;
    const { status, responsavelId } = ticketAtual;

    if (status === 'PENDENTE') {
      const assumir = botao('Assumir solicitação', 'primary', () => executar('assumir'));
      assumir.disabled = enviando;
      acoes.append(assumir);
    } else if (status === 'EM_ATENDIMENTO' && usuarioId !== null && responsavelId === usuarioId) {
      if (confirmandoConclusao) {
        const confirmar = botao('Confirmar conclusão', 'primary', () => executar('concluir'));
        const cancelar = botao('Cancelar', 'secondary', () => { confirmandoConclusao = false; desenhar(); });
        confirmar.disabled = enviando;
        cancelar.disabled = enviando;
        acoes.append(confirmar, cancelar);
      } else {
        const concluir = botao('Concluir solicitação', 'primary', () => { confirmandoConclusao = true; desenhar(); });
        concluir.disabled = enviando;
        acoes.append(concluir);
      }
    }
  }

  document.addEventListener('solicitacao-selecionada', async evento => {
    const ticket = evento.detail;
    if (!ticket || ticketAtual?.id !== ticket.id) confirmandoConclusao = false;
    if (avisoDoTicket !== ticket.id) informar('');
    ticketAtual = ticket;
    await carregarUsuario();
    // Ignora se outra solicitacao foi aberta enquanto o usuario era consultado.
    if (ticketAtual === ticket) desenhar();
  });

  // Ao abrir outra solicitacao (ou recarregar), as acoes da anterior saem de cena.
  new MutationObserver(() => {
    if (detalhe.getAttribute('aria-busy') === 'true') {
      ticketAtual = null;
      acoes.replaceChildren();
    }
  }).observe(detalhe, { attributes: true, attributeFilter: ['aria-busy'] });
})();
