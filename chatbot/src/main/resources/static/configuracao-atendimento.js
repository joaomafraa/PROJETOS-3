(() => {
  const formulario = document.querySelector('#form-config-atendimento');
  if (!formulario) return;

  const aviso = document.querySelector('#aviso-config-atendimento');
  const situacao = document.querySelector('#situacao-expediente');
  const campoFuso = document.querySelector('#config-fuso');
  const areaDias = document.querySelector('#config-dias');
  const campoAssuntos = document.querySelector('#config-assuntos');
  const campoMensagem = document.querySelector('#config-mensagem');
  const contador = document.querySelector('#config-mensagem-contador');
  const botaoSalvar = formulario.querySelector('button[type="submit"]');

  const dias = [
    { valor: 'MONDAY', nome: 'Segunda-feira' },
    { valor: 'TUESDAY', nome: 'Terça-feira' },
    { valor: 'WEDNESDAY', nome: 'Quarta-feira' },
    { valor: 'THURSDAY', nome: 'Quinta-feira' },
    { valor: 'FRIDAY', nome: 'Sexta-feira' },
    { valor: 'SATURDAY', nome: 'Sábado' },
    { valor: 'SUNDAY', nome: 'Domingo' }
  ];

  const fusos = [
    ['America/Recife', 'Recife (PE)'],
    ['America/Sao_Paulo', 'São Paulo / Brasília'],
    ['America/Fortaleza', 'Fortaleza (CE)'],
    ['America/Bahia', 'Salvador (BA)'],
    ['America/Belem', 'Belém (PA)'],
    ['America/Maceio', 'Maceió (AL)'],
    ['America/Manaus', 'Manaus (AM)'],
    ['America/Cuiaba', 'Cuiabá (MT)'],
    ['America/Campo_Grande', 'Campo Grande (MS)'],
    ['America/Porto_Velho', 'Porto Velho (RO)'],
    ['America/Boa_Vista', 'Boa Vista (RR)'],
    ['America/Rio_Branco', 'Rio Branco (AC)'],
    ['America/Noronha', 'Fernando de Noronha']
  ];

  let periodosExtras = [];
  let jaCarregou = false;
  let carregando = false;

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

  async function tokenCsrf() {
    const resposta = await fetch('/csrf-token', { cache: 'no-store' });
    if (!resposta.ok) throw new Error('Não foi possível preparar a operação. Atualize a página e tente novamente.');
    return resposta.json();
  }

  async function chamarApi(metodo = 'GET', corpo) {
    const opcoes = { method: metodo, cache: 'no-store', headers: {} };
    if (metodo !== 'GET') {
      const csrf = await tokenCsrf();
      opcoes.headers[csrf.headerName] = csrf.token;
      opcoes.headers['Content-Type'] = 'application/json';
      opcoes.body = JSON.stringify(corpo);
    }

    let resposta;
    try {
      resposta = await fetch('/api/admin/configuracao-atendimento', opcoes);
    } catch {
      throw new Error('Sem conexão com o servidor. Verifique a internet e tente novamente.');
    }
    if (resposta.status === 401) throw new Error('Sua sessão expirou. Entre novamente.');

    let dados = null;
    try { dados = await resposta.json(); } catch { /* resposta sem corpo */ }
    if (!resposta.ok) {
      const erro = new Error(dados?.mensagem || 'Não foi possível concluir a operação. Tente novamente.');
      erro.status = resposta.status;
      throw erro;
    }
    return dados;
  }

  function montarFusos(atual) {
    campoFuso.replaceChildren();
    const lista = fusos.some(([id]) => id === atual) || !atual ? fusos : [[atual, atual], ...fusos];
    lista.forEach(([id, nome]) => {
      const opcao = criar('option', nome);
      opcao.value = id;
      opcao.selected = id === atual;
      campoFuso.append(opcao);
    });
  }

  function montarDias(horarios) {
    areaDias.replaceChildren();
    periodosExtras = [];

    dias.forEach(({ valor, nome }) => {
      const doDia = horarios.filter(h => h.diaSemana === valor);
      const principal = doDia[0];
      periodosExtras.push(...doDia.slice(1));

      const linha = criar('div', null, 'config-dia');
      linha.dataset.dia = valor;

      const rotuloAtende = criar('label');
      const atende = document.createElement('input');
      atende.type = 'checkbox';
      atende.checked = Boolean(principal);
      atende.className = 'config-atende';
      rotuloAtende.append(atende, document.createTextNode(nome));

      const abertura = campoHora('Abre', `Abertura de ${nome}`, principal?.abertura || '08:00', 'config-abertura');
      const fechamento = campoHora('Fecha', `Fechamento de ${nome}`, principal?.fechamento || '18:00', 'config-fechamento');

      const atualizar = () => {
        abertura.input.disabled = !atende.checked;
        fechamento.input.disabled = !atende.checked;
      };
      atende.addEventListener('change', atualizar);
      atualizar();

      linha.append(rotuloAtende, abertura.rotulo, fechamento.rotulo);
      if (doDia.length > 1) {
        linha.append(criar('span', `+${doDia.length - 1} período(s) extra(s) mantido(s)`, 'admin-dica'));
      }
      areaDias.append(linha);
    });
  }

  function campoHora(texto, descricao, valor, classe) {
    const rotulo = criar('label');
    const input = document.createElement('input');
    input.type = 'time';
    input.step = 60;
    input.value = valor;
    input.className = classe;
    input.setAttribute('aria-label', descricao);
    rotulo.append(document.createTextNode(texto), input);
    return { rotulo, input };
  }

  function preencher(config) {
    montarFusos(config.fuso);
    montarDias(config.horarios || []);
    campoAssuntos.value = (config.assuntosAutomaticos || []).join('\n');
    campoMensagem.value = config.mensagemForaExpediente || '';
    atualizarContador();

    if (!config.configurada) {
      situacao.hidden = true;
      mostrarAviso('Ainda não configurado. Enquanto isso, o atendimento funciona como antes.');
      return;
    }
    situacao.hidden = false;
    situacao.dataset.disponivel = String(config.equipeDisponivelAgora);
    situacao.textContent = config.equipeDisponivelAgora ? 'Equipe em expediente agora' : 'Fora do expediente agora';
    const proxima = config.proximaAbertura ? ` Próximo expediente: ${config.proximaAbertura}.` : '';
    mostrarAviso(`Configuração carregada.${config.equipeDisponivelAgora ? '' : proxima}`);
  }

  function atualizarContador() {
    contador.textContent = String(campoMensagem.value.length);
  }

  function lerFormulario() {
    const horarios = [];
    let erro = null;

    areaDias.querySelectorAll('.config-dia').forEach(linha => {
      const atende = linha.querySelector('.config-atende');
      const abertura = linha.querySelector('.config-abertura');
      const fechamento = linha.querySelector('.config-fechamento');
      abertura.removeAttribute('aria-invalid');
      fechamento.removeAttribute('aria-invalid');
      if (!atende.checked) return;

      const nome = linha.querySelector('label').textContent.trim();
      if (!abertura.value || !fechamento.value) {
        abertura.setAttribute('aria-invalid', String(!abertura.value));
        fechamento.setAttribute('aria-invalid', String(!fechamento.value));
        erro = erro || { campo: abertura.value ? fechamento : abertura, texto: `Preencha os horários de ${nome}.` };
        return;
      }
      // "HH:mm" compara certo como texto.
      if (abertura.value >= fechamento.value) {
        fechamento.setAttribute('aria-invalid', 'true');
        erro = erro || { campo: fechamento, texto: `Em ${nome}, o fechamento precisa ser depois da abertura.` };
        return;
      }
      horarios.push({ diaSemana: linha.dataset.dia, abertura: abertura.value, fechamento: fechamento.value });
    });

    // Periodos extras so continuam se o dia ainda estiver marcado como atendido.
    const diasMarcados = new Set(horarios.map(h => h.diaSemana));
    horarios.push(...periodosExtras.filter(p => diasMarcados.has(p.diaSemana)));

    return {
      erro,
      dados: {
        fuso: campoFuso.value,
        mensagemForaExpediente: campoMensagem.value.trim(),
        horarios,
        assuntosAutomaticos: campoAssuntos.value.split('\n').map(a => a.trim()).filter(Boolean)
      }
    };
  }

  async function carregar() {
    if (carregando) return;
    carregando = true;
    mostrarAviso('Carregando configuração do atendimento…');
    try {
      preencher(await chamarApi());
      jaCarregou = true;
    } catch (erro) {
      if (erro.status !== 403) mostrarAviso(erro.message, true);
      else mostrarAviso('');
    } finally {
      carregando = false;
    }
  }

  formulario.addEventListener('submit', async evento => {
    evento.preventDefault();
    const { erro, dados } = lerFormulario();
    campoMensagem.removeAttribute('aria-invalid');

    if (erro) {
      mostrarAviso(erro.texto, true);
      erro.campo.focus();
      return;
    }
    if (dados.mensagemForaExpediente.length > 500) {
      campoMensagem.setAttribute('aria-invalid', 'true');
      mostrarAviso('A mensagem fora do expediente pode ter no máximo 500 caracteres.', true);
      campoMensagem.focus();
      return;
    }
    if (!dados.horarios.length && !confirm('Nenhum dia marcado: a equipe humana ficará sempre fora do expediente. Salvar assim mesmo?')) {
      return;
    }

    botaoSalvar.disabled = true;
    try {
      preencher(await chamarApi('PUT', dados));
      mostrarAviso('Configuração do atendimento salva.');
    } catch (erroApi) {
      mostrarAviso(erroApi.message, true);
    } finally {
      botaoSalvar.disabled = false;
    }
  });

  campoMensagem.addEventListener('input', atualizarContador);

  function aoAbrir() {
    const aberta = ['#administracao', '#titulo-administracao'].includes(location.hash);
    if (aberta && !jaCarregou) carregar();
  }
  window.addEventListener('hashchange', aoAbrir);
  aoAbrir();
})();
