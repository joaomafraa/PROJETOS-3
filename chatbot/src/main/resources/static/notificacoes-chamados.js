// Notificacoes de novos chamados (HU05.3).
// Cada atendente ve so os proprios avisos; quem decide quem recebe e a Administracao
// (responsaveis por "novos chamados"). Nada aqui envia e-mail ou Telegram.
(() => {
  const tela = document.querySelector('#notificacoes');
  if (!tela) return;

  const contador = document.querySelector('#contador-notificacoes');
  const lista = document.querySelector('#lista-notificacoes');
  const aviso = document.querySelector('#aviso-notificacoes');
  const filtro = document.querySelector('#filtro-notificacoes');
  const botaoAtualizar = document.querySelector('#atualizar-notificacoes');
  const botaoTodas = document.querySelector('#marcar-todas-notificacoes');

  const INTERVALO_CONTAGEM_MS = 30000;
  const nomesTipo = {
    SUPORTE: 'Suporte', ORCAMENTO: 'Orçamento', DEMONSTRACAO: 'Demonstração',
    REUNIAO: 'Reunião', VISITA_TECNICA: 'Visita técnica'
  };
  const nomesStatus = { PENDENTE: 'Pendente', EM_ATENDIMENTO: 'Em atendimento', CONCLUIDO: 'Concluído' };

  let carregando = false;

  // ---------- Utilitarios ----------

  function mostrarAviso(texto, erro = false) {
    aviso.textContent = texto;
    aviso.className = erro ? 'system-message admin-aviso erro' : 'system-message admin-aviso';
  }

  function criar(tag, texto, classe) {
    const elemento = document.createElement(tag);
    if (texto !== undefined && texto !== null) elemento.textContent = texto;
    if (classe) elemento.className = classe;
    return elemento;
  }

  function quando(iso) {
    const data = new Date(iso);
    if (Number.isNaN(data.getTime())) return '';
    const minutos = Math.round((Date.now() - data.getTime()) / 60000);
    if (minutos < 1) return 'agora';
    if (minutos < 60) return `há ${minutos} min`;
    return data.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }

  async function tokenCsrf() {
    const resposta = await fetch('/csrf-token', { cache: 'no-store' });
    if (!resposta.ok) throw new Error('Não foi possível preparar a operação. Atualize a página e tente novamente.');
    return resposta.json();
  }

  async function chamarApi(url, metodo = 'GET') {
    const opcoes = { method: metodo, cache: 'no-store', headers: {} };
    if (metodo !== 'GET') {
      const csrf = await tokenCsrf();
      opcoes.headers[csrf.headerName] = csrf.token;
    }
    let resposta;
    try {
      resposta = await fetch(url, opcoes);
    } catch {
      throw new Error('Sem conexão com o servidor. Verifique a internet e tente novamente.');
    }
    if (resposta.status === 401) throw new Error('Sua sessão expirou. Entre novamente.');
    let dados = null;
    try { dados = await resposta.json(); } catch { /* sem corpo */ }
    if (!resposta.ok) {
      const erro = new Error(dados?.mensagem || 'Não foi possível carregar as notificações.');
      erro.status = resposta.status;
      throw erro;
    }
    return dados;
  }

  // ---------- Contador no menu ----------

  function mostrarContador(naoLidas) {
    contador.hidden = !naoLidas;
    contador.textContent = naoLidas > 99 ? '99+' : String(naoLidas);
    contador.setAttribute('aria-label', `${naoLidas} notificação(ões) não lida(s)`);
  }

  async function atualizarContador() {
    if (document.hidden) return;
    try {
      const dados = await chamarApi('/api/notificacoes-chamados/contagem');
      mostrarContador(dados.naoLidas || 0);
    } catch {
      // Contador e so um atalho; a lista mostra o erro quando a pessoa abrir a tela.
    }
  }

  // ---------- Lista ----------

  function desenhar(notificacoes) {
    lista.replaceChildren();
    if (!notificacoes.length) {
      lista.append(criar('li', filtro.value === 'true'
        ? 'Nenhuma notificação não lida.'
        : 'Nenhuma notificação de novo chamado por enquanto.', 'vazio'));
      return;
    }
    notificacoes.forEach(n => lista.append(item(n)));
  }

  function item(n) {
    const li = criar('li', null, 'notificacao-item');
    li.dataset.lida = String(n.lida);

    const topo = criar('div', null, 'notificacao-topo');
    topo.append(criar('strong', `Novo chamado ${n.protocolo}`));
    const selo = criar('span', n.lida ? 'Lida' : 'Nova', 'status-badge');
    topo.append(selo);

    const partes = [nomesTipo[n.tipoSolicitacao] || n.tipoSolicitacao];
    if (n.clienteNome) partes.push(`Cliente: ${n.clienteNome}`);
    partes.push(`Situação atual: ${nomesStatus[n.statusChamado] || n.statusChamado}`);
    partes.push(quando(n.criadaEm));
    const meta = criar('p', partes.filter(Boolean).join(' · '), 'notificacao-meta');

    const descricao = criar('p', n.descricao || '', 'notificacao-descricao');

    const acoes = criar('div', null, 'acoes');
    const abrir = criar('a', 'Ver em Solicitações', 'button secondary');
    abrir.href = '#solicitacoes';
    abrir.addEventListener('click', () => { if (!n.lida) marcarLida(n, null); });
    acoes.append(abrir);
    if (!n.lida) {
      const lida = criar('button', 'Marcar como lida', 'button secondary');
      lida.type = 'button';
      lida.setAttribute('aria-label', `Marcar o chamado ${n.protocolo} como lido`);
      lida.addEventListener('click', () => marcarLida(n, lida));
      acoes.append(lida);
    }

    li.append(topo, meta, descricao, acoes);
    return li;
  }

  async function carregar() {
    if (carregando) return;
    carregando = true;
    botaoAtualizar.disabled = true;
    lista.setAttribute('aria-busy', 'true');
    mostrarAviso('Carregando notificações…');
    try {
      const dados = await chamarApi(`/api/notificacoes-chamados?somenteNaoLidas=${filtro.value}`);
      desenhar(dados);
      mostrarAviso(`${dados.length} notificação(ões).`);
      await atualizarContador();
    } catch (erro) {
      lista.replaceChildren();
      mostrarAviso(erro.message, true);
    } finally {
      carregando = false;
      botaoAtualizar.disabled = false;
      lista.setAttribute('aria-busy', 'false');
    }
  }

  async function marcarLida(n, botao) {
    if (botao) botao.disabled = true;
    try {
      await chamarApi(`/api/notificacoes-chamados/${n.id}/lida`, 'POST');
      n.lida = true;
      if (location.hash === '#notificacoes') await carregar();
      else await atualizarContador();
    } catch (erro) {
      mostrarAviso(erro.message, true);
      if (botao) botao.disabled = false;
    }
  }

  botaoTodas.addEventListener('click', async () => {
    botaoTodas.disabled = true;
    try {
      const dados = await chamarApi('/api/notificacoes-chamados/lidas', 'POST');
      await carregar();
      mostrarAviso(dados.marcadas ? `${dados.marcadas} notificação(ões) marcada(s) como lida(s).` : 'Nada para marcar.');
    } catch (erro) {
      mostrarAviso(erro.message, true);
    } finally {
      botaoTodas.disabled = false;
    }
  });

  botaoAtualizar.addEventListener('click', carregar);
  filtro.addEventListener('change', carregar);

  // ---------- Navegacao ----------

  function aoAbrir() {
    if (['#notificacoes', '#titulo-notificacoes'].includes(location.hash)) carregar();
  }
  window.addEventListener('hashchange', aoAbrir);
  document.addEventListener('visibilitychange', atualizarContador);
  setInterval(atualizarContador, INTERVALO_CONTAGEM_MS);
  atualizarContador();
  aoAbrir();
})();
