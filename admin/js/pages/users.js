/* Users: everyone on Salu-Salo and what they've done. Read-only in this version. */
window.Pages = window.Pages || {};
window.Pages.users = (function () {
  'use strict';
  const UI = window.UI;
  const { h, icon, fmt, btn, badge, avatar, identity, notice, dl, section, empty, errorState, skeletonPanel } = UI;
  const data = window.SaluData;

  let s = null; // page state while mounted

  const ROLE_OPTIONS = [['ALL', 'Everyone'], ['DONOR', 'Has posted'], ['RECIPIENT', 'Never posted'], ['ADMIN', 'Admins']];

  function youTag(row) { return row.isYou ? h('span', { class: 'you-tag' }, 'You') : null; }
  function emailBadge(u) {
    return u.emailVerified ? badge('verification', 'VERIFIED', 'Email verified') : badge('verification', 'NONE', 'Email not verified');
  }

  function mount(main) {
    s = { panelFor: null };
    s.list = window.ListView({
      what: 'users', noun: 'user', caption: 'Users',
      defaults: { q: '', role: 'ALL' },
      filters: [
        { key: 'q', type: 'search', label: 'Search users', placeholder: 'Search by name or email' },
        { key: 'role', type: 'select', label: 'Show', options: ROLE_OPTIONS }
      ],
      fetch: (q) => data.users(q),
      open: (row) => window.App.go('#/users/' + row.id),
      columns: [
        { label: 'User', className: 'cell-wide', cell: (u) => identity(avatar(u), [u.name, youTag(u)], 'Joined ' + fmt.date(u.joinedAt), '#/users/' + u.id) },
        { label: 'Email', hide: 'tablet', cell: (u) => h('span', { class: 'muted' }, u.email) },
        { label: 'Role', cell: (u) => badge('role', u.role) },
        { label: 'Email check', cell: emailBadge },
        { label: 'Reports', className: 'cell-num', cell: (u) => u.openReports ? UI.flagBadge(u.openReports) : h('span', { class: 'muted' }, 'None') },
        { label: h('span', { class: 'sr-only' }, 'Actions'), className: 'cell-actions', cell: (u) => rowMenu(u) }
      ],
      card: (u) => UI.rowCard({
        visual: avatar(u), title: u.name, href: '#/users/' + u.id,
        meta: { sub: u.email, items: [{ icon: 'event', text: 'Joined ' + fmt.date(u.joinedAt) }] },
        badges: [badge('role', u.role), emailBadge(u), u.openReports ? UI.flagBadge(u.openReports) : null]
      }),
      emptyState: (q, filtered) => filtered
        ? { iconName: 'search', title: q.q ? 'No users match “' + q.q + '”' : 'No users match this filter', text: 'Try another name or email, or show everyone.' }
        : { iconName: 'group', title: 'No users yet', text: 'People appear here once they create a Salu-Salo account.' }
    });
    main.append(
      UI.pageHead({ title: 'Users', sub: 'Everyone with a Salu-Salo account. “Donor” means they have posted food.' }),
      s.list.el);
    s.list.load();
  }

  function rowMenu(u) {
    return UI.menuButton('Actions for ' + u.name, () => [
      { label: 'View details', icon: 'visibility', onClick: () => window.App.go('#/users/' + u.id) },
      u.role === 'DONOR' ? { label: 'View listings', icon: 'lunchDining', onClick: () => viewListings(u) } : null
    ]);
  }
  function viewListings(u) {
    window.App.intent.listingsDonor = { id: u.id, name: u.name };
    window.App.go('#/listings');
  }

  async function openUser(id, keep) {
    s.panelFor = id;
    const onClose = () => window.App.closeTo('#/users');
    if (!keep) UI.openPanel({ label: 'User', title: 'Loading…', body: skeletonPanel(), onClose });
    let u;
    try { u = await data.user(id); } catch (err) {
      UI.openPanel({ label: 'User', title: 'Couldn’t load this user', body: errorState(() => openUser(id, true), 'this user'), onClose });
      return;
    }
    if (!s || s.panelFor !== id) return;
    if (!u) { UI.openPanel({ label: 'User', title: 'User not found', body: empty({ iconName: 'personOff', title: 'This account doesn’t exist', text: 'It may have been deleted, or the link is wrong.' }), onClose }); return; }

    const body = [
      h('div', { class: 'panel-hero' }, avatar(u, 'lg'),
        h('div', { class: 'panel-hero__text' },
          h('div', { class: 'badge-row' }, badge('role', u.role), emailBadge(u)),
          h('span', { class: 'muted' }, 'Joined ' + fmt.date(u.joinedAt)))),
      u.openReports.length ? notice({ tone: 'warning', iconName: 'flag', title: fmt.plural(u.openReports.length, 'open report') + ' about them or their listings', text: u.openReports.map((r) => r.reasonLabel).join(', '), action: h('a', { class: 'btn btn--secondary btn--sm', href: '#/reports/' + u.openReports[0].id }, 'Review') }) : null,
      section('Profile', dl([
        ['Email', u.email],
        ['Role', data.text.roles[u.role]],
        ['Joined', fmt.dateTime(u.joinedAt)]
      ])),
      section('Activity', h('div', { class: 'mini-stats' },
        miniStat(u.stats.listings, 'Listings posted'),
        miniStat(u.stats.reservations, 'Reservations made'),
        miniStat(u.stats.completed, 'Completed pickups')),
      h('p', { class: 'muted', style: { fontSize: 'var(--t-sm)' } },
        fmt.plural(u.stats.activeListings, 'listing') + ' up now · ' + fmt.plural(u.stats.handedOver, 'reservation') + ' handed over to others · ' + fmt.plural(u.stats.reportsAgainst, 'report') + ' about them')),
      section('Recent activity', u.events.length
        ? h('ul', { class: 'list' }, u.events.map((ev) => {
          const inner = [h('span', { class: 'icon-bubble icon-bubble--neutral' }, icon(ev.icon, 18)),
            h('span', { class: 'list__main' }, h('span', { class: 'list__title' }, ev.text), h('span', { class: 'list__meta' }, (ev.meta ? ev.meta + ' · ' : '') + fmt.relative(ev.at)))];
          return h('li', null, ev.link ? h('a', { class: 'list__item list__item--button', href: ev.link, style: { paddingInline: '0' } }, inner) : h('div', { class: 'list__item', style: { paddingInline: '0' } }, inner));
        }))
        : h('p', { class: 'muted' }, 'Nothing yet.'))
    ];
    const foot = u.stats.listings ? [btn({ label: 'View listings', icon: 'lunchDining', variant: 'secondary', onClick: () => viewListings(u) })] : null;
    UI.openPanel({ label: 'User', title: [u.name, youTag(u)], sub: u.email, body, foot, onClose });
  }
  function miniStat(value, label) {
    return h('div', { class: 'mini-stat' }, h('span', { class: 'mini-stat__value' }, fmt.num(value)), h('span', { class: 'mini-stat__label' }, label));
  }

  function update(route) {
    if (!s) return;
    if (route.id && route.id !== 'requests') openUser(route.id);
    else if (UI.panelIsOpen()) { s.panelFor = null; UI.closePanel(); }
  }

  return { title: 'Users', mount, update, unmount() { s = null; } };
})();
