/**
 * Renders the renderer-neutral panel spec of a declarative report (see docs/plans/reports.md): tabs -> rows -> panels. The server has
 * already executed all queries and formatted every value; this module only lays the panels out and draws them.
 *
 * - Panel renderers are registered per panel type (JamesReports.registerRenderer(type, fn)); a panel type without a renderer shows a
 *   placeholder instead of breaking the report, so later steps only add renderers.
 * - Charts go through a small adapter interface ({ renderChart(container, spec) }). The only adapter, for Apache ECharts, is the single
 *   place knowing the chart library, so it stays swappable (JamesReports.setChartAdapter(adapter)).
 * - Text panels render Markdown via marked with raw HTML disabled, then pass the result through an allow-list sanitizer: no report can
 *   inject markup, scripts, event handlers or javascript: links.
 */
var JamesReports = (function (global) {
    'use strict';

    var renderers = {};
    var chartAdapter = null;

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined && text !== null) node.textContent = text;
        return node;
    }

    function registerRenderer(type, renderer) {
        renderers[type] = renderer;
    }

    // region layout

    var instanceCounter = 0;

    function render(container, view, texts) {
        texts = texts || {};
        instanceCounter += 1;
        container.textContent = '';
        var tabs = (view && view.tabs) || [];
        if (tabs.length === 0) {
            container.appendChild(el('p', 'app-section-label mb-0', texts.noContent));
            return;
        }
        if (tabs.length === 1) {
            container.appendChild(renderTab(tabs[0], texts));
            return;
        }
        var idBase = 'report-' + instanceCounter + '-tab-';
        var nav = el('ul', 'nav nav-tabs mb-3');
        nav.setAttribute('role', 'tablist');
        var content = el('div', 'tab-content');
        tabs.forEach(function (tab, index) {
            var item = el('li', 'nav-item');
            item.setAttribute('role', 'presentation');
            var button = el('button', 'nav-link' + (index === 0 ? ' active' : ''), tab.name);
            button.type = 'button';
            button.id = idBase + index + '-button';
            button.setAttribute('data-bs-toggle', 'tab');
            button.setAttribute('data-bs-target', '#' + idBase + index);
            button.setAttribute('role', 'tab');
            button.setAttribute('aria-controls', idBase + index);
            button.setAttribute('aria-selected', index === 0 ? 'true' : 'false');
            button.setAttribute('data-testid', 'report-tab');
            item.appendChild(button);
            nav.appendChild(item);

            var pane = el('div', 'tab-pane fade' + (index === 0 ? ' show active' : ''));
            pane.id = idBase + index;
            pane.setAttribute('role', 'tabpanel');
            pane.setAttribute('aria-labelledby', button.id);
            pane.appendChild(renderTab(tab, texts));
            content.appendChild(pane);
        });
        container.appendChild(nav);
        container.appendChild(content);
    }

    function renderTab(tab, texts) {
        var wrapper = el('div', 'report-tab');
        (tab.rows || []).forEach(function (row) {
            var rowNode = el('div', 'row g-3 mb-3');
            rowNode.setAttribute('data-testid', 'report-row');
            (row.panels || []).forEach(function (panel) {
                var col = el('div', 'col-12 col-lg');
                var card = el('div', 'card app-card p-3 h-100');
                card.setAttribute('data-testid', 'report-panel');
                card.setAttribute('data-panel-type', panel.type);
                if (panel.title) card.appendChild(el('h3', 'fs-6 fw-semibold mb-2', panel.title));
                var body = el('div', 'report-panel-body');
                var renderer = renderers[panel.type];
                if (renderer) {
                    renderer(body, panel, texts);
                } else {
                    body.appendChild(el('p', 'app-section-label mb-0', panel.type));
                }
                card.appendChild(body);
                col.appendChild(card);
                rowNode.appendChild(col);
            });
            wrapper.appendChild(rowNode);
        });
        return wrapper;
    }

    // endregion

    // region table

    var NUMERIC_TYPES = { LONG: true, DOUBLE: true };

    registerRenderer('table', function (container, panel, texts) {
        var rows = panel.rows || [];
        if (rows.length === 0) {
            container.appendChild(el('p', 'app-section-label mb-0', texts.empty));
            return;
        }
        var columns = panel.columns || [];
        var responsive = el('div', 'table-responsive');
        var table = el('table', 'table table-sm mb-0 app-table');
        table.setAttribute('data-testid', 'report-table');
        var head = el('thead');
        var headRow = el('tr');
        columns.forEach(function (column) {
            var th = el('th', NUMERIC_TYPES[column.type] ? 'text-end' : '', column.label);
            th.scope = 'col';
            headRow.appendChild(th);
        });
        head.appendChild(headRow);
        table.appendChild(head);
        var body = el('tbody');
        rows.forEach(function (row) {
            var tr = el('tr');
            row.forEach(function (cell, index) {
                var column = columns[index] || {};
                tr.appendChild(el('td', NUMERIC_TYPES[column.type] ? 'text-end text-nowrap' : '', cell.text));
            });
            body.appendChild(tr);
        });
        table.appendChild(body);
        responsive.appendChild(table);
        container.appendChild(responsive);
        if (panel.truncated) {
            container.appendChild(el('p', 'app-section-label small mt-2 mb-0', texts.truncated));
        }
    });

    // endregion

    // region text (Markdown)

    var ALLOWED_TAGS = {
        P: true, BR: true, HR: true, H1: true, H2: true, H3: true, H4: true, H5: true, H6: true, STRONG: true, EM: true, DEL: true,
        CODE: true, PRE: true, BLOCKQUOTE: true, UL: true, OL: true, LI: true, A: true, TABLE: true, THEAD: true, TBODY: true, TR: true,
        TH: true, TD: true
    };
    var SAFE_URL = /^(https?:|mailto:|\/(?!\/)|#)/i;

    function escapeHtml(text) {
        return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    var markdown = null;

    function markdownParser() {
        if (markdown === null && global.marked && global.marked.Marked) {
            // Own instance, so the global marked used by the docs pages keeps its defaults; raw HTML in Markdown is shown as text.
            markdown = new global.marked.Marked({ gfm: true, breaks: true });
            markdown.use({ renderer: { html: function (token) { return escapeHtml(token.text || token.raw || ''); } } });
        }
        return markdown;
    }

    /** Keeps only allow-listed elements (others are replaced by their text) and attributes (safe link targets). */
    function sanitize(node) {
        Array.prototype.slice.call(node.childNodes).forEach(function (child) {
            if (child.nodeType === 3) return;
            if (child.nodeType !== 1 || !ALLOWED_TAGS[child.tagName]) {
                node.replaceChild(document.createTextNode(child.nodeType === 1 ? child.textContent : ''), child);
                return;
            }
            Array.prototype.slice.call(child.attributes).forEach(function (attribute) {
                var keep = child.tagName === 'A' && attribute.name === 'href' && SAFE_URL.test(attribute.value.trim());
                if (!keep && !(attribute.name === 'align' && (child.tagName === 'TH' || child.tagName === 'TD'))) {
                    child.removeAttribute(attribute.name);
                }
            });
            if (child.tagName === 'A') {
                child.setAttribute('rel', 'noopener noreferrer');
                child.setAttribute('target', '_blank');
            }
            sanitize(child);
        });
    }

    registerRenderer('text', function (container, panel) {
        var body = el('div', 'docs-content report-text');
        body.setAttribute('data-testid', 'report-text');
        var parser = markdownParser();
        if (!parser) {
            body.appendChild(el('p', 'mb-0', panel.markdown));
        } else {
            var template = document.createElement('template');
            template.innerHTML = parser.parse(panel.markdown || '');
            sanitize(template.content);
            body.appendChild(template.content);
        }
        container.appendChild(body);
    });

    // endregion

    // region charts

    /**
     * Apache ECharts adapter (loaded only on report pages, vendored as webjar). The chart spec is renderer-neutral and defined together
     * with the first chart panel types (Reports 2/8): { series: [{ name, style, points: [[x, y], ...] }], xAxis: { type }, yAxis: { unit } }.
     */
    var echartsAdapter = {
        name: 'echarts',
        available: function () {
            return typeof global.echarts !== 'undefined';
        },
        renderChart: function (container, spec) {
            if (!this.available()) throw new Error('ECharts is not loaded');
            var dark = document.documentElement.getAttribute('data-theme') !== 'light';
            var chart = global.echarts.init(container, dark ? 'dark' : null, { renderer: 'canvas' });
            chart.setOption(toEchartsOption(spec || {}));
            var onResize = function () { chart.resize(); };
            global.addEventListener('resize', onResize);
            return {
                dispose: function () {
                    global.removeEventListener('resize', onResize);
                    chart.dispose();
                }
            };
        }
    };

    function toEchartsOption(spec) {
        return {
            backgroundColor: 'transparent',
            tooltip: { trigger: 'axis' },
            legend: { show: (spec.series || []).length > 1 },
            xAxis: { type: (spec.xAxis && spec.xAxis.type) || 'category' },
            yAxis: { type: 'value', name: (spec.yAxis && spec.yAxis.unit) || '' },
            dataZoom: [{ type: 'inside' }],
            series: (spec.series || []).map(function (series) {
                return { name: series.name, type: series.style === 'column' ? 'bar' : 'line', areaStyle: series.style === 'area' ? {} : undefined, data: series.points || [] };
            })
        };
    }

    chartAdapter = echartsAdapter;

    function setChartAdapter(adapter) {
        chartAdapter = adapter;
    }

    function chart() {
        return chartAdapter;
    }

    // endregion

    return {
        render: render,
        registerRenderer: registerRenderer,
        setChartAdapter: setChartAdapter,
        chart: chart
    };
})(window);
