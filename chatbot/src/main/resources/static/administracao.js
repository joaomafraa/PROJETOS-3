// Tela de administracao (HU08.3).
// Usa somente as rotas /api/admin/**. Quem nao e administrador recebe 403 do backend
// e a tela mostra um aviso; esconder botoes aqui e so conforto, a protecao e no servidor.
(() => {
  const tela = document.querySelector('#administracao');
  if (!tela) return;

  const aviso = document.querySelector('#aviso-admin');
  const conteudo = document.querySelector('#conteudo-admin');
  const formulario = document.querySelector('#form-novo-atendente');
  const campoNome = document.querySelector('#novo-nome');
  const campoEmail = document.querySelector('#novo-email');
  const campoPerfil = document.querySelector('#novo-perfil');
  const corpoTabela = document.querySelector('#lista-atendentes');
  const filtroAtivo = document.querySelector('#filtro-ativo-admin');
  const botaoAtualizar = document.querySelector('#atualizar-admin');
  const areaResponsaveis = document.querySelector('#lista-responsaveis');

  const nomesPerfil = { ADMINISTRADOR: 'Administrador', COORDENADOR: 'Coordenador', ATENDENTE: 'Atendente' };
  const tiposNotificacao = [
    { tipo: 'NOVO_TICKET', titulo: 'Nova solicitação aberta' },
    { tipo: 'ATENDIMENTO_HUMANO_SOLICITADO', titulo: 'Cliente pediu atendimento humano' },
    { tipo: 'NOVO_LEAD', titulo: 'Novo lead registrado' }
  ];

  let jaCarregou = false;
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

  function botao(texto, classe = 'button secondary') {
    const b = criar('button', texto, classe);
    b.type = 'button';
    return b;
  }

  // O Spring exige o token CSRF em POST, PATCH e PUT.
  async function tokenCsrf() {
    const resposta = await fetch('/csrf-token', { cache: 'no-store' });
    if (!resposta.ok) throw new Error('Não foi possível preparar a operação. Atualize a página e tente novamente.');
    return resposta.json();
  }

  // Faz a chamada e transforma os erros em mensagens que a pessoa entende.
  async function chamarApi(url, metodo = 'GET', corpo) {
    const opcoes = { method: metodo, cache: 'no-store', headers: {} };
    if (metodo !== 'GET') {
      const csrf = await tokenCsrf();
      opcoes.headers[csrf.headerName] = csrf.token;
    }
    if (corpo !== undefined) {
      opcoes.headers['Content-Type'] = 'application/json';
      opcoes.body = JSON.stringify(corpo);
    }

    let resposta;
    try {
      resposta = await fetch(url, opcoes);
    } catch {
      throw new Error('Sem conexão com o servidor. Verifique a internet e tente novamente.');
    }

    if (resposta.status === 401) throw new Error('Sua sessão expirou. Entre novamente.');
    if (resposta.status === 204) return null;

    let dados = null;
    try { dados = await resposta.json(); } catch { /* resposta sem corpo */ }

    if (!resposta.ok) {
      const erro = new Error(dados?.mensagem || 'Não foi possível concluir a operação. Tente novamente.');
      erro.status = resposta.status;
      throw erro;
    }
    return dados;
  }

  // ---------- Carregamento ----------

  async function carregarTudo() {
    if (carregando) return;
    carregando = true;
    botaoAtualizar.disabled = true;
    corpoTabela.setAttribute('aria-busy', 'true');
    mostrarAviso('Carregando dados da equipe…');

    try {
      const filtro = filtroAtivo.value ? `?ativo=${filtroAtivo.value}` : '';
      // A lista de responsaveis precisa de todos os ativos, independente do filtro da tabela.
      const [equipe, ativos, responsaveis] = await Promise.all([
        chamarApi(`/api/admin/atendentes${filtro}`),
        chamarApi('/api/admin/atendentes?ativo=true'),
        chamarApi('/api/admin/responsaveis-notificacao')
      ]);
      conteudo.hidden = false;
      desenharTabela(equipe);
      desenharResponsaveis(ativos, responsaveis);
      jaCarregou = true;
      mostrarAviso(`${equipe.length} atendente(s) encontrado(s).`);
    } catch (erro) {
      if (erro.status === 403) {
        conteudo.hidden = true;
        mostrarAviso('Esta área é restrita a administradores.', true);
      } else {
        mostrarAviso(erro.message, true);
      }
    } finally {
      carregando = false;
      botaoAtualizar.disabled = false;
      corpoTabela.setAttribute('aria-busy', 'false');
    }
  }

  // ---------- Tabela da equipe ----------

  function desenharTabela(atendentes) {
    corpoTabela.replaceChildren();
    if (!atendentes.length) {
      const linha = criar('tr');
      const celula = criar('td', 'Nenhum atendente para mostrar com este filtro.', 'vazio');
      celula.colSpan = 5;
      linha.append(celula);
      corpoTabela.append(linha);
      return;
    }
    atendentes.forEach(atendente => corpoTabela.append(linhaAtendente(atendente)));
  }

  function linhaAtendente(atendente) {
    const linha = criar('tr');
    linha.append(criar('td', atendente.nome), criar('td', atendente.email));

    // Perfil: trocar o valor ja salva, depois de confirmar.
    const celulaPerfil = criar('td');
    const seletor = document.createElement('select');
    seletor.setAttribute('aria-label', `Perfil de ${atendente.nome}`);
    Object.entries(nomesPerfil).forEach(([valor, rotulo]) => {
      const opcao = criar('option', rotulo);
      opcao.value = valor;
      opcao.selected = valor === atendente.perfil;
      seletor.append(opcao);
    });
    seletor.addEventListener('change', () => alterarPerfil(atendente, seletor));
    celulaPerfil.append(seletor);

    const celulaSituacao = criar('td');
    const selo = criar('span', atendente.ativo ? 'Ativo' : 'Inativo', 'status-badge');
    selo.dataset.ativo = String(atendente.ativo);
    celulaSituacao.append(selo);

    const celulaAcoes = criar('td');
    const acoes = criar('div', null, 'acoes');
    const botaoSituacao = botao(atendente.ativo ? 'Desativar' : 'Ativar');
    botaoSituacao.setAttribute('aria-label', `${atendente.ativo ? 'Desativar' : 'Ativar'} ${atendente.nome}`);
    botaoSituacao.addEventListener('click', () => alternarSituacao(atendente, botaoSituacao));
    const botaoSenha = botao('Definir senha');
    botaoSenha.setAttribute('aria-label', `Definir senha de ${atendente.nome}`);
    botaoSenha.addEventListener('click', () => abrirCampoSenha(atendente, acoes, botaoSenha));
    acoes.append(botaoSituacao, botaoSenha);
    celulaAcoes.append(acoes);

    linha.append(celulaPerfil, celulaSituacao, celulaAcoes);
    return linha;
  }

  async function alterarPerfil(atendente, seletor) {
    const novo = seletor.value;
    if (!confirm(`Mudar o perfil de ${atendente.nome} para ${nomesPerfil[novo]}?`)) {
      seletor.value = atendente.perfil;
      return;
    }
    seletor.disabled = true;
    try {
      await chamarApi(`/api/admin/atendentes/${atendente.id}/perfil`, 'PATCH', { perfil: novo });
      await carregarTudo();
      mostrarAviso(`Perfil de ${atendente.nome} alterado para ${nomesPerfil[novo]}.`);
    } catch (erro) {
      seletor.value = atendente.perfil;
      mostrarAviso(erro.message, true);
    } finally {
      seletor.disabled = false;
    }
  }

  async function alternarSituacao(atendente, botaoClicado) {
    const acao = atendente.ativo ? 'desativar' : 'ativar';
    const pergunta = atendente.ativo
      ? `Desativar ${atendente.nome}? A pessoa deixa de conseguir entrar no painel.`
      : `Ativar ${atendente.nome} novamente?`;
    if (!confirm(pergunta)) return;

    botaoClicado.disabled = true;
    try {
      await chamarApi(`/api/admin/atendentes/${atendente.id}/${acao}`, 'POST');
      await carregarTudo();
      mostrarAviso(`${atendente.nome} foi ${atendente.ativo ? 'desativado(a)' : 'ativado(a)'}.`);
    } catch (erro) {
      mostrarAviso(erro.message, true);
      botaoClicado.disabled = false;
    }
  }

  // Campo de senha aparece na propria linha; nada de prompt(), que mostra a senha na tela.
  function abrirCampoSenha(atendente, acoes, botaoSenha) {
    if (acoes.querySelector('form')) return;
    botaoSenha.hidden = true;

    const form = criar('form', null, 'acoes');
    const campo = document.createElement('input');
    campo.type = 'password';
    campo.autocomplete = 'new-password';
    campo.minLength = 8;
    campo.placeholder = 'Mínimo de 8 caracteres';
    campo.setAttribute('aria-label', `Nova senha de ${atendente.nome}`);
    const salvar = criar('button', 'Salvar senha', 'button primary');
    salvar.type = 'submit';
    const cancelar = botao('Cancelar');

    const fechar = () => { form.remove(); botaoSenha.hidden = false; botaoSenha.focus(); };
    cancelar.addEventListener('click', fechar);
    form.addEventListener('submit', async evento => {
      evento.preventDefault();
      if (campo.value.length < 8) {
        campo.setAttribute('aria-invalid', 'true');
        mostrarAviso('A senha precisa ter pelo menos 8 caracteres.', true);
        campo.focus();
        return;
      }
      salvar.disabled = true;
      try {
        await chamarApi(`/api/admin/atendentes/${atendente.id}/senha`, 'PUT', { senha: campo.value });
        fechar();
        mostrarAviso(`Senha de ${atendente.nome} definida.`);
      } catch (erro) {
        mostrarAviso(erro.message, true);
        salvar.disabled = false;
      }
    });

    form.append(campo, salvar, cancelar);
    acoes.append(form);
    campo.focus();
  }

  // ---------- Cadastro ----------

  function marcarInvalido(campo, invalido) {
    if (invalido) campo.setAttribute('aria-invalid', 'true');
    else campo.removeAttribute('aria-invalid');
  }

  formulario.addEventListener('submit', async evento => {
    evento.preventDefault();
    const nome = campoNome.value.trim();
    const email = campoEmail.value.trim();
    const emailParece = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

    marcarInvalido(campoNome, !nome);
    marcarInvalido(campoEmail, !emailParece);
    if (!nome) { mostrarAviso('Informe o nome do atendente.', true); campoNome.focus(); return; }
    if (!emailParece) { mostrarAviso('Informe um e-mail válido.', true); campoEmail.focus(); return; }

    const enviar = formulario.querySelector('button[type="submit"]');
    enviar.disabled = true;
    try {
      const novo = await chamarApi('/api/admin/atendentes', 'POST', { nome, email, perfil: campoPerfil.value });
      formulario.reset();
      await carregarTudo();
      mostrarAviso(`${novo.nome} foi cadastrado(a). Agora defina a senha para liberar o acesso.`);
    } catch (erro) {
      mostrarAviso(erro.message, true);
    } finally {
      enviar.disabled = false;
    }
  });

  // ---------- Responsaveis por notificacao ----------

  function desenharResponsaveis(ativos, configurados) {
    areaResponsaveis.replaceChildren();

    tiposNotificacao.forEach(({ tipo, titulo }) => {
      const marcados = new Set(configurados.filter(r => r.tipo === tipo).map(r => r.atendenteId));
      const grupo = criar('fieldset');
      grupo.append(criar('legend', titulo));

      const opcoes = criar('div', null, 'opcoes');
      if (!ativos.length) {
        opcoes.append(criar('span', 'Nenhum atendente ativo para escolher.'));
      }
      ativos.forEach(atendente => {
        const rotulo = criar('label');
        const caixa = document.createElement('input');
        caixa.type = 'checkbox';
        caixa.value = atendente.id;
        caixa.checked = marcados.has(atendente.id);
        rotulo.append(caixa, document.createTextNode(atendente.nome));
        opcoes.append(rotulo);
      });

      // Responsavel que foi desativado depois continua salvo; avisamos para a pessoa revisar.
      const inativos = configurados.filter(r => r.tipo === tipo && !r.atendenteAtivo);
      if (inativos.length) {
        opcoes.append(criar('span', `Inativo(s) na lista atual: ${inativos.map(r => r.atendenteNome).join(', ')}. Salve para remover.`, 'admin-dica'));
      }

      const salvar = botao('Salvar responsáveis', 'button primary');
      salvar.addEventListener('click', () => salvarResponsaveis(tipo, titulo, opcoes, salvar));
      grupo.append(opcoes, salvar);
      areaResponsaveis.append(grupo);
    });
  }

  async function salvarResponsaveis(tipo, titulo, opcoes, salvar) {
    const ids = [...opcoes.querySelectorAll('input[type="checkbox"]:checked')].map(c => Number(c.value));
    salvar.disabled = true;
    try {
      await chamarApi(`/api/admin/responsaveis-notificacao/${tipo}`, 'PUT', { atendenteIds: ids });
      await carregarTudo();
      mostrarAviso(ids.length
        ? `Responsáveis por "${titulo}" atualizados.`
        : `Ninguém mais recebe "${titulo}".`);
    } catch (erro) {
      mostrarAviso(erro.message, true);
    } finally {
      salvar.disabled = false;
    }
  }

  // ---------- Navegacao ----------

  botaoAtualizar.addEventListener('click', carregarTudo);
  filtroAtivo.addEventListener('change', carregarTudo);

  function aoAbrir() {
    const aberta = ['#administracao', '#titulo-administracao'].includes(location.hash);
    if (aberta && !jaCarregou) carregarTudo();
  }
  window.addEventListener('hashchange', aoAbrir);
  aoAbrir();
})();
