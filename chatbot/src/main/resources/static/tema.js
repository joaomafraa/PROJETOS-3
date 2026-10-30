(() => {
  let tema = 'escuro';
  try { tema = localStorage.getItem('wxn-tema') === 'claro' ? 'claro' : 'escuro'; } catch {}
  document.documentElement.dataset.tema = tema;
  document.addEventListener('DOMContentLoaded', () => {
    const botao = document.querySelector('#alternar-tema');
    function atualizar() {
      botao.textContent = tema === 'escuro' ? 'Tema claro' : 'Tema escuro';
      botao.setAttribute('aria-label', `Ativar ${botao.textContent.toLowerCase()}`);
    }
    atualizar();
    botao.addEventListener('click', () => {
      tema = tema === 'escuro' ? 'claro' : 'escuro';
      document.documentElement.dataset.tema = tema;
      try { localStorage.setItem('wxn-tema', tema); } catch {}
      atualizar();
    });
  });
})();
