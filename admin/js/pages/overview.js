/* Overview: what is happening in Salu-Salo right now, on one screen. */
window.Pages = window.Pages || {};
window.Pages.overview = (function () {
  'use strict';
  const { h, icon, clear, fmt, btn, card, stat, notice, badge, avatar, thumb, empty, errorState, skeletonStats, skeletonList, skeleton } = window.UI;
  const data = window.SaluData;

  const EVENT_LOOK = {
    USER_JOINED: ['personAdd', ''], LISTING: ['lunchDining', ''], RESERVED: ['bookmark', 'orange'],
    CLAIMED: ['taskAlt', ''], VERIFICATION: ['verified', 'orange'], REPORT: ['flag', 'error']
  };
  let refs = null;
  let charts = [];

  function mount(main) {
    const refresh = btn({ label: 'Refresh', icon: 'refresh', variant: 'secondary', onClick: () => load(refresh) });
    const stats = h('div', { class: 'stats' }, skeletonStats(4));
    const activityBody = h('div', null, skeletonList(6));
    const reportsBody = h('div', null, skeletonList(2));
    const resBody = h('div', null, skeletonList(5));
    const rescueBody = h('div', { style: { display: 'grid', gap: '16px' } }, skeleton('50%', '40px'), skeleton('100%', '48px', 'skeleton--block'), skeleton('80%'), skeleton('70%'));

    main.append(
      window.UI.pageHead({ title: 'Overview', sub: 'What’s happening in Salu-Salo right now · ' + fmt.weekday(Date.now()), actions: [refresh] }),
      stats,
      h('div', { class: 'grid' },
        h('div', { class: 'span-8 stack' },
          card({ title: 'Recent activity', sub: 'Sign-ups, listings, reservations and reports from the app', className: 'card--flush', body: activityBody }),
          card({ title: 'Reservation activity', sub: 'The latest reservations and where they stand', className: 'card--flush', body: resBody,
            actions: h('a', { class: 'btn btn--ghost btn--sm', href: '#/reservations' }, 'All reservations', icon('chevronRight', 18)) })),
        h('div', { class: 'span-4 stack' },
          card({ title: 'Reports to review', className: 'card--flush', body: reportsBody,
            actions: h('a', { class: 'btn btn--ghost btn--sm', href: '#/reports' }, 'All', icon('chevronRight', 18)) }),
          card({ title: 'Food rescue', sub: 'The last 30 days', body: rescueBody }))));
    refs = { stats, activityBody, reportsBody, resBody, rescueBody };
    load();
  }

  async function load(button) {
    const r = refs;
    let res;
    try {
      res = button ? await window.UI.busy(button, () => data.overview()) : await data.overview();
    } catch (err) {
      if (refs !== r) return;
      clear(r.stats).append(h('div', { class: 'card', style: { gridColumn: '1 / -1' } }, errorState(() => load(), 'the overview')));
      [r.activityBody, r.reportsBody, r.resBody, r.rescueBody].forEach((b) => clear(b).append(h('p', { class: 'muted', style: { padding: '12px 20px' } }, 'Not loaded.')));
      return;
    }
    if (refs !== r) return;
    const c = res.counts;
    clear(r.stats).append(
      stat({ label: 'Total users', value: fmt.num(c.users), iconName: 'group', href: '#/users',
        meta: [h('span', { class: 'delta-up' }, '+' + c.usersThisWeek), ' new this week'] }),
      stat({ label: 'Active listings', value: fmt.num(c.activeListings), iconName: 'lunchDining', href: '#/listings',
        meta: [h('strong', null, fmt.num(c.listingsToday)), ' posted today'] }),
      stat({ label: 'Open reservations', value: fmt.num(c.openReservations), iconName: 'bookmark', href: '#/reservations',
        meta: [h('strong', null, fmt.num(c.reservationsToday)), ' made today · waiting for pickup'] }),
      stat({ label: 'Pending reports', value: fmt.num(c.pendingReports), iconName: 'flag', href: '#/reports', attention: c.pendingReports > 0,
        meta: c.pendingReports ? [h('strong', null, fmt.num(c.reportsToday)), ' new today · need review'] : 'Nothing to review' }));

    renderActivity(r.activityBody, res.activity);
    renderReports(r.reportsBody, res.reports);
    renderReservations(r.resBody, res.reservations);
    renderRescue(r.rescueBody, res.rescue);
  }

  function partsToNodes(parts) {
    return parts.map((p) => (typeof p === 'string' ? p : h('strong', null, p.b)));
  }
  function renderActivity(body, events) {
    clear(body);
    if (!events.length) {
      body.append(empty({ iconName: 'history', title: 'No recent activity', text: 'New sign-ups, listings, reservations and reports from the last three days show up here.' }));
      return;
    }
    body.append(h('ul', { class: 'list' }, events.map((ev) => {
      const [ic, tone] = EVENT_LOOK[ev.type] || ['circle', ''];
      return h('li', null, h('a', { class: 'list__item list__item--button', href: ev.link },
        h('span', { class: 'icon-bubble' + (tone ? ' icon-bubble--' + tone : '') }, icon(ic, 18)),
        h('span', { class: 'list__main' },
          h('span', { class: 'list__title' }, partsToNodes(ev.parts)),
          h('span', { class: 'list__meta' }, ev.meta + ' · ' + fmt.relative(ev.at)))));
    })));
  }
  function renderReports(body, rows) {
    clear(body);
    if (!rows.length) {
      body.append(empty({ iconName: 'taskAlt', title: 'No reports to review', text: 'Reports people send from a listing, a donor’s page or a chat appear here.' }));
      return;
    }
    body.append(h('ul', { class: 'list' }, rows.map((rp) => h('li', null, h('a', { class: 'list__item list__item--button', href: '#/reports/' + rp.id },
      h('span', { class: 'icon-bubble icon-bubble--error' }, icon(rp.targetType === 'LISTING' ? 'lunchDining' : 'person', 18)),
      h('span', { class: 'list__main' },
        h('span', { class: 'list__title' }, h('strong', null, rp.target.name)),
        h('span', { class: 'list__meta' }, rp.reasonLabel + ' · ' + fmt.relative(rp.createdAt))),
      h('span', { class: 'list__side' }, icon('chevronRight', 20)))))));
  }
  function renderReservations(body, rows) {
    clear(body);
    if (!rows.length) {
      body.append(empty({ iconName: 'bookmark', title: 'No reservations yet', text: 'Reservations appear here as soon as someone reserves food in the app.' }));
      return;
    }
    body.append(h('ul', { class: 'list' }, rows.map((r) => h('li', null, h('a', { class: 'list__item list__item--button', href: '#/reservations/' + r.id },
      window.UI.photo(r.listing.photoUrl, r.listing.category),
      h('span', { class: 'list__main' },
        h('span', { class: 'list__title' }, h('strong', null, r.recipient.name), ' reserved ' + fmt.qty(r.quantity, r.listing.unit)),
        h('span', { class: 'list__meta' }, r.listing.name + ' · from ' + r.donor.name + ' · ' + fmt.relative(r.createdAt))),
      h('span', { class: 'list__side' }, window.UI.badge('reservation', r.status)))))));
  }
  function renderRescue(body, rescue) {
    charts.forEach((ch) => ch.destroy && ch.destroy());
    charts = [];
    clear(body);
    const finished = rescue.completed30 + rescue.cancelled30;
    const rate = finished ? rescue.completed30 / finished : 0;
    const sparkBox = h('div');
    body.append(
      h('div', { style: { display: 'grid', gap: '4px' } },
        h('span', { class: 'hero-figure' }, fmt.num(rescue.itemsRescued30)),
        h('span', { class: 'muted' }, 'items picked up instead of thrown away')),
      h('div', { style: { display: 'grid', gap: '4px' } },
        sparkBox,
        h('span', { class: 'muted', style: { fontSize: 'var(--t-xs)' } }, 'Items rescued per week, last 12 weeks')),
      h('div', { style: { display: 'grid', gap: '8px' } },
        h('div', { style: { display: 'flex', justifyContent: 'space-between', gap: '8px' } },
          h('span', null, 'Pickup rate'), h('strong', null, fmt.percent(rate))),
        h('div', { class: 'meter', role: 'meter', 'aria-label': 'Pickup rate', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(Math.round(rate * 100)) },
          h('div', { class: 'meter__fill', style: { width: (rate * 100).toFixed(1) + '%' } })),
        h('span', { class: 'muted', style: { fontSize: 'var(--t-sm)' } }, fmt.num(rescue.completed30) + ' picked up, ' + fmt.num(rescue.cancelled30) + ' cancelled')),
      window.UI.dl([['Completed pickups', fmt.num(rescue.completed30)], ['Active donors', fmt.num(rescue.activeDonors30)]]));
    charts.push(window.SaluCharts.spark(sparkBox, rescue.weeklyItems, { label: 'Items rescued per week over the last 12 weeks, from ' + fmt.num(rescue.weeklyItems[0]) + ' to ' + fmt.num(rescue.weeklyItems[rescue.weeklyItems.length - 1]) }));
  }

  return {
    title: 'Overview',
    mount,
    unmount() { charts.forEach((ch) => ch.destroy && ch.destroy()); charts = []; refs = null; }
  };
})();
