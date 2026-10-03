/* Audit log: who did what, and when. Read-only. */
window.Pages = window.Pages || {};
window.Pages.audit = (function () {
  'use strict';
  const UI = window.UI;
  const { h, icon, fmt, avatar, notice } = UI;
  const data = window.SaluData;

  const ACTION_ICON = {
    'Approved verification': 'verified', 'Rejected verification': 'highlightOff', 'Verified account': 'verified',
    'Suspended account': 'block', 'Restored account': 'restartAlt', 'Removed listing': 'delete',
    'Dismissed report': 'doNotDisturbOn', 'Reviewed listing': 'taskAlt', 'Added note': 'editNote', 'Sent notification': 'send',
    'Scheduled notification': 'schedule', 'Cancelled notification': 'block', 'Changed setting': 'settings'
  };
  const DANGER = new Set(['Suspended account', 'Removed listing', 'Rejected verification']);

  function targetLink(a) {
    const href = { USER: '#/users/', LISTING: '#/listings/', REPORT: '#/reports/', NOTIFICATION: '#/notifications/', SETTING: '#/settings' }[a.targetType];
    const label = a.targetType === 'REPORT' ? 'Report ' + a.targetName : a.targetName;
    if (!href) return label;
    return h('a', { class: 'row-link', href: a.targetType === 'SETTING' ? href : href + a.targetId, style: { fontWeight: '500' } }, label);
  }
  function actionCell(a) {
    return h('span', { style: { display: 'inline-flex', alignItems: 'center', gap: '8px', fontWeight: '600', whiteSpace: 'nowrap' } },
      h('span', { class: 'icon-bubble' + (DANGER.has(a.action) ? ' icon-bubble--error' : ' icon-bubble--neutral'), style: { width: '28px', height: '28px' }, 'aria-hidden': 'true' }, icon(ACTION_ICON[a.action] || 'history', 16)),
      a.action,
      a.thisSession ? h('span', { class: 'badge badge--orange' }, 'Just now') : null);
  }
  function adminName(a) { return a.admin.isYou ? 'You' : a.admin.name; }

  function mount(main) {
    const f = data.auditFilters();
    const list = window.ListView({
      what: 'the audit log', noun: 'entry', nounPlural: 'entries', caption: 'Audit log', pageSize: 12,
      defaults: { q: '', adminId: 'ALL', action: 'ALL', range: 'ANY' },
      filters: [
        { key: 'q', type: 'search', label: 'Search the log', placeholder: 'Search a listing, report, admin or detail' },
        { key: 'adminId', type: 'select', label: 'Admin', options: [['ALL', 'All admins']].concat(f.admins.map((a) => [a.id, a.isYou ? 'You (' + a.name + ')' : a.name])) },
        { key: 'action', type: 'select', label: 'Action', options: [['ALL', 'All actions']].concat(f.actions.map((a) => [a, a])) },
        { key: 'range', type: 'select', label: 'When', options: [['ANY', 'Any time'], ['TODAY', 'Today'], ['7D', 'Last 7 days'], ['30D', 'Last 30 days']] }
      ],
      fetch: (q) => data.audit(q),
      avatars: false,
      columns: [
        { label: 'When', className: 'cell-muted', cell: (a) => h('div', { class: 'identity__text' }, h('span', { style: { color: 'var(--ink)' } }, fmt.moment(a.at)), h('span', { class: 'identity__meta' }, fmt.relative(a.at))) },
        { label: 'Admin', cell: (a) => h('div', { class: 'identity' }, avatar(a.admin, 'sm'), h('span', { style: { fontWeight: '500', whiteSpace: 'nowrap' } }, adminName(a))) },
        { label: 'Action', cell: actionCell },
        { label: 'Target', cell: targetLink },
        { label: 'Details', className: 'cell-wide', cell: (a) => h('span', { class: 'muted' }, a.details) }
      ],
      card: (a) => [
        h('div', { class: 'row-card__top' },
          h('span', { class: 'icon-bubble' + (DANGER.has(a.action) ? ' icon-bubble--error' : ' icon-bubble--neutral'), 'aria-hidden': 'true' }, icon(ACTION_ICON[a.action] || 'history', 18)),
          h('div', { class: 'identity__text', style: { flex: '1' } },
            h('span', { class: 'identity__name', style: { whiteSpace: 'normal' } }, adminName(a) + ' · ' + a.action.toLowerCase()),
            h('span', { class: 'identity__meta', style: { whiteSpace: 'normal' } }, fmt.moment(a.at) + ' · ' + fmt.relative(a.at)))),
        h('div', null, targetLink(a)),
        a.details ? h('p', { class: 'muted', style: { fontSize: 'var(--t-sm)', overflowWrap: 'anywhere' } }, a.details) : null
      ],
      emptyState: (q, filtered) => filtered
        ? { iconName: 'search', title: 'No entries match', text: 'Try another admin, action or time range.' }
        : { iconName: 'history', title: 'Nothing logged yet', text: 'Each admin action (removing a listing, dismissing a report, adding a note, marking a listing reviewed) is recorded here, and nobody can edit or delete an entry.' }
    });
    main.append(
      UI.pageHead({ title: 'Audit log', sub: 'Who did what, and when. Every admin action is recorded here.' }),
      list.el);
    list.load();
  }

  return { title: 'Audit log', mount };
})();
