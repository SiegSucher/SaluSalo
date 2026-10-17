/* Users: everyone on Salu-Salo and what they've done; suspend and restore (§36). */
window.Pages = window.Pages || {};
window.Pages.users = (function () {
  'use strict';
  const UI = window.UI;
  const { h, icon, fmt, btn, badge, avatar, identity, notice, confirm, toast, dl, section, empty, errorState, skeletonPanel } = UI;
  const data = window.SaluData;

  let s = null; // page state while mounted

  const ROLE_OPTIONS = [['ALL', 'Everyone'], ['DONOR', 'Has posted'], ['RECIPIENT', 'Never posted'], ['ADMIN', 'Admins'], ['SUSPENDED', 'Suspended']];

  function youTag(row) { return row.isYou ? h('span', { class: 'you-tag' }, 'You') : null; }
  function emailBadge(u) {
    return u.emailVerified ? badge('verification', 'VERIFIED', 'Email verified') : badge('verification', 'NONE', 'Email not verified');
  }

  function suspendedBadge(u) { return u.suspendedAt ? badge('account', 'SUSPENDED') : null; }

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
        { label: 'Role', cell: (u) => h('div', { class: 'badge-row' }, badge('role', u.role), suspendedBadge(u)) },
        { label: 'Email check', cell: emailBadge },
        { label: 'Reports', className: 'cell-num', cell: (u) => u.openReports ? UI.flagBadge(u.openReports) : h('span', { class: 'muted' }, 'None') },
        { label: h('span', { class: 'sr-only' }, 'Actions'), className: 'cell-actions', cell: (u) => rowMenu(u) }
      ],
      card: (u) => UI.rowCard({
        visual: avatar(u), title: u.name, href: '#/users/' + u.id,
        meta: { sub: u.email, items: [{ icon: 'event', text: 'Joined ' + fmt.date(u.joinedAt) }] },
        badges: [badge('role', u.role), suspendedBadge(u), emailBadge(u), u.openReports ? UI.flagBadge(u.openReports) : null]
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
          h('div', { class: 'badge-row' }, badge('role', u.role), suspendedBadge(u), emailBadge(u)),
          h('span', { class: 'muted' }, 'Joined ' + fmt.date(u.joinedAt)))),
      u.suspendedAt ? notice({
        tone: 'error', iconName: 'block',
        title: 'Suspended ' + fmt.date(u.suspendedAt) + (u.suspension ? ' by ' + u.suspension.byName : ''),
        text: u.suspension ? u.suspension.details : data.text.suspension[u.suspensionReason] || 'Suspended'
      }) : null,
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
    const foot = [];
    if (u.stats.listings) foot.push(btn({ label: 'View listings', icon: 'lunchDining', variant: 'secondary', onClick: () => viewListings(u) }));
    // Never for an admin or yourself (the server refuses those too).
    if (u.role !== 'ADMIN' && !u.isYou) {
      foot.push(u.suspendedAt
        ? btn({ label: 'Restore account', icon: 'restartAlt', variant: 'secondary', onClick: () => restoreUser(u) })
        : btn({ label: 'Suspend account', icon: 'block', variant: 'danger', onClick: () => suspendUser(u) }));
    }
    UI.openPanel({ label: 'User', title: [u.name, youTag(u)], sub: u.email, body, foot: foot.length ? foot : null, onClose });
  }
  function miniStat(value, label) {
    return h('div', { class: 'mini-stat' }, h('span', { class: 'mini-stat__value' }, fmt.num(value)), h('span', { class: 'mini-stat__label' }, label));
  }

  // The server's own refusal (P0001) is shown as it is; anything else gets a general text.
  const serverText = (fallback) => (err) => (err && err.code === 'P0001' && err.message) || fallback;

  async function suspendUser(u) {
    const c = u.suspendCounts || { listings: 0, reservations: 0 };
    let result = null;
    const ok = await confirm({
      title: 'Suspend ' + u.name + '?',
      text: 'They’ll be locked out of Salu-Salo until an admin restores the account. This will remove ' +
        fmt.plural(c.listings, 'active listing') + ' and cancel ' + fmt.plural(c.reservations, 'open reservation') + '.',
      confirmLabel: 'Suspend account', tone: 'danger', iconName: 'block',
      choice: { label: 'Reason they’ll see', placeholder: 'Choose a reason', options: Object.entries(data.text.suspension) },
      reason: { label: 'Private note', required: true, maxLength: 1000, placeholder: 'What happened, for other admins', hint: 'Only admins see this note.' },
      errorText: serverText('Unable to suspend this account. Please try again.'),
      action: async (note, reason) => { result = await data.suspendUser(u.id, reason, note); }
    });
    if (ok) {
      const bits = [];
      if (result && result.removedListings) bits.push(fmt.plural(result.removedListings, 'listing') + ' removed');
      if (result && result.cancelledReservations) bits.push(fmt.plural(result.cancelledReservations, 'reservation') + ' cancelled');
      toast('Account suspended.', { sub: bits.length ? bits.join(' · ') + '.' : null });
      refreshAfterAction(u.id);
    }
  }
  async function restoreUser(u) {
    const ok = await confirm({
      title: 'Restore ' + u.name + '’s account?',
      text: 'They can sign in and use Salu-Salo again. Removed listings and cancelled reservations stay as they are.',
      confirmLabel: 'Restore account', tone: 'primary', iconName: 'restartAlt',
      reason: { label: 'Note', required: false, maxLength: 1000, placeholder: 'For example: appeal accepted' },
      errorText: serverText('Unable to restore this account. Please try again.'),
      action: (note) => data.restoreUser(u.id, note)
    });
    if (ok) { toast('Account restored.'); refreshAfterAction(u.id); }
  }
  function refreshAfterAction(id) {
    if (!s) return;
    s.list.reload();
    if (UI.panelIsOpen() && s.panelFor === id) openUser(id, true);
  }

  function update(route) {
    if (!s) return;
    if (route.id && route.id !== 'requests') openUser(route.id);
    else if (UI.panelIsOpen()) { s.panelFor = null; UI.closePanel(); }
  }

  return { title: 'Users', mount, update, unmount() { s = null; } };
})();
