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
  if (resposta.status === 401) throw new Error('Autenticação obrigatória. Entre com as credenciais do atendente.');
  if (resposta.status === 403) throw new Error('O atendente autenticado não tem acesso ou está inativo.');
  if (resposta.status === 404) throw new Error('Consulta indisponível. Atualize a lista e tente novamente.');
  if (!resposta.ok) throw new Error('Não foi possível consultar o histórico. Tente novamente.');
  return resposta.json();
}
// Estado da lista: itens vindos da API, conversa selecionada e termo de busca.
const busca = document.querySelector('#busca');
const contador = document.querySelector('#contador-fila');
let conversasCarregadas = [];
let abaAtual = 'fila';
const abaFila = document.querySelector('#tab-fila');
const abaMeus = document.querySelector('#tab-meus');
function selecionarAba(aba, id = null) {
 abaAtual = aba;
 for (const [botao, ativo] of [[abaFila, aba === 'fila'], [abaMeus, aba === 'meus']]) { botao.classList.toggle('active', ativo); botao.setAttribute('aria-pressed', String(ativo)); }
 carregar(id);
}
abaFila.addEventListener('click', () => selecionarAba('fila'));
abaMeus.addEventListener('click', () => selecionarAba('meus'));
let selecionadaId = null;
let conversaAtual = null;
const assumirConversa = document.querySelector('#assumir-conversa');
const respostaHumana = document.querySelector('#mensagem-resposta');
const enviarResposta = document.querySelector('#enviar-resposta');
function controlesConversa() {
  const assumida = conversaAtual?.status === 'EM_ATENDIMENTO_HUMANO';
  assumirConversa.disabled = conversaAtual?.status !== 'AGUARDANDO_ATENDENTE';
  respostaHumana.disabled = !assumida; enviarResposta.disabled = !assumida;
}
async function postSeguro(url, corpo) {
 const csrf = await consultar('/csrf-token');
 const resposta = await fetch(url,{method:'POST',headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify(corpo)});
 if (!resposta.ok) throw new Error(resposta.status === 503 ? 'Envio sem confirmação. Confira antes de enviar novamente.' : 'Não foi possível concluir. Verifique suas permissões e tente novamente.');
}
assumirConversa.addEventListener('click', async () => {
 if (!conversaAtual) return; assumirConversa.disabled=true;
 try { await postSeguro(`/api/conversas/${conversaAtual.id}/assumir`,{}); conversaAtual.status='EM_ATENDIMENTO_HUMANO'; selecionarAba('meus', conversaAtual.id); }
 catch(e) { informar(e.message,true); controlesConversa(); }
});
enviarResposta.addEventListener('click', async () => {
 if (!conversaAtual || !respostaHumana.value.trim()) return; enviarResposta.disabled=true;
 try { await postSeguro(`/api/conversas/${conversaAtual.id}/mensagens`,{conteudo:respostaHumana.value}); respostaHumana.value=''; await abrir(conversaAtual); }
 catch(e) { informar(e.message,true); } finally { controlesConversa(); }
});

