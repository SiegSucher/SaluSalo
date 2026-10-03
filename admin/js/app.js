/*
 * The shell: the sidebar (full on desktops, a rail on tablets, a drawer on phones),
 * the top bar, and moving between pages with the address bar (#/users/U-1001).
 * Pages register themselves on window.Pages with mount(main, route), and optionally
 * update(route) for opening or closing their details panel, and unmount().
 */
(function () {
  'use strict';

  const { h, icon, clear } = window.UI;
  const Pages = window.Pages || {};
  const data = window.SaluData;

  const NAV = [
    { key: 'overview', label: 'Overview', icon: 'spaceDashboard' },
    { key: 'users', label: 'Users', icon: 'group', count: (c) => c.pendingVerifications, soft: true, countLabel: (n) => n + (n === 1 ? ' verification request' : ' verification requests') },
    { key: 'listings', label: 'Listings', icon: 'lunchDining' },
    { key: 'reservations', label: 'Reservations', icon: 'bookmark' },
    { key: 'reports', label: 'Reports', icon: 'flag', count: (c) => c.openReports, countLabel: (n) => n + (n === 1 ? ' open report' : ' open reports') },
    { key: 'analytics', label: 'Analytics', icon: 'monitoring' },
    { key: 'notifications', label: 'Notifications', icon: 'notifications', dot: (c) => c.unreadAlerts > 0, dotLabel: 'unread alerts' },
    { key: 'audit', label: 'Audit log', icon: 'history' },
    { key: 'settings', label: 'Settings', icon: 'settings' }
  ];

  const app = document.getElementById('app');
  const sidebar = document.getElementById('sidebar');
  const mainCol = app.querySelector('.main-col');
  const main = document.getElementById('main');
  const navList = document.getElementById('nav-list');
  const collapseBtn = document.getElementById('collapse-btn');
  const menuBtn = document.getElementById('menu-btn');
  const topbarTitle = document.getElementById('topbar-title');
  const bellDot = document.getElementById('bell-dot');
  const bell = document.getElementById('bell');

  // Static icons in the HTML.
  document.querySelectorAll('[data-icon]').forEach((el) => el.replaceWith(icon(el.dataset.icon, 22)));

  // The skip link moves focus without touching the address, which holds the page.
  document.querySelector('.skip-link').addEventListener('click', (e) => {
    e.preventDefault();
    main.focus();
  });

  /* ---------- Navigation list ---------- */
  let activeKey = null;
  function renderNav() {
    const c = data.counts();
    clear(navList);
    for (const item of NAV) {
      const n = item.count ? item.count(c) : 0;
      const showDot = item.dot && item.dot(c);
      navList.append(h('li', null, h('a', {
        class: 'nav__link', href: '#/' + item.key, 'data-tip': item.label,
        'aria-current': item.key === activeKey ? 'page' : null
      },
      h('span', { class: 'nav__icon' }, icon(item.icon, 22)),
      h('span', { class: 'nav__label' }, item.label),
      n ? h('span', { class: 'nav__count' + (item.soft ? ' nav__count--soft' : '') }, h('span', { 'aria-hidden': 'true' }, String(n)), h('span', { class: 'sr-only' }, ', ' + item.countLabel(n))) : null,
      showDot ? h('span', { class: 'nav__dot' }, h('span', { class: 'sr-only' }, ', ' + item.dotLabel)) : null)));
    }
    bellDot.hidden = !c.openReports;
    bell.setAttribute('aria-label', c.openReports ? 'Reports, ' + c.openReports + ' open' : 'Reports');
  }
  function renderProfile() {
    const me = data.me();
    document.getElementById('profile-name').textContent = me.name;
    document.getElementById('profile-avatar').textContent = window.UI.fmt.initials(me.name);
  }
  data.subscribe(() => { renderNav(); renderProfile(); });

  /* ---------- The sidebar on each screen size ---------- */
  const desktop = window.matchMedia('(min-width: 1200px)');
  const phone = window.matchMedia('(max-width: 767.98px)');
  let railPreferred = false;
  try { railPreferred = localStorage.getItem('salusalo-admin:rail') === '1'; } catch (e) { /* storage blocked */ }
  let floating = null; // the open drawer (phone) or overlay (tablet)

  function setCollapseButton() {
    const label = collapseBtn.querySelector('.collapse-btn__label');
    const svgOld = collapseBtn.querySelector('svg');
    let text;
    let iconName;
    if (desktop.matches) {
      text = railPreferred ? 'Expand menu' : 'Collapse menu';
      iconName = railPreferred ? 'keyboardDoubleArrowRight' : 'keyboardDoubleArrowLeft';
      collapseBtn.setAttribute('aria-expanded', String(!railPreferred));
    } else {
      text = floating ? 'Close menu' : 'Expand menu';
      iconName = floating ? 'keyboardDoubleArrowLeft' : 'keyboardDoubleArrowRight';
      collapseBtn.setAttribute('aria-expanded', String(!!floating));
    }
    label.textContent = text;
    if (svgOld) svgOld.replaceWith(icon(iconName, 22));
    collapseBtn.title = text;
  }
  function applyMode() {
    closeFloating(false);
    app.classList.toggle('is-rail', desktop.matches ? railPreferred : !phone.matches);
    sidebar.inert = phone.matches;
    setCollapseButton();
  }
  function openFloating() {
    if (floating) return;
    const onPhone = phone.matches;
    const scrim = h('div', { class: 'scrim' });
    document.body.append(scrim);
    scrim.addEventListener('click', () => closeFloating(true));
    requestAnimationFrame(() => scrim.classList.add('is-visible'));
    sidebar.inert = false;
    if (onPhone) app.classList.add('is-drawer-open');
    else { app.classList.remove('is-rail'); app.classList.add('is-overlay'); }
    mainCol.inert = true;
    sidebar.setAttribute('role', 'dialog');
    sidebar.setAttribute('aria-modal', 'true');
    const layer = { el: sidebar, onEscape: () => closeFloating(true) };
    window.UI.pushLayer(layer);
    floating = { scrim, layer, returnTo: onPhone ? menuBtn : collapseBtn };
    menuBtn.setAttribute('aria-expanded', 'true');
    setCollapseButton();
    const current = sidebar.querySelector('[aria-current="page"]') || sidebar.querySelector('a');
    setTimeout(() => current && current.focus(), 60);
  }
  function closeFloating(restoreFocus) {
    if (!floating) return;
    const { scrim, layer, returnTo } = floating;
    floating = null;
    window.UI.popLayer(layer);
    scrim.classList.remove('is-visible');
    setTimeout(() => scrim.remove(), 200);
    app.classList.remove('is-drawer-open', 'is-overlay');
    if (!desktop.matches && !phone.matches) app.classList.add('is-rail');
    if (phone.matches) sidebar.inert = true;
    mainCol.inert = false;
    sidebar.removeAttribute('role');
    sidebar.removeAttribute('aria-modal');
    menuBtn.setAttribute('aria-expanded', 'false');
    setCollapseButton();
    if (restoreFocus && returnTo) returnTo.focus();
  }
  collapseBtn.addEventListener('click', () => {
    if (desktop.matches) {
      railPreferred = !railPreferred;
      try { localStorage.setItem('salusalo-admin:rail', railPreferred ? '1' : '0'); } catch (e) { /* storage blocked */ }
      app.classList.toggle('is-rail', railPreferred);
      setCollapseButton();
    } else if (floating) closeFloating(true);
    else openFloating();
  });
  menuBtn.addEventListener('click', () => (floating ? closeFloating(true) : openFloating()));
  desktop.addEventListener('change', applyMode);
  phone.addEventListener('change', applyMode);

  /* ---------- Pages and the address bar ---------- */
  let current = { key: null, page: null };
  let prevHash = null;
  let currHash = location.hash;
  let firstRoute = true;
  let started = false;

  function parse() {
    const raw = decodeURIComponent(location.hash.replace(/^#\/?/, ''));
    const [page, id, sub] = raw.split('/');
    const key = NAV.some((n) => n.key === page) ? page : 'overview';
    return { page: key, id: id || null, sub: sub || null };
  }
  function route() {
    const r = parse();
    window.UI.closeMenu();
    closeFloating(false);
    if (current.key !== r.page) {
      if (current.page && current.page.unmount) current.page.unmount();
      window.UI.closePanel();
      clear(main);
      activeKey = r.page;
      current = { key: r.page, page: Pages[r.page] };
      renderNav();
      document.title = current.page.title + ' · Salu-Salo Admin';
      topbarTitle.textContent = current.page.title;
      current.page.mount(main, r);
      window.scrollTo(0, 0);
      if (!firstRoute) main.focus({ preventScroll: true });
    }
    firstRoute = false;
    if (current.page.update) current.page.update(r);
  }
  window.addEventListener('hashchange', () => { prevHash = currHash; currHash = location.hash; if (started) route(); });

  const norm = (hash) => (hash || '').replace(/^#\/?/, '');
  window.App = {
    go(hash) {
      if (norm(location.hash) === norm(hash)) route();
      else location.hash = hash;
    },
    /** Closes a details view: back to the list if we came from it, else replace. */
    closeTo(hash) {
      if (prevHash !== null && norm(prevHash) === norm(hash)) history.back();
      else location.replace('#/' + norm(hash));
    },
    /** A page other pages can prefill: consumed once by the page's mount(). */
    intent: {}
  };

  applyMode();
  /** Called by auth.js once the signed-in account passed the admin check. */
  window.App.start = function () {
    started = true;
    renderProfile();
    route();
  };
})();
