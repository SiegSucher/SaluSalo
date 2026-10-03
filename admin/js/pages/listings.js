/* Listings: moderate food posts. Admins review, dismiss reports or remove a listing; they don't edit food details. */
window.Pages = window.Pages || {};
window.Pages.listings = (function () {
  'use strict';
  const UI = window.UI;
  const { h, icon, clear, fmt, btn, badge, flagBadge, thumb, identity, notice, dl, section, empty, errorState, skeletonPanel, confirm, toast, busy } = UI;
  const data = window.SaluData;

  let s = null;

  const STATUS_OPTIONS = [['ALL', 'All statuses'], ['ACTIVE', 'Active (available or reserved)'], ['AVAILABLE', 'Available'], ['RESERVED', 'Reserved'], ['CLAIMED', 'Claimed'], ['EXPIRED', 'Expired'], ['REMOVED', 'Removed'], ['REPORTED', 'Reported']];
  const POSTED_OPTIONS = [['ANY', 'Any time'], ['TODAY', 'Today'], ['7D', 'Last 7 days'], ['30D', 'Last 30 days']];

  function qtyText(l) {
    if (l.status === 'AVAILABLE') return fmt.num(l.quantity) + ' of ' + fmt.qty(l.initialQuantity, l.unit) + ' left';
    if (l.status === 'RESERVED') return fmt.qty(l.initialQuantity, l.unit) + ', all reserved';
    return fmt.qty(l.initialQuantity, l.unit);
  }
  function statusCell(l) {
    return h('div', { class: 'badge-row' }, badge('listing', l.status), l.openReports ? flagBadge(l.openReports) : null);
  }

  function mount(main) {
    s = { panelFor: null, donor: window.App.intent.listingsDonor || null };
    window.App.intent.listingsDonor = null;
    s.list = window.ListView({
      what: 'listings', noun: 'listing', caption: 'Food listings',
      defaults: { q: '', status: 'ALL', posted: 'ANY' },
      initial: { donorId: s.donor ? s.donor.id : null },
      isFiltered: (q) => !!q.donorId,
      keepOnReset: () => ({ donorId: null }),
      filters: [
        { key: 'q', type: 'search', label: 'Search listings', placeholder: 'Search food, donor or listing ID' },
        { key: 'status', type: 'select', label: 'Status', options: STATUS_OPTIONS },
        { key: 'posted', type: 'select', label: 'Posted', options: POSTED_OPTIONS }
      ],
      fetch: (q) => data.listings(q),
      open: (l) => window.App.go('#/listings/' + l.id),
      flag: (l) => l.openReports > 0,
      columns: [
        { label: 'Food', className: 'cell-wide', cell: (l) => identity(UI.photo(l.photoUrl, l.category), l.name, l.category + (l.price ? ' · ' + fmt.unitPrice(l.price, l.unit) : ' · Free'), '#/listings/' + l.id) },
        { label: 'Donor', cell: (l) => h('a', { class: 'row-link', href: '#/users/' + l.donor.id, style: { fontWeight: '500' } }, l.donor.name) },
        { label: 'Quantity', className: 'cell-num', cell: (l) => qtyText(l) },
        { label: 'Pickup', className: 'cell-muted', hide: 'tablet', cell: (l) => fmt.window(l.pickupStart, l.pickupEnd) },
        { label: 'Status', cell: statusCell },
        { label: 'Posted', className: 'cell-muted', cell: (l) => fmt.moment(l.createdAt) },
        { label: h('span', { class: 'sr-only' }, 'Actions'), className: 'cell-actions', cell: (l) => rowMenu(l) }
      ],
      card: (l) => UI.rowCard({
        visual: UI.photo(l.photoUrl, l.category), title: l.name, href: '#/listings/' + l.id,
        meta: { sub: l.donor.name, items: [{ icon: 'inventory2', text: qtyText(l) }, { icon: 'schedule', text: fmt.window(l.pickupStart, l.pickupEnd) }] },
        badges: [badge('listing', l.status), l.openReports ? flagBadge(l.openReports) : null, h('span', { class: 'badge badge--outline' }, l.price ? fmt.unitPrice(l.price, l.unit) : 'Free')]
      }),
      emptyState: (q, filtered) => filtered
        ? { iconName: 'search', title: q.q ? 'No listings match “' + q.q + '”' : 'No listings match these filters', text: q.status === 'REPORTED' ? 'No listing has an open report right now.' : 'Try another search or clear the filters.' }
        : { iconName: 'lunchDining', title: 'No listings yet', text: 'Food that donors post in the app appears here.' },
      onLoaded: (res) => renderDonorChip(res)
    });
    main.append(
      UI.pageHead({ title: 'Listings', sub: 'Food posted by donors. Review what’s reported and remove what’s unsafe or misleading.' }),
      s.list.el);
    s.list.load();
  }

  function renderDonorChip(res) {
    clear(s.list.extras);
    if (!s.list.query.donorId) return;
    s.list.extras.append(h('span', { class: 'chip-filter' }, icon('storefront', 18), 'Donor: ' + (res.donorName || s.donor && s.donor.name || 'selected'),
      UI.iconBtn('close', 'Show all donors', () => { s.list.set({ donorId: null }); }, { small: true })));
  }

  function rowMenu(l) {
    return UI.menuButton('Actions for ' + l.name, () => [
      { label: 'View details', icon: 'visibility', onClick: () => window.App.go('#/listings/' + l.id) },
      { label: 'View donor', icon: 'storefront', onClick: () => window.App.go('#/users/' + l.donor.id) },
      l.openReports ? { label: 'Dismiss report' + (l.openReports > 1 ? 's' : ''), icon: 'doNotDisturbOn', onClick: () => dismissReports(l) } : null,
      l.status !== 'REMOVED' ? '-' : null,
      l.status !== 'REMOVED' ? { label: 'Remove listing', icon: 'delete', danger: true, onClick: () => removeListing(l) } : null
    ]);
  }

  /* ---------- Actions ---------- */
  async function removeListing(l) {
    const detail = s.detail && s.detail.id === l.id ? s.detail : null;
    const open = detail ? detail.reservations.filter((r) => r.status === 'RESERVED').length : l.openReservations;
    let result = null;
    const ok = await confirm({
      title: 'Remove this listing?',
      text: 'Are you sure you want to remove this food listing? “' + l.name + '” disappears from the app for everyone.' +
        (open ? ' ' + fmt.plural(open, 'open reservation') + ' will be cancelled, and the recipients alerted.' : ''),
      confirmLabel: 'Remove listing', tone: 'danger', iconName: 'delete',
      reason: { label: 'Reason', required: true, placeholder: 'For example: food was spoiled at pickup' },
      errorText: 'Unable to remove listing. Please try again.',
      action: async (reason) => { result = await data.removeListing(l.id, reason); }
    });
    if (ok) {
      const bits = [];
      if (result && result.cancelled) bits.push(fmt.plural(result.cancelled, 'reservation') + ' cancelled');
      if (result && result.closedReports) bits.push(fmt.plural(result.closedReports, 'report') + ' resolved');
      toast('Listing removed successfully.', { sub: bits.length ? bits.join(' · ') + '.' : null });
      refreshAfterAction();
    }
  }
  async function dismissReports(l) {
    const n = l.openReports;
    const ok = await confirm({
      title: n > 1 ? 'Dismiss these reports?' : 'Dismiss this report?',
      text: 'The listing stays up. ' + (n > 1 ? 'The ' + n + ' open reports are' : 'The report is') + ' closed with your note.',
      confirmLabel: n > 1 ? 'Dismiss reports' : 'Dismiss report', tone: 'primary', iconName: 'doNotDisturbOn',
      reason: { label: 'Note', required: false, placeholder: 'For example: checked with the donor; the food was fine' },
      errorText: 'Unable to dismiss the report. Please try again.',
      action: (note) => data.dismissListingReports(l.id, note)
    });
    if (ok) { toast(n > 1 ? 'Reports dismissed.' : 'Report dismissed.'); refreshAfterAction(); }
  }
  async function markReviewed(l, button) {
    try {
      await busy(button, () => data.markListingReviewed(l.id));
      toast('Marked as reviewed.', { sub: 'Saved in the audit log.' });
      refreshAfterAction();
    } catch (err) {
      toast('Unable to mark this listing as reviewed. Please try again.', { tone: 'error' });
    }
  }
  function refreshAfterAction() {
    if (!s) return;
    s.list.reload();
    if (UI.panelIsOpen() && s.panelFor) openListing(s.panelFor, true);
  }

  /* ---------- Details ---------- */
  async function openListing(id, keep) {
    s.panelFor = id;
    const onClose = () => window.App.closeTo('#/listings');
    if (!keep) UI.openPanel({ label: 'Listing', title: 'Loading…', body: skeletonPanel(), onClose });
    let l;
    try { l = await data.listing(id); } catch (err) {
      UI.openPanel({ label: 'Listing', title: 'Couldn’t load this listing', body: errorState(() => openListing(id, true), 'this listing'), onClose });
      return;
    }
    if (!s || s.panelFor !== id) return;
    if (!l) { UI.openPanel({ label: 'Listing', title: 'Listing not found', body: empty({ iconName: 'lunchDining', title: 'This listing doesn’t exist', text: 'It may have been deleted by its donor.' }), onClose }); return; }
    s.detail = l;
    const openReports = l.reports.filter((r) => r.status === 'OPEN');
    const body = [
      l.photoUrls.length
        ? h('div', { class: 'photo-strip' }, l.photoUrls.map((url, i) => h('a', { href: url, target: '_blank', rel: 'noopener noreferrer', 'aria-label': 'Open photo ' + (i + 1) + ' of ' + l.photoUrls.length },
          h('img', { src: url, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer' }))))
        : thumb(l.category, true, l.photos ? 'The photos couldn’t be loaded' : 'No photo; the app shows a plain tile'),
      h('div', { class: 'badge-row' }, badge('listing', l.status), openReports.length ? flagBadge(openReports.length) : null,
        h('span', { class: 'badge badge--outline' }, icon('sell', 14), l.price ? fmt.unitPrice(l.price, l.unit) : 'Free')),
      openReports.length ? notice({ tone: 'warning', iconName: 'flag', title: fmt.plural(openReports.length, 'open report'),
        text: openReports.map((r) => r.reasonLabel + ' (' + fmt.relative(r.createdAt) + ')').join('; '),
        action: h('a', { class: 'btn btn--secondary btn--sm', href: '#/reports/' + openReports[0].id }, 'Review') }) : null,
      l.status === 'REMOVED' ? notice({ tone: 'neutral', iconName: 'block', title: 'Removed by ' + (l.removedByName || 'an admin'), text: (l.removedReason || 'The donor removed it in the app.') + ' · ' + fmt.dateTime(l.updatedAt) }) : null,
      l.reviewedAt ? notice({ tone: 'info', iconName: 'taskAlt', text: 'Reviewed ' + fmt.relative(l.reviewedAt) + ' by ' + l.reviewedByName + '.' }) : null,
      section('Details', dl([
        ['Donor', h('span', null, h('a', { href: '#/users/' + l.donor.id }, l.donor.name), ' ', l.donor.verification === 'VERIFIED' ? badge('verification', 'VERIFIED') : null)],
        ['Quantity', qtyText(l)],
        ['Price', l.price ? fmt.unitPrice(l.price, l.unit) + ' · pay at pickup' : 'Free'],
        ['Pickup window', fmt.window(l.pickupStart, l.pickupEnd)],
        ['Pickup location', l.pickupLocation],
        ['Handover', data.text.handover[l.handover]],
        ['Allergens', l.allergens],
        ['Storage notes', l.storageNotes],
        ['Category', l.category],
        ['Description', l.description],
        ['Posted', fmt.dateTime(l.createdAt)],
        ['Expires', fmt.dateTime(l.expiresAt)],
        ['Listing ID', h('span', { class: 'mono' }, l.id)]
      ])),
      section('Reservations (' + l.reservations.length + ')', l.reservations.length
        ? h('ul', { class: 'list' }, l.reservations.map((r) => h('li', null, h('a', { class: 'list__item list__item--button', href: '#/reservations/' + r.id, style: { paddingInline: '0' } },
          UI.avatar(r.recipient, 'sm'),
          h('span', { class: 'list__main' }, h('span', { class: 'list__title' }, h('strong', null, r.recipient.name), ' · ' + fmt.qty(r.quantity, l.unit)), h('span', { class: 'list__meta' }, fmt.moment(r.createdAt))),
          h('span', { class: 'list__side' }, badge('reservation', r.status))))))
        : h('p', { class: 'muted' }, 'No one has reserved this listing.')),
      l.reports.length ? section('Reports (' + l.reports.length + ')', h('ul', { class: 'list' }, l.reports.map((r) => h('li', null, h('a', { class: 'list__item list__item--button', href: '#/reports/' + r.id, style: { paddingInline: '0' } },
        h('span', { class: 'icon-bubble icon-bubble--error' }, icon('flag', 18)),
        h('span', { class: 'list__main' }, h('span', { class: 'list__title' }, r.reasonLabel), h('span', { class: 'list__meta' }, fmt.ref(r.id) + ' · by ' + r.reporter.name + ' · ' + fmt.relative(r.createdAt))),
        h('span', { class: 'list__side' }, badge('report', r.status))))))) : null,
      h('p', { class: 'muted', style: { fontSize: 'var(--t-sm)' } }, 'Admins moderate listings; only the donor can edit the food details.')
    ];
    const foot = [];
    if (l.status !== 'REMOVED') {
      const reviewBtn = btn({ label: 'Mark as reviewed', icon: 'taskAlt', variant: 'secondary' });
      reviewBtn.addEventListener('click', () => markReviewed(l, reviewBtn));
      foot.push(reviewBtn);
      if (openReports.length) foot.push(btn({ label: openReports.length > 1 ? 'Dismiss reports' : 'Dismiss report', icon: 'doNotDisturbOn', variant: 'secondary', onClick: () => dismissReports(Object.assign({}, l, { openReports: openReports.length })) }));
      foot.push(h('span', { class: 'spacer' }));
      foot.push(btn({ label: 'Remove listing', icon: 'delete', variant: 'danger', onClick: () => removeListing(Object.assign({}, l, { openReservations: l.reservations.filter((r) => r.status === 'RESERVED').length })) }));
    } else {
      foot.push(btn({ label: 'View donor', icon: 'storefront', variant: 'secondary', href: '#/users/' + l.donor.id }));
    }
    UI.openPanel({ label: 'Listing', title: l.name, sub: 'Posted ' + fmt.relative(l.createdAt) + ' by ' + l.donor.name, body, foot, onClose });
  }

  function update(route) {
    if (!s) return;
    if (route.id) openListing(route.id);
    else if (UI.panelIsOpen()) { s.panelFor = null; UI.closePanel(); }
  }

  return {
    title: 'Listings',
    mount,
    update,
    unmount() { s = null; }
  };
})();
