// Fontes das respostas automaticas (HU02.2).
// Busca GET /api/historico/{conversaId}/fontes e mostra, em cada resposta do chatbot,
// os documentos citados ([1], [2]...) e os trechos que foram apenas consultados.
// Cada <li> da lista precisa de data-mensagem-id e data-remetente.
// Todo dado vindo da API e exibido com textContent.
(() => {
  function elemento(tag, texto, classe) {
    const item = document.createElement(tag);
    if (texto !== undefined && texto !== null) item.textContent = texto;
    if (classe) item.className = classe;
    return item;
  }

  function descricao(fonte) {
    const partes = [fonte.documentoTitulo];
    if (fonte.localizacao) partes.push(fonte.localizacao);
    return partes.join(' — ');
  }

  function itemFonte(fonte) {
    const item = elemento('li', null, 'fonte-item');
    const titulo = elemento('strong', `${fonte.numero ? `[${fonte.numero}] ` : ''}${descricao(fonte)}`);
    const versao = elemento('span', `Versão ${fonte.documentoVersao} · trecho ${fonte.trechoId}`, 'fonte-versao');
    const trecho = elemento('blockquote', fonte.excerto, 'fonte-trecho');
    item.append(titulo, versao, trecho);
    return item;
  }

  function bloco(titulo, fontes) {
    const secao = elemento('div', null, 'fontes-grupo');
    secao.append(elemento('p', titulo, 'fontes-grupo-titulo'));
    const lista = elemento('ol', null, 'fontes-lista');
    fontes.forEach(fonte => lista.append(itemFonte(fonte)));
    secao.append(lista);
    return secao;
  }

  function painel(registro) {
    const citadas = registro.citadas || [];
    const chavesCitadas = new Set(citadas.map(f => `${f.documentoId}|${f.documentoVersao}|${f.trechoId}`));
    // Trechos consultados que a resposta nao citou: informados a parte, sem afirmar que sustentam a resposta.
    const consultadas = (registro.recuperadas || [])
      .filter(f => !chavesCitadas.has(`${f.documentoId}|${f.documentoVersao}|${f.trechoId}`));
    const detalhes = elemento('details', null, 'fontes-resposta');
    const resumo = citadas.length
      ? `Fontes: ${citadas.length} citada${citadas.length > 1 ? 's' : ''}`
      : 'Fontes: nenhuma citação';
    detalhes.append(elemento('summary', consultadas.length ? `${resumo} · ${consultadas.length} consultada${consultadas.length > 1 ? 's' : ''}` : resumo));
    if (citadas.length) detalhes.append(bloco('Citadas na resposta', citadas));
    if (consultadas.length) detalhes.append(bloco('Também consultadas pela IA (não necessariamente usadas)', consultadas));
    if (!citadas.length && !consultadas.length) detalhes.append(elemento('p', 'Nenhum trecho registrado.', 'fontes-vazio'));
    return detalhes;
  }

  async function buscar(conversaId) {
    let resposta;
    try { resposta = await fetch(`/api/historico/${encodeURIComponent(conversaId)}/fontes`, { cache: 'no-store', headers: { Accept: 'application/json' } }); }
    catch { throw new Error('sem conexão'); }
    if (!resposta.ok) throw new Error(`status ${resposta.status}`);
    return resposta.json();
  }

  // aindaValido() devolve false se o usuario ja abriu outra conversa; assim nada e anexado na conversa errada.
  async function anexar(lista, conversaId, aindaValido = () => true) {
    const respostas = [...lista.querySelectorAll('li[data-remetente="CHATBOT"][data-mensagem-id]')];
    if (!respostas.length) return;
    let registros;
    try { registros = await buscar(conversaId); }
    catch {
      if (!aindaValido()) return;
      respostas.forEach(item => item.append(elemento('small', 'Fontes indisponíveis no momento.', 'fontes-aviso')));
      return;
    }
    if (!aindaValido()) return;
    const porMensagem = new Map(registros.map(registro => [String(registro.mensagemId), registro]));
    for (const item of respostas) {
      item.querySelector('.fontes-resposta, .fontes-aviso')?.remove();
      const registro = porMensagem.get(item.dataset.mensagemId);
      // Respostas antigas, de antes do registro de fontes, continuam sem o bloco.
      if (registro) item.append(painel(registro));
      else item.append(elemento('small', 'Sem fontes registradas', 'fontes-aviso'));
    }
  }

  window.fontesResposta = { anexar };
})();
