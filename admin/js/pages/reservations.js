/* Reservations: watch what's being reserved and picked up. Admins can't change reservations. */
window.Pages = window.Pages || {};
window.Pages.reservations = (function () {
  'use strict';
  const UI = window.UI;
  const { h, fmt, btn, badge, thumb, identity, notice, dl, section, empty, errorState, skeletonPanel } = UI;
  const data = window.SaluData;

  let s = null;

  function amount(r) {
    return fmt.qty(r.quantity, r.listing.unit) + (r.unitPrice ? ' · ' + fmt.peso(r.total) : ' · Free');
  }

  function mount(main) {
    s = { panelFor: null };
    s.list = window.ListView({
      what: 'reservations', noun: 'reservation', caption: 'Reservations',
      defaults: { q: '', status: 'ALL', date: 'ANY' },
      filters: [
        { key: 'q', type: 'search', label: 'Search reservations', placeholder: 'Search food, donor, recipient or ID' },
        { key: 'status', type: 'select', label: 'Status', options: [['ALL', 'All statuses'], ['RESERVED', 'Reserved'], ['CLAIMED', 'Claimed'], ['CANCELLED', 'Cancelled']] },
        { key: 'date', type: 'select', label: 'Reserved', options: [['ANY', 'Any time'], ['TODAY', 'Today'], ['7D', 'Last 7 days'], ['30D', 'Last 30 days']] }
      ],
      fetch: (q) => data.reservations(q),
      open: (r) => window.App.go('#/reservations/' + r.id),
      columns: [
        { label: 'Food', className: 'cell-wide', cell: (r) => identity(UI.photo(r.listing.photoUrl, r.listing.category), r.listing.name, r.id, '#/reservations/' + r.id) },
        { label: 'Donor', cell: (r) => h('a', { class: 'row-link', href: '#/users/' + r.donor.id, style: { fontWeight: '500' } }, r.donor.name) },
        { label: 'Recipient', cell: (r) => h('a', { class: 'row-link', href: '#/users/' + r.recipient.id, style: { fontWeight: '500' } }, r.recipient.name) },
        { label: 'Quantity', className: 'cell-num', cell: amount },
        { label: 'Reserved', className: 'cell-muted', cell: (r) => fmt.moment(r.createdAt) },
        { label: 'Status', cell: (r) => badge('reservation', r.status) }
      ],
      card: (r) => UI.rowCard({
        visual: UI.photo(r.listing.photoUrl, r.listing.category), title: r.listing.name, href: '#/reservations/' + r.id,
        meta: { sub: r.recipient.name + ' · from ' + r.donor.name, items: [{ icon: 'inventory2', text: amount(r) }, { icon: 'schedule', text: fmt.moment(r.createdAt) }] },
        badges: [badge('reservation', r.status)]
      }),
      emptyState: (q, filtered) => filtered
        ? { iconName: 'search', title: q.q ? 'No reservations match “' + q.q + '”' : 'No reservations match these filters', text: 'Try another search or a longer time range.' }
        : { iconName: 'bookmark', title: 'No reservations yet', text: 'Reservations appear here as soon as someone reserves food in the app.' }
    });
    main.append(
      UI.pageHead({ title: 'Reservations', sub: 'Who reserved what, and whether it was picked up.' }),
      notice({ tone: 'info', iconName: 'visibility', text: 'Admins can follow reservations here but can’t change them. Recipients cancel and donors confirm pickups in the app.' }),
      s.list.el);
    s.list.load();
  }

  async function openReservation(id, keep) {
    s.panelFor = id;
    const onClose = () => window.App.closeTo('#/reservations');
    if (!keep) UI.openPanel({ label: 'Reservation', title: 'Loading…', body: skeletonPanel(), onClose });
    let r;
    try { r = await data.reservation(id); } catch (err) {
      UI.openPanel({ label: 'Reservation', title: 'Couldn’t load this reservation', body: errorState(() => openReservation(id, true), 'this reservation'), onClose });
      return;
    }
    if (!s || s.panelFor !== id) return;
    if (!r) { UI.openPanel({ label: 'Reservation', title: 'Reservation not found', body: empty({ iconName: 'bookmark', title: 'This reservation doesn’t exist', text: 'Check the link and try again.' }), onClose }); return; }
    const body = [
      h('div', { class: 'panel-hero' }, UI.photo(r.listing.photoUrl, r.listing.category),
        h('div', { class: 'panel-hero__text' },
          h('span', { class: 'panel-hero__name' }, r.listing.name),
          h('div', { class: 'badge-row' }, badge('reservation', r.status), r.listingStatus === 'REMOVED' ? badge('listing', 'REMOVED', 'Listing removed') : null))),
      section('Reservation', dl([
        ['Recipient', h('a', { href: '#/users/' + r.recipient.id }, r.recipient.name)],
        ['Donor', h('a', { href: '#/users/' + r.donor.id }, r.donor.name)],
        ['Quantity', fmt.qty(r.quantity, r.listing.unit)],
        ['Cost', r.unitPrice ? fmt.peso(r.total) + ' (' + r.quantity + ' × ' + fmt.peso(r.unitPrice) + ') · pay at pickup' : 'Free'],
        ['Reserved', fmt.dateTime(r.createdAt)],
        ['Status', r.status === 'CANCELLED' ? r.timeline[r.timeline.length - 1].title : ({ RESERVED: 'Reserved, waiting for pickup', CLAIMED: 'Claimed (picked up)' })[r.status]],
        ['Pick up by', r.pickupDeadline ? fmt.dateTime(r.pickupDeadline) : null],
        ['Pickup window', fmt.window(r.pickupStart, r.pickupEnd)],
        ['Pickup location', r.pickupLocation],
        ['Handover', data.text.handover[r.handover] || null],
        ['Reservation ID', h('span', { class: 'mono' }, r.id)]
      ])),
      section('Timeline', UI.timeline(r.timeline)),
      h('p', { class: 'muted', style: { fontSize: 'var(--t-sm)' } }, 'Monitoring only. The recipient can cancel in the app while it’s reserved; the donor marks it picked up or releases it, and it expires by itself after its pickup time.')
    ];
    const foot = [
      btn({ label: 'View listing', icon: 'lunchDining', variant: 'secondary', href: '#/listings/' + r.listing.id }),
      btn({ label: 'View recipient', icon: 'person', variant: 'ghost', href: '#/users/' + r.recipient.id })
    ];
    UI.openPanel({ label: 'Reservation', title: r.listing.name, sub: r.id + ' · reserved ' + fmt.relative(r.createdAt), body, foot, onClose });
  }

  function update(route) {
    if (!s) return;
    if (route.id) openReservation(route.id);
    else if (UI.panelIsOpen()) { s.panelFor = null; UI.closePanel(); }
  }

  return {
    title: 'Reservations',
    mount,
    update,
    unmount() { s = null; }
  };
})();