// Remove acentos e caixa para a busca achar "joao" em "João".
function normalizar(texto) {
  return String(texto ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
// Hoje: só o horário; outros dias: dia/mês e horário.
function horario(valor) {
  const quando = new Date(valor);
  if (Number.isNaN(quando.getTime())) return '';
  const hora = quando.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  if (quando.toDateString() === new Date().toDateString()) return hora;
  return `${quando.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })} ${hora}`;
}
function marcarSelecionada() {
  lista.querySelectorAll('button.conversa').forEach(item =>
    item.setAttribute('aria-pressed', String(Number(item.dataset.id) === selecionadaId)));
}
function criarItem(conversa) {
  const botao = elemento('button', '', 'conversa');
  botao.type = 'button';
  botao.dataset.id = conversa.id;
  const nome = conversa.clienteNome || 'Cliente sem nome';
  const topo = elemento('div', '', 'conversa-topo');
  const hora = elemento('time', horario(conversa.dataInicio), 'conversa-hora');
  hora.dateTime = conversa.dataInicio;
  hora.title = conversa.dataInicio ? data(conversa.dataInicio) : '';
  topo.append(elemento('strong', nome), hora);
  const rodape = elemento('div', '', 'conversa-rodape');
  const selo = elemento('span', nomesStatus[conversa.status] || conversa.status || 'Sem status', 'status-badge');
  if (conversa.status) selo.dataset.status = conversa.status;
  rodape.append(selo, elemento('span', `#${conversa.id}`, 'conversa-id'));
  botao.append(topo, rodape);
  botao.setAttribute('aria-label', `${nome}, ${selo.textContent}, iniciada ${hora.title || 'em data desconhecida'}`);
  botao.addEventListener('click', () => abrir(conversa));
  return botao;
}
// Redesenha a lista a partir dos itens carregados, aplicando a busca por nome.
function desenhar(animar = true) {
  lista.classList.toggle('sem-animacao', !animar);
  const termo = normalizar(busca.value);
  const visiveis = termo
    ? conversasCarregadas.filter(conversa => normalizar(conversa.clienteNome).includes(termo))
    : conversasCarregadas;
  if (!conversasCarregadas.length) {
    mostrarEstado(lista, abaAtual === 'fila' ? 'Nenhuma conversa aguardando' : 'Nenhuma conversa assumida', abaAtual === 'fila' ? 'Novas conversas aguardando atendimento aparecerão aqui.' : 'As conversas que você assumir aparecerão aqui.');
  } else if (!visiveis.length) {
    mostrarEstado(lista, 'Nenhuma conversa encontrada', `Nenhum cliente com “${busca.value.trim()}” no nome. Limpe a busca para ver toda a fila.`);
  } else {
    lista.replaceChildren(...visiveis.map(criarItem));
    marcarSelecionada();
  }
  contador.textContent = termo
    ? `${visiveis.length} de ${conversasCarregadas.length}`
    : `${conversasCarregadas.length} conversa(s)`;
}
function limparSelecao() {
  selecionadaId = null;
  conversaAtual = null; controlesConversa();
  mensagens.replaceChildren();
  detalhes.textContent = 'Escolha uma conversa na caixa de entrada.';
  window.contextoAtendimento?.limpar();
  marcarSelecionada();
}
async function abrir(conversa) {
  const atual = ++requisicao;
  selecionadaId = conversa.id;
  conversaAtual = conversa; controlesConversa();
  marcarSelecionada();
  mostrarEstado(mensagens, 'Carregando mensagens…', '', 'carregando');
  detalhes.textContent = `Conversa #${conversa.id} · ${conversa.clienteNome || 'Cliente'} · ${nomesStatus[conversa.status] || conversa.status}`;
  informar('Carregando mensagens…');
  window.contextoAtendimento?.mostrar(conversa);
  try {
    const historico = await consultar(`/api/historico/${encodeURIComponent(conversa.id)}/mensagens`);
    let envios = [];
    let erroEnvios = false;
    try { envios = await consultar(`/api/conversas/${encodeURIComponent(conversa.id)}/envios`); }
    catch { erroEnvios = true; }
    if (atual !== requisicao) return;
    mensagens.replaceChildren();
    if (!historico.length) mostrarEstado(mensagens, 'Sem mensagens', 'Esta conversa ainda não tem mensagens salvas.');
    for (const mensagem of historico) {
      const item = elemento('li', '', 'mensagem');
      if (mensagem.remetente === 'CLIENTE') item.classList.add('cliente');
      if (mensagem.remetente === 'ATENDENTE') item.classList.add('humano');
      item.append(elemento('strong', `${mensagem.autor} · ${tipos[mensagem.remetente] || mensagem.remetente}`));
      const hora = elemento('time', data(mensagem.dataEnvio));
      hora.dateTime = mensagem.dataEnvio;
      item.append(hora, elemento('p', mensagem.conteudo));
      const envio = envios.find(e => e.mensagemId === mensagem.id);
      if (envio) {
        const status = {ENVIADO:'Enviada ao Telegram', PENDENTE:'Aguardando envio', INCERTO:'Entrega não confirmada — confira antes de reenviar', CONFIRMADO:'Recebimento confirmado pelo atendente'};
        item.append(elemento('small', status[envio.status] || 'Status de entrega indisponível', 'mensagem-status-envio'));
        if (envio.status === 'INCERTO' && conversa.status === 'EM_ATENDIMENTO_HUMANO') {
          const acoes = elemento('div', '', 'revisao-envio');
          for (const [rotulo, acao] of [['Confirmar que chegou', 'confirmar'], ['Reenviar mensagem', 'reenviar']]) {
            const botao = elemento('button', rotulo, 'button secondary'); botao.type = 'button';
            botao.addEventListener('click', async () => {
              if (acao === 'reenviar' && !window.confirm('A mensagem pode já ter chegado. Você verificou que ela não chegou e deseja reenviar?')) return;
              botao.disabled = true;
              try { await postSeguro(`/api/conversas/${conversa.id}/envios/${mensagem.id}/${acao}`, {}); await abrir(conversa); }
              catch (erro) { informar('Não foi possível concluir a revisão. Confira a entrega antes de tentar novamente.', true); botao.disabled = false; }
            });
            acoes.append(botao);
          }
          item.append(acoes);
        }
      }
      mensagens.append(item);
    }
    mensagens.scrollTop = mensagens.scrollHeight;
    if (erroEnvios) informar('Não foi possível verificar a entrega das mensagens.', true);
    else informar(historico.length ? `${historico.length} mensagem(ns) no histórico.` : 'Esta conversa ainda não tem mensagens salvas.');
  } catch (erro) {
    if (atual !== requisicao) return;
    const texto = erro.message.startsWith('Consulta indisponível')
      ? 'Conversa não encontrada. Atualize a lista e tente novamente.'
      : erro.message;
    mostrarEstado(mensagens, 'Não foi possível carregar as mensagens', 'Use “Tentar novamente” para consultar de novo.', 'erro');
    informar(texto, true, () => abrir(conversa));
    tentarNovamente.focus();
  }
}
// Consulta a fila de novo. Mantém a busca e, se a conversa aberta continuar na fila, reabre-a.
async function carregar(selecionarId = selecionadaId) {
  ++requisicao;
  atualizar.disabled = true;
  lista.setAttribute('aria-busy', 'true');
  if (!conversasCarregadas.length) mostrarEstado(lista, 'Carregando conversas…', '', 'carregando');
  informar('Carregando conversas…');
  let manter = null;
  try {
    conversasCarregadas = await consultar(abaAtual === 'fila' ? '/api/historico/fila' : '/api/historico/meus');
    manter = conversasCarregadas.find(conversa => conversa.id === selecionarId) || null;
    const saiu = selecionadaId !== null && !manter;
    if (!manter) limparSelecao();
    desenhar();
    if (!conversasCarregadas.length) informar(abaAtual === 'fila' ? 'A fila está vazia.' : 'Você ainda não tem conversas assumidas.');
    else if (saiu) informar('A conversa que estava aberta não está mais na fila.');
    else if (!manter) informar(`${conversasCarregadas.length} conversa(s) na fila. Selecione uma conversa.`);
  } catch (erro) {
    conversasCarregadas = [];
    limparSelecao();
    contador.textContent = 'Indisponível';
    mostrarEstado(lista, 'Não foi possível carregar a fila', 'Confira a mensagem acima e tente novamente.', 'erro');
    informar(erro.message, true, () => carregar(selecionarId));
    tentarNovamente.focus();
  } finally {
    atualizar.disabled = false;
    lista.setAttribute('aria-busy', 'false');
  }
  if (manter) abrir(manter);
}
busca.addEventListener('input', () => desenhar(false));
// Esc limpa a busca e devolve a lista completa.
busca.addEventListener('keydown', evento => {
  if (evento.key === 'Escape' && busca.value) { busca.value = ''; desenhar(false); }
});
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
    const csrfResposta = await fetch('/csrf-token', { cache: 'no-store' });
    if (!csrfResposta.ok) throw new Error('Não foi possível preparar a solicitação. Atualize a página e tente novamente.');
    const csrf = await csrfResposta.json();
    const resposta = await fetch('/api/historico/exemplo', {
      method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, body: '{}'
    });
    if (!resposta.ok) throw new Error('Não foi possível criar o exemplo. Verifique o modo local e se o atendente e os identificadores estão disponíveis.');
    const resultado = await resposta.json();
    await carregar(resultado.conversaId);
  } catch (erro) { informar(erro.message, true); }
  finally { criarExemplo.disabled = false; atualizar.disabled = false; }
});
