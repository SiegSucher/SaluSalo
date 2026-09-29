/* Notifications: announcements to app users, their history and status, and the admin's own alerts. */
window.Pages = window.Pages || {};
window.Pages.notifications = (function () {
  'use strict';
  const UI = window.UI;
  const { h, icon, clear, fmt, btn, badge, notice, dl, section, empty, errorState, skeletonList, skeletonPanel, confirm, toast, busy } = UI;
  const data = window.SaluData;

  let s = null;
  const ALERT_ICON = { REPORT: ['flag', 'error'], VERIFICATION: ['verified', 'orange'], SYSTEM: ['info', 'neutral'] };

  function mount(main, route) {
    s = { tab: route.id === 'alerts' ? 'alerts' : 'announcements', panelFor: null, filter: 'ALL' };
    s.tabs = h('div');
    s.body = h('div', { role: 'tabpanel', class: 'data' });
    main.append(
      UI.pageHead({ title: 'Notifications', sub: 'Announcements to people using the app, and alerts for admins.',
        actions: [btn({ label: 'Create notification', icon: 'add', variant: 'primary', href: '#/notifications/new' })] }),
      s.tabs, s.body);
    renderTabs();
    showTab();
  }
  function renderTabs() {
    const c = data.counts();
    clear(s.tabs).append(UI.tabs([
      { key: 'announcements', label: 'Announcements', icon: 'campaign' },
      { key: 'alerts', label: 'Your alerts', icon: 'notifications', count: c.unreadAlerts || null }
    ], s.tab, (key) => window.App.go(key === 'alerts' ? '#/notifications/alerts' : '#/notifications'), 'Notification lists'));
    s.body.setAttribute('aria-labelledby', 'tab-' + s.tab);
  }
  function showTab() {
    clear(s.body);
    if (s.tab === 'alerts') { s.alertsEl = h('div', { class: 'card card--flush' }, skeletonList(5)); s.body.append(s.alertsEl); loadAlerts(); return; }
    const seg = h('div', { class: 'segmented', role: 'group', 'aria-label': 'Show' },
      [['ALL', 'All'], ['SENT', 'Sent'], ['SCHEDULED', 'Scheduled'], ['DRAFT', 'Drafts']].map(([v, l]) =>
        h('button', { type: 'button', 'aria-pressed': String(v === s.filter), dataset: { v } }, l)));
    seg.addEventListener('click', (e) => {
      const b = e.target.closest('button');
      if (!b) return;
      s.filter = b.dataset.v;
      seg.querySelectorAll('button').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
      loadAnnouncements();
    });
    s.listEl = h('div', { class: 'data' }, UI.skeletonRows(4, false));
    s.body.append(
      notice({ tone: 'neutral', iconName: 'info', text: 'Sample only: the app has no announcement type yet, so nothing here reaches anyone’s phone.' }),
      h('div', { class: 'filters', style: { alignItems: 'center' } }, seg),
      s.listEl);
    loadAnnouncements();
  }

  async function loadAnnouncements() {
    const target = s.listEl;
    target.classList.add('is-refreshing');
    let rows;
    try { rows = await data.announcements({ status: s.filter }); } catch (err) {
      if (s && s.listEl === target) clear(target).append(h('div', { class: 'card' }, errorState(loadAnnouncements, 'notifications')));
      return;
    } finally { target.classList.remove('is-refreshing'); }
    if (!s || s.listEl !== target) return;
    clear(target);
    if (!rows.length) {
      target.append(h('div', { class: 'card' }, empty({
        iconName: 'campaign',
        title: s.filter === 'ALL' ? 'No notifications yet' : 'No ' + ({ SENT: 'sent notifications', SCHEDULED: 'scheduled notifications', DRAFT: 'drafts' })[s.filter],
        text: 'Announcements you send to donors and recipients appear here with their status.',
        action: { label: 'Create notification', icon: 'add', href: '#/notifications/new', variant: 'primary' }
      })));
      return;
    }
    const when = (a) => (a.status === 'SCHEDULED' ? 'Sends ' + fmt.moment(a.at) : a.status === 'DRAFT' ? 'Saved ' + fmt.relative(a.at) : a.status === 'CANCELLED' ? 'Cancelled' : fmt.moment(a.at));
    target.append(...UI.dataView({
      caption: 'Announcements', rows,
      open: (a) => window.App.go('#/notifications/' + a.id),
      columns: [
        { label: 'Notification', className: 'cell-wide', cell: (a) => h('div', { class: 'identity__text', style: { maxWidth: '420px' } },
          h('a', { class: 'row-link', href: '#/notifications/' + a.id }, a.title),
          h('span', { class: 'identity__meta' }, a.message)) },
        { label: 'Audience', cell: (a) => data.text.audience[a.audience] },
        { label: 'Status', cell: (a) => badge('announcement', a.status) },
        { label: 'Date', className: 'cell-muted', cell: when },
        { label: 'Reached', className: 'cell-num', cell: (a) => (a.status === 'SENT' ? fmt.plural(a.reach, 'person', 'people') : '—') },
        { label: 'By', className: 'cell-muted', hide: 'tablet', cell: (a) => a.byName }
      ],
      card: (a) => UI.rowCard({
        visual: h('span', { class: 'icon-bubble', 'aria-hidden': 'true' }, icon('campaign', 18)), title: a.title, href: '#/notifications/' + a.id,
        meta: { sub: a.message, items: [{ icon: 'group', text: data.text.audience[a.audience] }, { icon: 'schedule', text: when(a) }] },
        badges: [badge('announcement', a.status)]
      })
    }));
  }

  async function loadAlerts() {
    const target = s.alertsEl;
    let rows;
    try { rows = await data.alerts(); } catch (err) {
      if (s && s.alertsEl === target) clear(target).append(errorState(loadAlerts, 'your alerts'));
      return;
    }
    if (!s || s.alertsEl !== target) return;
    clear(target);
    const unread = rows.filter((a) => !a.read).length;
    const markAll = btn({ label: 'Mark all as read', icon: 'doneAll', variant: 'ghost', size: 'sm', disabled: !unread });
    markAll.addEventListener('click', async () => {
      try { await busy(markAll, () => data.markAlertsRead()); toast('All alerts marked as read.'); renderTabs(); loadAlerts(); } catch (err) { toast('Unable to update your alerts. Please try again.', { tone: 'error' }); }
    });
    target.append(h('div', { class: 'card__head' }, h('div', { class: 'card__head-text' }, h('h2', { class: 'section-title' }, 'Alerts for admins'), h('p', { class: 'section-sub' }, unread ? unread + ' unread' : 'All read')), markAll));
    if (!rows.length) {
      target.append(empty({ iconName: 'notifications', title: 'No alerts', text: 'New reports, verification requests and system messages for admins appear here.' }));
      return;
    }
    target.append(h('div', { class: 'card__body', style: { padding: '8px 0 0' } }, h('ul', { class: 'list' }, rows.map((a) => {
      const [ic, tone] = ALERT_ICON[a.type] || ['info', 'neutral'];
      const link = h('a', { class: 'list__item list__item--button', href: a.link },
        h('span', { class: 'icon-bubble icon-bubble--' + tone }, icon(ic, 18)),
        h('span', { class: 'list__main' },
          h('span', { class: 'list__title' }, a.read ? a.title : h('strong', null, a.title), a.read ? null : h('span', { class: 'sr-only' }, ' (unread)')),
          h('span', { class: 'list__meta' }, a.text + ' · ' + fmt.relative(a.at))),
        a.read ? null : h('span', { class: 'list__unread', 'aria-hidden': 'true' }));
      link.addEventListener('click', () => data.markAlertRead(a.id));
      return h('li', null, link);
    }))));
  }

  /* ---------- Details ---------- */
  async function openAnnouncement(id, keep) {
    s.panelFor = id;
    const onClose = () => window.App.closeTo('#/notifications');
    if (!keep) UI.openPanel({ label: 'Notification', title: 'Loading…', body: skeletonPanel(), onClose });
    let a;
    try { a = await data.announcement(id); } catch (err) {
      UI.openPanel({ label: 'Notification', title: 'Couldn’t load this notification', body: errorState(() => openAnnouncement(id, true), 'this notification'), onClose });
      return;
    }
    if (!s || s.panelFor !== id) return;
    if (!a) { UI.openPanel({ label: 'Notification', title: 'Not found', body: empty({ iconName: 'campaign', title: 'This notification doesn’t exist', text: 'A deleted draft, maybe.' }), onClose }); return; }
    const body = [
      h('div', { class: 'badge-row' }, badge('announcement', a.status)),
      section('Message', h('blockquote', { class: 'quote' }, a.message)),
      section('How it looks in the app', preview(a.title, a.message, a.status === 'SENT' ? fmt.relative(a.at) : 'now')),
      section('Details', dl([
        ['Audience', data.text.audience[a.audience]],
        ['Status', ({ SENT: 'Sent', SCHEDULED: 'Scheduled', DRAFT: 'Draft, not sent', CANCELLED: 'Cancelled, not sent' })[a.status]],
        [a.status === 'SCHEDULED' ? 'Sends' : a.status === 'SENT' ? 'Sent' : 'Saved', fmt.dateTime(a.at)],
        ['Reached', a.status === 'SENT' ? fmt.plural(a.reach, 'person', 'people') : '—'],
        ['Created by', a.byName]
      ]))
    ];
    const foot = [];
    if (a.status === 'SCHEDULED') foot.push(btn({ label: 'Cancel sending', icon: 'block', variant: 'danger-outline', onClick: () => cancelScheduled(a) }));
    if (a.status === 'DRAFT') {
      foot.push(btn({ label: 'Delete draft', icon: 'delete', variant: 'danger-outline', onClick: () => deleteDraft(a) }));
      foot.push(h('span', { class: 'spacer' }));
      foot.push(btn({ label: 'Edit and send', icon: 'send', variant: 'primary', onClick: () => { window.App.intent.draft = a; window.App.go('#/notifications/new'); } }));
    }
    UI.openPanel({ label: 'Notification', title: a.title, sub: data.text.audience[a.audience] + ' · ' + badgeWord(a.status), body, foot, onClose });
  }
  const badgeWord = (st) => ({ SENT: 'sent', SCHEDULED: 'scheduled', DRAFT: 'draft', CANCELLED: 'cancelled' })[st];

  async function cancelScheduled(a) {
    const ok = await confirm({
      title: 'Cancel this notification?', text: '“' + a.title + '” won’t be sent on ' + fmt.moment(a.at) + '.',
      confirmLabel: 'Cancel sending', cancelLabel: 'Keep it', tone: 'danger', iconName: 'block',
      errorText: 'Unable to cancel it. Please try again.',
      action: () => data.cancelScheduled(a.id)
    });
    if (ok) { toast('Scheduled notification cancelled.'); afterChange(); }
  }
  async function deleteDraft(a) {
    const ok = await confirm({
      title: 'Delete this draft?', text: '“' + a.title + '” will be deleted. This can’t be undone.',
      confirmLabel: 'Delete draft', cancelLabel: 'Keep it', tone: 'danger', iconName: 'delete',
      errorText: 'Unable to delete the draft. Please try again.',
      action: () => data.deleteDraft(a.id)
    });
    if (ok) { toast('Draft deleted.'); s.panelFor = null; window.App.closeTo('#/notifications'); afterChange(); }
  }
  function afterChange() {
    if (!s) return;
    renderTabs();
    if (s.tab === 'announcements') loadAnnouncements();
    if (UI.panelIsOpen() && s.panelFor && s.panelFor !== 'new') openAnnouncement(s.panelFor, true);
  }

  function preview(title, message, time) {
    return h('div', { class: 'preview-phone', 'aria-label': 'Preview' },
      h('div', { class: 'preview-phone__bar' }, h('span', null, 'Salu-Salo · Alerts'), h('span', null, time)),
      h('div', { class: 'preview-alert' },
        h('span', { class: 'icon-bubble' }, icon('campaign', 18)),
        h('div', { class: 'preview-alert__text' },
          h('span', { class: 'preview-alert__title' }, title || 'Your title'),
          h('span', { class: 'preview-alert__body' }, message || 'Your message appears here.'),
          h('span', { class: 'preview-alert__time' }, 'From Salu-Salo'))));
  }

  /* ---------- Create ---------- */
  function openComposer() {
    s.panelFor = 'new';
    const draft = window.App.intent.draft || null;
    window.App.intent.draft = null;
    const sizes = data.audienceSizes();
    const onClose = () => window.App.closeTo('#/notifications');
    const title = h('input', { class: 'input', id: 'n-title', maxlength: '60', value: draft ? draft.title : '', autocomplete: 'off', 'aria-describedby': 'n-title-count' });
    const message = h('textarea', { class: 'textarea', id: 'n-message', maxlength: '200', rows: '4', 'aria-describedby': 'n-message-count' });
    message.value = draft ? draft.message : '';
    const titleCount = h('span', { class: 'field__count', id: 'n-title-count' });
    const messageCount = h('span', { class: 'field__count', id: 'n-message-count' });
    const titleErr = h('span', { class: 'field__error', id: 'n-title-err', hidden: true }, 'Add a title.');
    const messageErr = h('span', { class: 'field__error', id: 'n-message-err', hidden: true }, 'Write the message.');
    const whenErr = h('span', { class: 'field__error', id: 'n-when-err', hidden: true }, 'Pick a time at least 15 minutes from now.');
    let audience = draft ? draft.audience : 'ALL';
    let when = 'NOW';
    const audienceChoices = h('div', { class: 'choice-grid', role: 'radiogroup', 'aria-label': 'Audience' },
      [['ALL', 'Everyone'], ['DONORS', 'Donors'], ['RECIPIENTS', 'Recipients']].map(([v, l]) => h('label', { class: 'choice' },
        h('input', { type: 'radio', name: 'n-audience', value: v, checked: v === audience || null }),
        h('span', { class: 'choice__title' }, l),
        h('span', { class: 'choice__meta' }, fmt.plural(sizes[v], 'person', 'people')))));
    const later = h('input', { class: 'input', id: 'n-at', type: 'datetime-local', 'aria-describedby': 'n-when-err' });
    const soon = new Date(Date.now() + 26 * 3600e3);
    soon.setMinutes(0, 0, 0);
    later.value = new Date(soon.getTime() - soon.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    const laterWrap = h('div', { class: 'field', hidden: true }, h('label', { class: 'field__label', for: 'n-at' }, 'Send on'), later, whenErr);
    const whenChoices = h('div', { class: 'choice-grid', role: 'radiogroup', 'aria-label': 'When to send', style: { gridTemplateColumns: 'repeat(2, minmax(0, 1fr))' } },
      [['NOW', 'Send now', 'Right after you confirm'], ['LATER', 'Schedule', 'Pick a date and time']].map(([v, l, m]) => h('label', { class: 'choice' },
        h('input', { type: 'radio', name: 'n-when', value: v, checked: v === when || null }),
        h('span', { class: 'choice__title' }, l), h('span', { class: 'choice__meta' }, m))));
    const previewBox = h('div');
    const sendBtn = btn({ label: 'Send notification', icon: 'send', variant: 'primary' });
    const draftBtn = btn({ label: 'Save draft', icon: 'draft', variant: 'secondary' });

    function sync() {
      titleCount.textContent = title.value.length + ' / 60';
      messageCount.textContent = message.value.length + ' / 200';
      clear(previewBox).append(preview(title.value.trim(), message.value.trim(), 'now'));
      laterWrap.hidden = when !== 'LATER';
      sendBtn.lastChild.textContent = when === 'LATER' ? 'Schedule notification' : 'Send notification';
    }
    title.addEventListener('input', () => { titleErr.hidden = true; title.classList.remove('is-invalid'); sync(); });
    message.addEventListener('input', () => { messageErr.hidden = true; message.classList.remove('is-invalid'); sync(); });
    audienceChoices.addEventListener('change', (e) => { audience = e.target.value; });
    whenChoices.addEventListener('change', (e) => { when = e.target.value; sync(); });
    later.addEventListener('input', () => { whenErr.hidden = true; later.classList.remove('is-invalid'); });

    function validate(requireAll) {
      let ok = true;
      const mark = (input, err, bad) => {
        err.hidden = !bad;
        input.classList.toggle('is-invalid', bad);
        if (bad) input.setAttribute('aria-invalid', 'true'); else input.removeAttribute('aria-invalid');
        if (bad && ok) { input.focus(); ok = false; }
      };
      mark(title, titleErr, !title.value.trim());
      if (requireAll) mark(message, messageErr, !message.value.trim());
      if (requireAll && when === 'LATER') mark(later, whenErr, !later.value || new Date(later.value).getTime() < Date.now() + 15 * 60000);
      return ok;
    }
    sendBtn.addEventListener('click', async () => {
      if (!validate(true)) return;
      const at = when === 'LATER' ? new Date(later.value).getTime() : null;
      const ok = await confirm({
        title: when === 'LATER' ? 'Schedule this notification?' : 'Send this notification?',
        text: (when === 'LATER' ? 'It will go to ' : 'It goes to ') + fmt.plural(sizes[audience], 'person', 'people') + ' (' + data.text.audience[audience].toLowerCase() + ')' + (when === 'LATER' ? ' on ' + fmt.moment(at) + '.' : ' right away. You can’t unsend it.'),
        confirmLabel: when === 'LATER' ? 'Schedule' : 'Send', tone: 'primary', iconName: 'send',
        errorText: 'Unable to send the notification. Please try again.',
        action: () => data.createAnnouncement({ title: title.value.trim(), message: message.value.trim(), audience, when, at, replaceId: draft ? draft.id : null })
      });
      if (ok) {
        toast(when === 'LATER' ? 'Notification scheduled.' : 'Notification sent successfully.', { sub: 'Sample only: nothing was delivered to phones.' });
        s.filter = 'ALL';
        window.App.closeTo('#/notifications');
        afterChange();
      }
    });
    draftBtn.addEventListener('click', async () => {
      if (!validate(false)) return;
      try {
        await busy(draftBtn, () => data.createAnnouncement({ title: title.value.trim(), message: message.value.trim(), audience, when: 'NOW', draft: true, replaceId: draft ? draft.id : null }));
        toast('Draft saved.');
        window.App.closeTo('#/notifications');
        afterChange();
      } catch (err) {
        toast('Unable to save the draft. Please try again.', { tone: 'error' });
      }
    });

    const body = [
      h('div', { class: 'field' }, h('label', { class: 'field__label', for: 'n-title' }, 'Title'), title, h('div', { style: { display: 'flex', justifyContent: 'space-between', gap: '8px' } }, titleErr, titleCount)),
      h('div', { class: 'field' }, h('label', { class: 'field__label', for: 'n-message' }, 'Message'), message, h('div', { style: { display: 'flex', justifyContent: 'space-between', gap: '8px' } }, messageErr, messageCount)),
      h('div', { class: 'field' }, h('span', { class: 'field__label' }, 'Who gets it'), audienceChoices),
      h('div', { class: 'field' }, h('span', { class: 'field__label' }, 'When'), whenChoices),
      laterWrap,
      section('Preview', previewBox, h('p', { class: 'muted', style: { fontSize: 'var(--t-sm)' } }, 'Shown in the app’s Alerts list. Sample only: the app can’t receive announcements yet.'))
    ];
    UI.openPanel({ label: 'Create notification', title: draft ? 'Edit draft' : 'New notification', sub: 'An in-app alert for people using Salu-Salo', body, foot: [draftBtn, h('span', { class: 'spacer' }), sendBtn], onClose });
    sync();
    setTimeout(() => title.focus(), 260);
  }

  function update(route) {
    if (!s) return;
    const wantTab = route.id === 'alerts' ? 'alerts' : (route.id ? s.tab : 'announcements');
    if (wantTab !== s.tab && (route.id === 'alerts' || !route.id)) { s.tab = wantTab; renderTabs(); showTab(); }
    if (route.id === 'new') openComposer();
    else if (route.id && route.id !== 'alerts') openAnnouncement(route.id);
    else if (UI.panelIsOpen()) { s.panelFor = null; UI.closePanel(); }
  }

  return {
    title: 'Notifications',
    mount,
    update,
    unmount() { s = null; }
  };
})();
