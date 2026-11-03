/**
 * Renders the renderer-neutral panel spec of a declarative report (see docs/plans/reports.md): tabs -> rows -> panels. The server has
 * already executed all queries and formatted every value; this module only lays the panels out and draws them.
 *
 * - Panel renderers are registered per panel type (JamesReports.registerRenderer(type, fn(container, panel, texts, context))); a panel type
 *   without a renderer shows a placeholder instead of breaking the report, so later steps only add renderers. Renderers creating charts hand
 *   their handle to context.track(handle), so a re-render disposes them.
 * - Charts go through a small adapter interface ({ renderChart(container, spec) }). The only adapter, for Apache ECharts, is the single
 *   place knowing the chart library, so it stays swappable (JamesReports.setChartAdapter(adapter)).
 * - Text panels render Markdown via marked with raw HTML disabled, then pass the result through an allow-list sanitizer: no report can
 *   inject markup, scripts, event handlers or javascript: links.
 * - Report variables (view.variables) render as select controls above the tabs; a change hands all selections to texts.onVariablesChange,
 *   and the page re-requests the report from the server with them.
 * - Warnings panels list the findings of the report's checks; a tab with findings shows their number (tab.warnings) as badge on its label.
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

    /** Disposes the charts of a previous render into [container] (e.g. the editor preview re-rendering). */
    function disposeCharts(container) {
        (container.__jamesReportCharts || []).forEach(function (handle) {
            try { handle.dispose(); } catch (e) { /* already gone */ }
        });
        container.__jamesReportCharts = [];
    }

    function render(container, view, texts) {
        texts = texts || {};
        instanceCounter += 1;
        disposeCharts(container);
        var context = { track: function (handle) { container.__jamesReportCharts.push(handle); } };
        container.textContent = '';
        renderVariables(container, (view && view.variables) || [], texts);
        var tabs = (view && view.tabs) || [];
        if (tabs.length === 0) {
            container.appendChild(el('p', 'app-section-label mb-0', texts.noContent));
            return;
        }
        if (tabs.length === 1) {
            container.appendChild(renderTab(tabs[0], texts, context));
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
            if (tab.warnings > 0) {
                var badge = el('span', 'badge rounded-pill text-bg-warning ms-2', String(tab.warnings));
                badge.setAttribute('data-testid', 'report-tab-warnings');
                if (texts.warningsCount) badge.title = tab.warnings + ' ' + texts.warningsCount;
                button.appendChild(badge);
            }
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
            pane.appendChild(renderTab(tab, texts, context));
            content.appendChild(pane);
        });
        container.appendChild(nav);
        container.appendChild(content);
    }

    /**
     * Variable controls (time range, select) on top of the report. Changing one calls texts.onVariablesChange(selections) with the selected
     * value per variable name; the page re-requests the report with them (the server executes it again, nothing is filtered here).
     */
    function renderVariables(container, variables, texts) {
        if (variables.length === 0) return;
        var bar = el('div', 'row g-2 mb-3 align-items-end');
        bar.setAttribute('data-testid', 'report-variables');
        bar.setAttribute('role', 'group');
        if (texts.variablesLabel) bar.setAttribute('aria-label', texts.variablesLabel);
        var selections = {};
        variables.forEach(function (variable) { selections[variable.name] = variable.selected; });
        variables.forEach(function (variable) {
            var col = el('div', 'col-12 col-sm-auto');
            var id = 'report-' + instanceCounter + '-var-' + variable.name;
            var label = el('label', 'form-label small mb-1', variable.label);
            label.setAttribute('for', id);
            var select = el('select', 'form-select form-select-sm');
            select.id = id;
            select.setAttribute('data-testid', 'report-variable');
            select.setAttribute('data-variable', variable.name);
            (variable.options || []).forEach(function (option) {
                var node = el('option', null, option.label);
                node.value = option.value;
                if (option.value === variable.selected) node.selected = true;
                select.appendChild(node);
            });
            select.disabled = typeof texts.onVariablesChange !== 'function';
            select.addEventListener('change', function () {
                selections[variable.name] = select.value;
                texts.onVariablesChange(Object.assign({}, selections));
            });
            col.appendChild(label);
            col.appendChild(select);
            bar.appendChild(col);
        });
        container.appendChild(bar);
    }

    function renderTab(tab, texts, context) {
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
                    renderer(body, panel, texts, context);
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

    /** Named threshold colors of stat panels (see ReportValues.COLOR_NAMES); any other value is a validated #rgb/#rrggbb color. */
    var COLORS = {
        green: '#2f9e5b', red: '#d64545', orange: '#e8892c', yellow: '#d4ac0d', blue: '#3a7bd5', purple: '#8a5cd6', gray: '#8a8f98'
    };
    var numberFormat = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 2 });

    /**
     * Apache ECharts adapter (loaded only on report pages, vendored as webjar). It maps the renderer-neutral chart spec built by the server
     * (ChartPanelSpec: xAxis { type: category|time, categories }, yAxes [{ position, unit }], series [{ name, style, yAxis, points: [{ x, y,
     * text }] }], stack, zoom, horizontal) to an ECharts option; no other code knows the chart library.
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
            // Also covers charts in hidden tabs: they get their size once the tab is shown.
            var observer = typeof global.ResizeObserver !== 'undefined' ? new global.ResizeObserver(function () { chart.resize(); }) : null;
            var onResize = function () { chart.resize(); };
            if (observer) observer.observe(container); else global.addEventListener('resize', onResize);
            return {
                dispose: function () {
                    if (observer) observer.disconnect(); else global.removeEventListener('resize', onResize);
                    chart.dispose();
                }
            };
        }
    };

    function unitLabel(unit) {
        return function (value) {
            return numberFormat.format(value) + (unit ? ' ' + unit : '');
        };
    }

    function tooltip(params) {
        var list = Array.isArray(params) ? params : [params];
        if (list.length === 0) return '';
        var lines = list.filter(function (p) { return p.data && p.data.text; }).map(function (p) {
            return p.marker + escapeHtml(p.seriesName) + ': <b>' + escapeHtml(p.data.text) + '</b>';
        });
        return escapeHtml(list[0].axisValueLabel || list[0].name || '') + '<br>' + lines.join('<br>');
    }

    function toEchartsOption(spec) {
        var horizontal = !!spec.horizontal;
        var category = !spec.xAxis || spec.xAxis.type !== 'time';
        var seriesSpecs = spec.series || [];
        var axisSpecs = spec.yAxes && spec.yAxes.length ? spec.yAxes : [{ position: 'left', unit: null }];
        var xAxis = category
            ? { type: 'category', data: (spec.xAxis && spec.xAxis.categories) || [], inverse: horizontal, axisLabel: { hideOverlap: true } }
            : { type: 'time' };
        var valueAxes = axisSpecs.map(function (axis, index) {
            return {
                type: 'value',
                position: horizontal ? (axis.position === 'right' ? 'top' : 'bottom') : axis.position,
                name: axis.unit || '',
                alignTicks: index > 0,
                splitLine: { show: index === 0 },
                axisLabel: { formatter: unitLabel(axis.unit) }
            };
        });
        var series = seriesSpecs.map(function (item) {
            var data = (item.points || []).map(function (point) {
                return category ? { value: point.y, text: point.text } : { value: [point.x, point.y], text: point.text };
            });
            var out = {
                name: item.name,
                type: item.style === 'column' ? 'bar' : 'line',
                data: data,
                connectNulls: false,
                showSymbol: data.length <= 60
            };
            if (horizontal) out.xAxisIndex = item.yAxis || 0; else out.yAxisIndex = item.yAxis || 0;
            if (item.style === 'area') out.areaStyle = { opacity: 0.25 };
            if (spec.stack && item.style !== 'line') out.stack = 'total';
            return out;
        });
        var zoomAxis = horizontal ? { yAxisIndex: 0 } : { xAxisIndex: 0 };
        var dataZoom = spec.zoom
            ? [Object.assign({ type: 'inside' }, zoomAxis), Object.assign({ type: 'slider', height: 18, bottom: 6 }, zoomAxis)]
            : [];
        var legend = series.length > 1;
        return {
            backgroundColor: 'transparent',
            grid: { left: 8, right: 8, top: legend ? 40 : 24, bottom: spec.zoom && !horizontal ? 40 : 8, containLabel: true },
            tooltip: { trigger: 'axis', axisPointer: { type: category ? 'shadow' : 'line' }, formatter: tooltip },
            legend: { show: legend, type: 'scroll', top: 0 },
            xAxis: horizontal ? valueAxes : xAxis,
            yAxis: horizontal ? xAxis : valueAxes,
            dataZoom: dataZoom,
            series: series
        };
    }

    chartAdapter = echartsAdapter;

    function setChartAdapter(adapter) {
        chartAdapter = adapter;
    }

    function chart() {
        return chartAdapter;
    }

    function renderChartPanel(container, panel, texts, context) {
        var hasPoints = (panel.series || []).some(function (series) {
            return (series.points || []).some(function (point) { return point.y !== null && point.y !== undefined; });
        });
        if (!hasPoints) {
            container.appendChild(el('p', 'app-section-label mb-0', texts.empty));
            return;
        }
        var categories = (panel.xAxis && panel.xAxis.categories) || [];
        var height = panel.horizontal ? Math.max(240, categories.length * 26 + 70) : 300;
        var node = el('div', 'report-chart');
        node.style.width = '100%';
        node.style.height = height + 'px';
        node.setAttribute('data-testid', 'report-chart');
        container.appendChild(node);
        try {
            var handle = chartAdapter.renderChart(node, panel);
            if (context && handle) context.track(handle);
        } catch (e) {
            container.removeChild(node);
            container.appendChild(el('p', 'app-section-label mb-0', texts.chartUnavailable || ''));
        }
    }

    registerRenderer('timeseries', renderChartPanel);
    registerRenderer('bar', renderChartPanel);

    // endregion

    // region stat

    var TREND_ARROWS = { up: '▲', down: '▼', flat: '▶' };

    registerRenderer('stat', function (container, panel, texts) {
        var value = el('div', 'fs-2 fw-semibold lh-sm text-nowrap', panel.text);
        value.setAttribute('data-testid', 'report-stat-value');
        if (panel.color) value.style.color = COLORS[panel.color] || panel.color;
        container.appendChild(value);
        var comparison = panel.comparison;
        if (comparison) {
            var line = el('div', 'app-section-label small mt-1');
            line.setAttribute('data-testid', 'report-stat-comparison');
            line.title = comparison.periodText;
            var delta = (TREND_ARROWS[comparison.trend] || '') + ' ' + comparison.deltaText + (comparison.percentText ? ' (' + comparison.percentText + ')' : '');
            line.appendChild(el('span', 'fw-semibold', delta));
            line.appendChild(document.createTextNode(' ' + (texts.statPrevious || '') + ': ' + comparison.previousText));
            container.appendChild(line);
        }
    });

    // endregion

    // region warnings

    registerRenderer('warnings', function (container, panel, texts) {
        var warnings = panel.warnings || [];
        if (warnings.length === 0) {
            container.appendChild(el('p', 'app-section-label mb-0', texts.warningsEmpty || texts.empty));
            return;
        }
        var list = el('ul', 'list-unstyled mb-0 report-warnings');
        list.setAttribute('data-testid', 'report-warnings');
        warnings.forEach(function (warning) {
            var error = warning.severity === 'error';
            var item = el('li', 'd-flex gap-2 align-items-baseline py-1');
            item.setAttribute('data-testid', 'report-warning');
            item.setAttribute('data-severity', warning.severity);
            var marker = el('span', error ? 'text-danger' : 'text-warning', error ? '●' : '▲');
            marker.setAttribute('aria-hidden', 'true');
            item.appendChild(marker);
            var body = el('span');
            if (warning.label) body.appendChild(el('span', 'fw-semibold me-2 text-nowrap', warning.label));
            body.appendChild(document.createTextNode(warning.message));
            item.appendChild(body);
            list.appendChild(item);
        });
        container.appendChild(list);
        if (panel.truncated) {
            container.appendChild(el('p', 'app-section-label small mt-2 mb-0', texts.warningsTruncated || texts.truncated));
        }
    });

    // endregion

    return {
        render: render,
        registerRenderer: registerRenderer,
        setChartAdapter: setChartAdapter,
        chart: chart
    };
})(window);
