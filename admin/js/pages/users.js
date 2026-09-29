/* Users: everyone on Salu-Salo, their details, and the verification requests queue. */
window.Pages = window.Pages || {};
window.Pages.users = (function () {
  'use strict';
  const UI = window.UI;
  const { h, icon, clear, fmt, btn, badge, avatar, identity, notice, dl, section, empty, errorState, skeletonPanel, confirm, toast, busy } = UI;
  const data = window.SaluData;

  let s = null; // page state while mounted

  const ROLE_OPTIONS = [['ALL', 'All roles'], ['DONOR', 'Donors'], ['RECIPIENT', 'Recipients'], ['ADMIN', 'Admins']];
  const VERIF_OPTIONS = [['ALL', 'Any'], ['VERIFIED', 'Verified'], ['PENDING', 'Pending'], ['NONE', 'Not verified'], ['REJECTED', 'Rejected']];
  const ACCOUNT_OPTIONS = [['ALL', 'Any'], ['ACTIVE', 'Active'], ['SUSPENDED', 'Suspended']];

  function youTag(row) { return row.isYou ? h('span', { class: 'you-tag' }, 'You') : null; }
  function userMeta(u) { return u.org ? u.kind + ' · ' + u.city : u.city; }

  function mount(main, route) {
    s = { tab: route.id === 'requests' || route.sub === 'verify' ? 'requests' : 'all', panelFor: null };
    const counts = data.counts();
    s.tabs = h('div');
    s.body = h('div', { role: 'tabpanel', class: 'data' });
    s.list = window.ListView({
      what: 'users', noun: 'user', caption: 'Users',
      defaults: { q: '', role: 'ALL', verification: 'ALL', account: 'ALL' },
      filters: [
        { key: 'q', type: 'search', label: 'Search users', placeholder: 'Search by name or email' },
        { key: 'role', type: 'select', label: 'Role', options: ROLE_OPTIONS },
        { key: 'verification', type: 'select', label: 'Verification', options: VERIF_OPTIONS },
        { key: 'account', type: 'select', label: 'Account', options: ACCOUNT_OPTIONS }
      ],
      fetch: (q) => data.users(q),
      open: (row) => window.App.go('#/users/' + row.id),
      columns: [
        { label: 'User', className: 'cell-wide', cell: (u) => identity(avatar(u), [u.name, youTag(u)], userMeta(u), '#/users/' + u.id) },
        { label: 'Email', hide: 'tablet', cell: (u) => h('span', { class: 'muted' }, u.email) },
        { label: 'Role', cell: (u) => badge('role', u.role) },
        { label: 'Verification', cell: (u) => badge('verification', u.verification) },
        { label: 'Account', cell: (u) => badge('account', u.accountStatus) },
        { label: 'Joined', className: 'cell-muted', cell: (u) => fmt.date(u.joinedAt) },
        { label: h('span', { class: 'sr-only' }, 'Actions'), className: 'cell-actions', cell: (u) => rowMenu(u) }
      ],
      card: (u) => UI.rowCard({
        visual: avatar(u), title: u.name, href: '#/users/' + u.id,
        meta: { sub: u.email, items: [{ icon: 'event', text: 'Joined ' + fmt.date(u.joinedAt) }, { icon: 'locationOn', text: u.city }] },
        badges: [badge('role', u.role), badge('verification', u.verification), u.accountStatus === 'SUSPENDED' ? badge('account', u.accountStatus) : null]
      }),
      emptyState: (q, filtered) => filtered
        ? { iconName: 'search', title: q.q ? 'No users match “' + q.q + '”' : 'No users match these filters', text: 'Try another name or email, or clear the filters to see everyone.' }
        : { iconName: 'group', title: 'No users yet', text: 'People appear here once they create a Salu-Salo account.' }
    });
    main.append(
      UI.pageHead({ title: 'Users', sub: fmt.plural(counts.pendingVerifications, 'verification request') + ' waiting · accounts, roles and verification' }),
      s.tabs, s.body);
    renderTabs();
    showTab();
  }

  function renderTabs() {
    const c = data.counts();
    clear(s.tabs).append(UI.tabs([
      { key: 'all', label: 'All users', icon: 'group' },
      { key: 'requests', label: 'Verification requests', icon: 'verified', count: c.pendingVerifications }
    ], s.tab, (key) => window.App.go(key === 'requests' ? '#/users/requests' : '#/users'), 'User lists'));
    s.body.setAttribute('aria-labelledby', 'tab-' + s.tab);
  }
  function showTab() {
    clear(s.body);
    if (s.tab === 'all') {
      s.body.append(s.list.el);
      s.list.load();
    } else {
      s.queue = h('div', { class: 'data' }, UI.skeletonRows(4));
      s.body.append(
        notice({ tone: 'info', text: 'Donors ask to be verified by sending documents from the app. Check that the documents match the account, then approve or reject.' }),
        s.queue);
      loadQueue();
    }
  }
  async function loadQueue() {
    const target = s.queue;
    let rows;
    try { rows = await data.verificationQueue(); } catch (err) {
      if (s && s.queue === target) clear(target).append(h('div', { class: 'card' }, errorState(loadQueue, 'verification requests')));
      return;
    }
    if (!s || s.queue !== target) return;
    clear(target);
    if (!rows.length) {
      target.append(h('div', { class: 'card' }, empty({ iconName: 'verified', title: 'No verification requests', text: 'You’re all caught up. When a donor sends documents to get verified, the request appears here.' })));
      return;
    }
    target.append(...UI.dataView({
      caption: 'Verification requests, oldest first',
      rows,
      open: (u) => window.App.go('#/users/' + u.id + '/verify'),
      columns: [
        { label: 'Applicant', className: 'cell-wide', cell: (u) => identity(avatar(u), u.name, u.requestType, '#/users/' + u.id + '/verify') },
        { label: 'Submitted', className: 'cell-muted', cell: (u) => fmt.relative(u.submittedAt) },
        { label: 'Email', cell: (u) => u.emailVerified ? badge('verification', 'VERIFIED', 'Email verified') : badge('verification', 'NONE', 'Email not verified') },
        { label: 'Listings posted', className: 'cell-num', cell: (u) => fmt.num(u.listings) },
        { label: 'Reports', className: 'cell-num', cell: (u) => u.openReports ? UI.flagBadge(u.openReports) : h('span', { class: 'muted' }, 'None') },
        { label: h('span', { class: 'sr-only' }, 'Action'), className: 'cell-actions', cell: (u) => h('a', { class: 'btn btn--secondary btn--sm', href: '#/users/' + u.id + '/verify', 'aria-label': 'Review ' + u.name }, 'Review') }
      ],
      card: (u) => UI.rowCard({
        visual: avatar(u), title: u.name, href: '#/users/' + u.id + '/verify',
        meta: { sub: u.requestType, items: [{ icon: 'schedule', text: 'Sent ' + fmt.relative(u.submittedAt) }, { icon: 'lunchDining', text: fmt.plural(u.listings, 'listing') }] },
        badges: [u.emailVerified ? badge('verification', 'VERIFIED', 'Email verified') : badge('verification', 'NONE', 'Email not verified')]
      })
    }));
    target.append(h('p', { class: 'results-meta' }, fmt.plural(rows.length, 'request') + ' waiting, oldest first'));
  }

  function rowMenu(u) {
    return UI.menuButton('Actions for ' + u.name, () => [
      { label: 'View details', icon: 'visibility', onClick: () => window.App.go('#/users/' + u.id) },
      u.verification === 'PENDING' ? { label: 'Review verification', icon: 'verified', onClick: () => window.App.go('#/users/' + u.id + '/verify') } : null,
      u.role === 'DONOR' ? { label: 'View listings', icon: 'lunchDining', onClick: () => viewListings(u) } : null,
      u.role !== 'ADMIN' ? '-' : null,
      u.role !== 'ADMIN' && u.accountStatus === 'ACTIVE' ? { label: 'Suspend account', icon: 'block', danger: true, onClick: () => suspend(u) } : null,
      u.accountStatus === 'SUSPENDED' ? { label: 'Restore account', icon: 'restartAlt', onClick: () => restore(u) } : null
    ]);
  }
  function viewListings(u) {
    window.App.intent.listingsDonor = { id: u.id, name: u.name };
    window.App.go('#/listings');
  }

  /* ---------- Actions ---------- */
  async function suspend(u, reportId) {
    const ok = await confirm({
      title: 'Suspend this account?',
      text: 'Are you sure you want to suspend this account? ' + u.name + ' won’t be able to sign in or use Salu-Salo until an admin restores it.',
      confirmLabel: 'Suspend user', tone: 'danger', iconName: 'block',
      reason: { label: 'Reason', required: true, placeholder: 'For example: asked recipients for payment before pickup' },
      errorText: 'Unable to suspend this account. Please try again.',
      action: async (reason) => { s.lastResult = await data.suspendUser(u.id, reason, reportId); }
    });
    if (ok) {
      const closed = s && s.lastResult ? s.lastResult.closedReports : 0;
      toast('Account suspended.', { sub: closed ? fmt.plural(closed, 'open report') + ' about them marked resolved.' : null });
      refreshAfterAction();
    }
  }
  async function restore(u) {
    const ok = await confirm({
      title: 'Restore this account?',
      text: u.name + ' will be able to sign in and use Salu-Salo again.',
      confirmLabel: 'Restore account', tone: 'primary', iconName: 'restartAlt',
      reason: { label: 'Note', required: false, placeholder: 'Why the account is being restored' },
      errorText: 'Unable to restore this account. Please try again.',
      action: (note) => data.restoreUser(u.id, note)
    });
    if (ok) { toast('Account restored.'); refreshAfterAction(); }
  }
  async function verifyWithout(u) {
    const ok = await confirm({
      title: 'Verify without documents?',
      text: 'Only do this if you’ve confirmed who ' + u.name + ' is another way, for example in person. They’ll get a Verified badge.',
      confirmLabel: 'Verify account', tone: 'primary', iconName: 'verified',
      reason: { label: 'How did you check?', required: true, placeholder: 'For example: met the owner at the store' },
      errorText: 'Unable to verify this user. Please try again.',
      action: (note) => data.verifyWithoutRequest(u.id, note)
    });
    if (ok) { toast('User successfully verified.'); refreshAfterAction(); }
  }
  async function approve(u, button, others) {
    try {
      await busy([button].concat(others || []), () => data.approveVerification(u.id));
      toast('User successfully verified.', { sub: u.name + ' now shows a Verified badge.' });
      refreshAfterAction();
    } catch (err) {
      toast('Unable to verify this user. Please try again.', { tone: 'error' });
    }
  }
  async function reject(u) {
    const ok = await confirm({
      title: 'Reject this request?',
      text: 'Are you sure you want to reject this verification request? ' + u.name + ' can send new documents later.',
      confirmLabel: 'Reject', tone: 'danger', iconName: 'highlightOff',
      reason: { label: 'Reason', required: true, placeholder: 'For example: the permit photo is unreadable', hint: 'Saved in the audit log. Tell them what to fix if they try again.' },
      errorText: 'Unable to reject this request. Please try again.',
      action: (reason) => data.rejectVerification(u.id, reason)
    });
    if (ok) { toast('Verification request rejected.'); refreshAfterAction(); }
  }
  function refreshAfterAction() {
    if (!s) return;
    renderTabs();
    if (s.tab === 'all') s.list.reload(); else loadQueue();
    if (UI.panelIsOpen() && s.panelFor) {
      if (s.panelFor.kind === 'verify') openVerification(s.panelFor.id, true);
      else openUser(s.panelFor.id, true);
    }
  }

  /* ---------- Details ---------- */
  const listRoute = () => (s && s.tab === 'requests' ? '#/users/requests' : '#/users');
  function panelShell(kind, id, label) {
    s.panelFor = { kind, id };
    return UI.openPanel({ label, title: 'Loading…', body: skeletonPanel(), onClose: () => window.App.closeTo(listRoute()) });
  }
  async function openUser(id, keep) {
    const panel = keep ? { update: (o) => UI.openPanel(Object.assign({ onClose: () => window.App.closeTo(listRoute()) }, o)) } : panelShell('user', id, 'User');
    s.panelFor = { kind: 'user', id };
    let u;
    try { u = await data.user(id); } catch (err) {
      panel.update({ label: 'User', title: 'Couldn’t load this user', body: errorState(() => openUser(id, true), 'this user'), foot: null });
      return;
    }
    if (!s || !s.panelFor || s.panelFor.id !== id || s.panelFor.kind !== 'user') return;
    if (!u) { panel.update({ label: 'User', title: 'User not found', body: empty({ iconName: 'personOff', title: 'This account doesn’t exist', text: 'It may have been deleted, or the link is wrong.' }), foot: null }); return; }

    const body = [
      h('div', { class: 'panel-hero' }, avatar(u, 'lg'),
        h('div', { class: 'panel-hero__text' },
          h('div', { class: 'badge-row' }, badge('role', u.role), badge('verification', u.verification), badge('account', u.accountStatus)),
          h('span', { class: 'muted' }, (u.org ? u.kind + ' · ' : '') + 'Joined ' + fmt.date(u.joinedAt)))),
      u.suspension ? notice({ tone: 'error', title: 'Suspended ' + fmt.relative(u.suspension.at) + ' by ' + u.suspension.byName, text: u.suspension.reason }) : null,
      u.verification === 'PENDING' ? notice({ tone: 'warning', title: 'Waiting for verification', text: u.request.type + ' · sent ' + fmt.relative(u.request.submittedAt) + '.', action: h('a', { class: 'btn btn--secondary btn--sm', href: '#/users/' + u.id + '/verify' }, 'Review') }) : null,
      u.openReports.length ? notice({ tone: 'warning', iconName: 'flag', title: fmt.plural(u.openReports.length, 'open report') + ' about them or their listings', text: u.openReports.map((r) => r.reasonLabel).join(', '), action: h('a', { class: 'btn btn--secondary btn--sm', href: '#/reports/' + u.openReports[0].id }, 'Review') }) : null,
      section('Profile', dl([
        ['Email', h('span', null, u.email + ' ', u.emailVerified ? badge('verification', 'VERIFIED', 'Email verified') : badge('verification', 'NONE', 'Not verified'))],
        ['Role', ({ DONOR: 'Donor', RECIPIENT: 'Recipient', ADMIN: 'Admin' })[u.role]],
        ['Account status', u.accountStatus === 'ACTIVE' ? 'Active' : 'Suspended'],
        ['Verification', ({ VERIFIED: 'Verified', PENDING: 'Pending', NONE: 'Not verified', REJECTED: 'Rejected' })[u.verification] + (u.request && u.request.decidedAt ? ' · ' + fmt.date(u.request.decidedAt) + (u.request.decidedByName ? ' by ' + u.request.decidedByName : '') : '')],
        ['Joined', fmt.dateTime(u.joinedAt)],
        ['Last active', fmt.relative(u.lastActiveAt)],
        ['Area', u.barangay + ', ' + u.city]
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
    foot.push(h('span', { class: 'spacer' }));
    if (u.role !== 'ADMIN' && u.accountStatus === 'ACTIVE') foot.push(btn({ label: 'Suspend account', icon: 'block', variant: 'danger-outline', onClick: () => suspend(u) }));
    if (u.accountStatus === 'SUSPENDED') foot.push(btn({ label: 'Restore account', icon: 'restartAlt', variant: 'secondary', onClick: () => restore(u) }));
    if (u.verification === 'PENDING') foot.push(btn({ label: 'Review verification', icon: 'verified', variant: 'primary', href: '#/users/' + u.id + '/verify' }));
    else if (u.verification !== 'VERIFIED' && u.role !== 'ADMIN') foot.push(btn({ label: 'Verify account', icon: 'verified', variant: 'primary', onClick: () => verifyWithout(u) }));
    panel.update({ label: 'User', title: [u.name, youTag(u)], sub: u.email, body, foot });
  }
  function miniStat(value, label) {
    return h('div', { class: 'mini-stat' }, h('span', { class: 'mini-stat__value' }, fmt.num(value)), h('span', { class: 'mini-stat__label' }, label));
  }

  async function openVerification(id, keep) {
    const panel = keep ? { update: (o) => UI.openPanel(Object.assign({ onClose: () => window.App.closeTo(listRoute()) }, o)) } : panelShell('verify', id, 'Verification request');
    s.panelFor = { kind: 'verify', id };
    let u;
    try { u = await data.user(id); } catch (err) {
      panel.update({ label: 'Verification request', title: 'Couldn’t load this request', body: errorState(() => openVerification(id, true), 'this request'), foot: null });
      return;
    }
    if (!s || !s.panelFor || s.panelFor.id !== id || s.panelFor.kind !== 'verify') return;
    if (!u || !u.request) {
      panel.update({ label: 'Verification request', title: u ? u.name : 'Not found', body: empty({ iconName: 'verified', title: 'No verification request', text: u ? u.name + ' hasn’t sent documents to be verified.' : 'This account doesn’t exist.' }), foot: u ? [btn({ label: 'View user', variant: 'secondary', href: '#/users/' + u.id })] : null });
      return;
    }
    const rq = u.request;
    const decided = u.verification !== 'PENDING';
    const body = [
      decided ? notice({
        tone: u.verification === 'VERIFIED' ? 'success' : 'error',
        title: u.verification === 'VERIFIED' ? 'Approved' + (rq.decidedByName ? ' by ' + rq.decidedByName : '') : 'Rejected' + (rq.decidedByName ? ' by ' + rq.decidedByName : ''),
        text: (rq.decidedAt ? fmt.dateTime(rq.decidedAt) : '') + (rq.decisionNote ? ' · ' + rq.decisionNote : '')
      }) : notice({ tone: 'warning', iconName: 'pending', title: 'Waiting for your decision', text: 'Check that the documents match this account, then approve or reject.' }),
      section('Who is being verified',
        h('div', { class: 'panel-hero' }, avatar(u, 'lg'),
          h('div', { class: 'panel-hero__text' }, h('span', { class: 'panel-hero__name' }, u.name),
            h('div', { class: 'badge-row' }, badge('role', u.role), badge('account', u.accountStatus)))),
        dl([
          ['Email', h('span', null, u.email + ' ', u.emailVerified ? badge('verification', 'VERIFIED', 'Email verified') : badge('verification', 'NONE', 'Not verified'))],
          ['Joined', fmt.date(u.joinedAt) + ' (' + fmt.relative(u.joinedAt) + ')'],
          ['Area', u.barangay + ', ' + u.city]
        ])),
      section('What they sent',
        dl([
          ['Request', rq.type],
          rq.businessName ? ['Business name', rq.businessName] : [null, null],
          rq.businessType ? ['Business type', rq.businessType] : [null, null],
          ['Address', rq.address],
          ['Sent', fmt.dateTime(rq.submittedAt)]
        ].filter((p) => p[0])),
        rq.documents && rq.documents.length ? h('div', { class: 'doc-list' }, rq.documents.map((d) => h('div', { class: 'doc' },
          icon(d.file.endsWith('.pdf') ? 'description' : 'idCard', 28),
          h('strong', null, d.name),
          h('span', null, d.detail + ' · ends in ' + d.last4),
          h('span', null, 'Preview not available in sample data')))) : null,
        rq.note ? h('blockquote', { class: 'quote' }, rq.note, h('span', { class: 'quote__source' }, 'Note from ' + u.name)) : null),
      section('Supporting information',
        h('div', { class: 'mini-stats' },
          miniStat(u.stats.listings, 'Listings posted'),
          miniStat(u.stats.handedOver, 'Handed over'),
          miniStat(u.stats.reportsAgainst + u.stats.reportsOnListings, 'Reports')),
        u.openReports.length ? notice({ tone: 'warning', iconName: 'flag', text: 'There ' + (u.openReports.length === 1 ? 'is 1 open report' : 'are ' + u.openReports.length + ' open reports') + ' about them or their listings. Consider reviewing it first.' }) : null,
        !u.emailVerified ? notice({ tone: 'neutral', text: 'Their email address isn’t verified yet.' }) : null),
      section('What approving does', h('p', { class: 'muted' }, 'Approving shows a Verified badge on ' + u.name + '’s profile and listings. Rejecting keeps the account as it is; they can send new documents. In this sample, neither changes the app.'))
    ];
    let foot = null;
    if (!decided) {
      const rejectBtn = btn({ label: 'Reject', icon: 'close', variant: 'danger-outline', onClick: () => reject(u) });
      const approveBtn = btn({ label: 'Approve', icon: 'check', variant: 'primary' });
      approveBtn.addEventListener('click', () => approve(u, approveBtn, [rejectBtn]));
      foot = [btn({ label: 'View user', variant: 'ghost', href: '#/users/' + u.id }), h('span', { class: 'spacer' }), rejectBtn, approveBtn];
    } else {
      foot = [btn({ label: 'View user', variant: 'secondary', href: '#/users/' + u.id })];
    }
    panel.update({ label: 'Verification request', title: u.name, sub: rq.type + ' · sent ' + fmt.relative(rq.submittedAt), body, foot });
  }

  function update(route) {
    if (!s) return;
    const wantTab = route.id === 'requests' ? 'requests' : route.id ? s.tab : 'all';
    if (wantTab !== s.tab) { s.tab = wantTab; renderTabs(); showTab(); }
    if (route.id && route.id.startsWith('U-')) {
      if (route.sub === 'verify') openVerification(route.id);
      else openUser(route.id);
    } else if (UI.panelIsOpen()) {
      s.panelFor = null;
      UI.closePanel();
    }
  }

  return {
    title: 'Users',
    mount,
    update,
    unmount() { s = null; }
  };
})();
