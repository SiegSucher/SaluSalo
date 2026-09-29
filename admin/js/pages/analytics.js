/* Analytics: is Salu-Salo helping reduce food waste? One range filter scopes every number and chart. */
window.Pages = window.Pages || {};
window.Pages.analytics = (function () {
  'use strict';
  const UI = window.UI;
  const { h, clear, fmt, stat, errorState, skeletonStats, skeleton } = UI;
  const Charts = window.SaluCharts;
  const data = window.SaluData;

  let s = null;
  const RANGES = [[4, 'Last 4 weeks'], [8, 'Last 8 weeks'], [12, 'Last 12 weeks']];

  function mount(main) {
    s = { weeks: 12, charts: [], token: 0 };
    const seg = h('div', { class: 'segmented', role: 'group', 'aria-label': 'Time range' },
      RANGES.map(([w, label]) => h('button', { type: 'button', 'aria-pressed': String(w === s.weeks), dataset: { weeks: String(w) } }, label)));
    seg.addEventListener('click', (e) => {
      const b = e.target.closest('button');
      if (!b) return;
      s.weeks = Number(b.dataset.weeks);
      seg.querySelectorAll('button').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
      load();
    });
    s.kpis = h('div', { class: 'stats stats--7' }, skeletonStats(7));
    s.chartsEl = h('div', { class: 'grid' }, Array.from({ length: 4 }, () => h('div', { class: 'span-6 card', style: { padding: '20px', display: 'grid', gap: '12px' } }, skeleton('40%', '16px'), skeleton('60%'), skeleton('100%', '200px', 'skeleton--block'))));
    s.region = h('div', { style: { display: 'grid', gap: '24px' }, 'aria-live': 'off' }, s.kpis, s.chartsEl);
    main.append(
      UI.pageHead({ title: 'Analytics', sub: 'Is Salu-Salo helping reduce food waste? Weekly numbers from the app.' }),
      h('div', { class: 'filters', style: { alignItems: 'center' } }, seg, h('span', { class: 'results-meta' }, 'Weeks end today.')),
      s.region,
      h('p', { class: 'muted', style: { fontSize: 'var(--t-sm)' } }, 'Items are counted in each listing’s own unit: pieces, packs, meals, boxes or kilograms.'));
    load();
  }

  function destroyCharts() { s.charts.forEach((c) => c.destroy && c.destroy()); s.charts = []; }

  async function load() {
    const token = ++s.token;
    s.region.style.opacity = s.loaded ? '0.5' : '';
    s.region.setAttribute('aria-busy', 'true');
    let res;
    try { res = await data.analytics({ weeks: s.weeks }); } catch (err) {
      if (!s || token !== s.token) return;
      destroyCharts();
      clear(s.kpis).append(h('div', { class: 'card', style: { gridColumn: '1 / -1' } }, errorState(load, 'analytics')));
      clear(s.chartsEl);
      s.region.style.opacity = '';
      s.region.removeAttribute('aria-busy');
      return;
    }
    if (!s || token !== s.token) return;
    s.loaded = true;
    s.region.style.opacity = '';
    s.region.removeAttribute('aria-busy');
    render(res);
  }

  function delta(now, before) {
    if (before == null) return null;
    if (!before) return now ? h('span', { class: 'delta-up' }, 'New') : null;
    const change = (now - before) / before;
    const pct = Math.round(change * 100);
    return h('span', { class: pct >= 0 ? 'delta-up' : '', style: pct < 0 ? { color: 'var(--ink-2)', fontWeight: '600' } : null }, (pct >= 0 ? '+' : '−') + Math.abs(pct) + '%');
  }

  function render(res) {
    destroyCharts();
    const k = res.kpis;
    const p = res.previous;
    const range = RANGES.find(([w]) => w === s.weeks)[1].toLowerCase();
    const vs = p ? ' vs previous ' + s.weeks + ' weeks' : ' in the ' + range;
    const tile = (label, value, iconName, prev, extra) => stat({
      label, value: fmt.num(value), iconName,
      meta: p ? [delta(value, prev), vs] : (extra || 'in the ' + range)
    });
    clear(s.kpis).append(
      tile('Listings posted', k.listings, 'lunchDining', p && p.listings),
      tile('Reservations made', k.reservations, 'bookmark', p && p.reservations),
      tile('Completed pickups', k.completed, 'taskAlt', p && p.completed),
      tile('Items rescued', k.itemsRescued, 'eco', p && p.itemsRescued),
      tile('Expired listings', k.expired, 'schedule', p && p.expired, 'ended with food left'),
      tile('Active donors', k.activeDonors, 'storefront', p && p.activeDonors, 'posted at least once'),
      tile('Active recipients', k.activeRecipients, 'person', p && p.activeRecipients, 'reserved at least once'));

    const labels = res.weekStarts.map((t) => fmt.day(t).replace(/, \d{4}$/, ''));
    const tipHead = (i) => 'Week of ' + labels[i];
    const green = 'var(--green)';
    const ser = res.series;
    const first = (arr) => arr[0];
    const last = (arr) => arr[arr.length - 1];
    const trend = (arr, noun) => {
      const a = first(arr);
      const b = last(arr);
      if (!a) return fmt.num(b) + ' ' + noun + ' in the latest week.';
      const pct = Math.round(((b - a) / a) * 100);
      return fmt.num(b) + ' ' + noun + ' in the latest week, ' + (pct >= 0 ? 'up ' + pct + '%' : 'down ' + Math.abs(pct) + '%') + ' from the first week shown.';
    };
    const finished = ser.completed.reduce((x, y) => x + y, 0) + ser.cancelled.reduce((x, y) => x + y, 0);
    const pickedShare = finished ? ser.completed.reduce((x, y) => x + y, 0) / finished : 0;
    const sc = res.statusCounts;
    const totalListed = Object.values(sc).reduce((x, y) => x + y, 0);

    const listingsCard = Charts.card({
      title: 'Listings over time', takeaway: trend(ser.listings, 'listings'),
      build: (el) => Charts.line(el, { labels, series: [{ name: 'Listings posted', values: ser.listings, color: green }], ariaLabel: 'Listings posted per week. ' + trend(ser.listings, 'listings'), tipHead }),
      tableFor: () => Charts.table({ labels, series: [{ name: 'Listings posted', values: ser.listings }] })
    });
    const reservationsCard = Charts.card({
      title: 'Reservations over time', takeaway: trend(ser.reservations, 'reservations'),
      build: (el) => Charts.line(el, { labels, series: [{ name: 'Reservations made', values: ser.reservations, color: green }], ariaLabel: 'Reservations made per week. ' + trend(ser.reservations, 'reservations'), tipHead }),
      tableFor: () => Charts.table({ labels, series: [{ name: 'Reservations made', values: ser.reservations }] })
    });
    const outcomeSeries = [
      { name: 'Picked up', values: ser.completed, color: green },
      { name: 'Cancelled', values: ser.cancelled, color: 'var(--chart-muted)' }];
    const outcomeCard = Charts.card({
      title: 'Completed vs cancelled reservations',
      takeaway: fmt.percent(pickedShare) + ' of finished reservations were picked up; the rest were cancelled.',
      legendEl: Charts.legend(outcomeSeries),
      build: (el) => Charts.columns(el, { labels, series: outcomeSeries, ariaLabel: 'Reservations picked up and cancelled per week. ' + fmt.percent(pickedShare) + ' were picked up.', tipHead }),
      tableFor: () => Charts.table({ labels, series: outcomeSeries })
    });
    const rescuedCard = Charts.card({
      title: 'Food rescued over time', takeaway: fmt.num(ser.itemsRescued.reduce((x, y) => x + y, 0)) + ' items picked up in the ' + range + '.',
      build: (el) => Charts.line(el, { labels, series: [{ name: 'Items rescued', values: ser.itemsRescued, color: green }], ariaLabel: 'Items rescued per week. ' + trend(ser.itemsRescued, 'items'), tipHead }),
      tableFor: () => Charts.table({ labels, series: [{ name: 'Items rescued', values: ser.itemsRescued }] })
    });
    const statusItems = [
      { label: 'Claimed', value: sc.CLAIMED, iconName: 'taskAlt' },
      { label: 'Available', value: sc.AVAILABLE, iconName: 'checkCircle' },
      { label: 'Reserved', value: sc.RESERVED, iconName: 'bookmark' },
      { label: 'Expired', value: sc.EXPIRED, iconName: 'schedule' },
      { label: 'Removed', value: sc.REMOVED, iconName: 'block' }];
    const statusCard = Charts.card({
      title: 'Listing status distribution',
      takeaway: fmt.percent(totalListed ? sc.CLAIMED / totalListed : 0) + ' of the ' + fmt.num(totalListed) + ' listings posted in this period were fully claimed; ' + fmt.percent(totalListed ? sc.EXPIRED / totalListed : 0) + ' expired with food left.',
      build: (el) => Charts.bars(el, { items: statusItems }),
      tableFor: () => Charts.table({ labels: statusItems.map((i) => i.label), series: [{ name: 'Listings', values: statusItems.map((i) => i.value) }], labelHead: 'Status' })
    });
    s.charts.push(listingsCard, reservationsCard, outcomeCard, rescuedCard, statusCard);
    clear(s.chartsEl).append(
      h('div', { class: 'span-6' }, listingsCard.el),
      h('div', { class: 'span-6' }, reservationsCard.el),
      h('div', { class: 'span-6' }, outcomeCard.el),
      h('div', { class: 'span-6' }, rescuedCard.el),
      h('div', { class: 'span-12' }, statusCard.el));
  }

  return {
    title: 'Analytics',
    mount,
    unmount() { if (s) destroyCharts(); s = null; }
  };
})();
