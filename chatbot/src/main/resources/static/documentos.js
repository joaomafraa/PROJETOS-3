(() => {
  const form = document.querySelector('#form-documento');
  const titulo = document.querySelector('#documento-titulo');
  const conteudo = document.querySelector('#documento-conteudo');
  const arquivo = document.querySelector('#documento-arquivo');
  const lista = document.querySelector('#documentos-lista');
  const aviso = document.querySelector('#documentos-aviso');
  const drop = document.querySelector('#documento-drop');
  const arquivoInfo = document.querySelector('#documento-arquivo-info');
  const contagem = document.querySelector('#documento-contagem');
  const salvar = form.querySelector('[type=submit]');
  let editando = null;
  const informar = texto => { aviso.textContent = texto; };
  async function api(url, method = 'GET', body) {
    const opcoes = { method, cache: 'no-store', headers: {} };
    if (method !== 'GET') {
      const token = await fetch('/csrf-token', { cache: 'no-store' });
      if (!token.ok) throw new Error('Não foi possível obter o token de segurança.');
      const csrf = await token.json(); opcoes.headers[csrf.headerName] = csrf.token;
      opcoes.headers['Content-Type'] = 'application/json'; opcoes.body = JSON.stringify(body);
    }
    const res = await fetch(url, opcoes);
    const dados = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(res.status === 403 ? 'Área restrita à administração.' : dados.mensagem || 'Não foi possível concluir a operação.');
    return dados;
  }
  function atualizarContagem() { contagem.textContent = `${conteudo.value.length.toLocaleString('pt-BR')} / 200.000 caracteres`; }
  function limpar() { editando = null; form.reset(); arquivoInfo.textContent = 'Nenhum arquivo selecionado'; salvar.textContent = 'Salvar documento'; atualizarContagem(); }
  async function carregar() {
    try {
      const documentos = await api('/api/admin/documentos'); lista.replaceChildren();
      if (!documentos.length) { const vazio = document.createElement('p'); vazio.className = 'document-empty'; vazio.textContent = 'Nenhum documento cadastrado ainda.'; lista.append(vazio); }
      for (const d of documentos) {
        const linha = document.createElement('div'); linha.className = 'document-item';
        const nome = document.createElement('strong'); nome.textContent = d.titulo;
        const estado = document.createElement('p'); estado.textContent = `Versão ${d.versao} · ${d.ativo ? 'Ativo' : 'Inativo'}`;
        const editar = document.createElement('button'); editar.type = 'button'; editar.className = 'button secondary'; editar.textContent = 'Editar';
        editar.addEventListener('click', () => { editando = d.id; titulo.value = d.titulo; conteudo.value = d.conteudo; arquivo.value = ''; arquivoInfo.textContent = 'Editando documento cadastrado'; salvar.textContent = 'Salvar alterações'; atualizarContagem(); titulo.focus(); });
        const ativar = document.createElement('button'); ativar.type = 'button'; ativar.className = 'button secondary'; ativar.textContent = d.ativo ? 'Desativar' : 'Ativar';
        ativar.addEventListener('click', async () => { try { await api(`/api/admin/documentos/${d.id}/estado`, 'PATCH', { ativo: !d.ativo }); informar('Estado atualizado.'); await carregar(); } catch (e) { informar(e.message); } });
        const detalhes = document.createElement('div'); detalhes.className = 'document-item-details'; detalhes.append(nome, estado);
        const acoes = document.createElement('div'); acoes.className = 'document-item-actions'; acoes.append(editar, ativar);
        linha.append(detalhes, acoes); lista.append(linha);
      }
    } catch (e) { informar(e.message); }
  }
  async function carregarArquivo(f) {
    if (!f) return;
    if (!/\.(txt|md)$/i.test(f.name) || f.size > 800000) { informar('Escolha um arquivo TXT ou MD de até 800 KB.'); arquivo.value = ''; return; }
    try {
      const bytes = await f.arrayBuffer(); const texto = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
      if (texto.length > 200000) throw new Error('O arquivo excede 200.000 caracteres.');
      if (!texto.trim()) throw new Error('O arquivo está vazio. Escolha um arquivo com conteúdo.');
      conteudo.value = texto; arquivoInfo.textContent = f.name; atualizarContagem();
      if (!titulo.value) titulo.value = f.name.replace(/\.(txt|md)$/i, ''); informar('Arquivo carregado. Revise o conteúdo e salve.');
    } catch (e) { arquivo.value = ''; informar(e instanceof TypeError ? 'O arquivo precisa estar codificado em UTF-8.' : e.message || 'Não foi possível ler o arquivo.'); }
  }
  arquivo.addEventListener('change', () => carregarArquivo(arquivo.files[0]));
  conteudo.addEventListener('input', atualizarContagem);
  for (const evento of ['dragenter', 'dragover']) drop.addEventListener(evento, e => { e.preventDefault(); drop.classList.add('is-dragging'); });
  drop.addEventListener('dragleave', e => { if (!drop.contains(e.relatedTarget)) drop.classList.remove('is-dragging'); });
  drop.addEventListener('drop', e => {
    e.preventDefault(); drop.classList.remove('is-dragging');
    if (e.dataTransfer.files.length !== 1) { informar('Arraste apenas um arquivo por vez.'); return; }
    carregarArquivo(e.dataTransfer.files[0]);
  });
  form.addEventListener('submit', async e => {
    e.preventDefault();
    const rotulo = salvar.textContent; salvar.disabled = true; salvar.textContent = 'Salvando…';
    try { await api(editando ? `/api/admin/documentos/${editando}` : '/api/admin/documentos', editando ? 'PUT' : 'POST', { titulo: titulo.value, conteudo: conteudo.value }); limpar(); informar('Documento salvo.'); await carregar(); }
    catch (erro) { salvar.textContent = rotulo; informar(erro.message); }
    finally { salvar.disabled = false; }
  });
  document.querySelector('#documento-cancelar').addEventListener('click', limpar);
  window.addEventListener('hashchange', () => { if (location.hash === '#administracao') carregar(); });
  if (location.hash === '#administracao') carregar();
})();
