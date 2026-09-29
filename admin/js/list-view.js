/*
 * A searchable, filterable, paged list: one filter row, then a table on larger
 * screens or cards on phones, then pagination. Shared by Users, Listings,
 * Reservations, Reports and the Audit log so they behave the same way.
 *
 * The first load shows skeleton rows; later loads keep the current rows, dimmed,
 * until the new ones arrive. A failed load shows a retry state.
 */
window.ListView = function ListView(cfg) {
  'use strict';
  const { h, clear, filters, dataView, pager, empty, errorState, skeletonRows } = window.UI;

  const state = { query: Object.assign({ page: 1 }, cfg.defaults, cfg.initial || {}), result: null, token: 0 };
  const results = h('div', { class: 'data' });
  const live = h('p', { class: 'sr-only', role: 'status' });
  const extras = h('div', { style: { display: 'flex', gap: '8px', flexWrap: 'wrap' } });
  const filterRow = cfg.filters && cfg.filters.length
    ? filters(cfg.filters, state.query, () => { state.query.page = 1; load(); }, Object.assign({}, cfg.defaults))
    : null;
  const el = h('div', { class: 'data' }, filterRow, extras, live, results);

  function isFiltered() {
    return Object.keys(cfg.defaults).some((k) => String(state.query[k]) !== String(cfg.defaults[k])) || (cfg.isFiltered && cfg.isFiltered(state.query));
  }

  async function load() {
    const token = ++state.token;
    if (!state.result) {
      clear(results).append(skeletonRows(Math.min(cfg.pageSize || 10, 8), cfg.avatars !== false));
      results.setAttribute('aria-busy', 'true');
    } else {
      results.classList.add('is-refreshing');
      results.setAttribute('aria-busy', 'true');
    }
    try {
      const res = await cfg.fetch(Object.assign({}, state.query, { pageSize: cfg.pageSize || 10 }));
      if (token !== state.token) return;
      state.result = res;
      state.query.page = res.page;
      render();
      live.textContent = res.total ? (res.total === 1 ? '1 ' + (cfg.noun || 'result') : res.total.toLocaleString('en-US') + ' ' + (cfg.nounPlural || (cfg.noun || 'result') + 's')) + ' found.' : 'Nothing found.';
      if (cfg.onLoaded) cfg.onLoaded(res);
    } catch (err) {
      if (token !== state.token) return;
      console.warn(err);
      state.result = null;
      clear(results).append(h('div', { class: 'card' }, errorState(() => load(), cfg.what)));
    } finally {
      if (token === state.token) {
        results.classList.remove('is-refreshing');
        results.removeAttribute('aria-busy');
      }
    }
  }

  function render() {
    const res = state.result;
    clear(results);
    if (!res.rows.length) {
      const e = cfg.emptyState(state.query, isFiltered());
      if (isFiltered() && !e.action) {
        e.action = { label: 'Clear filters', icon: 'close', onClick: () => api.reset() };
      }
      results.append(h('div', { class: 'card' }, empty(e)));
      return;
    }
    results.append(...dataView({ columns: cfg.columns, rows: res.rows, card: cfg.card, open: cfg.open, flag: cfg.flag, caption: cfg.caption }));
    results.append(pager({
      page: res.page, pages: res.pages, total: res.total, pageSize: res.pageSize, noun: cfg.noun, nounPlural: cfg.nounPlural,
      onChange: (p) => { state.query.page = p; load(); el.scrollIntoView({ block: 'start', behavior: 'smooth' }); }
    }));
  }

  const api = {
    el,
    extras,
    load,
    reload: () => load(),
    query: state.query,
    set(partial) { Object.assign(state.query, partial, { page: 1 }); load(); },
    reset() {
      const keep = cfg.keepOnReset ? cfg.keepOnReset(state.query) : {};
      Object.keys(state.query).forEach((k) => { if (k !== 'page') delete state.query[k]; });
      Object.assign(state.query, cfg.defaults, keep, { page: 1 });
      if (filterRow) {
        filterRow.querySelectorAll('input, select').forEach((c) => {
          const def = cfg.filters.find((f) => c.id.startsWith('f-' + f.key + '-'));
          if (def) c.value = state.query[def.key];
        });
        const clearBtn = filterRow.querySelector('.filters__clear');
        if (clearBtn) clearBtn.hidden = true;
        const clearSearch = filterRow.querySelector('.search__clear');
        if (clearSearch) clearSearch.hidden = true;
      }
      load();
    }
  };
  return api;
};
