/* Notifications: announcements to people using the app (admin_send_announcement, 20261014) and what was sent. */
window.Pages = window.Pages || {};
window.Pages.notifications = (function () {
  'use strict';
  const UI = window.UI;
  const { h, icon, clear, fmt, btn, dl, section, empty, errorState, skeletonPanel, confirm, toast } = UI;
  const data = window.SaluData;

  const TITLE_MAX = 80;
  const MESSAGE_MAX = 500;
  const AUDIENCES = [['ALL', 'Every account'], ['DONORS', 'Has posted a listing'], ['RECIPIENTS', 'Has never posted']];
  const by = (a) => (a.admin.isYou ? 'You' : a.admin.name);
  const people = (n) => fmt.plural(n, 'person', 'people');

  let s = null;

  function mount(main) {
    s = { panelFor: null };
    s.list = window.ListView({
      what: 'announcements', noun: 'announcement', caption: 'Announcements', pageSize: 10, avatars: false,
      defaults: {},
      fetch: (q) => data.announcements(q),
      open: (a) => window.App.go('#/notifications/' + a.id),
      columns: [
        { label: 'Announcement', className: 'cell-wide', cell: (a) => h('div', { class: 'identity__text', style: { maxWidth: '420px' } },
          h('a', { class: 'row-link', href: '#/notifications/' + a.id }, a.title),
          h('span', { class: 'identity__meta' }, a.message)) },
        { label: 'Audience', cell: (a) => data.text.audience[a.audience] },
        { label: 'Reached', className: 'cell-num', cell: (a) => people(a.reach) },
        { label: 'Sent', className: 'cell-muted', cell: (a) => fmt.moment(a.at) },
        { label: 'By', className: 'cell-muted', hide: 'tablet', cell: by }
      ],
      card: (a) => UI.rowCard({
        visual: h('span', { class: 'icon-bubble', 'aria-hidden': 'true' }, icon('campaign', 18)), title: a.title, href: '#/notifications/' + a.id,
        meta: { sub: a.message, items: [{ icon: 'group', text: data.text.audience[a.audience] + ' · ' + people(a.reach) }, { icon: 'schedule', text: fmt.moment(a.at) }] }
      }),
      emptyState: () => ({
        iconName: 'campaign', title: 'No announcements yet', text: 'Announcements you send appear here, with who got them and when.',
        action: { label: 'New announcement', icon: 'add', href: '#/notifications/new', variant: 'primary' }
      })
    });
    main.append(
      UI.pageHead({ title: 'Notifications', sub: 'Announcements to people using the Salu-Salo app.',
        actions: [btn({ label: 'New announcement', icon: 'add', variant: 'primary', href: '#/notifications/new' })] }),
      s.list.el);
    s.list.load();
  }

  /* Drawn like the app's Alerts row: icon, title, text, the "Announcement" label. */
  function preview(title, message, time) {
    return h('div', { class: 'preview-phone', 'aria-label': 'Preview' },
      h('div', { class: 'preview-phone__bar' }, h('span', null, 'Salu-Salo · Alerts'), h('span', null, time)),
      h('div', { class: 'preview-alert' },
        h('span', { class: 'icon-bubble' }, icon('campaign', 18)),
        h('div', { class: 'preview-alert__text' },
          h('span', { class: 'preview-alert__title' }, title || 'Your title'),
          h('span', { class: 'preview-alert__body' }, message || 'Your message appears here.'),
          h('span', { class: 'preview-alert__time' }, 'Announcement'))));
  }

  /* ---------- Details ---------- */
  async function openAnnouncement(id, keep) {
    s.panelFor = id;
    const onClose = () => window.App.closeTo('#/notifications');
    if (!keep) UI.openPanel({ label: 'Announcement', title: 'Loading…', body: skeletonPanel(), onClose });
    let a;
    try { a = await data.announcement(id); } catch (err) {
      if (s && s.panelFor === id) UI.openPanel({ label: 'Announcement', title: 'Couldn’t load this announcement', body: errorState(() => openAnnouncement(id, true), 'this announcement'), onClose });
      return;
    }
    if (!s || s.panelFor !== id) return;
    if (!a) { UI.openPanel({ label: 'Announcement', title: 'Announcement not found', body: empty({ iconName: 'campaign', title: 'This announcement doesn’t exist', text: 'Check the link and try again.' }), onClose }); return; }
    UI.openPanel({
      label: 'Announcement', title: a.title, sub: data.text.audience[a.audience] + ' · sent ' + fmt.relative(a.at), onClose,
      body: [
        section('Message', h('blockquote', { class: 'quote quote--lines' }, a.message)),
        section('How it looks in the app', preview(a.title, a.message, fmt.relative(a.at))),
        section('Details', dl([
          ['Audience', data.text.audience[a.audience]],
          ['Reached', people(a.reach)],
          ['Sent', fmt.dateTime(a.at)],
          ['Sent by', by(a)]
        ]))
      ]
    });
  }

  /* ---------- New announcement ---------- */
  async function openComposer() {
    s.panelFor = 'new';
    const onClose = () => window.App.closeTo('#/notifications');
    UI.openPanel({ label: 'New announcement', title: 'Loading…', body: skeletonPanel(), onClose });
    let sizes;
    try { sizes = await data.audienceSizes(); } catch (err) {
      if (s && s.panelFor === 'new') UI.openPanel({ label: 'New announcement', title: 'Couldn’t load the audiences', body: errorState(openComposer, 'the audiences'), onClose });
      return;
    }
    if (!s || s.panelFor !== 'new') return;

    const title = h('input', { class: 'input', id: 'n-title', maxlength: String(TITLE_MAX), autocomplete: 'off', 'aria-describedby': 'n-title-count' });
    const message = h('textarea', { class: 'textarea', id: 'n-message', maxlength: String(MESSAGE_MAX), rows: '5', 'aria-describedby': 'n-message-count' });
    const titleCount = h('span', { class: 'field__count', id: 'n-title-count' });
    const messageCount = h('span', { class: 'field__count', id: 'n-message-count' });
    const titleErr = h('span', { class: 'field__error', hidden: true }, 'Add a title.');
    const messageErr = h('span', { class: 'field__error', hidden: true }, 'Write the message.');
    let audience = 'ALL';
    const audienceChoices = h('div', { class: 'choice-grid', role: 'radiogroup', 'aria-label': 'Who gets it' },
      AUDIENCES.map(([v, meta]) => h('label', { class: 'choice' },
        h('input', { type: 'radio', name: 'n-audience', value: v, checked: v === audience || null }),
        h('span', { class: 'choice__title' }, data.text.audience[v]),
        h('span', { class: 'choice__meta' }, people(sizes[v]) + ' · ' + meta))));
    const previewBox = h('div');
    const sendBtn = btn({ label: 'Send announcement', icon: 'send', variant: 'primary' });

    function sync() {
      titleCount.textContent = title.value.length + ' / ' + TITLE_MAX;
      messageCount.textContent = message.value.length + ' / ' + MESSAGE_MAX;
      clear(previewBox).append(preview(title.value.trim(), message.value.trim(), 'now'));
    }
    function mark(input, err, bad) {
      err.hidden = !bad;
      input.classList.toggle('is-invalid', bad);
      if (bad) input.setAttribute('aria-invalid', 'true'); else input.removeAttribute('aria-invalid');
    }
    title.addEventListener('input', () => { mark(title, titleErr, false); sync(); });
    message.addEventListener('input', () => { mark(message, messageErr, false); sync(); });
    audienceChoices.addEventListener('change', (e) => { audience = e.target.value; });

    sendBtn.addEventListener('click', async () => {
      const t = title.value.trim();
      const m = message.value.trim();
      mark(title, titleErr, !t);
      mark(message, messageErr, !m);
      if (!t) { title.focus(); return; }
      if (!m) { message.focus(); return; }
      let reach = 0;
      const ok = await confirm({
        title: 'Send to ' + people(sizes[audience]) + '?',
        text: 'It goes to every account in this audience right away. You can’t unsend it. Phones need the latest app version.',
        confirmLabel: 'Send', tone: 'primary', iconName: 'send',
        // The server's own text for a refusal (the limit, an empty audience); never a failure's details.
        // Any other failure may have sent it anyway (a lost answer), so it never invites a blind retry.
        errorText: (err) => (err && err.code === 'P0001' ? err.message : 'Couldn’t confirm it was sent. Close this form and check the list before sending it again.'),
        action: async () => {
          try { reach = (await data.sendAnnouncement({ title: t, message: m, audience })).reach; } catch (err) {
            if (!(err && err.code === 'P0001') && s) s.list.reload();
            throw err;
          }
        }
      });
      if (!ok) return;
      toast('Announcement sent to ' + people(reach) + '.', { sub: 'Saved in the audit log.' });
      window.App.closeTo('#/notifications');
      if (s) s.list.reload();
    });

    const counted = (err, count) => h('div', { style: { display: 'flex', justifyContent: 'space-between', gap: '8px' } }, err, count);
    UI.openPanel({
      label: 'New announcement', title: 'New announcement', sub: 'An alert in the Salu-Salo app', onClose,
      foot: [h('span', { class: 'spacer' }), sendBtn],
      body: [
        h('div', { class: 'field' }, h('label', { class: 'field__label', for: 'n-title' }, 'Title'), title, counted(titleErr, titleCount)),
        h('div', { class: 'field' }, h('label', { class: 'field__label', for: 'n-message' }, 'Message'), message, counted(messageErr, messageCount)),
        h('div', { class: 'field' }, h('span', { class: 'field__label' }, 'Who gets it'), audienceChoices),
        section('Preview', previewBox, h('p', { class: 'muted', style: { fontSize: 'var(--t-sm)' } },
          'Shown in the app’s Alerts, where long messages are cut after 3 lines; tapping it shows the whole text. It arrives whatever the person’s alert settings are.'))
      ]
    });
    sync();
    setTimeout(() => title.focus(), 260);
  }

  function update(route) {
    if (!s) return;
    if (route.id === 'new') openComposer();
    else if (route.id) openAnnouncement(route.id);
    else if (UI.panelIsOpen()) { s.panelFor = null; UI.closePanel(); }
  }

  return {
    title: 'Notifications',
    mount,
    update,
    unmount() { s = null; }
  };
})();
