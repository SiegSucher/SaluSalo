/*
 * Shared building blocks for every admin page: elements, icons, formatting, badges,
 * tables that become cards on phones, filters, pagination, the details panel,
 * confirmation dialogs, menus, toasts, and empty, error and loading states.
 * Text is always inserted as text (never as HTML).
 */
window.UI = (function () {
  'use strict';

  const SVG_NS = 'http://www.w3.org/2000/svg';

  /* ---------- Elements ---------- */
  function h(tag, attrs, ...children) {
    const el = document.createElement(tag);
    if (attrs) {
      for (const [key, value] of Object.entries(attrs)) {
        if (value == null || value === false) continue;
        if (key === 'class') el.className = value;
        else if (key === 'dataset') Object.assign(el.dataset, value);
        else if (key === 'style' && typeof value === 'object') Object.assign(el.style, value);
        else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2).toLowerCase(), value);
        else if (key === 'value') el.value = value;
        else el.setAttribute(key, value === true ? '' : value);
      }
    }
    append(el, children);
    return el;
  }
  function append(el, children) {
    for (const child of children.flat(Infinity)) {
      if (child == null || child === false || child === '') continue;
      el.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return el;
  }
  function clear(el) { while (el.firstChild) el.removeChild(el.firstChild); return el; }

  function icon(name, size, label) {
    const svg = document.createElementNS(SVG_NS, 'svg');
    const px = String(size || 20);
    svg.setAttribute('viewBox', '0 -960 960 960');
    svg.setAttribute('width', px);
    svg.setAttribute('height', px);
    svg.setAttribute('fill', 'currentColor');
    svg.setAttribute('focusable', 'false');
    if (label) { svg.setAttribute('role', 'img'); svg.setAttribute('aria-label', label); } else svg.setAttribute('aria-hidden', 'true');
    const path = document.createElementNS(SVG_NS, 'path');
    path.setAttribute('d', window.SaluIcons[name] || window.SaluIcons.circle);
    svg.append(path);
    return svg;
  }

  /* ---------- Formatting (English, like the app) ---------- */
  const dateFmt = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  const dayFmt = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' });
  const timeFmt = new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' });
  const weekdayFmt = new Intl.DateTimeFormat('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
  const startOfDay = (t) => new Date(t).setHours(0, 0, 0, 0);
  const fmt = {
    /** A short reference for a UUID, like "#3F2A9C01". */
    ref: (id) => '#' + String(id || '').slice(0, 8).toUpperCase(),
    num: (n) => Number(n).toLocaleString('en-US'),
    compact: (n) => (n >= 10000 ? (n / 1000).toFixed(1).replace(/\.0$/, '') + 'K' : Number(n).toLocaleString('en-US')),
    peso: (n) => '₱' + Number(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    percent: (n) => Math.round(n * 100) + '%',
    date: (t) => dateFmt.format(t),
    time: (t) => timeFmt.format(t),
    weekday: (t) => weekdayFmt.format(t),
    dateTime: (t) => dateFmt.format(t) + ', ' + timeFmt.format(t),
    /** "Today", "Tomorrow", "Yesterday", "Sep 24" or "Sep 24, 2025", like the app's dayLabel(). */
    day(t) {
      const diff = Math.round((startOfDay(t) - startOfDay(Date.now())) / 86400000);
      if (diff === 0) return 'Today';
      if (diff === 1) return 'Tomorrow';
      if (diff === -1) return 'Yesterday';
      return new Date(t).getFullYear() === new Date().getFullYear() ? dayFmt.format(t) : dateFmt.format(t);
    },
    /** "Today, 4:00 PM – 6:00 PM", like the app's pickupWindowLabel(). */
    window(start, end) {
      if (!start) return '—';
      const sameDay = startOfDay(start) === startOfDay(end);
      return fmt.day(start) + ', ' + fmt.time(start) + ' – ' + (sameDay ? '' : fmt.day(end) + ', ') + fmt.time(end);
    },
    moment: (t) => fmt.day(t) + ', ' + fmt.time(t),
    relative(t) {
      const diff = Date.now() - t;
      const future = diff < 0;
      const s = Math.abs(diff) / 1000;
      if (s < 60) return future ? 'in a moment' : 'just now';
      const m = Math.round(s / 60);
      if (m < 60) return future ? 'in ' + m + ' min' : m + (m === 1 ? ' minute ago' : ' minutes ago');
      const hr = Math.round(m / 60);
      if (hr < 24) return future ? 'in ' + hr + (hr === 1 ? ' hour' : ' hours') : hr + (hr === 1 ? ' hour ago' : ' hours ago');
      const d = Math.round(hr / 24);
      if (!future && d === 1) return 'yesterday';
      if (d < 7) return future ? 'in ' + d + ' days' : d + ' days ago';
      if (future) return fmt.day(t);
      if (d < 30) { const w = Math.round(d / 7); return w + (w === 1 ? ' week ago' : ' weeks ago'); }
      if (d < 365) { const mo = Math.max(1, Math.round(d / 30.4)); return mo + (mo === 1 ? ' month ago' : ' months ago'); }
      const y = Math.round(d / 365);
      return y + (y === 1 ? ' year ago' : ' years ago');
    },
    qty(n, unit) {
      const one = { pcs: 'pc', packs: 'pack', meals: 'meal', boxes: 'box' };
      return fmt.num(n) + ' ' + (n === 1 ? (one[unit] || unit) : unit);
    },
    unitPrice(price, unit) {
      if (!price) return 'Free';
      const one = { pcs: 'pc', packs: 'pack', meals: 'meal', boxes: 'box' };
      return fmt.peso(price) + ' / ' + (one[unit] || unit);
    },
    initials(name) {
      const words = String(name).replace(/[^\p{L}\p{N}\s'-]/gu, '').split(/\s+/).filter(Boolean);
      return ((words[0] || '?')[0] + (words.length > 1 ? words[words.length - 1][0] : (words[0] || '?')[1] || '')).toUpperCase();
    },
    plural: (n, one, many) => fmt.num(n) + ' ' + (n === 1 ? one : (many || one + 's'))
  };

  /* ---------- Badges (styles match the app's StatusBadge) ---------- */
  const BADGES = {
    listing: { AVAILABLE: ['green', 'checkCircle', 'Available'], RESERVED: ['orange', 'bookmark', 'Reserved'], CLAIMED: ['solid', 'taskAlt', 'Claimed'], EXPIRED: ['neutral', 'schedule', 'Expired'], REMOVED: ['neutral', 'block', 'Removed'] },
    reservation: { RESERVED: ['orange', 'bookmark', 'Reserved'], CLAIMED: ['solid', 'taskAlt', 'Claimed'], CANCELLED: ['error', 'highlightOff', 'Cancelled'] },
    verification: { VERIFIED: ['green', 'verified', 'Verified'], PENDING: ['orange', 'pending', 'Pending'], REJECTED: ['error', 'highlightOff', 'Rejected'], NONE: ['neutral', 'radioButtonUnchecked', 'Not verified'] },
    account: { ACTIVE: ['green', 'checkCircle', 'Active'], SUSPENDED: ['error', 'block', 'Suspended'] },
    role: { DONOR: ['outline', 'storefront', 'Donor'], RECIPIENT: ['outline', 'person', 'Recipient'], ADMIN: ['outline', 'adminPanelSettings', 'Admin'] },
    report: { OPEN: ['orange', 'flag', 'Open'], RESOLVED: ['green', 'taskAlt', 'Resolved'], DISMISSED: ['neutral', 'doNotDisturbOn', 'Dismissed'] },
    announcement: { SENT: ['green', 'checkCircle', 'Sent'], SCHEDULED: ['orange', 'schedule', 'Scheduled'], DRAFT: ['neutral', 'draft', 'Draft'], CANCELLED: ['neutral', 'block', 'Cancelled'] }
  };
  function badge(group, value, labelOverride) {
    const def = (BADGES[group] || {})[value] || ['neutral', 'circle', String(value)];
    return h('span', { class: 'badge badge--' + def[0] }, icon(def[1], 14), labelOverride || def[2]);
  }
  function flagBadge(count) {
    return h('span', { class: 'badge badge--urgent' }, icon('flag', 14), count > 1 ? count + ' reports' : 'Reported');
  }

  /* ---------- People and food ---------- */
  function avatar(person, size) {
    const cls = 'avatar' + (size ? ' avatar--' + size : '') + (person && person.org ? ' avatar--org' : '');
    return h('span', { class: cls, 'aria-hidden': 'true' }, fmt.initials(person ? person.name : '?'));
  }
  const CATEGORY_ICON = { 'Bread & pastries': 'bakeryDining', Vegetables: 'eco', Fruit: 'nutrition', 'Cooked meals': 'ramenDining', 'Packaged food': 'inventory2', Other: 'restaurant' };
  function thumb(category, big, label) {
    if (big) {
      return h('div', { class: 'thumb thumb--lg', role: 'img', 'aria-label': label || category },
        h('div', { class: 'thumb__inner' }, icon(CATEGORY_ICON[category] || 'restaurant', 40), h('span', { class: 'thumb__label' }, label || category)));
    }
    return h('span', { class: 'thumb', 'aria-hidden': 'true' }, icon(CATEGORY_ICON[category] || 'restaurant', 22));
  }
  /** A listing photo from a signed URL, or the category tile when there is none. */
  function photo(url, category, big, label) {
    if (!url) return thumb(category, big, label);
    const img = h('img', { src: url, alt: big ? (label || category) : '', loading: 'lazy', referrerpolicy: 'no-referrer' });
    return big ? h('div', { class: 'thumb thumb--lg thumb--photo' }, img) : h('span', { class: 'thumb thumb--photo', 'aria-hidden': 'true' }, img);
  }
  function identity(visual, name, meta, href) {
    return h('div', { class: 'identity' }, visual,
      h('div', { class: 'identity__text' },
        href ? h('a', { class: 'identity__name row-link', href }, name) : h('span', { class: 'identity__name' }, name),
        meta ? h('span', { class: 'identity__meta' }, meta) : null));
  }

  /* ---------- Buttons ---------- */
  function btn(opts) {
    const o = typeof opts === 'string' ? { label: opts } : opts;
    const cls = ['btn', 'btn--' + (o.variant || 'secondary')];
    if (o.size) cls.push('btn--' + o.size);
    if (o.block) cls.push('btn--block');
    if (o.className) cls.push(o.className);
    const tag = o.href ? 'a' : 'button';
    const el = h(tag, {
      class: cls.join(' '), type: o.href ? null : (o.type || 'button'), href: o.href || null,
      'aria-label': o.ariaLabel || null, disabled: o.disabled || null, id: o.id || null,
      onClick: o.onClick || null
    }, o.icon ? icon(o.icon, o.size === 'sm' ? 18 : 20) : null, o.label ? h('span', null, o.label) : null);
    return el;
  }
  function iconBtn(name, label, onClick, extra) {
    const b = h('button', { class: 'icon-btn' + (extra && extra.small ? ' icon-btn--sm' : ''), type: 'button', 'aria-label': label, title: label, onClick }, icon(name, 22));
    return b;
  }
  /** Runs an async action with the button disabled and a spinner inside it. */
  async function busy(button, fn) {
    const buttons = [].concat(button).filter(Boolean);
    buttons.forEach((b) => { b.disabled = true; });
    if (buttons[0]) { buttons[0].classList.add('is-busy'); buttons[0].setAttribute('aria-busy', 'true'); }
    try { return await fn(); } finally {
      buttons.forEach((b) => { b.disabled = false; b.classList.remove('is-busy'); b.removeAttribute('aria-busy'); });
    }
  }

  /* ---------- Layout pieces ---------- */
  function pageHead({ title, sub, actions }) {
    return h('header', { class: 'page-head' },
      h('div', { class: 'page-head__text' }, h('h1', { class: 'page-title' }, title), sub ? h('p', { class: 'page-sub' }, sub) : null),
      actions && actions.length ? h('div', { class: 'page-head__actions' }, actions) : null);
  }
  function card({ title, sub, actions, body, foot, className, id, labelId }) {
    const headId = labelId || (title ? 'c-' + Math.random().toString(36).slice(2, 8) : null);
    return h('section', { class: 'card' + (className ? ' ' + className : ''), id: id || null, 'aria-labelledby': headId },
      title ? h('div', { class: 'card__head' },
        h('div', { class: 'card__head-text' }, h('h2', { class: 'section-title', id: headId }, title), sub ? h('p', { class: 'section-sub' }, sub) : null),
        actions || null) : null,
      body !== undefined ? h('div', { class: 'card__body' }, body) : null,
      foot ? h('div', { class: 'card__foot' }, foot) : null);
  }
  function stat({ label, value, meta, iconName, href, attention, id }) {
    return h(href ? 'a' : 'div', { class: 'stat' + (attention ? ' stat--attention' : ''), href: href || null, id: id || null },
      h('div', { class: 'stat__top' }, h('span', { class: 'stat__label' }, label),
        iconName ? h('span', { class: 'icon-bubble' + (attention ? ' icon-bubble--orange' : ''), style: { width: '32px', height: '32px' } }, icon(iconName, 18)) : null),
      h('span', { class: 'stat__value' }, value),
      meta ? h('span', { class: 'stat__meta' }, meta) : null);
  }
  function notice({ tone, title, text, iconName, action }) {
    const icons = { info: 'info', warning: 'warning', error: 'error', neutral: 'info', success: 'checkCircle' };
    return h('div', { class: 'notice' + (tone && tone !== 'info' && tone !== 'success' ? ' notice--' + tone : ''), role: tone === 'error' ? 'alert' : null },
      icon(iconName || icons[tone || 'info'], 20),
      h('div', { class: 'notice__body' }, title ? h('span', { class: 'notice__title' }, title) : null, text ? h('span', null, text) : null),
      action || null);
  }
  function dl(pairs) {
    const el = h('dl', { class: 'dl' });
    for (const [term, value] of pairs) {
      if (value == null || value === false) continue;
      el.append(h('dt', null, term), h('dd', null, value));
    }
    return el;
  }
  function section(title, ...content) {
    return h('section', { class: 'panel-section' }, title ? h('h3', { class: 'panel-section__title' }, title) : null, content);
  }
  function timeline(items) {
    return h('ol', { class: 'timeline' }, items.map((it) =>
      h('li', { class: 'timeline__item' + (it.state === 'current' ? ' is-current' : it.state === 'pending' ? ' is-pending' : it.state === 'bad' ? ' is-bad' : '') },
        h('span', { class: 'timeline__dot' }, icon(it.icon || 'check', 16)),
        h('div', { class: 'timeline__text' },
          h('span', { class: 'timeline__title' }, it.title, it.state === 'current' ? h('span', { class: 'sr-only' }, ' (current step)') : null),
          it.at ? h('span', { class: 'timeline__meta' }, fmt.dateTime(it.at)) : null,
          it.meta ? h('span', { class: 'timeline__meta' }, it.meta) : null))));
  }

  /* ---------- Empty, error and loading ---------- */
  function empty({ iconName, title, text, action, tone }) {
    return h('div', { class: 'empty' + (tone === 'error' ? ' empty--error' : ''), role: tone === 'error' ? 'alert' : 'status' },
      h('div', { class: 'empty__icon' }, icon(iconName || 'search', 28)),
      h('p', { class: 'empty__title' }, title),
      text ? h('p', { class: 'empty__text' }, text) : null,
      action ? btn(Object.assign({ variant: 'secondary' }, action)) : null);
  }
  function errorState(onRetry, what) {
    return empty({
      tone: 'error', iconName: 'error', title: 'Couldn’t load ' + (what || 'this page'),
      text: 'Check your connection and try again. Nothing was changed.',
      action: onRetry ? { label: 'Try again', icon: 'refresh', onClick: onRetry } : null
    });
  }
  function skeleton(width, height, extra) {
    return h('span', { class: 'skeleton' + (extra ? ' ' + extra : ''), style: { width: width || '100%', height: height || '12px' }, 'aria-hidden': 'true' });
  }
  function skeletonRows(n, withAvatar) {
    return h('div', { class: 'table-wrap', 'aria-busy': 'true', 'aria-label': 'Loading' },
      Array.from({ length: n || 6 }, (_, i) => h('div', { class: 'sk-row' },
        withAvatar !== false ? skeleton('36px', '36px', 'skeleton--circle') : null,
        h('div', { class: 'sk-lines' }, skeleton((40 + (i * 13) % 30) + '%'), skeleton((20 + (i * 7) % 20) + '%', '10px')),
        h('span', { class: 'hide-phone', style: { width: '18%' } }, skeleton('100%')),
        skeleton('72px', '24px'))));
  }
  function skeletonStats(n) {
    return Array.from({ length: n }, () => h('div', { class: 'stat', 'aria-hidden': 'true' }, skeleton('50%'), skeleton('40%', '28px'), skeleton('70%', '10px')));
  }
  function skeletonList(n) {
    return h('div', { 'aria-busy': 'true', 'aria-label': 'Loading' }, Array.from({ length: n || 4 }, () =>
      h('div', { class: 'list__item' }, skeleton('36px', '36px', 'skeleton--circle'), h('div', { class: 'sk-lines' }, skeleton('60%'), skeleton('35%', '10px')))));
  }
  function skeletonPanel() {
    return h('div', { style: { display: 'grid', gap: '16px' }, 'aria-busy': 'true', 'aria-label': 'Loading' },
      h('div', { class: 'panel-hero' }, skeleton('56px', '56px', 'skeleton--circle'), h('div', { class: 'sk-lines' }, skeleton('55%', '16px'), skeleton('35%'))),
      skeleton('100%', '72px', 'skeleton--block'), skeleton('80%'), skeleton('90%'), skeleton('60%'), skeleton('100%', '120px', 'skeleton--block'));
  }

  /* ---------- Filters ---------- */
  /**
   * One filter row: a search box and selects. defs: [{ key, type: 'search'|'select', label,
   * placeholder, options: [[value, label]] }]. onChange gets the whole values object.
   */
  function filters(defs, values, onChange, defaults) {
    const row = h('div', { class: 'filters', role: 'search' });
    let timer = null;
    const controls = {};
    const clearBtn = btn({ label: 'Clear filters', variant: 'ghost', size: 'sm', icon: 'close', className: 'filters__clear', onClick: () => {
      Object.assign(values, defaults);
      for (const [key, el] of Object.entries(controls)) el.value = values[key];
      sync();
      onChange(values);
    } });
    function isDefault() { return Object.keys(defaults).every((k) => String(values[k]) === String(defaults[k])); }
    function sync() { clearBtn.hidden = isDefault(); }
    for (const def of defs) {
      const id = 'f-' + def.key + '-' + Math.random().toString(36).slice(2, 6);
      if (def.type === 'search') {
        const input = h('input', { class: 'input', id, type: 'search', placeholder: def.placeholder, value: values[def.key] || '', autocomplete: 'off', 'aria-label': def.label });
        const clearSearch = iconBtn('close', 'Clear search', () => { input.value = ''; values[def.key] = ''; sync(); onChange(values); input.focus(); }, { small: true });
        clearSearch.classList.add('search__clear');
        clearSearch.hidden = !input.value;
        input.addEventListener('input', () => {
          clearSearch.hidden = !input.value;
          clearTimeout(timer);
          timer = setTimeout(() => { values[def.key] = input.value; sync(); onChange(values); }, 250);
        });
        controls[def.key] = input;
        row.append(h('div', { class: 'search' }, icon('search', 20), input, clearSearch));
      } else {
        const select = h('select', { class: 'select', id }, def.options.map(([v, label]) => h('option', { value: v }, label)));
        select.value = values[def.key];
        select.addEventListener('change', () => { values[def.key] = select.value; sync(); onChange(values); });
        controls[def.key] = select;
        row.append(h('div', { class: 'field' }, h('label', { class: 'field__label', for: id }, def.label), select));
      }
    }
    row.append(clearBtn);
    sync();
    return row;
  }

  /* ---------- Table + phone cards ---------- */
  /**
   * columns: [{ label, cell(row) => node|string, className, hide: 'tablet' }]
   * card(row) => node for phones; open(row) opens details; href(row) for the row link.
   */
  function dataView({ columns, rows, card: cardFor, open, flag, caption }) {
    const wrap = h('div', { class: 'table-wrap has-cards' });
    const table = h('table', { class: 'table' },
      caption ? h('caption', { class: 'sr-only' }, caption) : null,
      h('thead', null, h('tr', null, columns.map((c) => h('th', { scope: 'col', class: (c.className || '') + (c.hide === 'tablet' ? ' hide-tablet' : '') }, c.label)))),
      h('tbody', null, rows.map((row) => {
        const tr = h('tr', { class: (open ? 'is-clickable' : '') + (flag && flag(row) ? ' is-flagged' : '') },
          columns.map((c) => h('td', { class: (c.className || '') + (c.hide === 'tablet' ? ' hide-tablet' : '') }, c.cell(row))));
        if (open) {
          tr.addEventListener('click', (e) => {
            if (e.target.closest('a, button, input, select, label')) return;
            open(row);
          });
        }
        return tr;
      })));
    wrap.append(table);
    const list = h('ul', { class: 'row-cards', 'aria-label': caption || null }, rows.map((row) => {
      const li = h('li', { class: 'row-card' + (flag && flag(row) ? ' is-flagged' : '') }, cardFor(row));
      if (open) {
        li.addEventListener('click', (e) => {
          if (e.target.closest('a, button, input, select, label')) return;
          open(row);
        });
      }
      return li;
    }));
    return [wrap, list];
  }
  function rowCard({ visual, title, href, meta, badges, trailing }) {
    return [
      h('div', { class: 'row-card__top' }, identity(visual, title, meta && meta.sub, href), trailing || h('span', { class: 'row-card__chev' }, icon('chevronRight', 22))),
      badges && badges.length ? h('div', { class: 'badge-row' }, badges) : null,
      meta && meta.items && meta.items.length ? h('div', { class: 'row-card__meta' }, meta.items.map((it) => h('span', null, it.icon ? icon(it.icon, 16) : null, it.text))) : null
    ];
  }

  function pager({ page, pages, total, pageSize, onChange, noun, nounPlural }) {
    if (total <= pageSize) return h('p', { class: 'results-meta' }, total ? 'Showing all ' + fmt.plural(total, noun || 'result', nounPlural) : '');
    const from = (page - 1) * pageSize + 1;
    const to = Math.min(total, page * pageSize);
    const go = (p) => () => onChange(p);
    const nums = [];
    const add = (p) => nums.push(h('button', { class: 'pager__page', type: 'button', 'aria-current': p === page ? 'page' : null, 'aria-label': 'Page ' + p, onClick: go(p) }, String(p)));
    const window_ = new Set([1, pages, page - 1, page, page + 1].filter((p) => p >= 1 && p <= pages));
    let last = 0;
    for (const p of Array.from(window_).sort((a, b) => a - b)) {
      if (p - last > 1) nums.push(h('span', { class: 'pager__gap', 'aria-hidden': 'true' }, '…'));
      add(p);
      last = p;
    }
    const prevIcon = iconBtn('chevronLeft', 'Previous page', go(page - 1));
    const nextIcon = iconBtn('chevronRight', 'Next page', go(page + 1));
    prevIcon.disabled = page <= 1;
    nextIcon.disabled = page >= pages;
    return h('nav', { class: 'pager', 'aria-label': 'Pages' },
      h('p', { class: 'results-meta' }, 'Showing ' + fmt.num(from) + '–' + fmt.num(to) + ' of ' + fmt.plural(total, noun || 'result', nounPlural)),
      h('div', { class: 'pager__pages' },
        btn({ label: 'Previous', icon: 'chevronLeft', variant: 'ghost', size: 'sm', disabled: page <= 1, onClick: go(page - 1) }),
        nums,
        btn({ label: 'Next', variant: 'ghost', size: 'sm', disabled: page >= pages, onClick: go(page + 1) })),
      h('div', { class: 'pager__compact' }, prevIcon, h('span', null, 'Page ' + page + ' of ' + pages), nextIcon));
  }

  /* ---------- Tabs ---------- */
  function tabs(items, selected, onSelect, label) {
    const list = h('div', { class: 'tabs', role: 'tablist', 'aria-label': label });
    const buttons = items.map((it) => {
      const b = h('button', {
        class: 'tab', role: 'tab', type: 'button', id: 'tab-' + it.key,
        'aria-selected': String(it.key === selected), tabindex: it.key === selected ? '0' : '-1',
        onClick: () => onSelect(it.key)
      }, it.icon ? icon(it.icon, 18) : null, it.label, it.count != null ? h('span', { class: 'tab__count' }, fmt.num(it.count)) : null);
      return b;
    });
    list.addEventListener('keydown', (e) => {
      const i = buttons.indexOf(document.activeElement);
      if (i < 0) return;
      let next = null;
      if (e.key === 'ArrowRight') next = buttons[(i + 1) % buttons.length];
      if (e.key === 'ArrowLeft') next = buttons[(i - 1 + buttons.length) % buttons.length];
      if (e.key === 'Home') next = buttons[0];
      if (e.key === 'End') next = buttons[buttons.length - 1];
      if (next) { e.preventDefault(); next.focus(); next.click(); }
    });
    append(list, buttons);
    return list;
  }

  /* ---------- Focus handling for panels, dialogs and the drawer ---------- */
  const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
  function trapFocus(container, e) {
    if (e.key !== 'Tab') return;
    const items = Array.from(container.querySelectorAll(FOCUSABLE)).filter((el) => el.offsetParent !== null || el === document.activeElement);
    if (!items.length) { e.preventDefault(); container.focus(); return; }
    const first = items[0];
    const last = items[items.length - 1];
    if (e.shiftKey && (document.activeElement === first || document.activeElement === container)) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
  }
  const layers = [];
  document.addEventListener('keydown', (e) => {
    const top = layers[layers.length - 1];
    if (!top) return;
    if (e.key === 'Escape' && top.onEscape) { e.preventDefault(); top.onEscape(); }
    else trapFocus(top.el, e);
  });
  function pushLayer(layer) { layers.push(layer); }
  function popLayer(layer) { const i = layers.indexOf(layer); if (i >= 0) layers.splice(i, 1); }
  function hasLayer() { return layers.length > 0; }

  function inert(on) {
    const app = document.getElementById('app');
    if (!app) return;
    if (on) app.setAttribute('inert', ''); else app.removeAttribute('inert');
  }

  /* ---------- Details panel (side sheet; full screen on phones) ---------- */
  const panelState = { el: null, scrim: null, layer: null, onClose: null, returnFocus: null };
  function openPanel({ title, sub, body, foot, onClose, label }) {
    const root = document.getElementById('panel-root');
    const fresh = !panelState.el;
    if (fresh) {
      panelState.returnFocus = document.activeElement;
      panelState.scrim = h('div', { class: 'scrim', onClick: () => requestClose() });
      panelState.el = h('aside', { class: 'panel', role: 'dialog', 'aria-modal': 'true', tabindex: '-1' });
      root.append(panelState.scrim, panelState.el);
      panelState.layer = { el: panelState.el, onEscape: () => requestClose() };
      pushLayer(panelState.layer);
      inert(true);
      document.body.style.overflow = 'hidden';
      requestAnimationFrame(() => { panelState.scrim.classList.add('is-visible'); panelState.el.classList.add('is-open'); });
    }
    panelState.onClose = onClose;
    renderPanel({ title, sub, body, foot, label });
    if (fresh) setTimeout(() => panelState.el && panelState.el.focus(), 30);
    return { update: (o) => renderPanel(Object.assign({ title, sub, body, foot, label }, o)), close: closePanel };
  }
  function renderPanel({ title, sub, body, foot, label }) {
    const el = panelState.el;
    if (!el) return;
    const titleId = 'panel-title';
    clear(el);
    el.setAttribute('aria-labelledby', titleId);
    el.append(
      h('div', { class: 'panel__head' },
        h('button', { class: 'icon-btn panel__back', type: 'button', 'aria-label': 'Back', onClick: () => requestClose() }, icon('arrowBack', 22)),
        h('div', { class: 'panel__titles' },
          label ? h('span', { class: 'eyebrow' }, label) : null,
          h('h2', { class: 'panel__title', id: titleId }, title || ''),
          sub ? h('p', { class: 'panel__sub' }, sub) : null),
        h('button', { class: 'icon-btn panel__close', type: 'button', 'aria-label': 'Close', onClick: () => requestClose() }, icon('close', 22))),
      h('div', { class: 'panel__body' }, body),
      foot && [].concat(foot).filter(Boolean).length ? h('div', { class: 'panel__foot' }, foot) : null);
  }
  function requestClose() { if (panelState.onClose) panelState.onClose(); else closePanel(); }
  function closePanel() {
    if (!panelState.el) return;
    const { el, scrim, layer, returnFocus } = panelState;
    popLayer(layer);
    Object.assign(panelState, { el: null, scrim: null, layer: null, onClose: null, returnFocus: null });
    if (!hasLayer()) { inert(false); document.body.style.overflow = ''; }
    el.classList.remove('is-open');
    scrim.classList.remove('is-visible');
    setTimeout(() => { el.remove(); scrim.remove(); }, 230);
    if (returnFocus && document.contains(returnFocus)) returnFocus.focus({ preventScroll: true });
  }
  const panelIsOpen = () => !!panelState.el;

  /* ---------- Confirmation dialog ---------- */
  /**
   * confirm({ title, text, confirmLabel, tone: 'danger'|'primary', iconName, choice: { label,
   * placeholder, options: [[value, label]] }, reason: { label, required, placeholder, hint,
   * maxLength }, action: async (reason, choice) => {}, errorText: string | (err) => string })
   * Resolves true once the action succeeded, false if cancelled.
   */
  function confirm(o) {
    return new Promise((resolve) => {
      const root = document.getElementById('dialog-root');
      const returnFocus = document.activeElement;
      const scrim = h('div', { class: 'scrim' });
      const titleId = 'dlg-title-' + Math.random().toString(36).slice(2, 7);
      const textId = titleId + '-text';
      let reasonEl = null;
      let reasonErr = null;
      const errorBox = h('div', { hidden: true });
      let choiceEl = null;
      let choiceErr = null;
      if (o.choice) {
        const cid = titleId + '-choice';
        choiceEl = h('select', { class: 'select', id: cid },
          [h('option', { value: '' }, o.choice.placeholder || 'Choose…')]
            .concat(o.choice.options.map(([v, label]) => h('option', { value: v }, label))));
        choiceErr = h('span', { class: 'field__error', id: cid + '-err', hidden: true }, 'Choose a reason.');
        choiceEl.addEventListener('change', () => { choiceErr.hidden = true; choiceEl.classList.remove('is-invalid'); choiceEl.removeAttribute('aria-invalid'); });
      }
      if (o.reason) {
        const rid = titleId + '-reason';
        reasonEl = h('textarea', { class: 'textarea', id: rid, rows: '3', maxlength: String((o.reason && o.reason.maxLength) || 300), placeholder: o.reason.placeholder || '', 'aria-describedby': rid + '-hint' });
        reasonErr = h('span', { class: 'field__error', id: rid + '-err', hidden: true }, 'Write a short reason. It goes into the audit log.');
        reasonEl.addEventListener('input', () => { reasonErr.hidden = true; reasonEl.classList.remove('is-invalid'); reasonEl.removeAttribute('aria-invalid'); });
      }
      const cancelBtn = btn({ label: o.cancelLabel || 'Cancel', variant: 'secondary', onClick: () => done(false) });
      const okBtn = btn({ label: o.confirmLabel, variant: o.tone === 'primary' ? 'primary' : 'danger', onClick: submit });
      const dialog = h('div', { class: 'dialog', role: 'alertdialog', 'aria-modal': 'true', 'aria-labelledby': titleId, 'aria-describedby': textId, tabindex: '-1' },
        h('div', { class: 'dialog__icon' + (o.tone === 'primary' ? ' dialog__icon--green' : '') }, icon(o.iconName || (o.tone === 'primary' ? 'check' : 'warning'), 24)),
        h('h2', { class: 'dialog__title', id: titleId }, o.title),
        h('p', { class: 'dialog__text', id: textId }, o.text),
        o.choice ? h('div', { class: 'field' },
          h('label', { class: 'field__label', for: choiceEl.id }, o.choice.label),
          choiceEl, choiceErr) : null,
        o.reason ? h('div', { class: 'field' },
          h('label', { class: 'field__label', for: reasonEl.id }, o.reason.label + (o.reason.required ? '' : ' (optional)')),
          reasonEl,
          h('span', { class: 'field__hint', id: reasonEl.id + '-hint' }, o.reason.hint || 'Saved in the audit log with your name.'),
          reasonErr) : null,
        errorBox,
        h('div', { class: 'dialog__actions' }, cancelBtn, okBtn));
      const wrap = h('div', { class: 'dialog-wrap' }, dialog);
      root.append(scrim, wrap);
      scrim.addEventListener('click', () => { if (!okBtn.classList.contains('is-busy')) done(false); });
      wrap.addEventListener('click', (e) => { if (e.target === wrap && !okBtn.classList.contains('is-busy')) done(false); });
      const layer = { el: dialog, onEscape: () => { if (!okBtn.classList.contains('is-busy')) done(false); } };
      pushLayer(layer);
      inert(true);
      requestAnimationFrame(() => { scrim.classList.add('is-visible'); dialog.classList.add('is-open'); });
      setTimeout(() => (choiceEl || reasonEl || cancelBtn).focus(), 40);

      async function submit() {
        const choice = choiceEl ? choiceEl.value : null;
        if (choiceEl && !choice) {
          choiceErr.hidden = false;
          choiceEl.classList.add('is-invalid');
          choiceEl.setAttribute('aria-invalid', 'true');
          choiceEl.setAttribute('aria-describedby', choiceErr.id);
          choiceEl.focus();
          return;
        }
        const reason = reasonEl ? reasonEl.value.trim() : '';
        if (o.reason && o.reason.required && !reason) {
          reasonErr.hidden = false;
          reasonEl.classList.add('is-invalid');
          reasonEl.setAttribute('aria-invalid', 'true');
          reasonEl.setAttribute('aria-describedby', reasonEl.id + '-hint ' + reasonErr.id);
          reasonEl.focus();
          return;
        }
        errorBox.hidden = true;
        clear(errorBox);
        try {
          await busy([okBtn, cancelBtn], () => (o.action ? o.action(reason, choice) : null));
          done(true);
        } catch (err) {
          console.warn(err);
          errorBox.hidden = false;
          const errorText = typeof o.errorText === 'function' ? o.errorText(err) : o.errorText;
          errorBox.append(notice({ tone: 'error', text: errorText || 'That didn’t work. Please try again.' }));
          okBtn.focus();
        }
      }
      function done(result) {
        popLayer(layer);
        if (!hasLayer()) inert(false); else if (panelState.el) { /* the panel stays open and interactive */ }
        dialog.classList.remove('is-open');
        scrim.classList.remove('is-visible');
        setTimeout(() => { scrim.remove(); wrap.remove(); }, 190);
        if (returnFocus && document.contains(returnFocus)) returnFocus.focus({ preventScroll: true });
        resolve(result);
      }
    });
  }

  /** A dialog that only informs: a title, content and one Close button. */
  function info({ title, body, closeLabel }) {
    const root = document.getElementById('dialog-root');
    const returnFocus = document.activeElement;
    const scrim = h('div', { class: 'scrim' });
    const titleId = 'info-title-' + Math.random().toString(36).slice(2, 7);
    const closeBtn = btn({ label: closeLabel || 'Close', variant: 'primary', onClick: () => done() });
    const dialog = h('div', { class: 'dialog', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': titleId, tabindex: '-1' },
      h('div', { class: 'dialog__icon dialog__icon--green' }, icon('info', 24)),
      h('h2', { class: 'dialog__title', id: titleId }, title),
      h('div', { class: 'dialog__text', style: { display: 'grid', gap: '10px' } }, body),
      h('div', { class: 'dialog__actions' }, closeBtn));
    const wrap = h('div', { class: 'dialog-wrap' }, dialog);
    root.append(scrim, wrap);
    const layer = { el: dialog, onEscape: done };
    pushLayer(layer);
    inert(true);
    scrim.addEventListener('click', done);
    wrap.addEventListener('click', (e) => { if (e.target === wrap) done(); });
    requestAnimationFrame(() => { scrim.classList.add('is-visible'); dialog.classList.add('is-open'); });
    setTimeout(() => closeBtn.focus(), 40);
    function done() {
      popLayer(layer);
      if (!hasLayer()) inert(false);
      dialog.classList.remove('is-open');
      scrim.classList.remove('is-visible');
      setTimeout(() => { scrim.remove(); wrap.remove(); }, 190);
      if (returnFocus && document.contains(returnFocus)) returnFocus.focus({ preventScroll: true });
    }
  }

  /* ---------- Menus ---------- */
  let openMenuState = null;
  function menu(anchor, items) {
    closeMenu();
    const el = h('div', { class: 'menu', role: 'menu' });
    const buttons = [];
    for (const it of items) {
      if (it === '-') { el.append(h('div', { class: 'menu__sep', role: 'separator' })); continue; }
      if (!it) continue;
      const b = h('button', { class: 'menu__item' + (it.danger ? ' menu__item--danger' : ''), role: 'menuitem', type: 'button', tabindex: '-1', onClick: () => { closeMenu(); it.onClick(); } }, it.icon ? icon(it.icon, 20) : null, it.label);
      buttons.push(b);
      el.append(b);
    }
    document.body.append(el);
    const r = anchor.getBoundingClientRect();
    const w = el.offsetWidth;
    const hgt = el.offsetHeight;
    let left = r.right - w;
    if (left < 8) left = 8;
    let top = r.bottom + 6;
    if (top + hgt > window.innerHeight - 8) top = Math.max(8, r.top - hgt - 6);
    el.style.left = left + 'px';
    el.style.top = top + 'px';
    anchor.setAttribute('aria-expanded', 'true');
    const onKey = (e) => {
      const i = buttons.indexOf(document.activeElement);
      if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); closeMenu(); anchor.focus(); }
      else if (e.key === 'ArrowDown') { e.preventDefault(); buttons[(i + 1) % buttons.length].focus(); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); buttons[(i - 1 + buttons.length) % buttons.length].focus(); }
      else if (e.key === 'Tab') { closeMenu(); }
    };
    const onDown = (e) => { if (!el.contains(e.target) && e.target !== anchor && !anchor.contains(e.target)) closeMenu(); };
    document.addEventListener('keydown', onKey, true);
    document.addEventListener('pointerdown', onDown, true);
    window.addEventListener('resize', closeMenu, { once: true });
    openMenuState = { el, anchor, onKey, onDown };
    if (buttons[0]) buttons[0].focus();
  }
  function closeMenu() {
    if (!openMenuState) return;
    const { el, anchor, onKey, onDown } = openMenuState;
    openMenuState = null;
    document.removeEventListener('keydown', onKey, true);
    document.removeEventListener('pointerdown', onDown, true);
    anchor.setAttribute('aria-expanded', 'false');
    el.remove();
  }
  function menuButton(label, items) {
    const b = h('button', { class: 'icon-btn icon-btn--sm', type: 'button', 'aria-label': label, 'aria-haspopup': 'menu', 'aria-expanded': 'false', title: label }, icon('moreVert', 20));
    b.addEventListener('click', (e) => { e.stopPropagation(); if (openMenuState && openMenuState.anchor === b) closeMenu(); else menu(b, typeof items === 'function' ? items() : items); });
    return b;
  }

  /* ---------- Toasts ---------- */
  function toast(message, opts) {
    const o = opts || {};
    const tone = o.tone || 'success';
    const region = document.getElementById(tone === 'error' ? 'toast-alerts' : 'toast-root');
    const icons = { success: 'checkCircle', error: 'error', info: 'info' };
    const el = h('div', { class: 'toast toast--' + tone },
      h('span', { class: 'toast__icon' }, icon(icons[tone], 20)),
      h('div', { class: 'toast__text' }, h('span', null, message), o.sub ? h('span', { class: 'toast__sub' }, o.sub) : null),
      h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Dismiss', onClick: () => remove() }, icon('close', 18)));
    region.append(el);
    const t = setTimeout(remove, o.timeout || (tone === 'error' ? 9000 : 5000));
    function remove() { clearTimeout(t); el.remove(); }
    return remove;
  }

  /* ---------- Forms ---------- */
  function field({ label, input, hint, error, count }) {
    return h('div', { class: 'field' },
      h('label', { class: 'field__label', for: input.id }, label), input,
      hint ? h('span', { class: 'field__hint', id: input.id + '-hint' }, hint) : null,
      count || null, error || null);
  }
  function switchRow({ id, label, hint, checked, onChange, disabled }) {
    const input = h('input', { type: 'checkbox', id, role: 'switch', checked: checked || null, disabled: disabled || null, 'aria-describedby': hint ? id + '-hint' : null });
    if (onChange) input.addEventListener('change', () => onChange(input.checked));
    return h('div', { class: 'switch-row' },
      h('div', { class: 'switch-row__text' }, h('label', { class: 'switch-row__label', for: id }, label), hint ? h('span', { class: 'switch-row__hint', id: id + '-hint' }, hint) : null),
      h('span', { class: 'switch' }, input, h('span', { class: 'switch__track', 'aria-hidden': 'true' })));
  }

  return {
    h, append, clear, icon, fmt, badge, flagBadge, avatar, thumb, photo, identity, btn, iconBtn, busy,
    pageHead, card, stat, notice, dl, section, timeline, empty, errorState, skeleton, skeletonRows,
    skeletonStats, skeletonList, skeletonPanel, filters, dataView, rowCard, pager, tabs,
    openPanel, closePanel, panelIsOpen, confirm, info, menu, menuButton, closeMenu, toast, field, switchRow,
    trapFocus, pushLayer, popLayer, hasLayer, inert, CATEGORY_ICON
  };
})();
