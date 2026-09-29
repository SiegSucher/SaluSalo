/* Settings: account, notifications, moderation and system preferences. Sample only: nothing here changes the app's backend. */
window.Pages = window.Pages || {};
window.Pages.settings = (function () {
  'use strict';
  const UI = window.UI;
  const { h, icon, clear, btn, notice, switchRow, errorState, skeleton, toast, busy, confirm } = UI;
  const data = window.SaluData;

  let s = null;
  const SECTIONS = [['account', 'Account', 'person'], ['notifications', 'Notifications', 'notifications'], ['moderation', 'Moderation', 'gavel'], ['system', 'System', 'tune'], ['sample', 'Sample data', 'info']];

  const DESCRIBE = {
    name: (a, b) => 'Name: ' + a + ' → ' + b,
    newReports: (a, b) => 'Alerts for new reports ' + (b ? 'on' : 'off'),
    verificationRequests: (a, b) => 'Alerts for verification requests ' + (b ? 'on' : 'off'),
    system: (a, b) => 'System alerts ' + (b ? 'on' : 'off'),
    emailDigest: (a, b) => 'Daily email summary ' + (b ? 'on' : 'off'),
    autoExpire: (a, b) => 'Expire listings automatically ' + (b ? 'on' : 'off'),
    autoHideAfterReports: (a, b) => 'Hide a listing automatically after ' + b + ' open reports (was ' + a + ')',
    requireRemovalReason: (a, b) => 'Reason required to remove a listing ' + (b ? 'on' : 'off'),
    donorVerification: (a, b) => 'Needed to post: ' + VERIF_LABEL[b] + ' (was ' + VERIF_LABEL[a] + ')',
    maxPerReservation: (a, b) => 'Largest reservation: ' + b + ' units (was ' + a + ')',
    holdUntilWindowEnd: (a, b) => 'Hold reservations until the pickup window ends ' + (b ? 'on' : 'off'),
    defaultExpiryHours: (a, b) => 'Default expiry: ' + b + ' hours (was ' + a + ')',
    expiringSoonHours: (a, b) => '“Expires soon” warning: ' + b + ' hours before (was ' + a + ')'
  };
  const VERIF_LABEL = { NONE: 'nothing', EMAIL: 'a verified email', ID: 'a verified account' };

  function mount(main) {
    s = {};
    const nav = h('nav', { class: 'settings-nav', 'aria-label': 'Settings sections' },
      SECTIONS.map(([id, label]) => h('button', { type: 'button', class: 'settings-nav__item', dataset: { target: id }, onClick: () => {
        const el = document.getElementById('set-' + id);
        if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'start' }); const head = el.querySelector('h2'); if (head) head.focus({ preventScroll: true }); }
      } }, label)));
    s.sections = h('div', { class: 'settings-sections' }, Array.from({ length: 3 }, () => h('div', { class: 'card', style: { padding: '20px', display: 'grid', gap: '14px' } }, skeleton('30%', '16px'), skeleton('100%', '40px', 'skeleton--block'), skeleton('100%', '40px', 'skeleton--block'))));
    main.append(
      UI.pageHead({ title: 'Settings', sub: 'Your admin account and how the dashboard handles moderation.' }),
      h('div', { class: 'settings-layout' }, nav, s.sections));
    s.nav = nav;
    load();
  }

  async function load() {
    let st;
    try { st = await data.settings(); } catch (err) {
      if (s) clear(s.sections).append(h('div', { class: 'card' }, errorState(load, 'settings')));
      return;
    }
    if (!s) return;
    clear(s.sections).append(
      accountSection(st.account),
      notificationsSection(st.notifications),
      moderationSection(st.moderation),
      systemSection(st.system),
      sampleSection());
    watchSections();
  }

  function watchSections() {
    if (!('IntersectionObserver' in window)) return;
    const items = s.nav.querySelectorAll('.settings-nav__item');
    const io = new IntersectionObserver((entries) => {
      entries.forEach((e) => {
        if (!e.isIntersecting) return;
        const id = e.target.id.replace('set-', '');
        items.forEach((b) => b.classList.toggle('is-active', b.dataset.target === id));
      });
    }, { rootMargin: '-30% 0px -60% 0px' });
    s.sections.querySelectorAll('.settings-section').forEach((sec) => io.observe(sec));
    s.io = io;
  }

  /** A settings card whose Save button turns on once something changed. */
  function sectionCard(id, title, sub, content, onSave) {
    const saveBtn = btn({ label: 'Save changes', variant: 'primary', disabled: !!onSave });
    const card = h('section', { class: 'card settings-section', id: 'set-' + id, 'aria-labelledby': 'set-' + id + '-title' },
      h('div', { class: 'card__head' }, h('div', { class: 'card__head-text' },
        h('h2', { class: 'section-title', id: 'set-' + id + '-title', tabindex: '-1' }, title), sub ? h('p', { class: 'section-sub' }, sub) : null)),
      h('div', { class: 'card__body' }, content),
      onSave ? h('div', { class: 'card__foot' }, saveBtn) : null);
    if (onSave) {
      card.addEventListener('input', () => { saveBtn.disabled = false; });
      card.addEventListener('change', () => { saveBtn.disabled = false; });
      saveBtn.addEventListener('click', async () => {
        try {
          const changed = await busy(saveBtn, onSave);
          saveBtn.disabled = true;
          toast(changed ? 'Settings saved.' : 'Nothing to save.', { sub: changed ? 'Sample only: kept in this page until you reload.' : null, tone: changed ? 'success' : 'info' });
        } catch (err) {
          if (err && err.validation) return;
          toast('Unable to save settings. Please try again.', { tone: 'error' });
        }
      });
    }
    return card;
  }
  async function save(section, values) {
    const res = await data.saveSettings(section, values, (key, a, b) => (DESCRIBE[key] ? DESCRIBE[key](a, b) : key));
    return res.changed;
  }
  function numberField(id, label, value, min, max, hint) {
    const input = h('input', { class: 'input', id, type: 'number', inputmode: 'numeric', min: String(min), max: String(max), value: String(value), style: { maxWidth: '140px' }, 'aria-describedby': hint ? id + '-hint' : null });
    return { input, el: UI.field({ label, input, hint }) };
  }
  function readNumber(input, min, max) {
    const v = Math.round(Number(input.value));
    const ok = Number.isFinite(v) && v >= min && v <= max;
    input.classList.toggle('is-invalid', !ok);
    if (!ok) { input.setAttribute('aria-invalid', 'true'); input.focus(); const e = new Error('invalid'); e.validation = true; toast('Enter a number from ' + min + ' to ' + max + '.', { tone: 'error' }); throw e; }
    input.removeAttribute('aria-invalid');
    return v;
  }

  function accountSection(acc) {
    const name = h('input', { class: 'input', id: 'set-name', value: acc.name, autocomplete: 'name', maxlength: '60' });
    const email = h('input', { class: 'input', id: 'set-email', value: acc.email, readonly: true, 'aria-describedby': 'set-email-hint' });
    const profile = h('div', { class: 'settings-group' },
      h('h3', { class: 'settings-group__title' }, 'Admin profile'),
      h('div', { class: 'form-grid' },
        UI.field({ label: 'Name', input: name }),
        UI.field({ label: 'Email', input: email, hint: 'Your sign-in email. It’s changed through Firebase Authentication, not here.' }),
        h('div', { class: 'field full' }, h('span', { class: 'field__label' }, 'Role'), h('span', null, 'Administrator'))));

    const current = h('input', { class: 'input', id: 'pw-current', type: 'password', autocomplete: 'current-password' });
    const next = h('input', { class: 'input', id: 'pw-new', type: 'password', autocomplete: 'new-password', 'aria-describedby': 'pw-new-hint' });
    const again = h('input', { class: 'input', id: 'pw-again', type: 'password', autocomplete: 'new-password' });
    const pwError = h('p', { class: 'field__error', role: 'alert', hidden: true });
    const pwBtn = btn({ label: 'Update password', icon: 'lock', variant: 'secondary', type: 'submit' });
    const pwForm = h('form', { class: 'settings-group', novalidate: true },
      h('h3', { class: 'settings-group__title' }, 'Change password'),
      h('div', { class: 'form-grid' },
        h('div', { class: 'full' }, UI.field({ label: 'Current password', input: current })),
        UI.field({ label: 'New password', input: next, hint: 'At least 6 characters, as in the app.' }),
        UI.field({ label: 'Repeat new password', input: again })),
      pwError,
      h('div', { class: 'form-actions', style: { marginTop: '16px' } }, pwBtn));
    pwForm.addEventListener('input', (e) => e.stopPropagation());
    pwForm.addEventListener('change', (e) => e.stopPropagation());
    pwForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      pwError.hidden = true;
      let problem = null;
      if (!current.value) problem = [current, 'Enter your current password.'];
      else if (next.value.length < 6) problem = [next, 'Your new password must be at least 6 characters.'];
      else if (next.value !== again.value) problem = [again, 'The new passwords don’t match.'];
      if (problem) { pwError.textContent = problem[1]; pwError.hidden = false; problem[0].focus(); return; }
      await busy(pwBtn, () => new Promise((r) => setTimeout(r, 500)));
      current.value = ''; next.value = ''; again.value = '';
      toast('Password not changed.', { tone: 'info', sub: 'The dashboard isn’t connected to Firebase yet, so this form is only a preview.' });
    });
    return sectionCard('account', 'Account', 'Your admin profile and password.', [profile, pwForm],
      () => save('account', { name: name.value.trim() || acc.name }));
  }

  function notificationsSection(n) {
    const ids = { newReports: 'set-n-reports', verificationRequests: 'set-n-verif', system: 'set-n-system', emailDigest: 'set-n-digest' };
    const content = h('div', null,
      switchRow({ id: ids.newReports, label: 'New reports', hint: 'Alert me when someone reports a listing or a person.', checked: n.newReports }),
      switchRow({ id: ids.verificationRequests, label: 'Verification requests', hint: 'Alert me when a donor sends documents to be verified.', checked: n.verificationRequests }),
      switchRow({ id: ids.system, label: 'System notifications', hint: 'Expired listings, sent announcements and other updates.', checked: n.system }),
      switchRow({ id: ids.emailDigest, label: 'Daily email summary', hint: 'One email each morning with yesterday’s numbers.', checked: n.emailDigest }));
    return sectionCard('notifications', 'Notifications', 'What shows up in Your alerts.', content, () => save('notifications', Object.fromEntries(Object.entries(ids).map(([k, id]) => [k, document.getElementById(id).checked]))));
  }

  function moderationSection(m) {
    const hide = numberField('set-m-hide', 'Hide a listing automatically after', m.autoHideAfterReports, 1, 10, 'Open reports. Admins still decide what happens next.');
    const verif = h('select', { class: 'select', id: 'set-m-verif', style: { maxWidth: '320px' } },
      [['NONE', 'Nothing, anyone can post'], ['EMAIL', 'A verified email'], ['ID', 'A verified account (documents checked)']].map(([v, l]) => h('option', { value: v }, l)));
    verif.value = m.donorVerification;
    const content = h('div', null,
      h('div', { class: 'settings-group' },
        h('h3', { class: 'settings-group__title' }, 'Listings'),
        switchRow({ id: 'set-m-expire', label: 'Expire listings automatically', hint: 'Mark a listing expired when its pickup window ends. The server doesn’t do this yet; the app hides old listings itself.', checked: m.autoExpire }),
        switchRow({ id: 'set-m-reason', label: 'Ask for a reason to remove a listing', hint: 'The reason goes into the audit log.', checked: m.requireRemovalReason }),
        h('div', { style: { paddingTop: '14px', borderTop: '1px solid var(--line)' } }, hide.el)),
      h('div', { class: 'settings-group' },
        h('h3', { class: 'settings-group__title' }, 'Verification requirements'),
        UI.field({ label: 'Donors need this before they can post', input: verif, hint: 'Today anyone with an account can post. ID verification is planned for Phase 6.' })));
    return sectionCard('moderation', 'Moderation', 'Rules for listings and verification.', content, () => save('moderation', {
      autoExpire: document.getElementById('set-m-expire').checked,
      requireRemovalReason: document.getElementById('set-m-reason').checked,
      autoHideAfterReports: readNumber(hide.input, 1, 10),
      donorVerification: verif.value
    }));
  }

  function systemSection(sy) {
    const max = numberField('set-s-max', 'Largest reservation', sy.maxPerReservation, 1, 50, 'Units one person can reserve from a listing at once.');
    const expiry = numberField('set-s-expiry', 'Default listing expiry, in hours', sy.defaultExpiryHours, 1, 168, 'Used when a donor doesn’t set an expiry.');
    const soon = numberField('set-s-soon', 'Show “Expires soon”, hours before', sy.expiringSoonHours, 1, 24, 'The orange label on listings in the app.');
    const content = h('div', null,
      h('div', { class: 'settings-group' },
        h('h3', { class: 'settings-group__title' }, 'Reservations'),
        h('div', { class: 'form-grid' }, max.el),
        h('div', { style: { marginTop: '12px' } }, switchRow({ id: 'set-s-hold', label: 'Hold reservations until the pickup window ends', hint: 'After that, food that wasn’t picked up can go to someone else.', checked: sy.holdUntilWindowEnd }))),
      h('div', { class: 'settings-group' },
        h('h3', { class: 'settings-group__title' }, 'Food expiry'),
        h('div', { class: 'form-grid' }, expiry.el, soon.el)));
    return sectionCard('system', 'System', 'Reservation and expiry defaults.', content, () => save('system', {
      maxPerReservation: readNumber(max.input, 1, 50),
      holdUntilWindowEnd: document.getElementById('set-s-hold').checked,
      defaultExpiryHours: readNumber(expiry.input, 1, 168),
      expiringSoonHours: readNumber(soon.input, 1, 24)
    }));
  }

  function sampleSection() {
    const mode = h('select', { class: 'select', id: 'set-fail', style: { maxWidth: '320px' } },
      [['none', 'Off'], ['actions', 'Make actions fail'], ['loads', 'Make pages fail to load']].map(([v, l]) => h('option', { value: v }, l)));
    mode.value = data.getFailMode();
    mode.addEventListener('change', () => {
      data.setFailMode(mode.value);
      toast(mode.value === 'none' ? 'Failures turned off.' : 'Failures turned on for this tab.', { tone: 'info', sub: mode.value === 'none' ? null : 'Use it to see the error messages. Turn it off here when you’re done.' });
    });
    const reset = btn({ label: 'Reset sample data', icon: 'refresh', variant: 'secondary', onClick: async () => {
      const ok = await confirm({
        title: 'Reset the sample data?', text: 'Everything you changed in this dashboard goes back to the starting examples.',
        confirmLabel: 'Reset', tone: 'danger', iconName: 'refresh',
        action: () => { data.reset(); }
      });
      if (ok) toast('Sample data reset.');
    } });
    return sectionCard('sample', 'Sample data', 'The dashboard isn’t connected to Salu-Salo yet.', [
      notice({ tone: 'warning', text: 'All numbers, people and records are generated examples. Changes last until you reload, and nothing reaches the app.' }),
      h('div', { class: 'settings-group' },
        h('h3', { class: 'settings-group__title' }, 'Try the error messages'),
        UI.field({ label: 'Pretend the server fails', input: mode, hint: 'Only in this browser tab.' })),
      h('div', { class: 'settings-group' }, h('h3', { class: 'settings-group__title' }, 'Start over'), reset)
    ], null);
  }

  return {
    title: 'Settings',
    mount,
    unmount() { if (s && s.io) s.io.disconnect(); s = null; }
  };
})();
