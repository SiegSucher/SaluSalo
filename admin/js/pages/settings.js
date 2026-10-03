/* Settings: the signed-in admin, and Log out. */
window.Pages = window.Pages || {};
window.Pages.settings = (function () {
  'use strict';
  const UI = window.UI;
  const { h, btn, notice, dl, card } = UI;
  const data = window.SaluData;

  function mount(main) {
    const me = data.me();
    main.append(
      UI.pageHead({ title: 'Settings', sub: 'Your admin account.' }),
      card({ title: 'Account', body: h('div', { style: { display: 'grid', gap: '16px' } },
        dl([['Name', me.name], ['Email', me.email], ['Role', 'Administrator']]),
        notice({ tone: 'neutral', text: 'Change your name or password in the Salu-Salo app. Admin access is given and taken back in the database.' }),
        h('div', null, btn({ label: 'Log out', icon: 'logout', variant: 'secondary', onClick: () => window.SaluAuth.signOut() }))) }));
  }
  return { title: 'Settings', mount };
})();
