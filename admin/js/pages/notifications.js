/* Notifications (announcements to app users): not connected yet. */
window.Pages = window.Pages || {};
window.Pages.notifications = (function () {
  'use strict';
  const { h, pageHead, empty } = window.UI;
  return {
    title: 'Notifications',
    mount(main) {
      main.append(pageHead({ title: 'Notifications', sub: 'Announcements to people using the app.' }),
        h('div', { class: 'card' }, empty({ iconName: 'notifications', title: 'Not connected yet', text: 'Sending announcements needs a new kind of alert in the Salu-Salo app first. It comes in a later update.' })));
    }
  };
})();
