/* Reports: the moderation queue and the report review. */
window.Pages = window.Pages || {};
window.Pages.reports = (function () {
  'use strict';
  const UI = window.UI;
  const { h, icon, clear, fmt, btn, badge, avatar, thumb, identity, notice, dl, section, empty, errorState, skeletonPanel, confirm, toast, busy } = UI;
  const data = window.SaluData;

  let s = null;

  const REASON_OPTIONS = [['ALL', 'All reasons']]
    .concat(Object.entries(data.text.reasons.LISTING).filter(([k]) => k !== 'OTHER').map(([k, v]) => [k, 'Listing: ' + v]))
    .concat(Object.entries(data.text.reasons.USER).filter(([k]) => k !== 'OTHER').map(([k, v]) => [k, 'Person: ' + v]))
    .concat([['OTHER', 'Other']]);

  function targetCell(r) {
    const isListing = r.targetType === 'LISTING';
    return h('div', { class: 'identity' },
      h('span', { class: 'icon-bubble icon-bubble--neutral', style: { width: '32px', height: '32px' }, 'aria-hidden': 'true' }, icon(isListing ? 'lunchDining' : 'person', 18)),
      h('div', { class: 'identity__text' },
        h('span', { class: 'identity__name' }, r.target.name),
        h('span', { class: 'identity__meta' }, isListing ? 'Listing' : 'Person')));
  }

  function mount(main, route) {
    s = { panelFor: null, status: 'OPEN' };
    s.tabs = h('div');
    s.list = window.ListView({
      what: 'reports', noun: 'report', caption: 'Reports',
      defaults: { q: '', target: 'ALL', reason: 'ALL' },
      initial: { status: 'OPEN' },
      keepOnReset: (q) => ({ status: q.status }),
      filters: [
        { key: 'q', type: 'search', label: 'Search reports', placeholder: 'Search report ID, reporter or what was reported' },
        { key: 'target', type: 'select', label: 'Reported', options: [['ALL', 'Listings and people'], ['LISTING', 'Listings'], ['USER', 'People']] },
        { key: 'reason', type: 'select', label: 'Reason', options: REASON_OPTIONS }
      ],
      fetch: (q) => data.reports(q),
      open: (r) => window.App.go('#/reports/' + r.id),
      avatars: false,
      columns: [
        { label: 'Report', cell: (r) => h('div', { class: 'identity__text' }, h('a', { class: 'row-link mono', href: '#/reports/' + r.id }, r.id), h('span', { class: 'identity__meta' }, data.text.sources[r.source])) },
        { label: 'Reporter', cell: (r) => h('a', { class: 'row-link', href: '#/users/' + r.reporter.id, style: { fontWeight: '500' } }, r.reporter.name) },
        { label: 'Reported', className: 'cell-wide', cell: targetCell },
        { label: 'Reason', cell: (r) => r.reasonLabel },
        { label: 'Date', className: 'cell-muted', cell: (r) => fmt.moment(r.createdAt) },
        { label: 'Status', cell: (r) => badge('report', r.status) },
        { label: h('span', { class: 'sr-only' }, 'Action'), className: 'cell-actions', cell: (r) => h('a', { class: 'btn btn--' + (r.status === 'OPEN' ? 'primary' : 'secondary') + ' btn--sm', href: '#/reports/' + r.id, 'aria-label': (r.status === 'OPEN' ? 'Review ' : 'View ') + r.id }, r.status === 'OPEN' ? 'Review' : 'View') }
      ],
      card: (r) => UI.rowCard({
        visual: h('span', { class: 'icon-bubble icon-bubble--error', 'aria-hidden': 'true' }, icon(r.targetType === 'LISTING' ? 'lunchDining' : 'person', 18)),
        title: r.reasonLabel, href: '#/reports/' + r.id,
        meta: { sub: (r.targetType === 'LISTING' ? 'Listing: ' : 'Person: ') + r.target.name, items: [{ icon: 'person', text: 'By ' + r.reporter.name }, { icon: 'schedule', text: fmt.relative(r.createdAt) }] },
        badges: [badge('report', r.status), h('span', { class: 'badge badge--outline mono' }, r.id)]
      }),
      emptyState: (q, filtered) => {
        if (filtered) return { iconName: 'search', title: 'No reports match', text: 'Try another search or clear the filters.' };
        if (q.status === 'OPEN') return { iconName: 'taskAlt', title: 'No reports waiting', text: 'You’re all caught up. Reports people send from a listing, a donor’s page or a chat appear here.' };
        return { iconName: 'flag', title: 'No ' + ({ RESOLVED: 'resolved', DISMISSED: 'dismissed', ALL: '' })[q.status] + ' reports', text: 'Reports move here once someone takes action on them.' };
      }
    });
    main.append(
      UI.pageHead({ title: 'Reports', sub: 'Listings and people reported from the app. Open reports need a decision.' }),
      s.tabs,
      h('div', { role: 'tabpanel', id: 'reports-panel', 'aria-labelledby': 'tab-OPEN' }, s.list.el));
    renderTabs();
    s.list.load();
  }
  function renderTabs() {
    const c = data.counts();
    clear(s.tabs).append(UI.tabs([
      { key: 'OPEN', label: 'Open', icon: 'flag', count: c.openReports },
      { key: 'RESOLVED', label: 'Resolved', icon: 'taskAlt' },
      { key: 'DISMISSED', label: 'Dismissed', icon: 'doNotDisturbOn' },
      { key: 'ALL', label: 'All reports', icon: 'history' }
    ], s.status, (key) => {
      s.status = key;
      renderTabs();
      const panel = document.getElementById('reports-panel');
      if (panel) panel.setAttribute('aria-labelledby', 'tab-' + key);
      s.list.set({ status: key });
    }, 'Report status'));
  }

  /* ---------- Actions ---------- */
  async function dismiss(r) {
    const ok = await confirm({
      title: 'Dismiss this report?',
      text: 'Nothing changes for ' + r.target.name + '. The report closes with your note, and ' + r.reporter.name + ' isn’t told.',
      confirmLabel: 'Dismiss report', tone: 'primary', iconName: 'doNotDisturbOn',
      reason: { label: 'Note', required: false, placeholder: 'For example: checked the photos; the listing is accurate' },
      errorText: 'Unable to dismiss this report. Please try again.',
      action: (note) => data.dismissReport(r.id, note)
    });
    if (ok) { toast('Report dismissed.'); refreshAfterAction(); }
  }
  async function removeListing(r, l) {
    let result = null;
    const open = l.openReservations || 0;
    const ok = await confirm({
      title: 'Remove this listing?',
      text: 'Are you sure you want to remove this food listing? “' + l.name + '” disappears from the app for everyone.' + (open ? ' ' + fmt.plural(open, 'open reservation') + ' will be cancelled, and the recipients alerted.' : ''),
      confirmLabel: 'Remove listing', tone: 'danger', iconName: 'delete',
      reason: { label: 'Reason', required: true, placeholder: 'For example: food was spoiled at pickup' },
      errorText: 'Unable to remove listing. Please try again.',
      action: async (reason) => { result = await data.removeListing(l.id, reason, r.id); }
    });
    if (ok) {
      toast('Listing removed successfully.', { sub: result && result.closedReports ? fmt.plural(result.closedReports, 'report') + ' on it marked resolved.' : null });
      refreshAfterAction();
    }
  }
  async function suspend(r, person) {
    let result = null;
    const ok = await confirm({
      title: 'Suspend this account?',
      text: 'Are you sure you want to suspend this account? ' + person.name + ' won’t be able to sign in or use Salu-Salo until an admin restores it.',
      confirmLabel: 'Suspend user', tone: 'danger', iconName: 'block',
      reason: { label: 'Reason', required: true, placeholder: 'For example: asked for payment before pickup' },
      errorText: 'Unable to suspend this account. Please try again.',
      action: async (reason) => { result = await data.suspendUser(person.id, reason, r.id); }
    });
    if (ok) {
      toast('Account suspended.', { sub: result && result.closedReports ? fmt.plural(result.closedReports, 'report') + ' marked resolved.' : null });
      refreshAfterAction();
    }
  }
  async function addNote(r, textarea, button) {
    const text = textarea.value.trim();
    if (!text) { textarea.focus(); return; }
    try {
      await busy(button, () => data.addReportNote(r.id, text));
      toast('Note added.');
      openReport(r.id, true);
    } catch (err) {
      toast('Unable to save your note. Please try again.', { tone: 'error' });
    }
  }
  function refreshAfterAction() {
    if (!s) return;
    renderTabs();
    s.list.reload();
    if (UI.panelIsOpen() && s.panelFor) openReport(s.panelFor, true);
  }

  /* ---------- Review ---------- */
  async function openReport(id, keep) {
    s.panelFor = id;
    const onClose = () => window.App.closeTo('#/reports');
    if (!keep) UI.openPanel({ label: 'Report', title: 'Loading…', body: skeletonPanel(), onClose });
    let r;
    try { r = await data.report(id); } catch (err) {
      UI.openPanel({ label: 'Report', title: 'Couldn’t load this report', body: errorState(() => openReport(id, true), 'this report'), onClose });
      return;
    }
    if (!s || s.panelFor !== id) return;
    if (!r) { UI.openPanel({ label: 'Report', title: 'Report not found', body: empty({ iconName: 'flag', title: 'This report doesn’t exist', text: 'Check the link and try again.' }), onClose }); return; }

    const isListing = r.targetType === 'LISTING';
    const t = r.targetDetail;
    const noteInput = h('textarea', { class: 'textarea', id: 'report-note', rows: '3', maxlength: '500', placeholder: 'Only admins see notes' });
    const noteBtn = btn({ label: 'Add note', icon: 'editNote', variant: 'secondary', size: 'sm' });
    noteBtn.addEventListener('click', () => addNote(r, noteInput, noteBtn));

    const targetBlock = isListing
      ? (t ? h('div', { class: 'card', style: { padding: '14px 16px', display: 'grid', gap: '10px', boxShadow: 'none' } },
        identity(thumb(t.category), t.name, 'From ' + t.donor.name + ' · posted ' + fmt.relative(t.createdAt), '#/listings/' + t.id),
        h('div', { class: 'badge-row' }, badge('listing', t.status), badge('account', t.donorAccountStatus, t.donorAccountStatus === 'ACTIVE' ? 'Donor active' : 'Donor suspended')))
        : notice({ tone: 'neutral', text: 'The listing “' + (r.listingName || 'unknown') + '” was deleted by its donor.' }))
      : h('div', { class: 'card', style: { padding: '14px 16px', display: 'grid', gap: '10px', boxShadow: 'none' } },
        identity(avatar(t), t.name, t.email, '#/users/' + t.id),
        h('div', { class: 'badge-row' }, badge('role', t.role), badge('verification', t.verification), badge('account', t.accountStatus)));

    const body = [
      r.status !== 'OPEN' && r.resolution ? notice({
        tone: r.status === 'RESOLVED' ? 'success' : 'neutral',
        iconName: r.status === 'RESOLVED' ? 'taskAlt' : 'doNotDisturbOn',
        title: (r.status === 'RESOLVED' ? ({ LISTING_REMOVED: 'Listing removed', USER_SUSPENDED: 'Account suspended' })[r.resolution.action] || 'Resolved' : 'Dismissed') + ' by ' + r.resolution.byName,
        text: fmt.dateTime(r.resolution.at) + (r.resolution.note ? ' · ' + r.resolution.note : '')
      }) : notice({ tone: 'warning', iconName: 'flag', title: 'Needs a decision', text: 'Read what was reported and check the ' + (isListing ? 'listing' : 'person') + '. Then dismiss the report or take action.' }),
      section('What was reported',
        h('div', { class: 'badge-row' }, badge('report', r.status), h('span', { class: 'badge badge--outline' }, icon(isListing ? 'lunchDining' : 'person', 14), isListing ? 'Listing' : 'Person')),
        dl([['Reason', r.reasonLabel], ['Where', data.text.sources[r.source]], ['Reported', fmt.dateTime(r.createdAt) + ' (' + fmt.relative(r.createdAt) + ')'], ['Report ID', h('span', { class: 'mono' }, r.id)]]),
        r.details ? h('blockquote', { class: 'quote' }, r.details, h('span', { class: 'quote__source' }, 'Written by ' + r.reporter.name)) : h('p', { class: 'muted' }, 'The reporter didn’t add details.'),
        r.source === 'CHAT' ? h('p', { class: 'muted', style: { fontSize: 'var(--t-sm)' } }, 'Chats stay private: admins see the reporter’s words, not the conversation.') : null),
      section(isListing ? 'Reported listing' : 'Reported person', targetBlock),
      section('Reporter',
        identity(avatar(r.reporterDetail), r.reporterDetail.name, r.reporterDetail.email, '#/users/' + r.reporterDetail.id),
        h('p', { class: 'muted', style: { fontSize: 'var(--t-sm)' } }, 'Joined ' + fmt.date(r.reporterDetail.joinedAt) + ' · ' + fmt.plural(r.reporterDetail.reportsFiled, 'report') + ' sent in total')),
      r.otherReports.length ? section('Other reports about this ' + (isListing ? 'listing' : 'person'), h('ul', { class: 'list' }, r.otherReports.map((o) => h('li', null, h('a', { class: 'list__item list__item--button', href: '#/reports/' + o.id, style: { paddingInline: '0' } },
        h('span', { class: 'list__main' }, h('span', { class: 'list__title' }, o.reasonLabel), h('span', { class: 'list__meta' }, o.id + ' · ' + fmt.relative(o.createdAt))),
        h('span', { class: 'list__side' }, badge('report', o.status))))))) : null,
      section('Admin notes',
        r.notes.length ? h('ul', { class: 'list' }, r.notes.map((n) => h('li', { class: 'list__item', style: { paddingInline: '0', alignItems: 'flex-start' } },
          UI.avatar({ name: n.by }, 'sm'),
          h('span', { class: 'list__main' }, h('span', { class: 'list__title', style: { fontWeight: '400' } }, n.text), h('span', { class: 'list__meta' }, n.by + ' · ' + fmt.relative(n.at)))))) : h('p', { class: 'muted' }, 'No notes yet.'),
        h('div', { class: 'field' }, h('label', { class: 'field__label', for: 'report-note' }, 'Add a note'), noteInput),
        h('div', null, noteBtn))
    ];

    const foot = [];
    if (r.status === 'OPEN') {
      foot.push(btn({ label: 'Dismiss report', icon: 'doNotDisturbOn', variant: 'secondary', onClick: () => dismiss(r) }));
      foot.push(h('span', { class: 'spacer' }));
      if (isListing && t && t.status !== 'REMOVED') foot.push(btn({ label: 'Remove listing', icon: 'delete', variant: 'danger', onClick: () => removeListing(r, t) }));
      const person = isListing ? (t ? t.donor : null) : t;
      const personActive = isListing ? t && t.donorAccountStatus === 'ACTIVE' : t && t.accountStatus === 'ACTIVE' && t.role !== 'ADMIN';
      if (person && personActive) foot.push(btn({ label: isListing ? 'Suspend donor' : 'Suspend user', icon: 'block', variant: 'danger-outline', onClick: () => suspend(r, person) }));
    } else if (isListing && t) {
      foot.push(btn({ label: 'View listing', icon: 'lunchDining', variant: 'secondary', href: '#/listings/' + t.id }));
    } else if (!isListing) {
      foot.push(btn({ label: 'View user', icon: 'person', variant: 'secondary', href: '#/users/' + t.id }));
    }
    UI.openPanel({ label: 'Report ' + r.id, title: r.reasonLabel, sub: (isListing ? 'Listing: ' : 'Person: ') + r.target.name, body, foot, onClose });
  }

  function update(route) {
    if (!s) return;
    if (route.id) openReport(route.id);
    else if (UI.panelIsOpen()) { s.panelFor = null; UI.closePanel(); }
  }

  return {
    title: 'Reports',
    mount,
    update,
    unmount() { s = null; }
  };
})();
