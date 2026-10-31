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
function informar(texto, erro = false) {
  aviso.textContent = texto;
  aviso.className = erro ? 'system-message erro' : 'system-message';
}
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
  mensagens.replaceChildren();
  detalhes.textContent = `Conversa #${conversa.id} · ${conversa.clienteNome || 'Cliente'} · ${nomesStatus[conversa.status] || conversa.status}`;
  informar('Carregando mensagens…');
  window.contextoAtendimento?.mostrar(conversa);
  try {
    const historico = await consultar(`/api/historico/${encodeURIComponent(conversa.id)}/mensagens`);
    if (atual !== requisicao) return;
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
    informar(historico.length ? `${historico.length} mensagem(ns) no histórico.` : 'Esta conversa ainda não tem mensagens salvas.');
  } catch (erro) { if (atual === requisicao) informar(erro.message, true); }
}
async function carregar(selecionarId) {
  ++requisicao;
  atualizar.disabled = true;
  lista.replaceChildren();
  mensagens.replaceChildren();
  detalhes.textContent = 'Escolha uma conversa na lista.';
  window.contextoAtendimento?.limpar();
  informar('Carregando conversas…');
  try {
    const conversas = await consultar('/api/historico/fila');
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
  } catch (erro) { informar(erro.message, true); }
  finally { atualizar.disabled = false; }
  const selecionado = lista.querySelector('[data-abrir]');
  if (selecionado) selecionado.click();
}
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
