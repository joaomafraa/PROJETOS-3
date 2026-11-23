(() => {
  const form = document.querySelector('#form-documento');
  const titulo = document.querySelector('#documento-titulo');
  const conteudo = document.querySelector('#documento-conteudo');
  const arquivo = document.querySelector('#documento-arquivo');
  const lista = document.querySelector('#documentos-lista');
  const aviso = document.querySelector('#documentos-aviso');
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
  function limpar() { editando = null; form.reset(); form.querySelector('[type=submit]').textContent = 'Salvar documento'; }
  async function carregar() {
    try {
      const documentos = await api('/api/admin/documentos'); lista.replaceChildren();
      if (!documentos.length) lista.textContent = 'Nenhum documento cadastrado.';
      for (const d of documentos) {
        const linha = document.createElement('div'); linha.className = 'admin-card';
        const nome = document.createElement('strong'); nome.textContent = d.titulo;
        const estado = document.createElement('p'); estado.textContent = `Versão ${d.versao} · ${d.ativo ? 'Ativo' : 'Inativo'}`;
        const editar = document.createElement('button'); editar.type = 'button'; editar.className = 'button secondary'; editar.textContent = 'Editar';
        editar.addEventListener('click', () => { editando = d.id; titulo.value = d.titulo; conteudo.value = d.conteudo; form.querySelector('[type=submit]').textContent = 'Salvar alterações'; titulo.focus(); });
        const ativar = document.createElement('button'); ativar.type = 'button'; ativar.className = 'button secondary'; ativar.textContent = d.ativo ? 'Desativar' : 'Ativar';
        ativar.addEventListener('click', async () => { try { await api(`/api/admin/documentos/${d.id}/estado`, 'PATCH', { ativo: !d.ativo }); informar('Estado atualizado.'); await carregar(); } catch (e) { informar(e.message); } });
        linha.append(nome, estado, editar, ativar); lista.append(linha);
      }
    } catch (e) { informar(e.message); }
  }
  arquivo.addEventListener('change', async () => {
    const f = arquivo.files[0]; if (!f) return;
    if (!f.name.toLowerCase().endsWith('.txt') || f.size > 800000) { informar('Escolha um arquivo TXT de até 800 KB.'); arquivo.value = ''; return; }
    try {
      const bytes = await f.arrayBuffer(); conteudo.value = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
      if (conteudo.value.length > 200000) { conteudo.value = ''; throw new Error('O arquivo excede 200.000 caracteres.'); }
      if (!titulo.value) titulo.value = f.name.replace(/\.txt$/i, ''); informar('Arquivo carregado. Revise e salve.');
    } catch (e) { informar(e.message || 'Não foi possível ler o arquivo como UTF-8.'); }
  });
  form.addEventListener('submit', async e => {
    e.preventDefault();
    try { await api(editando ? `/api/admin/documentos/${editando}` : '/api/admin/documentos', editando ? 'PUT' : 'POST', { titulo: titulo.value, conteudo: conteudo.value }); limpar(); informar('Documento salvo.'); await carregar(); }
    catch (erro) { informar(erro.message); }
  });
  document.querySelector('#documento-cancelar').addEventListener('click', limpar);
  window.addEventListener('hashchange', () => { if (location.hash === '#administracao') carregar(); });
  if (location.hash === '#administracao') carregar();
})();
