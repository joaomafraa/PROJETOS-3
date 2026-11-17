(() => {
  const list = document.querySelector('#lista-leads');
  const notice = document.querySelector('#aviso-leads');
  const search = document.querySelector('#busca-leads');
  const refresh = document.querySelector('#atualizar-leads');
  const form = document.querySelector('#form-lead');
  const formNotice = document.querySelector('#aviso-form-lead');
  const submit = form.querySelector('button[type="submit"]');
  let items = [], loaded = false;

  function element(tag, value, cls) {
    const node = document.createElement(tag);
    node.textContent = value;
    if (cls) node.className = cls;
    return node;
  }
  function render() {
    const query = search.value.trim().toLowerCase();
    const filtered = items.filter(x => `${x.nome || ''} ${x.contato || ''}`.toLowerCase().includes(query));
    list.replaceChildren();
    if (!filtered.length) {
      list.append(element('p', items.length ? 'Nenhum lead encontrado.' : 'Nenhum lead cadastrado ainda.', 'lead-empty'));
      return;
    }
    for (const lead of filtered) {
      const card = element('article', '', 'lead-card');
      card.append(element('h2', lead.nome || 'Nome não informado'), element('p', lead.contato, 'lead-contact'));
      const data = element('dl', '', 'lead-data');
      for (const [key, value] of [['Origem', lead.origem], ['Necessidade', lead.necessidade]]) {
        const row = document.createElement('div');
        row.append(element('dt', key), element('dd', value || 'Não informado'));
        data.append(row);
      }
      card.append(data);
      list.append(card);
    }
  }
  async function load() {
    refresh.disabled = true;
    list.setAttribute('aria-busy', 'true');
    notice.textContent = 'Carregando leads…';
    try {
      const response = await fetch('/api/leads', { cache: 'no-store' });
      if (response.status === 401) throw Error('Sua sessão expirou. Entre novamente.');
      if (response.status === 403) throw Error('Você não tem acesso aos leads.');
      if (!response.ok) throw Error('Não foi possível carregar os leads. Tente novamente.');
      const data = await response.json();
      if (!Array.isArray(data)) throw Error('Resposta inválida ao consultar os leads.');
      items = data;
      loaded = true;
      notice.textContent = `${items.length} lead(s) registrado(s).`;
      render();
    } catch (error) {
      notice.textContent = error.message || 'Não foi possível conectar. Tente novamente.';
    } finally {
      refresh.disabled = false;
      list.setAttribute('aria-busy', 'false');
    }
  }
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    submit.disabled = true;
    formNotice.textContent = 'Registrando lead…';
    try {
      const tokenResponse = await fetch('/csrf-token', { cache: 'no-store' });
      if (!tokenResponse.ok) throw Error('Não foi possível preparar o cadastro. Entre novamente e tente outra vez.');
      const csrf = await tokenResponse.json();
      const values = new FormData(form);
      const response = await fetch('/api/leads', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify(Object.fromEntries(values))
      });
      if (response.status === 401) throw Error('Sua sessão expirou. Entre novamente.');
      if (response.status === 403) throw Error('Você não tem permissão ou sua sessão expirou. Entre novamente.');
      if (response.status === 409) throw Error('Já existe um lead com este contato e esta necessidade. Consulte a lista.');
      if (response.status === 400) {
        const data = await response.json().catch(() => ({}));
        throw Error(data.erro || 'Confira os dados informados e tente novamente.');
      }
      if (!response.ok) throw Error('Não foi possível registrar o lead. Tente novamente.');
      form.reset();
      formNotice.textContent = 'Lead registrado com sucesso.';
      await load();
    } catch (error) {
      formNotice.textContent = error.message || 'Não foi possível conectar. Tente novamente.';
    } finally {
      submit.disabled = false;
    }
  });
  refresh.addEventListener('click', load);
  search.addEventListener('input', render);
  function open() { if (location.hash === '#leads' && !loaded) load(); }
  window.addEventListener('hashchange', open);
  open();
})();
