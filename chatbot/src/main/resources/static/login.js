(() => {
  const form = document.querySelector('#login-form');
  const aviso = document.querySelector('#login-aviso');
  const botao = document.querySelector('.login-submit');
  const tentar = document.querySelector('#login-retry');
  const email = document.querySelector('#username');
  const senha = document.querySelector('#password');
  const campo = document.querySelector('#csrf');
  let pronto = false;
  let enviando = false;

  function mostrarErro(mensagem, permitirTentar = false) {
    aviso.textContent = mensagem;
    aviso.hidden = false;
    tentar.hidden = !permitirTentar;
  }

  function limparErro() {
    aviso.hidden = true;
    tentar.hidden = true;
    aviso.textContent = '';
  }

  async function carregarToken() {
    const resposta = await fetch('/csrf-token', { credentials: 'same-origin', cache: 'no-store' });
    if (!resposta.ok) throw new Error('Token indisponível');
    const dados = await resposta.json();
    if (!dados.parameterName || !dados.token) throw new Error('Token inválido');
    campo.name = dados.parameterName;
    campo.value = dados.token;
  }

  async function prepararAcesso() {
    pronto = false;
    botao.disabled = true;
    tentar.disabled = true;
    try {
      await carregarToken();
      pronto = true;
      botao.disabled = false;
      if (!new URLSearchParams(location.search).has('erro')) limparErro();
    } catch {
      mostrarErro('Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.', true);
    } finally {
      tentar.disabled = false;
    }
  }

  if (new URLSearchParams(location.search).has('erro')) {
    mostrarErro('Não foi possível entrar. Verifique seu e-mail e senha ou fale com o administrador.');
    email.setAttribute('aria-invalid', 'true');
    senha.setAttribute('aria-invalid', 'true');
  }

  const erros = { username: document.querySelector('#username-error'), password: document.querySelector('#password-error') };
  function limparCampo(input) {
    input.removeAttribute('aria-invalid');
    erros[input.id].hidden = true;
    erros[input.id].textContent = '';
  }
  function erroCampo(input, mensagem) {
    input.setAttribute('aria-invalid', 'true');
    erros[input.id].textContent = mensagem;
    erros[input.id].hidden = false;
  }
  for (const input of [email, senha]) {
    input.addEventListener('input', () => {
      limparCampo(input);
      if (pronto && !aviso.hidden) limparErro();
    });
  }

  tentar.addEventListener('click', () => { limparErro(); prepararAcesso(); });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!pronto || enviando) return;
    limparErro();
    limparCampo(email);
    limparCampo(senha);
    const emailVazio = !email.value.trim();
    if (emailVazio || email.validity.typeMismatch) erroCampo(email, emailVazio ? 'Informe seu e-mail.' : 'Informe um e-mail válido.');
    if (!senha.value) erroCampo(senha, 'Informe sua senha.');
    if (emailVazio || email.validity.typeMismatch || !senha.value) {
      (emailVazio || email.validity.typeMismatch ? email : senha).focus();
      return;
    }
    enviando = true;
    botao.disabled = true;
    botao.textContent = 'Entrando...';
    limparErro();
    try {
      await carregarToken();
      form.submit();
    } catch {
      enviando = false;
      pronto = false;
      botao.innerHTML = 'Entrar no painel <span aria-hidden="true">↗</span>';
      mostrarErro('Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.', true);
    }
  });
  prepararAcesso();
})();
