/*
 * Small SVG charts for the dashboard: a line or area chart, stacked columns, horizontal
 * bars and a sparkline. Thin marks, hairline grids, one clean scale per chart, a
 * crosshair tooltip that also works with the arrow keys, and a table for every chart.
 * Colours come from the brand tokens in admin.css; text never takes a series colour.
 */
window.SaluCharts = (function () {
  'use strict';

  const { h, icon, clear } = window.UI;
  const NS = 'http://www.w3.org/2000/svg';

  function svg(tag, attrs) {
    const el = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs || {})) if (v != null) el.setAttribute(k, v);
    return el;
  }
  function niceScale(maxValue, count) {
    const target = Math.max(1, maxValue);
    const raw = target / (count || 4);
    const mag = Math.pow(10, Math.floor(Math.log10(raw)));
    const norm = raw / mag;
    let step = (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 2.5 ? 2.5 : norm <= 5 ? 5 : 10) * mag;
    if (step < 1) step = 1;
    const max = Math.ceil(target / step) * step;
    const ticks = [];
    for (let v = 0; v <= max + 1e-9; v += step) ticks.push(Math.round(v * 100) / 100);
    return { max, ticks };
  }
  const tickText = (v) => (v >= 10000 ? (v / 1000).toFixed(v % 1000 ? 1 : 0) + 'K' : v.toLocaleString('en-US'));

  /** Re-renders when the container's width changes. */
  function responsive(el, render) {
    let lastWidth = 0;
    let frame = 0;
    const run = () => {
      const w = Math.round(el.clientWidth);
      if (!w || w === lastWidth) return;
      lastWidth = w;
      render(w);
    };
    const ro = new ResizeObserver(() => { cancelAnimationFrame(frame); frame = requestAnimationFrame(run); });
    ro.observe(el);
    requestAnimationFrame(run);
    return () => ro.disconnect();
  }

  function tooltip(container) {
    const tip = h('div', { class: 'chart-tip', hidden: true, role: 'presentation' });
    container.append(tip);
    return {
      show(x, y, head, rows) {
        clear(tip);
        tip.append(h('span', { class: 'chart-tip__head' }, head));
        for (const r of rows) {
          tip.append(h('span', { class: 'chart-tip__row' },
            h('span', { class: 'chart-tip__key', style: { background: r.color } }),
            h('span', { class: 'chart-tip__value' }, r.value),
            h('span', { class: 'chart-tip__name' }, r.name)));
        }
        tip.hidden = false;
        const cw = container.clientWidth;
        const tw = tip.offsetWidth;
        let left = x + 14;
        if (left + tw > cw) left = x - tw - 14;
        tip.style.left = Math.max(0, left) + 'px';
        tip.style.top = Math.max(0, y - tip.offsetHeight / 2) + 'px';
      },
      hide() { tip.hidden = true; }
    };
  }

  function xLabelEvery(n, plotWidth, minGap) {
    const fit = Math.max(2, Math.floor(plotWidth / (minGap || 64)));
    return Math.max(1, Math.ceil(n / fit));
  }

  /**
   * Line chart (area for a single series).
   * opts: { labels, series: [{ name, values, color }], height, format, ariaLabel, area }
   */
  function line(container, opts) {
    const { labels, series, format } = opts;
    const height = opts.height || 220;
    const fmtValue = format || ((v) => v.toLocaleString('en-US'));
    const wrap = h('div', { class: 'chart' });
    container.append(wrap);
    const tip = tooltip(wrap);
    let focusIndex = labels.length - 1;

    const stop = responsive(wrap, (width) => {
      wrap.querySelectorAll('svg').forEach((s) => s.remove());
      const m = { top: 18, right: 22, bottom: 28, left: 44 };
      const pw = Math.max(40, width - m.left - m.right);
      const ph = height - m.top - m.bottom;
      const all = series.flatMap((s) => s.values);
      const { max, ticks } = niceScale(Math.max.apply(null, all.concat([1])), 4);
      const n = labels.length;
      const x = (i) => m.left + (n === 1 ? pw / 2 : (i * pw) / (n - 1));
      const y = (v) => m.top + ph - (v / max) * ph;

      const root = svg('svg', { viewBox: '0 0 ' + width + ' ' + height, width, height, role: 'img', 'aria-label': opts.ariaLabel, tabindex: '0', focusable: 'true' });
      const grid = svg('g');
      for (const t of ticks) {
        grid.append(svg('line', { class: t === 0 ? 'axis-line' : 'grid-line', x1: m.left, x2: m.left + pw, y1: y(t), y2: y(t) }));
        const label = svg('text', { x: m.left - 8, y: y(t) + 4, 'text-anchor': 'end' });
        label.textContent = tickText(t);
        grid.append(label);
      }
      const every = xLabelEvery(n, pw);
      labels.forEach((lab, i) => {
        if ((n - 1 - i) % every !== 0) return;
        const t = svg('text', { x: x(i), y: height - 8, 'text-anchor': i === 0 ? 'start' : i === n - 1 ? 'end' : 'middle' });
        t.textContent = lab;
        grid.append(t);
      });
      root.append(grid);

      series.forEach((s) => {
        const pts = s.values.map((v, i) => [x(i), y(v)]);
        const d = pts.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ',' + p[1].toFixed(1)).join('');
        if (opts.area !== false && series.length === 1) {
          root.append(svg('path', { d: d + 'L' + x(n - 1).toFixed(1) + ',' + y(0) + 'L' + x(0).toFixed(1) + ',' + y(0) + 'Z', fill: 'var(--chart-wash)', stroke: 'none' }));
        }
        root.append(svg('path', { d, fill: 'none', stroke: s.color, 'stroke-width': 2, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' }));
        const last = pts[pts.length - 1];
        root.append(svg('circle', { cx: last[0], cy: last[1], r: 5, fill: s.color, stroke: 'var(--surface)', 'stroke-width': 2 }));
      });
      // Label the endpoint of a single series (selective labelling).
      if (series.length === 1) {
        const lastV = series[0].values[n - 1];
        const lab = svg('text', { class: 'value-label', x: x(n - 1) - 8, y: Math.max(12, y(lastV) - 10), 'text-anchor': 'end' });
        lab.textContent = fmtValue(lastV);
        root.append(lab);
      }

      const cross = svg('line', { class: 'crosshair', y1: m.top, y2: m.top + ph, visibility: 'hidden' });
      const dots = series.map((s) => svg('circle', { r: 4, fill: s.color, stroke: 'var(--surface)', 'stroke-width': 2, visibility: 'hidden' }));
      root.append(cross, ...dots);
      const hit = svg('rect', { x: m.left - 10, y: m.top, width: pw + 20, height: ph, fill: 'transparent' });
      root.append(hit);
      wrap.prepend(root);

      function showAt(i) {
        focusIndex = i;
        cross.setAttribute('x1', x(i)); cross.setAttribute('x2', x(i)); cross.setAttribute('visibility', 'visible');
        series.forEach((s, k) => { dots[k].setAttribute('cx', x(i)); dots[k].setAttribute('cy', y(s.values[i])); dots[k].setAttribute('visibility', 'visible'); });
        const topY = Math.min.apply(null, series.map((s) => y(s.values[i])));
        tip.show(x(i), Math.max(24, topY), opts.tipHead ? opts.tipHead(i) : labels[i], series.map((s) => ({ color: s.color, name: s.name, value: fmtValue(s.values[i]) })));
      }
      function hide() { cross.setAttribute('visibility', 'hidden'); dots.forEach((d) => d.setAttribute('visibility', 'hidden')); tip.hide(); }
      hit.addEventListener('pointermove', (e) => {
        const r = root.getBoundingClientRect();
        const px = ((e.clientX - r.left) / r.width) * width;
        showAt(Math.max(0, Math.min(n - 1, Math.round(((px - m.left) / pw) * (n - 1)))));
      });
      hit.addEventListener('pointerleave', hide);
      root.addEventListener('focus', () => showAt(focusIndex));
      root.addEventListener('blur', hide);
      root.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowLeft') { e.preventDefault(); showAt(Math.max(0, focusIndex - 1)); }
        if (e.key === 'ArrowRight') { e.preventDefault(); showAt(Math.min(n - 1, focusIndex + 1)); }
        if (e.key === 'Escape') hide();
      });
    });
    return { el: wrap, destroy: stop };
  }

  /** Stacked columns with a 2 px surface gap between segments. */
  function columns(container, opts) {
    const { labels, series, format } = opts;
    const height = opts.height || 220;
    const fmtValue = format || ((v) => v.toLocaleString('en-US'));
    const wrap = h('div', { class: 'chart' });
    container.append(wrap);
    const tip = tooltip(wrap);
    let focusIndex = labels.length - 1;

    const stop = responsive(wrap, (width) => {
      wrap.querySelectorAll('svg').forEach((s) => s.remove());
      const m = { top: 18, right: 12, bottom: 28, left: 44 };
      const pw = Math.max(40, width - m.left - m.right);
      const ph = height - m.top - m.bottom;
      const n = labels.length;
      const totals = labels.map((_, i) => series.reduce((s, ser) => s + ser.values[i], 0));
      const { max, ticks } = niceScale(Math.max.apply(null, totals.concat([1])), 4);
      const band = pw / n;
      const barW = Math.max(6, Math.min(24, band * 0.56));
      const y = (v) => m.top + ph - (v / max) * ph;

      const root = svg('svg', { viewBox: '0 0 ' + width + ' ' + height, width, height, role: 'img', 'aria-label': opts.ariaLabel, tabindex: '0', focusable: 'true' });
      for (const t of ticks) {
        root.append(svg('line', { class: t === 0 ? 'axis-line' : 'grid-line', x1: m.left, x2: m.left + pw, y1: y(t), y2: y(t) }));
        const lab = svg('text', { x: m.left - 8, y: y(t) + 4, 'text-anchor': 'end' });
        lab.textContent = tickText(t);
        root.append(lab);
      }
      const every = xLabelEvery(n, pw, 56);
      const cols = [];
      labels.forEach((lab, i) => {
        const cx = m.left + band * i + band / 2;
        const g = svg('g');
        let base = y(0);
        let drawnBelow = false;
        series.forEach((s, k) => {
          const v = s.values[i];
          if (!v) return;
          let segH = (v / max) * ph;
          if (drawnBelow) { base -= 2; segH -= 2; } // the 2 px surface gap between segments
          const top = base - segH;
          if (segH <= 0) { base = top; return; }
          const isTop = series.slice(k + 1).every((x) => !x.values[i]);
          const r = isTop ? Math.min(4, segH / 2, barW / 2) : 0;
          const x0 = cx - barW / 2;
          const d = 'M' + x0 + ',' + (top + segH) + 'V' + (top + r) + (r ? 'Q' + x0 + ',' + top + ' ' + (x0 + r) + ',' + top : '') +
            'H' + (x0 + barW - r) + (r ? 'Q' + (x0 + barW) + ',' + top + ' ' + (x0 + barW) + ',' + (top + r) : '') + 'V' + (top + segH) + 'Z';
          g.append(svg('path', { class: 'bar', d, fill: s.color }));
          base = top;
          drawnBelow = true;
        });
        if ((n - 1 - i) % every === 0) {
          const t = svg('text', { x: cx, y: height - 8, 'text-anchor': 'middle' });
          t.textContent = lab;
          g.append(t);
        }
        const hitRect = svg('rect', { class: 'bar-hit', x: m.left + band * i, y: m.top, width: band, height: ph });
        hitRect.addEventListener('pointerenter', () => showAt(i));
        hitRect.addEventListener('pointerleave', hide);
        g.append(hitRect);
        cols.push(g);
        root.append(g);
      });
      wrap.prepend(root);
      function showAt(i) {
        focusIndex = i;
        cols.forEach((g, k) => g.querySelectorAll('.bar').forEach((b) => b.classList.toggle('is-hover', k === i)));
        const cx = m.left + band * i + band / 2;
        const rows = series.map((s) => ({ color: s.color, name: s.name, value: fmtValue(s.values[i]) })).reverse();
        tip.show(cx, Math.max(28, y(totals[i])), (opts.tipHead ? opts.tipHead(i) : labels[i]) + ' · ' + fmtValue(totals[i]) + ' total', rows);
      }
      function hide() { cols.forEach((g) => g.querySelectorAll('.bar').forEach((b) => b.classList.remove('is-hover'))); tip.hide(); }
      root.addEventListener('focus', () => showAt(focusIndex));
      root.addEventListener('blur', hide);
      root.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowLeft') { e.preventDefault(); showAt(Math.max(0, focusIndex - 1)); }
        if (e.key === 'ArrowRight') { e.preventDefault(); showAt(Math.min(n - 1, focusIndex + 1)); }
        if (e.key === 'Escape') hide();
      });
    });
    return { el: wrap, destroy: stop };
  }

  /** Horizontal bars in HTML, value and share always visible. items: [{ label, value, iconName }] */
  function bars(container, { items, format, color }) {
    const total = items.reduce((s, it) => s + it.value, 0) || 1;
    const max = Math.max.apply(null, items.map((it) => it.value).concat([1]));
    const fmtValue = format || ((v) => v.toLocaleString('en-US'));
    const list = h('ul', { class: 'hbars', style: { listStyle: 'none', margin: '0', padding: '0', display: 'grid', gap: '14px' } },
      items.map((it) => h('li', { style: { display: 'grid', gap: '6px' } },
        h('div', { style: { display: 'flex', justifyContent: 'space-between', gap: '12px', alignItems: 'baseline' } },
          h('span', { style: { display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: '500' } }, it.iconName ? icon(it.iconName, 18) : null, it.label),
          h('span', { class: 'num', style: { color: 'var(--ink-2)', fontSize: 'var(--t-sm)' } },
            h('strong', { style: { color: 'var(--ink)', fontWeight: '600' } }, fmtValue(it.value)), ' · ' + Math.round((it.value / total) * 100) + '%')),
        h('div', { style: { height: '10px', borderRadius: '5px', background: 'var(--neutral)', overflow: 'hidden' }, 'aria-hidden': 'true' },
          h('div', { style: { width: Math.max(it.value ? 2 : 0, (it.value / max) * 100) + '%', height: '100%', borderRadius: '5px', background: color || 'var(--green)' } })))));
    container.append(list);
    return { el: list, destroy() {} };
  }

  /** Sparkline: the trend in a muted line, the current week as a green dot. */
  function spark(container, values, opts) {
    const o = opts || {};
    const height = o.height || 48;
    const wrap = h('div', { class: 'chart', 'aria-hidden': o.label ? null : 'true' });
    container.append(wrap);
    const stop = responsive(wrap, (width) => {
      wrap.querySelectorAll('svg').forEach((s) => s.remove());
      const max = Math.max.apply(null, values.concat([1]));
      const pad = 6;
      const x = (i) => pad + (i * (width - pad * 2)) / (values.length - 1);
      const y = (v) => pad + (height - pad * 2) * (1 - v / max);
      const d = values.map((v, i) => (i ? 'L' : 'M') + x(i).toFixed(1) + ',' + y(v).toFixed(1)).join('');
      const root = svg('svg', { viewBox: '0 0 ' + width + ' ' + height, width, height, role: o.label ? 'img' : null, 'aria-label': o.label || null });
      root.append(
        svg('path', { d: d + 'L' + x(values.length - 1) + ',' + (height - pad) + 'L' + x(0) + ',' + (height - pad) + 'Z', fill: 'var(--chart-wash)' }),
        svg('path', { d, fill: 'none', stroke: 'var(--chart-muted)', 'stroke-width': 2, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' }),
        svg('circle', { cx: x(values.length - 1), cy: y(values[values.length - 1]), r: 4.5, fill: 'var(--green)', stroke: 'var(--surface)', 'stroke-width': 2 }));
      wrap.append(root);
    });
    return { el: wrap, destroy: stop };
  }

  /** The table twin of a chart. */
  function table({ labels, series, format, labelHead }) {
    const fmtValue = format || ((v) => v.toLocaleString('en-US'));
    return h('div', { style: { overflowX: 'auto' } },
      h('table', { class: 'chart-table' },
        h('thead', null, h('tr', null, h('th', { scope: 'col' }, labelHead || 'Week of'), series.map((s) => h('th', { scope: 'col' }, s.name)))),
        h('tbody', null, labels.map((lab, i) => h('tr', null, h('th', { scope: 'row', style: { fontWeight: '500', color: 'var(--ink)' } }, lab), series.map((s) => h('td', null, fmtValue(s.values[i]))))))));
  }

  /** A legend for two or more series: swatch shape follows the mark (bar or line). */
  function legend(series, shape) {
    return h('div', { class: 'legend' }, series.map((s) => h('span', { class: 'legend__item' },
      h('span', { class: 'legend__swatch', style: shape === 'line' ? { height: '3px', borderRadius: '2px', background: s.color } : { background: s.color } }), s.name)));
  }

  /**
   * A chart in a card with a "Table" toggle. build(body) draws the chart and returns
   * { destroy }; tableFor() returns the table twin.
   */
  function card({ title, takeaway, legendEl, build, tableFor, className }) {
    const body = h('div', { class: 'chart-body' });
    let showingTable = false;
    let chart = null;
    const toggle = window.UI.btn({ label: 'Show table', icon: 'tableRows', variant: 'ghost', size: 'sm', onClick: () => { showingTable = !showingTable; draw(); } });
    toggle.setAttribute('aria-pressed', 'false');
    function draw() {
      if (chart && chart.destroy) chart.destroy();
      chart = null;
      clear(body);
      toggle.lastChild.textContent = showingTable ? 'Show chart' : 'Show table';
      toggle.setAttribute('aria-pressed', String(showingTable));
      if (showingTable) body.append(tableFor());
      else chart = build(body);
    }
    const el = window.UI.card({
      title, className: 'chart-card' + (className ? ' ' + className : ''), actions: toggle,
      body: [takeaway ? h('p', { class: 'chart-takeaway' }, takeaway) : null, legendEl || null, body]
    });
    draw();
    return { el, destroy() { if (chart && chart.destroy) chart.destroy(); } };
  }

  return { line, columns, bars, spark, table, legend, card, niceScale };
})();
