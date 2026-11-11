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
    const solicitacoes = document.querySelector('#solicitacoes');
    // Telas do painel: a chave e o endereco (#...) usado no menu.
    const telas = {
      '#atendimentos': { elemento: atendimentos, rotulo: 'Atendimentos', topo: 'Central de conversas',
        titulo: 'Caixa de entrada', foco: '#titulo-lista', pular: '#conversas', textoPular: 'Ir para a lista de conversas' },
      '#solicitacoes': { elemento: solicitacoes, rotulo: 'Atendimentos', topo: 'Solicitações dos clientes',
        titulo: 'Solicitações', foco: '#titulo-solicitacoes', pular: '#titulo-solicitacoes', textoPular: 'Ir para as solicitações' },
      '#configuracoes': { elemento: configuracoes, rotulo: 'Preferências', topo: 'Configurações do painel',
        titulo: 'Configurações', foco: '#titulo-configuracoes', pular: '#titulo-configuracoes', textoPular: 'Ir para configurações' }
    };
    function telaAtual() {
      if (['#configuracoes', '#titulo-configuracoes'].includes(location.hash)) return '#configuracoes';
      if (solicitacoes && ['#solicitacoes', '#titulo-solicitacoes'].includes(location.hash)) return '#solicitacoes';
      return '#atendimentos';
    }
    function navegar(focar = false) {
      const atual = telaAtual();
      const tela = telas[atual];
      Object.entries(telas).forEach(([chave, item]) => { if (item.elemento) item.elemento.hidden = chave !== atual; });
      // Aviso e botoes do topo pertencem a caixa de entrada.
      document.querySelector('#barra-aviso').hidden = atual !== '#atendimentos';
      document.querySelector('.topbar-actions').hidden = atual !== '#atendimentos';
      document.querySelector('.topbar-label').textContent = tela.rotulo;
      document.querySelector('.topbar strong').textContent = tela.topo;
      document.title = `${tela.titulo} | WXN`;
      document.querySelector('.skip-link').href = tela.pular;
      document.querySelector('.skip-link').textContent = tela.textoPular;
      links.forEach(link => {
        const ativo = link.hash === atual;
        link.classList.toggle('active', ativo);
        if (ativo) link.setAttribute('aria-current', 'page');
        else link.removeAttribute('aria-current');
      });
      if (focar) {
        const titulo = document.querySelector(tela.foco);
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
