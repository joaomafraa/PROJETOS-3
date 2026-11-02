(() => {
  let tema = 'escuro';
  try { tema = localStorage.getItem('wxn-tema') === 'claro' ? 'claro' : 'escuro'; } catch {}
  document.documentElement.dataset.tema = tema;
  document.addEventListener('DOMContentLoaded', () => {
    const botao = document.querySelector('#alternar-tema');
    const configuracoes = document.querySelector('#configuracoes');
    const atendimentos = document.querySelector('.inbox-layout');
    const links = document.querySelectorAll('.main-nav a');
    links[0].setAttribute('href', '#atendimentos');
    links[0].setAttribute('aria-label', 'Atendimentos');
    function navegar(focar = false) {
      const emConfiguracoes = ['#configuracoes', '#titulo-configuracoes'].includes(location.hash);
      configuracoes.hidden = !emConfiguracoes;
      atendimentos.hidden = emConfiguracoes;
      document.querySelector('#barra-aviso').hidden = emConfiguracoes;
      document.querySelector('.topbar-actions').hidden = emConfiguracoes;
      document.querySelector('.topbar-label').textContent = emConfiguracoes ? 'Preferências' : 'Atendimentos';
      document.querySelector('.topbar strong').textContent = emConfiguracoes ? 'Configurações do painel' : 'Central de conversas';
      document.title = `${emConfiguracoes ? 'Configurações' : 'Caixa de entrada'} | WXN`;
      document.querySelector('.skip-link').href = emConfiguracoes ? '#titulo-configuracoes' : '#conversas';
      document.querySelector('.skip-link').textContent = emConfiguracoes ? 'Ir para configurações' : 'Ir para a lista de conversas';
      links.forEach(link => {
        const ativo = link.hash === (emConfiguracoes ? '#configuracoes' : '#atendimentos');
        link.classList.toggle('active', ativo);
        if (ativo) link.setAttribute('aria-current', 'page');
        else link.removeAttribute('aria-current');
      });
      if (focar) {
        const titulo = document.querySelector(emConfiguracoes ? '#titulo-configuracoes' : '#titulo-lista');
        titulo.setAttribute('tabindex', '-1');
        titulo.focus({ preventScroll: true });
      }
    }
    window.addEventListener('hashchange', () => navegar(true));
    navegar();
    function atualizar() {
      botao.textContent = tema === 'escuro' ? 'Tema claro' : 'Tema escuro';
      botao.setAttribute('aria-label', `Ativar ${botao.textContent.toLowerCase()}`);
      document.querySelector('#tema-atual').textContent = `Tema ${tema} ativo`;
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
