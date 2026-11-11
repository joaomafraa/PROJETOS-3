// Tela de solicitacoes (HU10.1 e HU10.2).
// Lista os tickets da empresa com filtros e paginacao (GET /api/tickets)
// e mostra os detalhes, o solicitante e o historico (GET /api/tickets/{id}).
// Todo dado vindo da API e exibido com textContent.
(() => {
  const tela = document.querySelector('#solicitacoes');
  if (!tela) return;

  const TAMANHO_PAGINA = 10;
  const tipos = { SUPORTE: 'Suporte', ORCAMENTO: 'Orçamento', DEMONSTRACAO: 'Demonstração', REUNIAO: 'Reunião', VISITA_TECNICA: 'Visita técnica' };
  const status = { PENDENTE: 'Pendente', EM_ATENDIMENTO: 'Em atendimento', CONCLUIDO: 'Concluído' };
  const statusConversa = { EM_ATENDIMENTO_AUTOMATICO: 'Atendimento automático', AGUARDANDO_ATENDENTE: 'Aguardando atendente', EM_ATENDIMENTO_HUMANO: 'Atendimento humano', ENCERRADA: 'Encerrada' };
  const remetentes = { CLIENTE: 'Cliente', CHATBOT: 'Chatbot', ATENDENTE: 'Atendente' };
  const NAO_INFORMADO = 'Não informado';

  const filtroTipo = document.querySelector('#filtro-tipo');
  const filtroStatus = document.querySelector('#filtro-status');
  const filtroResponsavel = document.querySelector('#filtro-responsavel');
  const limparFiltros = document.querySelector('#limpar-filtros');
  const atualizar = document.querySelector('#atualizar-solicitacoes');
  const aviso = document.querySelector('#aviso-solicitacoes');
  const tentar = document.querySelector('#tentar-solicitacoes');
  const lista = document.querySelector('#lista-solicitacoes');
  const total = document.querySelector('#total-solicitacoes');
  const anterior = document.querySelector('#pagina-anterior');
  const proxima = document.querySelector('#pagina-proxima');
  const paginaAtual = document.querySelector('#pagina-atual');
  const subtitulo = document.querySelector('#subtitulo-detalhe-solicitacao');
  const detalhe = document.querySelector('#detalhe-solicitacao');

  let pagina = 0;
  let totalPaginas = 0;
  let selecionadaId = null;
  let itens = [];
  let carregouUmaVez = false;
  let pedidoLista = 0;
  let pedidoDetalhe = 0;
  let repetir = null;

  function elemento(tag, texto, classe) {
    const item = document.createElement(tag);
    if (texto !== undefined && texto !== null) item.textContent = texto;
    if (classe) item.className = classe;
    return item;
  }
  function dataHora(valor) {
    if (!valor) return null;
    const quando = new Date(valor);
    return Number.isNaN(quando.getTime()) ? null : quando.toLocaleString('pt-BR');
  }
  function selo(texto, valor) {
    const item = elemento('span', texto, 'status-badge');
    if (valor) item.dataset.status = valor;
    return item;
  }
  function informar(texto, erro = false, acao = null) {
    aviso.textContent = texto;
    aviso.className = erro ? 'system-message erro' : 'system-message';
    repetir = erro ? acao : null;
    tentar.hidden = !repetir;
  }
  function mostrarEstado(destino, titulo, texto, tipo = '') {
    const item = elemento(destino.tagName === 'OL' ? 'li' : 'div', null, `estado ${tipo}`.trim());
    item.append(elemento('strong', titulo));
    if (texto) item.append(elemento('span', texto));
    destino.replaceChildren(item);
  }

  async function consultar(url, naoEncontrado) {
    let resposta;
    try { resposta = await fetch(url, { cache: 'no-store', headers: { Accept: 'application/json' } }); }
    catch { throw new Error('Não foi possível conectar. Confira sua conexão e tente novamente.'); }
    if (resposta.status === 401) throw new Error('Sua sessão expirou. Entre novamente para consultar as solicitações.');
    if (resposta.status === 403) throw new Error('Seu usuário não tem acesso às solicitações ou está inativo.');
    if (resposta.status === 404) throw new Error(naoEncontrado);
    if (resposta.status === 400) throw new Error('Filtro ou página inválidos. Limpe os filtros e tente novamente.');
    if (!resposta.ok) throw new Error('Não foi possível consultar as solicitações. Tente novamente.');
    return resposta.json();
  }

  // Monta os parametros conforme o contrato de GET /api/tickets.
  function parametros() {
    const busca = new URLSearchParams({ pagina: String(pagina), tamanho: String(TAMANHO_PAGINA) });
    if (filtroTipo.value) busca.set('tipo', filtroTipo.value);
    if (filtroStatus.value) busca.set('status', filtroStatus.value);
    if (filtroResponsavel.value === 'sem') busca.set('semResponsavel', 'true');
    else if (filtroResponsavel.value) busca.set('responsavelId', filtroResponsavel.value);
    return busca;
  }
  function temFiltro() {
    return Boolean(filtroTipo.value || filtroStatus.value || filtroResponsavel.value);
  }

  // ---------- Filtro por responsavel ----------
  async function carregarResponsaveis() {
    try {
      const opcoes = await consultar('/api/tickets/responsaveis', 'Lista de responsáveis indisponível.');
      const voce = opcoes.find(opcao => opcao.voce);
      const novas = [];
      if (voce) novas.push(new Option('Meus tickets', String(voce.id)));
      const grupo = document.createElement('optgroup');
      grupo.label = 'Atendentes';
      opcoes.filter(opcao => !opcao.voce).forEach(opcao =>
        grupo.append(new Option(opcao.ativo ? opcao.nome : `${opcao.nome} (inativo)`, String(opcao.id))));
      filtroResponsavel.append(...novas);
      if (grupo.children.length) filtroResponsavel.append(grupo);
    } catch {
      // Sem a lista, os filtros "Todos" e "Sem responsável" continuam funcionando.
      filtroResponsavel.title = 'Não foi possível carregar a lista de atendentes.';
    }
  }

  // ---------- Lista ----------
  function criarItem(ticket) {
    const botao = elemento('button', null, 'request-item');
    botao.type = 'button';
    botao.dataset.id = ticket.id;
    const topo = elemento('div', null, 'request-item-top');
    topo.append(elemento('strong', ticket.protocolo), selo(status[ticket.status] || ticket.status, ticket.status));
    const meio = elemento('div', null, 'request-item-meta');
    meio.append(elemento('span', tipos[ticket.tipo] || ticket.tipo), elemento('span', ticket.clienteNome || 'Cliente sem nome'));
    const rodape = elemento('div', null, 'request-item-meta');
    const quando = elemento('time', dataHora(ticket.dataAbertura) || '');
    if (ticket.dataAbertura) quando.dateTime = ticket.dataAbertura;
    rodape.append(quando, elemento('span', ticket.responsavelNome ? `Responsável: ${ticket.responsavelNome}` : 'Sem responsável'));
    botao.append(topo, meio, rodape);
    botao.setAttribute('aria-label',
      `${ticket.protocolo}, ${tipos[ticket.tipo] || ticket.tipo}, ${status[ticket.status] || ticket.status}, ` +
      `${ticket.clienteNome || 'cliente sem nome'}, ${ticket.responsavelNome ? `responsável ${ticket.responsavelNome}` : 'sem responsável'}`);
    botao.addEventListener('click', () => abrir(ticket.id));
    return botao;
  }
  function marcarSelecionada() {
    lista.querySelectorAll('button.request-item').forEach(item =>
      item.setAttribute('aria-pressed', String(Number(item.dataset.id) === selecionadaId)));
  }
  function atualizarPaginacao(resultado) {
    totalPaginas = resultado ? resultado.totalPaginas : 0;
    anterior.disabled = !resultado || pagina <= 0;
    proxima.disabled = !resultado || pagina + 1 >= totalPaginas;
    paginaAtual.textContent = totalPaginas ? `Página ${pagina + 1} de ${totalPaginas}` : 'Página 1';
  }
  function bloquearControles(bloquear) {
    [filtroTipo, filtroStatus, filtroResponsavel, limparFiltros, atualizar].forEach(controle => { controle.disabled = bloquear; });
    if (bloquear) { anterior.disabled = true; proxima.disabled = true; }
  }

  async function carregar() {
    const atual = ++pedidoLista;
    carregouUmaVez = true;
    bloquearControles(true);
    lista.setAttribute('aria-busy', 'true');
    if (!itens.length) mostrarEstado(lista, 'Carregando solicitações…', '', 'carregando');
    informar('Carregando solicitações…');
    let resultado = null;
    try {
      resultado = await consultar(`/api/tickets?${parametros()}`, 'Consulta de solicitações indisponível.');
      if (atual !== pedidoLista) return;
      // Pagina alem do fim (ex.: itens removidos): volta para a ultima existente.
      if (!resultado.conteudo.length && pagina > 0 && resultado.totalPaginas > 0) {
        pagina = resultado.totalPaginas - 1;
        bloquearControles(false);
        return carregar();
      }
      itens = resultado.conteudo;
      total.textContent = resultado.totalElementos === 1 ? '1 solicitação' : `${resultado.totalElementos} solicitações`;
      if (!itens.length) {
        mostrarEstado(lista,
          temFiltro() ? 'Nenhuma solicitação com esses filtros' : 'Nenhuma solicitação registrada',
          temFiltro() ? 'Altere ou limpe os filtros para ver outras solicitações.' : 'Os tickets abertos pelos clientes aparecerão aqui.');
        informar(temFiltro() ? 'Nenhuma solicitação encontrada com os filtros escolhidos.' : 'Ainda não há solicitações registradas.');
      } else {
        lista.replaceChildren(...itens.map(criarItem));
        marcarSelecionada();
        informar(`Mostrando ${itens.length} de ${resultado.totalElementos} solicitação(ões).`);
      }
    } catch (erro) {
      if (atual !== pedidoLista) return;
      itens = [];
      resultado = null;
      total.textContent = 'Indisponível';
      mostrarEstado(lista, 'Não foi possível carregar as solicitações', 'Confira o aviso acima e tente novamente.', 'erro');
      informar(erro.message, true, carregar);
    } finally {
      if (atual === pedidoLista) {
        bloquearControles(false);
        lista.setAttribute('aria-busy', 'false');
        atualizarPaginacao(resultado);
      }
    }
  }

  // ---------- Detalhes ----------
  function campo(lista, rotulo, valor) {
    const linha = elemento('div');
    const vazio = valor === null || valor === undefined || String(valor).trim() === '';
    const dado = elemento('dd', vazio ? NAO_INFORMADO : null);
    if (vazio) dado.className = 'indisponivel';
    else if (valor instanceof Node) dado.append(valor);
    else dado.textContent = String(valor);
    linha.append(elemento('dt', rotulo), dado);
    lista.append(linha);
  }
  function bloco(titulo) {
    const secao = elemento('section', null, 'request-block');
    secao.append(elemento('h3', titulo));
    const dados = elemento('dl', null, 'context-data request-data');
    secao.append(dados);
    return { secao, dados };
  }
  function limparDetalhe() {
    selecionadaId = null;
    subtitulo.textContent = 'Escolha uma solicitação na lista.';
    mostrarEstado(detalhe, 'Nenhuma solicitação selecionada', 'Os dados do solicitante e o histórico da conversa aparecem aqui.');
    marcarSelecionada();
  }
  function desenharDetalhe(ticket) {
    subtitulo.textContent = `${ticket.protocolo} · ${tipos[ticket.tipo] || ticket.tipo}`;

    const pedido = bloco('Solicitação');
    campo(pedido.dados, 'Protocolo', ticket.protocolo);
    campo(pedido.dados, 'Tipo', tipos[ticket.tipo] || ticket.tipo);
    campo(pedido.dados, 'Status', selo(status[ticket.status] || ticket.status, ticket.status));
    campo(pedido.dados, 'Responsável', ticket.responsavelNome || 'Sem responsável');
    campo(pedido.dados, 'Aberta em', dataHora(ticket.dataAbertura));
    campo(pedido.dados, 'Concluída em', ticket.status === 'CONCLUIDO' ? dataHora(ticket.dataConclusao) : 'Ainda não concluída');
    const descricao = elemento('p', ticket.descricao, 'request-description');
    pedido.secao.append(descricao);

    const pessoa = bloco('Solicitante');
    const solicitante = ticket.solicitante || {};
    campo(pessoa.dados, 'Nome', solicitante.nome);
    campo(pessoa.dados, 'Telefone', solicitante.numeroTelefone);
    campo(pessoa.dados, 'E-mail', solicitante.email);
    campo(pessoa.dados, 'Identidade', solicitante.identidadeConfirmada ? 'Confirmada' : 'Não confirmada');

    const origem = bloco('Conversa de origem');
    const conversa = ticket.conversaOrigem || {};
    campo(origem.dados, 'Conversa', conversa.id ? `#${conversa.id}` : null);
    campo(origem.dados, 'Situação', statusConversa[conversa.status] || conversa.status);
    campo(origem.dados, 'Chatbot', conversa.chatbotNome);
    campo(origem.dados, 'Iniciada em', dataHora(conversa.dataInicio));

    const historico = elemento('section', null, 'request-block');
    historico.append(elemento('h3', 'Histórico da conversa'));
    const mensagens = elemento('ol', null, 'request-history');
    mensagens.setAttribute('aria-label', `Mensagens da conversa de origem de ${ticket.protocolo}`);
    if (!ticket.historico || !ticket.historico.length) {
      mostrarEstado(mensagens, 'Sem mensagens', 'A conversa de origem ainda não tem mensagens salvas.');
    } else {
      for (const mensagem of ticket.historico) {
        const item = elemento('li', null, 'mensagem');
        if (mensagem.remetente === 'CLIENTE') item.classList.add('cliente');
        if (mensagem.remetente === 'ATENDENTE') item.classList.add('humano');
        item.append(elemento('strong', `${mensagem.autor} · ${remetentes[mensagem.remetente] || mensagem.remetente}`));
        const hora = elemento('time', dataHora(mensagem.dataEnvio) || '');
        if (mensagem.dataEnvio) hora.dateTime = mensagem.dataEnvio;
        item.append(hora, elemento('p', mensagem.conteudo));
        mensagens.append(item);
      }
    }
    historico.append(mensagens);

    detalhe.replaceChildren(pedido.secao, pessoa.secao, origem.secao, historico);
    document.dispatchEvent(new CustomEvent('solicitacao-selecionada', { detail: ticket }));
  }

  async function abrir(id) {
    const atual = ++pedidoDetalhe;
    selecionadaId = id;
    marcarSelecionada();
    subtitulo.textContent = 'Carregando…';
    detalhe.setAttribute('aria-busy', 'true');
    mostrarEstado(detalhe, 'Carregando detalhes…', '', 'carregando');
    try {
      const ticket = await consultar(`/api/tickets/${encodeURIComponent(id)}`,
        'Solicitação não encontrada. Ela pode ter sido removida ou pertencer a outra empresa.');
      if (atual !== pedidoDetalhe) return;
      desenharDetalhe(ticket);
    } catch (erro) {
      if (atual !== pedidoDetalhe) return;
      subtitulo.textContent = 'Não foi possível abrir a solicitação.';
      mostrarEstado(detalhe, 'Não foi possível carregar os detalhes', erro.message, 'erro');
      const repetirDetalhe = elemento('button', 'Tentar novamente', 'button secondary');
      repetirDetalhe.type = 'button';
      repetirDetalhe.addEventListener('click', () => abrir(id));
      detalhe.firstChild.append(repetirDetalhe);
    } finally {
      if (atual === pedidoDetalhe) detalhe.setAttribute('aria-busy', 'false');
    }
  }

  // Outras partes do painel (ex.: assumir/concluir) podem pedir para recarregar.
  window.solicitacoes = {
    recarregar: async () => { await carregar(); if (selecionadaId) await abrir(selecionadaId); }
  };

  // ---------- Eventos ----------
  [filtroTipo, filtroStatus, filtroResponsavel].forEach(filtro =>
    filtro.addEventListener('change', () => { pagina = 0; carregar(); }));
  limparFiltros.addEventListener('click', () => {
    filtroTipo.value = '';
    filtroStatus.value = '';
    filtroResponsavel.value = '';
    pagina = 0;
    carregar();
  });
  atualizar.addEventListener('click', () => window.solicitacoes.recarregar());
  tentar.addEventListener('click', () => { if (repetir) repetir(); });
  anterior.addEventListener('click', () => { if (pagina > 0) { pagina -= 1; carregar(); } });
  proxima.addEventListener('click', () => { if (pagina + 1 < totalPaginas) { pagina += 1; carregar(); } });
  // Setas, Home e End movem o foco entre as solicitacoes da pagina.
  lista.addEventListener('keydown', evento => {
    const botoes = [...lista.querySelectorAll('button.request-item')];
    const posicao = botoes.indexOf(document.activeElement);
    if (posicao < 0) return;
    const alvo = { ArrowDown: botoes[posicao + 1], ArrowUp: botoes[posicao - 1], Home: botoes[0], End: botoes.at(-1) }[evento.key];
    if (!alvo) return;
    evento.preventDefault();
    alvo.focus();
  });

  // Consulta so quando a tela e aberta pela primeira vez.
  // Usa o endereco, porque a troca de tela (tema.js) pode acontecer depois deste script.
  function aoMostrar() {
    const aberta = ['#solicitacoes', '#titulo-solicitacoes'].includes(location.hash);
    if (!aberta || carregouUmaVez) return;
    limparDetalhe();
    carregarResponsaveis();
    carregar();
  }
  window.addEventListener('hashchange', aoMostrar);
  aoMostrar();
})();
