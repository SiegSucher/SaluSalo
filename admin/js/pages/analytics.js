/* Analytics: not connected yet. Overview has the main numbers. */
window.Pages = window.Pages || {};
window.Pages.analytics = (function () {
  'use strict';
  const { h, pageHead, empty } = window.UI;
  return {
    title: 'Analytics',
    mount(main) {
      main.append(pageHead({ title: 'Analytics', sub: 'Trends over time.' }),
        h('div', { class: 'card' }, empty({ iconName: 'monitoring', title: 'Not connected yet', text: 'Charts over time come in a later update. Overview shows the main numbers and the last 30 days of food rescued.', action: { label: 'Go to Overview', href: '#/overview' } })));
    }
  };
})();
