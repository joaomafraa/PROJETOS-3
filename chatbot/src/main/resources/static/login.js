(() => {
  const aviso = document.querySelector('#login-aviso');
  const botao = document.querySelector('.login-submit');
  if (new URLSearchParams(location.search).has('erro')) {
    aviso.textContent = 'Não foi possível entrar. Verifique seu e-mail e senha ou fale com o administrador.';
    aviso.hidden = false;
  }
  fetch('/csrf-token', { credentials: 'same-origin', cache: 'no-store' })
    .then(resposta => { if (!resposta.ok) throw new Error(); return resposta.json(); })
    .then(dados => { const campo = document.querySelector('#csrf'); campo.name = dados.parameterName; campo.value = dados.token; botao.disabled = false; })
    .catch(() => { aviso.textContent = 'Não foi possível carregar o acesso. Atualize a página e tente novamente.'; aviso.hidden = false; });
})();
