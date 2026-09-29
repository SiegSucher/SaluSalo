/*
 * SAMPLE DATA ONLY. Nothing here comes from Salu-Salo's database.
 *
 * The admin dashboard is not connected yet. Under the app's security rules an account
 * can read only its own rows, and no admin-only server functions exist, so the pages
 * show these generated examples instead. The records use the real tables' field
 * names and values (food_posts, reservations, reports, notifications), so data.js can
 * later swap them for real queries without changing the pages.
 *
 * People, businesses and addresses are invented; every email uses example.com.
 * A fixed seed makes every load show the same records, placed relative to "now".
 */
window.SaluSample = (function () {
  'use strict';

  const MIN = 60 * 1000;
  const HOUR = 60 * MIN;
  const DAY = 24 * HOUR;

  function mulberry32(seed) {
    let a = seed;
    return function () {
      a |= 0; a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  const FIRST = ['Ana', 'Jose', 'Angelica', 'Paolo', 'Kristine', 'Carlo', 'Bea', 'Miguel', 'Joy', 'Rafael',
    'Liza', 'Enzo', 'Camille', 'Nico', 'Patricia', 'Gabriel', 'Andrea', 'Joshua', 'Nicole', 'Adrian', 'Hazel',
    'Kevin', 'Rhea', 'Paul', 'Grace', 'Jerome', 'Ivy', 'Mateo', 'Sofia', 'Leah', 'Ramon', 'Teresa', 'Dante',
    'Celia', 'Arnel', 'Marites', 'Lorna', 'Vince', 'Trisha', 'Mark', 'Jenny', 'Ronald', 'Aileen', 'Benjie',
    'Cecille', 'Dennis', 'Ella', 'Francis', 'Gina', 'Harold', 'Irene', 'Jomar', 'Karen', 'Lito', 'Mylene',
    'Noel', 'Olive', 'Pia', 'Rico', 'Shiela', 'Tonyo', 'Vilma', 'Wendell', 'Yna', 'Zaldy'];
  const LAST = ['Reyes', 'Garcia', 'Mendoza', 'Bautista', 'Villanueva', 'Ramos', 'Aquino', 'Castillo', 'Flores',
    'Gonzales', 'Torres', 'Navarro', 'Soriano', 'Domingo', 'Pascual', 'Salazar', 'Manalo', 'Lim', 'Tan', 'Cruz',
    'Rivera', 'Valdez', 'Fernandez', 'Aguilar', 'Del Rosario', 'Santiago', 'Morales', 'Ocampo', 'Estrada',
    'Lopez', 'Castro', 'De Guzman', 'Panganiban', 'Macaraeg', 'Dizon', 'Serrano', 'Tolentino', 'Evangelista'];
  const BUSINESSES = [
    ["Aling Nena's Kitchen", 'Carinderia'], ['Bayanihan Bakeshop', 'Bakery'], ['Green Basket Grocery', 'Grocery'],
    ['Kusina ni Lola Ising', 'Restaurant'], ['Lutong Bahay Express', 'Restaurant'], ["Mang Ben's Sari-Sari", 'Sari-sari store'],
    ['FreshPick Produce Stall', 'Market stall'], ['Carinderia Rosario', 'Carinderia'], ['Café Tres Marias', 'Café'],
    ["Mama Lou's Catering", 'Caterer'], ['Palengke Pantry', 'Grocery'], ['Pan de Coco House', 'Bakery'],
    ['Sabor Filipino Grill', 'Restaurant'], ['Tindahan Uno Mart', 'Grocery'], ['Bahay Kubo Veggies', 'Market stall'],
    ["Kape't Tinapay", 'Café'], ['Masarap Meals Commissary', 'Caterer'], ["Lola Cora's Kakanin", 'Bakery'],
    ['Sunrise Mini Mart', 'Grocery'], ['Isla Fruit Stand', 'Market stall'], ['Hapag Kainan', 'Restaurant'],
    ['Panaderia Liwayway', 'Bakery']];
  const PLACES = [
    ['Brgy. San Isidro', 'Quezon City'], ['Brgy. Malanday', 'Marikina'], ['Brgy. Kapitolyo', 'Pasig'],
    ['Brgy. Poblacion', 'Makati'], ['Brgy. Santo Niño', 'Marikina'], ['Brgy. Bagong Silang', 'Caloocan'],
    ['Brgy. Sampaloc', 'Manila'], ['Brgy. Addition Hills', 'Mandaluyong'], ['Brgy. Western Bicutan', 'Taguig'],
    ['Brgy. Malinta', 'Valenzuela'], ['Brgy. Batasan Hills', 'Quezon City'], ['Brgy. Tondo', 'Manila']];

  // name, category, unit, quantity range, price range per unit (₱), allergens, storage, days it keeps
  const FOODS = [
    ['Pandesal', 'Bread & pastries', 'pcs', [12, 60], [3, 5], 'Wheat (gluten), milk', 'Keep covered at room temperature; best eaten today.', 1],
    ['Ensaymada', 'Bread & pastries', 'packs', [3, 12], [45, 90], 'Wheat (gluten), milk, eggs, butter', 'Room temperature; eat within 2 days.', 2],
    ['Spanish bread', 'Bread & pastries', 'pcs', [10, 40], [5, 8], 'Wheat (gluten), milk, butter', 'Keep in a closed bag at room temperature.', 1],
    ['Ube cheese pandesal', 'Bread & pastries', 'pcs', [10, 30], [10, 15], 'Wheat (gluten), milk, cheese', 'Keep covered; reheat briefly if firm.', 1],
    ['Bread loaves', 'Bread & pastries', 'pcs', [3, 12], [45, 65], 'Wheat (gluten), milk', 'Room temperature in a closed bag.', 2],
    ['Chicken adobo rice meals', 'Cooked meals', 'meals', [5, 25], [45, 80], 'Soy', 'Refrigerate within 2 hours; reheat until steaming.', 1],
    ['Pancit canton', 'Cooked meals', 'boxes', [3, 10], [60, 120], 'Wheat (gluten), soy, shrimp', 'Refrigerate; eat within the day.', 1],
    ['Vegetable lumpia', 'Cooked meals', 'pcs', [10, 40], [8, 12], 'Wheat (gluten), soy', 'Refrigerate; reheat in a pan or oven.', 1],
    ['Arroz caldo', 'Cooked meals', 'meals', [5, 20], [35, 60], 'No common allergens', 'Refrigerate; reheat before eating.', 1],
    ['Sinigang na baboy', 'Cooked meals', 'meals', [4, 15], [60, 90], 'No common allergens', 'Refrigerate; reheat until boiling.', 1],
    ['Tomatoes', 'Vegetables', 'kg', [2, 12], [30, 50], 'No common allergens', 'Cool, dry place; soft but good for cooking.', 3],
    ['Kangkong bundles', 'Vegetables', 'packs', [5, 20], [10, 20], 'No common allergens', 'Keep cool and damp; use within 2 days.', 2],
    ['Pechay', 'Vegetables', 'packs', [4, 16], [15, 25], 'No common allergens', 'Refrigerate in a loose bag.', 3],
    ['Eggplant', 'Vegetables', 'kg', [2, 8], [40, 60], 'No common allergens', 'Cool, dry place.', 4],
    ['Lakatan bananas', 'Fruit', 'kg', [2, 10], [40, 70], 'No common allergens', 'Room temperature; very ripe.', 2],
    ['Carabao mangoes', 'Fruit', 'kg', [2, 8], [60, 110], 'No common allergens', 'Room temperature; eat within 2 days.', 2],
    ['Calamansi', 'Fruit', 'kg', [1, 5], [50, 80], 'No common allergens', 'Refrigerate for up to a week.', 5],
    ['Canned sardines', 'Packaged food', 'pcs', [6, 24], [18, 25], 'Fish', 'Sealed cans; best-before date printed on each can.', 6],
    ['Instant noodles', 'Packaged food', 'packs', [10, 40], [10, 14], 'Wheat (gluten), soy', 'Dry storage; close to the best-before date.', 6],
    ['Rice (well-milled)', 'Packaged food', 'kg', [5, 25], [40, 50], 'No common allergens', 'Dry, sealed container.', 7],
    ['Assorted biscuits', 'Packaged food', 'packs', [5, 20], [12, 25], 'Wheat (gluten), milk', 'Dry storage; unopened packs.', 6],
    ['Kakanin assortment', 'Other', 'boxes', [2, 8], [80, 150], 'Coconut, glutinous rice', 'Room temperature; eat within the day.', 1],
    ['Leche flan', 'Other', 'pcs', [2, 8], [60, 120], 'Eggs, milk', 'Keep refrigerated.', 2]];
  // What each kind of donor tends to post (indexes into FOODS).
  const MENU = {
    Bakery: [0, 1, 2, 3, 4, 21], Café: [1, 3, 4, 21, 22], Carinderia: [5, 7, 8, 9, 6],
    Restaurant: [5, 6, 7, 9, 22], Caterer: [5, 6, 7, 8, 21], Grocery: [17, 18, 19, 20, 10, 14],
    'Sari-sari store': [17, 18, 20, 19], 'Market stall': [10, 11, 12, 13, 14, 15, 16],
    Household: [0, 4, 5, 8, 10, 12, 14, 15, 16, 18, 19, 21, 22]
  };

  function create(now) {
    now = now || Date.now();
    const rand = mulberry32(20260929);
    const int = (lo, hi) => lo + Math.floor(rand() * (hi - lo + 1));
    const pick = (list) => list[Math.floor(rand() * list.length)];
    const chance = (p) => rand() < p;
    const roundTo = (ts, step) => Math.round(ts / step) * step;
    const within = (a, b) => a + Math.floor(rand() * Math.max(1, b - a));
    const slug = (s) => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '.').replace(/^\.|\.$/g, '');

    /* ---------- People ---------- */
    const users = [];
    const usedNames = new Set();
    let userSeq = 1000;
    function personName() {
      for (;;) {
        const name = pick(FIRST) + ' ' + pick(LAST);
        if (!usedNames.has(name)) { usedNames.add(name); return name; }
      }
    }
    function addUser(u) {
      const place = u.place || pick(PLACES);
      const user = Object.assign({
        id: 'U-' + (++userSeq),
        org: false,
        kind: u.role === 'RECIPIENT' ? null : 'Household',
        barangay: place[0],
        city: place[1],
        emailVerified: chance(0.86),
        verification: 'NONE',
        request: null,
        accountStatus: 'ACTIVE',
        suspension: null,
        suspendAt: null,
        joinedAt: now - int(1, 125) * DAY - int(0, DAY),
        lastActiveAt: null
      }, u);
      delete user.place;
      if (!user.email) user.email = slug(user.name) + '@example.com';
      usedNames.add(user.name);
      users.push(user);
      return user;
    }

    const you = addUser({ name: 'Salu-Salo Admin', email: 'admin@example.com', role: 'ADMIN', kind: 'Staff', emailVerified: true, verification: 'VERIFIED', joinedAt: now - 150 * DAY, isYou: true });
    const carla = addUser({ name: 'Carla Reyes', role: 'ADMIN', kind: 'Staff', emailVerified: true, verification: 'VERIFIED', joinedAt: now - 132 * DAY });
    const jon = addUser({ name: 'Jon Mercado', role: 'ADMIN', kind: 'Staff', emailVerified: true, verification: 'VERIFIED', joinedAt: now - 118 * DAY });
    const admins = [you, carla, jon];

    const businesses = BUSINESSES.map(([name, kind]) => addUser({
      name, kind, org: true, role: 'DONOR', emailVerified: true,
      email: slug(name.replace(/'/g, '')) + '@example.com',
      joinedAt: now - int(24, 128) * DAY - int(0, DAY)
    }));
    const households = [];
    for (let i = 0; i < 48; i++) households.push(addUser({ name: personName(), role: 'DONOR' }));
    const recipients = [];
    recipients.push(addUser({ name: 'Maria Santos', role: 'RECIPIENT', emailVerified: false, joinedAt: now - 2 * MIN, place: PLACES[0] }));
    recipients.push(addUser({ name: 'Juan Dela Cruz', role: 'RECIPIENT', emailVerified: true, joinedAt: now - 41 * DAY, place: PLACES[0] }));
    // More of this week's sign-ups, so "new this week" has a realistic count.
    const fresh = [37 * MIN, 3 * HOUR + 12 * MIN, 9 * HOUR, 20 * HOUR, 26 * HOUR, 2 * DAY + 3 * HOUR, 3 * DAY, 3 * DAY + 7 * HOUR, 4 * DAY + 2 * HOUR, 5 * DAY + 5 * HOUR, 6 * DAY + HOUR];
    for (const ago of fresh) recipients.push(addUser({ name: personName(), role: 'RECIPIENT', joinedAt: now - ago }));
    while (recipients.length < 175) {
      recipients.push(addUser({ name: personName(), role: 'RECIPIENT', joinedAt: now - int(8, 125) * DAY - int(0, DAY) }));
    }
    const donors = businesses.concat(households);

    // Accounts that were suspended (decided up front so nothing happens after it).
    const suspendedPlan = [
      [households[5], 'SCAM', 'Asked recipients for payment before pickup and never showed up.', 12],
      [recipients[60], 'NO_SHOW', 'Reserved large amounts many times without picking them up.', 18],
      [recipients[88], 'ABUSIVE', 'Threatening messages to a donor in chat.', 25]];
    for (const [u, , , daysAgo] of suspendedPlan) {
      u.joinedAt = Math.min(u.joinedAt, now - (daysAgo + 20) * DAY);
      u.suspendAt = now - daysAgo * DAY;
    }
    const canActAt = (u, at) => at > u.joinedAt && !(u.suspendAt && at > u.suspendAt);

    /* Verification requests (sample: ID verification is Phase 6 and has no table yet). */
    const DOCS = {
      org: [['Business permit', 'Mayor’s permit, current year'], ['DTI or SEC registration', 'Registration certificate']],
      person: [['Valid government ID', 'UMID, driver’s license or passport'], ['Proof of address', 'Barangay certificate or utility bill']]
    };
    function makeRequest(user, submittedAt, status, decidedBy) {
      const docs = user.org ? DOCS.org : DOCS.person;
      const decidedAt = status === 'PENDING' ? null : Math.min(now - HOUR, submittedAt + int(3, 30) * HOUR);
      user.verification = status;
      user.request = {
        submittedAt,
        type: user.org ? 'Food business' : 'Household donor',
        businessName: user.org ? user.name : null,
        businessType: user.org ? user.kind : null,
        address: user.barangay + ', ' + user.city,
        documents: docs.map(([name, detail], i) => ({ name, detail, file: slug(name) + (i ? '.pdf' : '.jpg'), last4: String(int(1000, 9999)) })),
        note: user.org
          ? pick(['We post our unsold bread and meals every evening.', 'Surplus from our lunch service, packed the same day.', 'Mostly produce that is still good but can’t be sold.'])
          : pick(['I cook extra on weekends and share with neighbours.', 'Sharing extra groceries from our family sari-sari store.', 'Fruit from our backyard trees.']),
        decidedAt,
        decidedBy: decidedBy ? decidedBy.id : null,
        decisionNote: status === 'REJECTED' ? 'The permit photo is unreadable; asked for a clearer copy.' : null
      };
    }
    function decidedRequest(u, status, by) {
      const submitted = Math.min(u.joinedAt + int(1, 6) * DAY, now - int(2, 6) * DAY);
      if (submitted <= u.joinedAt) return;
      makeRequest(u, submitted, status, by);
    }
    const pendingOrgs = [businesses[7], businesses[16], businesses[21]]; // Carinderia Rosario, Masarap Meals, Panaderia Liwayway
    const pendingPeople = [households[3], households[11]];
    const pendingAgo = [3 * HOUR + 10 * MIN, 26 * HOUR, 50 * MIN, 5 * HOUR + 40 * MIN, 2 * DAY + 4 * HOUR];
    pendingOrgs.concat(pendingPeople).forEach((u, i) => {
      u.joinedAt = Math.min(u.joinedAt, now - 20 * DAY);
      makeRequest(u, now - pendingAgo[i], 'PENDING', null);
    });
    businesses.forEach((u, i) => {
      if (u.request) return;
      if (i === 13) makeRequest(u, now - 9 * DAY, 'REJECTED', carla);
      else if (i !== 18) decidedRequest(u, 'VERIFIED', pick(admins));
    });
    households.slice(15, 25).forEach((u) => decidedRequest(u, 'VERIFIED', pick(admins)));
    recipients.slice(20, 28).forEach((u) => decidedRequest(u, 'VERIFIED', pick(admins)));

    /* ---------- Listings ---------- */
    const listings = [];
    let listingSeq = 20000;
    function addListing(donor, food, createdAt, extra) {
      const [name, category, unit, qRange, pRange, allergens, storage, keepsDays] = food;
      const qty = int(qRange[0], qRange[1]);
      const priced = donor.org ? chance(0.45) : chance(0.15);
      const start = roundTo(createdAt + int(1, 5) * HOUR, 30 * MIN);
      const end = start + (keepsDays >= 3 ? int(1, 3) * DAY : int(2, 4) * HOUR);
      const l = Object.assign({
        id: 'L-' + (++listingSeq),
        name, category, unit, allergens,
        storageNotes: storage,
        description: pick([
          'Freshly made today. Please bring your own container.',
          'Unsold from today’s batch, still good for eating.',
          'Surplus from an event this afternoon.',
          'Good quality, just past its best look.',
          'Packed and ready for pickup at the counter.']),
        donorId: donor.id,
        price: priced ? int(pRange[0], pRange[1]) : 0,
        initialQuantity: qty,
        quantity: qty,
        pickupStart: start,
        pickupEnd: end,
        expiresAt: end + int(1, 6) * HOUR,
        pickupLocation: (donor.org ? donor.name + ', ' : '') + donor.barangay + ', ' + donor.city,
        handover: chance(0.6) ? 'MEET_AT_PICKUP_LOCATION' : 'COLLECT_FROM_PROVIDER',
        photos: chance(0.85) ? int(1, 4) : 0,
        status: 'AVAILABLE',
        createdAt,
        updatedAt: createdAt,
        reviewedAt: null,
        reviewedBy: null,
        removedBy: null,
        removedReason: null
      }, extra || {});
      listings.push(l);
      return l;
    }
    const donorPick = () => (chance(0.62) ? pick(businesses) : pick(households));
    const foodFor = (donor) => FOODS[pick(MENU[donor.kind] || MENU.Household)];

    // About two thousand listings over 13 weeks, growing from ~10 to ~34 a day.
    for (let d = 90; d >= 0; d--) {
      const perDay = Math.round(10 + 24 * (1 - d / 90) + (rand() - 0.5) * 6);
      for (let k = 0; k < perDay; k++) {
        const createdAt = now - d * DAY - Math.floor(rand() * DAY);
        if (createdAt > now - 20 * MIN) continue;
        const donor = donorPick();
        if (!canActAt(donor, createdAt)) continue;
        addListing(donor, foodFor(donor), createdAt);
      }
    }
    // The newest listing, as in the brief: Aling Nena's Kitchen posted bread 8 minutes ago.
    const nena = businesses[0];
    const nenaBread = addListing(nena, FOODS[4], now - 8 * MIN, { price: 0 });
    nenaBread.pickupStart = roundTo(now + 2 * HOUR, 30 * MIN);
    nenaBread.pickupEnd = nenaBread.pickupStart + 2 * HOUR;
    nenaBread.expiresAt = nenaBread.pickupEnd + 4 * HOUR;
    listings.sort((a, b) => b.createdAt - a.createdAt);
    const listingById = new Map(listings.map((l) => [l.id, l]));

    // The 126 newest are still up: 104 with food left and 22 fully reserved. Those posted more
    // than five hours ago have their pickup window open now; newer ones open later.
    const active = listings.slice(0, 126);
    active.forEach((l, i) => {
      l.status = i % 6 === 3 || i === 124 ? 'RESERVED' : 'AVAILABLE';
      if (l === nenaBread) return;
      if (now - l.createdAt > 5 * HOUR) {
        l.pickupStart = Math.max(roundTo(l.createdAt + HOUR, 30 * MIN), roundTo(now - int(1, 4) * HOUR, 30 * MIN));
        l.pickupEnd = roundTo(now + int(1, 6) * HOUR, 30 * MIN);
      } else if (l.pickupEnd < now + HOUR) {
        l.pickupStart = roundTo(now + int(1, 6) * HOUR, 30 * MIN);
        l.pickupEnd = l.pickupStart + int(2, 4) * HOUR;
      }
      l.expiresAt = l.pickupEnd + int(1, 6) * HOUR;
    });
    const closed = listings.slice(126);
    closed.forEach((l) => {
      if (l.pickupEnd > now - HOUR) {
        l.pickupEnd = now - int(2, 20) * HOUR;
        l.pickupStart = l.pickupEnd - int(2, 4) * HOUR;
        l.expiresAt = l.pickupEnd + int(1, 4) * HOUR;
      }
      const r = rand();
      l.status = r < 0.017 ? 'REMOVED' : r < 0.175 ? 'EXPIRED' : 'CLAIMED';
      l.updatedAt = Math.min(now, l.pickupEnd);
      if (l.status === 'REMOVED') {
        l.removedBy = l.donorId; // removed by the donor in the app, unless an admin did (below)
        l.updatedAt = within(l.createdAt + 30 * MIN, Math.max(l.createdAt + 31 * MIN, l.pickupStart));
      }
    });

    /* ---------- Reservations ---------- */
    const reservations = [];
    let resSeq = 40000;
    const potentialRecipients = recipients.slice(1).concat(households);
    function recipientFor(l, at) {
      for (let tries = 0; tries < 30; tries++) {
        const u = pick(potentialRecipients);
        if (u.id !== l.donorId && canActAt(u, at)) return u;
      }
      return recipients[1];
    }
    function addReservation(l, qty, status, createdAt, updatedAt, extra) {
      const r = Object.assign({
        id: 'R-' + (++resSeq),
        listingId: l.id,
        donorId: l.donorId,
        recipientId: null,
        quantity: qty,
        unitPrice: l.price,
        status,
        cancelReason: null,
        cancelledBy: null,
        createdAt,
        updatedAt: updatedAt || createdAt,
        quantityBefore: null
      }, extra || {});
      if (!r.recipientId) r.recipientId = recipientFor(l, createdAt).id;
      reservations.push(r);
      return r;
    }
    function splitQty(total, parts) {
      const out = [];
      let left = total;
      for (let i = parts; i > 1; i--) {
        const q = Math.max(1, Math.min(left - (i - 1), Math.round(left / i + (rand() - 0.5) * left / i)));
        out.push(q); left -= q;
      }
      out.push(left);
      return out.filter((q) => q > 0);
    }

    for (const l of closed) {
      if (l.status === 'CLAIMED') {
        for (const q of splitQty(l.initialQuantity, Math.min(l.initialQuantity, int(1, 3)))) {
          const made = within(l.createdAt + 5 * MIN, Math.min(l.pickupStart, now));
          addReservation(l, q, 'CLAIMED', made, within(Math.max(made, l.pickupStart), Math.min(l.pickupEnd, now)));
        }
        if (chance(0.22)) {
          const made = within(l.createdAt + 5 * MIN, l.pickupStart);
          addReservation(l, int(1, 3), 'CANCELLED', made, made + int(10, 180) * MIN, { cancelledBy: 'RECIPIENT' });
        }
        l.quantity = 0;
      } else if (l.status === 'EXPIRED') {
        let left = l.initialQuantity;
        if (chance(0.45) && left > 1) {
          const q = int(1, Math.max(1, Math.floor(left / 2)));
          const made = within(l.createdAt + 5 * MIN, l.pickupStart);
          addReservation(l, q, 'CLAIMED', made, within(l.pickupStart, l.pickupEnd));
          left -= q;
        }
        if (chance(0.35)) {
          const made = within(l.createdAt + 5 * MIN, l.pickupStart);
          addReservation(l, int(1, 3), 'CANCELLED', made, made + int(10, 240) * MIN, { cancelledBy: 'RECIPIENT' });
        }
        l.quantity = left;
      } else if (chance(0.6)) {
        const made = within(l.createdAt + 5 * MIN, l.updatedAt);
        addReservation(l, int(1, 3), 'CANCELLED', made, l.updatedAt, { cancelReason: 'LISTING_REMOVED', cancelledBy: 'LISTING_REMOVED' });
      }
    }

    // Juan Dela Cruz reserved 3 items 15 minutes ago (the brief's example).
    const juan = recipients[1];
    const juanListing = active.find((l) => l.status === 'AVAILABLE' && l !== nenaBread && l.initialQuantity >= 8 && l.donorId !== juan.id && l.createdAt < now - 40 * MIN);
    addReservation(juanListing, 3, 'RESERVED', now - 15 * MIN, null, { recipientId: juan.id });
    // Reservations on the listings that are still up. Where the pickup window has opened,
    // some were already picked up; 53 more (with Juan's, 54) are still waiting for pickup.
    const planned = [];
    active.forEach((l) => {
      if (l === nenaBread || l === juanListing || now - l.createdAt < 40 * MIN) return;
      let total = l.initialQuantity;
      if (l.status === 'AVAILABLE') {
        const dayOld = now - l.createdAt > DAY;
        if (l.initialQuantity < 2 || chance(dayOld ? 0.05 : 0.25)) return; // nobody has reserved it yet
        // Food that has been up for a day is mostly spoken for; newer food less so.
        const share = dayOld ? 0.8 + rand() * 0.15 : 0.3 + rand() * 0.5;
        total = Math.max(1, Math.min(l.initialQuantity - 1, Math.round(l.initialQuantity * share)));
      }
      const started = l.pickupStart < now - 30 * MIN;
      splitQty(total, Math.min(total, now - l.createdAt > DAY ? int(2, 3) : int(1, 3))).forEach((q, k) => {
        const made = within(l.createdAt + 5 * MIN, now - 20 * MIN);
        const canClaim = started && made < now - 25 * MIN;
        // A fully reserved listing keeps at least one reservation waiting, or it would be closed.
        planned.push({ l, q, made, open: !canClaim || (l.status === 'RESERVED' && k === 0) });
      });
    });
    let open = planned.filter((p) => p.open);
    for (const p of open.slice().reverse()) {
      if (open.length <= 53) break;
      if (p.l.status === 'AVAILABLE') { planned.splice(planned.indexOf(p), 1); open.splice(open.indexOf(p), 1); }
    }
    for (const p of planned) {
      if (open.length >= 53) break;
      if (!p.open) { p.open = true; open.push(p); }
    }
    for (const p of planned) {
      if (p.open) addReservation(p.l, p.q, 'RESERVED', p.made);
      else addReservation(p.l, p.q, 'CLAIMED', p.made, within(Math.max(p.made + 10 * MIN, p.l.pickupStart), now - 5 * MIN));
    }
    const takenOn = (l) => reservations.filter((r) => r.listingId === l.id && (r.status === 'RESERVED' || r.status === 'CLAIMED')).reduce((sum, r) => sum + r.quantity, 0);
    const availableActive = active.filter((l) => l.status === 'AVAILABLE' && l !== nenaBread && l !== juanListing && l.initialQuantity >= 3 && now - l.createdAt > 4 * HOUR);
    for (let i = 0; i < 6; i++) {
      const l = availableActive[(i * 11 + 3) % availableActive.length];
      const made = within(l.createdAt + 5 * MIN, now - 2 * HOUR);
      addReservation(l, int(1, 2), 'CANCELLED', made, Math.min(now - 30 * MIN, made + int(10, 90) * MIN), { cancelledBy: 'RECIPIENT' });
    }
    for (const l of active) l.quantity = l.status === 'RESERVED' ? 0 : Math.max(1, l.initialQuantity - takenOn(l));

    // What each listing had left when a reservation was made (for the reservation timeline).
    const leftAfter = new Map();
    reservations.slice().sort((a, b) => a.createdAt - b.createdAt).forEach((r) => {
      const l = listingById.get(r.listingId);
      const left = leftAfter.has(r.listingId) ? leftAfter.get(r.listingId) : l.initialQuantity;
      r.quantityBefore = Math.max(left, r.quantity);
      leftAfter.set(r.listingId, r.status === 'CANCELLED' ? left : Math.max(0, left - r.quantity));
    });
    reservations.sort((a, b) => b.createdAt - a.createdAt);

    /* ---------- Reports ---------- */
    const reports = [];
    let reportSeq = 1000;
    function addReport(r) {
      const target = r.targetType === 'LISTING' ? listingById.get(r.listingId) : null;
      const rep = Object.assign({
        id: 'RPT-' + (++reportSeq),
        source: r.targetType === 'LISTING' ? 'LISTING' : pick(['DONOR_PAGE', 'CHAT']),
        status: 'OPEN',
        listingName: target ? target.name : null,
        details: null,
        resolution: null,
        notes: []
      }, r);
      if (!rep.reporterId) {
        const avoid = target ? target.donorId : rep.userId;
        let reporter = pick(recipients.slice(2));
        while (reporter.id === avoid || !canActAt(reporter, rep.createdAt)) reporter = pick(recipients.slice(2));
        rep.reporterId = reporter.id;
      }
      reports.push(rep);
      return rep;
    }
    const used = new Set([nenaBread, juanListing]);
    function activeFor(pred) {
      const l = active.find((x) => !used.has(x) && pred(x));
      if (l) { used.add(l); l.photos = Math.max(1, l.photos); }
      return l;
    }
    const flan = activeFor((l) => l.name === 'Leche flan') || activeFor((l) => l.category === 'Other') || activeFor(() => true);
    const packs = activeFor((l) => l.unit === 'packs' && l.initialQuantity >= 8) || activeFor(() => true);
    const noodles = activeFor((l) => l.category === 'Packaged food') || activeFor(() => true);
    const meals = activeFor((l) => l.category === 'Cooked meals' && l.status === 'AVAILABLE') || activeFor(() => true);
    [
      { targetType: 'LISTING', listingId: flan.id, reason: 'UNSAFE_FOOD', details: 'The ' + flan.name.toLowerCase() + ' smelled sour when I picked it up, and the container was warm.', createdAt: now - 52 * MIN },
      { targetType: 'LISTING', listingId: packs.id, reason: 'MISLEADING', details: 'The listing said ' + packs.initialQuantity + ' ' + packs.unit + ' but only 4 were left when I arrived on time.', createdAt: now - 4 * HOUR },
      { targetType: 'LISTING', listingId: noodles.id, reason: 'SPAM_FAKE', details: 'Same photo posted several times this week with different prices.', createdAt: now - 20 * HOUR },
      { targetType: 'LISTING', listingId: meals.id, reason: 'OTHER', details: 'The pickup address leads to a closed gate and nobody answers the chat.', createdAt: now - 30 * HOUR },
      { targetType: 'USER', userId: recipients[40].id, reason: 'NO_SHOW', details: 'Reserved 10 meals twice this week and never came to pick them up.', createdAt: now - 6 * HOUR, source: 'DONOR_PAGE', reporterId: businesses[3].id },
      { targetType: 'USER', userId: households[9].id, reason: 'ABUSIVE', details: 'Sent rude messages when I asked to move the pickup time by 30 minutes.', createdAt: now - 2 * DAY, source: 'CHAT' },
      { targetType: 'USER', userId: households[27].id, reason: 'SCAM', details: 'Asked me to send the payment by GCash before pickup, then stopped replying.', createdAt: now - 2 * DAY - 5 * HOUR, source: 'CHAT' }
    ].forEach(addReport);

    // Resolved reports: nine listings removed by admins and three accounts suspended.
    const removedByAdmin = closed.filter((l) => l.status === 'REMOVED').slice(0, 9);
    const listingReasons = [['UNSAFE_FOOD', 'Food was spoiled at pickup.'], ['MISLEADING', 'Photos show a different item than what was handed over.'], ['SPAM_FAKE', 'Duplicate listing posted many times.']];
    removedByAdmin.forEach((l, i) => {
      const by = pick(admins);
      const [reason, details] = listingReasons[i % 3];
      const reportedAt = l.createdAt + Math.floor((l.updatedAt - l.createdAt) * (0.3 + 0.4 * rand()));
      const rep = addReport({ targetType: 'LISTING', listingId: l.id, reason, details, createdAt: reportedAt, status: 'RESOLVED' });
      rep.resolution = { action: 'LISTING_REMOVED', by: by.id, at: l.updatedAt, note: 'Removed after checking the report and photos.' };
      l.removedBy = by.id;
      l.removedReason = details;
    });
    for (const [u, reason, details] of suspendedPlan) {
      const by = pick(admins);
      const rep = addReport({ targetType: 'USER', userId: u.id, reason, details, createdAt: u.suspendAt - int(3, 20) * HOUR, status: 'RESOLVED', source: reason === 'NO_SHOW' ? 'DONOR_PAGE' : 'CHAT' });
      rep.resolution = { action: 'USER_SUSPENDED', by: by.id, at: u.suspendAt, note: 'Suspended after reviewing the report.' };
      u.accountStatus = 'SUSPENDED';
      u.suspension = { reason: details, at: u.suspendAt, by: by.id, reportId: rep.id };
    }
    const dismissTexts = [
      ['UNSAFE_FOOD', 'Bread was a bit hard.', 'Day-old bread is expected; the listing says so.'],
      ['MISLEADING', 'Pickup time was different.', 'Donor changed the time in chat and the recipient agreed.'],
      ['OTHER', 'Wrong category.', 'Minor; the listing is otherwise fine.'],
      ['SPAM', 'Too many messages.', 'Normal pickup questions.'],
      ['NO_SHOW', 'Was late by 10 minutes.', 'Late but picked up; no pattern.'],
      ['SPAM_FAKE', 'Looks like a duplicate.', 'Two different batches on different days.']];
    const claimedOld = closed.filter((x) => x.status === 'CLAIMED');
    for (let i = 0; i < 18; i++) {
      const [reason, details, note] = dismissTexts[i % dismissTexts.length];
      const by = pick(admins);
      let rep;
      if (reason === 'SPAM' || reason === 'NO_SHOW') {
        const u = pick(donors.concat(recipients.slice(100)).filter((x) => x.accountStatus === 'ACTIVE' && !x.suspendAt));
        rep = addReport({ targetType: 'USER', userId: u.id, reason, details, createdAt: Math.max(u.joinedAt + DAY, now - int(2, 60) * DAY - int(0, DAY)), status: 'DISMISSED' });
      } else {
        const l = pick(claimedOld);
        rep = addReport({ targetType: 'LISTING', listingId: l.id, reason, details, createdAt: Math.min(now - DAY, l.pickupEnd + int(1, 20) * HOUR), status: 'DISMISSED' });
      }
      rep.resolution = { action: 'DISMISSED', by: by.id, at: Math.min(now - HOUR, rep.createdAt + int(2, 30) * HOUR), note };
    }
    reports.sort((a, b) => b.createdAt - a.createdAt);
    reports.find((r) => r.listingId === flan.id).notes.push({ by: carla.id, at: now - 30 * MIN, text: 'Asked the donor in chat how the flan was stored. Waiting for a reply.' });

    // One account was suspended and later restored.
    const restoredUser = recipients[120];
    const restoredAt = now - 8 * DAY;

    /* ---------- Announcements (sample: the app has no announcement type yet) ---------- */
    const audienceSize = { ALL: users.length, DONORS: donors.length, RECIPIENTS: recipients.length };
    const announcements = [
      { title: 'Pickup reminder: bring your own bag', message: 'Help us cut plastic: bring a bag or container when you pick up food.', audience: 'ALL', status: 'SENT', at: now - 2 * DAY - 3 * HOUR, by: you.id },
      { title: 'Holiday pickups this long weekend', message: 'Some donors close early from Oct 31 to Nov 2. Check the pickup window before you reserve.', audience: 'ALL', status: 'SCHEDULED', at: now + 2 * DAY + 5 * HOUR, by: carla.id },
      { title: 'Food safety tips for cooked meals', message: 'Keep cooked food covered, cool it within 2 hours, and list everything it contains.', audience: 'DONORS', status: 'DRAFT', at: now - 5 * HOUR, by: you.id },
      { title: 'New: rate your donor after pickup', message: 'You can now rate a donor from the reservation in Activity once the food is picked up.', audience: 'RECIPIENTS', status: 'SENT', at: now - 9 * DAY, by: jon.id },
      { title: 'Short maintenance tonight, 11 PM to 12 AM', message: 'Salu-Salo may be slow for up to an hour. Reservations stay as they are.', audience: 'ALL', status: 'SENT', at: now - 16 * DAY, by: carla.id },
      { title: 'Donors: list allergens on every post', message: 'Allergens are now required on new listings. Add them when you edit older ones too.', audience: 'DONORS', status: 'SENT', at: now - 21 * DAY, by: you.id },
      { title: 'Welcome to Salu-Salo', message: 'Browse food near you, reserve what you need, and pick it up on time.', audience: 'RECIPIENTS', status: 'SENT', at: now - 34 * DAY, by: jon.id }
    ].map((a, i) => Object.assign({
      id: 'N-' + (301 + i),
      reach: a.status === 'SENT' ? Math.round(audienceSize[a.audience] * (0.93 + rand() * 0.06)) : 0,
      createdAt: Math.min(a.at, now) - int(1, 20) * HOUR
    }, a));

    /* ---------- The admin's own alerts ---------- */
    const flanReport = reports.find((r) => r.listingId === flan.id && r.status === 'OPEN');
    const noShowReport = reports.find((r) => r.userId === recipients[40].id && r.status === 'OPEN');
    const alerts = [
      { type: 'REPORT', title: 'New report: ' + flan.name, text: 'Unsafe or spoiled food, reported from the listing page.', at: flanReport.createdAt, read: false, link: '#/reports/' + flanReport.id },
      { type: 'VERIFICATION', title: 'Verification request: ' + pendingOrgs[2].name, text: 'Food business · sent a business permit and registration.', at: pendingOrgs[2].request.submittedAt, read: false, link: '#/users/' + pendingOrgs[2].id + '/verify' },
      { type: 'VERIFICATION', title: 'Verification request: ' + pendingOrgs[0].name, text: 'Food business · sent a business permit and registration.', at: pendingOrgs[0].request.submittedAt, read: false, link: '#/users/' + pendingOrgs[0].id + '/verify' },
      { type: 'REPORT', title: 'New report about a user', text: 'Didn’t show up, reported by ' + businesses[3].name + '.', at: noShowReport.createdAt, read: true, link: '#/reports/' + noShowReport.id },
      { type: 'SYSTEM', title: '9 listings expired without pickup yesterday', text: 'Most were cooked meals posted after 6 PM.', at: now - 14 * HOUR, read: true, link: '#/listings' },
      { type: 'SYSTEM', title: 'Announcement sent', text: '“' + announcements[0].title + '” reached ' + announcements[0].reach + ' people.', at: announcements[0].at, read: true, link: '#/notifications/N-301' }
    ].map((a, i) => Object.assign({ id: 'A-' + (501 + i) }, a));

    /* ---------- Audit log (sample: no audit table exists yet) ---------- */
    const audit = [];
    let auditSeq = 7000;
    function log(at, adminId, action, targetType, targetId, targetName, details) {
      audit.push({ id: 'AU-' + (++auditSeq), at, adminId, action, targetType, targetId, targetName, details });
    }
    for (const u of users) {
      if (!u.request || !u.request.decidedAt || u.role === 'ADMIN' || u.request.decidedAt < now - 60 * DAY) continue;
      if (u.verification === 'VERIFIED') log(u.request.decidedAt, u.request.decidedBy, 'Approved verification', 'USER', u.id, u.name, u.request.type + ': documents match the account.');
      if (u.verification === 'REJECTED') log(u.request.decidedAt, u.request.decidedBy, 'Rejected verification', 'USER', u.id, u.name, u.request.decisionNote);
    }
    for (const u of users) {
      if (u.suspension) log(u.suspension.at, u.suspension.by, 'Suspended account', 'USER', u.id, u.name, u.suspension.reason + ' (' + u.suspension.reportId + ')');
    }
    log(restoredAt - 6 * DAY, jon.id, 'Suspended account', 'USER', restoredUser.id, restoredUser.name, 'Several no-shows in one week.');
    log(restoredAt, carla.id, 'Restored account', 'USER', restoredUser.id, restoredUser.name, 'Explained the no-shows (a family emergency) and agreed to cancel in the app next time.');
    for (const l of removedByAdmin) {
      const rep = reports.find((r) => r.listingId === l.id && r.status === 'RESOLVED');
      log(rep.resolution.at, rep.resolution.by, 'Removed listing', 'LISTING', l.id, l.name, l.removedReason + ' (' + rep.id + ')');
    }
    for (const r of reports) {
      if (r.status === 'DISMISSED') log(r.resolution.at, r.resolution.by, 'Dismissed report', 'REPORT', r.id, r.id, r.resolution.note);
    }
    const audienceWord = { ALL: 'everyone', DONORS: 'donors', RECIPIENTS: 'recipients' };
    for (const a of announcements) {
      if (a.status === 'SENT') log(a.at, a.by, 'Sent notification', 'NOTIFICATION', a.id, a.title, 'To ' + a.reach + ' people (' + audienceWord[a.audience] + ').');
      if (a.status === 'SCHEDULED') log(a.createdAt, a.by, 'Scheduled notification', 'NOTIFICATION', a.id, a.title, 'To ' + audienceWord[a.audience] + '.');
    }
    const reviewed = active[40];
    reviewed.reviewedAt = now - 3 * DAY;
    reviewed.reviewedBy = jon.id;
    log(reviewed.reviewedAt, jon.id, 'Reviewed listing', 'LISTING', reviewed.id, reviewed.name, 'Checked after a recipient asked about allergens. No change needed.');
    log(now - 11 * DAY, you.id, 'Changed setting', 'SETTING', 'moderation', 'Moderation', 'Hide a listing automatically after 3 open reports (was 5).');
    log(now - 27 * DAY, carla.id, 'Changed setting', 'SETTING', 'system', 'Reservations', 'Largest reservation: 10 units (was 20).');
    audit.sort((a, b) => b.at - a.at);

    /* ---------- Settings (sample values; nothing here changes the app) ---------- */
    const settings = {
      account: { name: you.name, email: you.email },
      notifications: { newReports: true, verificationRequests: true, system: true, emailDigest: false },
      moderation: { autoExpire: true, autoHideAfterReports: 3, requireRemovalReason: true, donorVerification: 'EMAIL' },
      system: { maxPerReservation: 10, holdUntilWindowEnd: true, defaultExpiryHours: 24, expiringSoonHours: 2 }
    };

    for (const u of users) {
      const last = Math.max(u.joinedAt, now - int(0, 12) * DAY - int(0, DAY));
      u.lastActiveAt = u.suspendAt ? Math.min(last, u.suspendAt) : last;
      delete u.suspendAt;
    }
    recipients[0].lastActiveAt = now - 2 * MIN; // Maria Santos, just signed up

    return { now, youId: you.id, users, listings, reservations, reports, announcements, alerts, audit, settings };
  }

  return { create, MIN, HOUR, DAY };
})();
