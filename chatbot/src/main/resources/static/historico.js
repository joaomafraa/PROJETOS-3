const lista = document.querySelector('#conversas');
const mensagens = document.querySelector('#mensagens');
const aviso = document.querySelector('#aviso');
const detalhes = document.querySelector('#detalhes');
const atualizar = document.querySelector('#atualizar');
let requisicao = 0;
const nomesStatus = { EM_ATENDIMENTO_AUTOMATICO: 'Atendimento automático', AGUARDANDO_ATENDENTE: 'Aguardando atendente', EM_ATENDIMENTO_HUMANO: 'Atendimento humano', ENCERRADA: 'Encerrada' };
const tipos = { CLIENTE: 'Cliente', CHATBOT: 'Chatbot', ATENDENTE: 'Atendente' };
function elemento(tag, texto, classe) {
  const item = document.createElement(tag);
  item.textContent = texto;
  if (classe) item.className = classe;
  return item;
}
function data(valor) { return new Date(valor).toLocaleString('pt-BR'); }
const barraAviso = document.querySelector('#barra-aviso');
const tentarNovamente = document.querySelector('#tentar-novamente');
let repetir = null;
// Mostra o aviso; com `acao`, exibe o botão para repetir a consulta que falhou.
function informar(texto, erro = false, acao = null) {
  aviso.textContent = texto;
  aviso.className = erro ? 'system-message erro' : 'system-message';
  barraAviso.classList.toggle('ativa', Boolean(texto));
  repetir = erro ? acao : null;
  tentarNovamente.hidden = !repetir;
}
// Substitui o conteúdo de um contêiner por uma mensagem de estado (carregando, vazio ou erro).
function mostrarEstado(destino, titulo, detalhe, tipo = '') {
  const item = elemento(destino.tagName === 'OL' ? 'li' : 'p', '', `estado ${tipo}`.trim());
  item.append(elemento('strong', titulo));
  if (detalhe) item.append(elemento('span', detalhe));
  destino.replaceChildren(item);
}
tentarNovamente.addEventListener('click', () => { if (repetir) repetir(); });
async function consultar(url) {
  let resposta;
  try { resposta = await fetch(url, { cache: 'no-store' }); }
  catch { throw new Error('Não foi possível conectar. Confira se a aplicação está rodando.'); }
  if (resposta.status === 403) throw new Error('O atendente do painel não está cadastrado ou está inativo. Configure PAINEL_ATENDENTE_ID com um atendente ativo.');
  if (resposta.status === 404) throw new Error('Consulta indisponível. Verifique o modo painel-local ou atualize a lista.');
  if (!resposta.ok) throw new Error('Não foi possível consultar o histórico. Tente novamente.');
  return resposta.json();
}
async function abrir(conversa, botao) {
  const atual = ++requisicao;
  lista.querySelectorAll('button').forEach(item => item.setAttribute('aria-pressed', String(item === botao)));
  mostrarEstado(mensagens, 'Carregando mensagens…', '', 'carregando');
  detalhes.textContent = `Conversa #${conversa.id} · ${conversa.clienteNome || 'Cliente'} · ${nomesStatus[conversa.status] || conversa.status}`;
  informar('Carregando mensagens…');
  window.contextoAtendimento?.mostrar(conversa);
  try {
    const historico = await consultar(`/api/historico/${encodeURIComponent(conversa.id)}/mensagens`);
    if (atual !== requisicao) return;
    mensagens.replaceChildren();
    if (!historico.length) mostrarEstado(mensagens, 'Sem mensagens', 'Esta conversa ainda não tem mensagens salvas.');
    for (const mensagem of historico) {
      const item = elemento('li', '', 'mensagem');
      if (mensagem.remetente === 'CLIENTE') item.classList.add('cliente');
      if (mensagem.remetente === 'ATENDENTE') item.classList.add('humano');
      item.append(elemento('strong', `${mensagem.autor} · ${tipos[mensagem.remetente] || mensagem.remetente}`));
      const horario = elemento('time', data(mensagem.dataEnvio));
      horario.dateTime = mensagem.dataEnvio;
      item.append(horario, elemento('p', mensagem.conteudo));
      mensagens.append(item);
    }
    mensagens.scrollTop = mensagens.scrollHeight;
    informar(historico.length ? `${historico.length} mensagem(ns) no histórico.` : 'Esta conversa ainda não tem mensagens salvas.');
  } catch (erro) {
    if (atual !== requisicao) return;
    const texto = erro.message.startsWith('Consulta indisponível')
      ? 'Conversa não encontrada. Atualize a lista e tente novamente.'
      : erro.message;
    mostrarEstado(mensagens, 'Não foi possível carregar as mensagens', 'Use “Tentar novamente” para consultar de novo.', 'erro');
    informar(texto, true, () => abrir(conversa, botao));
    tentarNovamente.focus();
  }
}
async function carregar(selecionarId) {
  ++requisicao;
  atualizar.disabled = true;
  lista.setAttribute('aria-busy', 'true');
  mostrarEstado(lista, 'Carregando conversas…', '', 'carregando');
  mensagens.replaceChildren();
  detalhes.textContent = 'Escolha uma conversa na lista.';
  window.contextoAtendimento?.limpar();
  informar('Carregando conversas…');
  try {
    const conversas = await consultar('/api/historico/fila');
    lista.replaceChildren();
    if (!conversas.length) mostrarEstado(lista, 'Nenhuma conversa aguardando', 'Novas conversas aguardando atendimento aparecerão aqui. Use “Atualizar lista” para consultar de novo.');
    for (const conversa of conversas) {
      const botao = elemento('button', '', 'conversa');
      botao.type = 'button';
      botao.setAttribute('aria-pressed', 'false');
      botao.append(elemento('strong', conversa.clienteNome || 'Cliente sem nome'), elemento('span', `#${conversa.id} · ${nomesStatus[conversa.status] || conversa.status}`), elemento('span', data(conversa.dataInicio), 'muted'));
      botao.addEventListener('click', () => abrir(conversa, botao));
      lista.append(botao);
      if (conversa.id === selecionarId) botao.dataset.abrir = "true";
    }
    informar(conversas.length ? 'Selecione uma conversa.' : 'A fila está vazia. Novas conversas aguardando atendimento aparecerão aqui.');
  } catch (erro) {
    mostrarEstado(lista, 'Não foi possível carregar a fila', 'Confira a mensagem acima e tente novamente.', 'erro');
    informar(erro.message, true, () => carregar(selecionarId));
    tentarNovamente.focus();
  }
  finally { atualizar.disabled = false; lista.setAttribute('aria-busy', 'false'); }
  const selecionado = lista.querySelector('[data-abrir]');
  if (selecionado) selecionado.click();
}
// Setas, Home e End movem o foco entre as conversas; Enter ou Espaço abrem a conversa em foco.
lista.addEventListener('keydown', evento => {
  const itens = [...lista.querySelectorAll('button.conversa')];
  const posicao = itens.indexOf(document.activeElement);
  if (posicao < 0) return;
  const alvo = { ArrowDown: itens[posicao + 1], ArrowUp: itens[posicao - 1], Home: itens[0], End: itens.at(-1) }[evento.key];
  if (!alvo) return;
  evento.preventDefault();
  alvo.focus();
});
atualizar.addEventListener('click', () => carregar());
carregar();
const criarExemplo = document.querySelector('#exemplo');
criarExemplo.addEventListener('click', async () => {
  criarExemplo.disabled = true;
  atualizar.disabled = true;
  informar('Salvando atendimento de exemplo no banco…');
  try {
    const resposta = await fetch('/api/historico/exemplo', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}'
    });
    if (!resposta.ok) throw new Error('Não foi possível criar o exemplo. Verifique o modo local e se o atendente e os identificadores estão disponíveis.');
    const resultado = await resposta.json();
    await carregar(resultado.conversaId);
  } catch (erro) { informar(erro.message, true); }
  finally { criarExemplo.disabled = false; atualizar.disabled = false; }
});
