/**
 * Renders and wires up ".unit-value-field" containers: the number+granularity-dropdown / text-mode data entry
 * widget used for LONG/DOUBLE properties with a configured PropertyUnit. Mirrors the granularity catalog and
 * parsing/formatting rules of domain-api's PropertyUnit.kt/UnitFormat.kt (kept in sync manually, since this is a
 * client-side convenience layer only - the server always re-parses and re-validates the submitted raw text).
 *
 * A single hidden input per field is the source of truth for submission: it always holds text accepted by the
 * server's parseUnitValue (a bare number, or unit-suffixed text such as "15km 400m"). The visible number+dropdown
 * or text controls are just a view onto that hidden value, kept in sync via event listeners on user input and via
 * explicit resync calls wherever code elsewhere in the page sets the hidden value programmatically (defaults,
 * snapshot replay).
 */
var UNIT_GRANULARITIES = {
    TIME: [
        { value: 'MILLISECONDS', symbol: 'ms', factor: 1 },
        { value: 'SECONDS', symbol: 's', factor: 1000 },
        { value: 'MINUTES', symbol: 'min', factor: 60000 },
        { value: 'HOURS', symbol: 'h', factor: 3600000 },
        { value: 'DAYS', symbol: 'd', factor: 86400000 }
    ],
    DISTANCE: [
        { value: 'MILLIMETERS', symbol: 'mm', factor: 1 },
        { value: 'CENTIMETERS', symbol: 'cm', factor: 10 },
        { value: 'METERS', symbol: 'm', factor: 1000 },
        { value: 'KILOMETERS', symbol: 'km', factor: 1000000 }
    ]
};

function unitGranularitiesFor(family) {
    return UNIT_GRANULARITIES[family] || [];
}

function unitGranularitySymbol(family, granularityValue) {
    var g = unitGranularitiesFor(family).find(function (x) { return x.value === granularityValue; });
    return g ? g.symbol : '';
}

function unitGranularityFactor(family, granularityValue) {
    var g = unitGranularitiesFor(family).find(function (x) { return x.value === granularityValue; });
    return g ? g.factor : null;
}

/** Trims floating-point noise (e.g. 17.229999999999997) from a computed number, returning a plain-string representation. */
function unitTrimNumber(n) {
    if (!isFinite(n)) return '';
    var rounded = Math.round(n * 1e9) / 1e9;
    return String(rounded);
}

function unitParseNumber(raw) {
    if (!/^-?\d+([.,]\d+)?$/.test(raw)) return null;
    var n = parseFloat(raw.replace(',', '.'));
    return isNaN(n) ? null : n;
}

/** Converts a number (or numeric string) from one granularity of a family to another. Returns '' if inputs are invalid/empty. */
function convertUnitValue(value, family, fromGranularity, toGranularity) {
    if (value === '' || value == null) return '';
    var num = typeof value === 'number' ? value : unitParseNumber(String(value));
    if (num === null || isNaN(num)) return '';
    var fromFactor = unitGranularityFactor(family, fromGranularity);
    var toFactor = unitGranularityFactor(family, toGranularity);
    if (!fromFactor || !toFactor) return '';
    return unitTrimNumber(num * fromFactor / toFactor);
}

/**
 * Parses free-form unit text (a bare number, or unit-suffixed text like "15km 400m") into a number expressed in
 * storageGranularity, mirroring UnitFormat.kt's parseUnitValue. Returns null if raw doesn't match either format.
 */
function parseUnitText(raw, family, storageGranularityValue) {
    var trimmed = (raw || '').trim();
    if (!trimmed) return null;
    var plain = unitParseNumber(trimmed);
    if (plain !== null) return plain;

    var granularities = unitGranularitiesFor(family);
    var storage = granularities.find(function (g) { return g.value === storageGranularityValue; });
    if (!storage) return null;

    var symbols = granularities
        .slice()
        .sort(function (a, b) { return b.symbol.length - a.symbol.length; })
        .map(function (g) { return g.symbol.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); });
    var numberPattern = '-?\\d+(?:[.,]\\d+)?';
    var symbolPattern = symbols.join('|');
    var formatRe = new RegExp('^(?:' + numberPattern + '\\s*(?:' + symbolPattern + ')\\s*)+$');
    if (!formatRe.test(trimmed)) return null;

    var tokenRe = new RegExp('(' + numberPattern + ')\\s*(' + symbolPattern + ')', 'g');
    var totalInSmallestUnit = 0;
    var match;
    while ((match = tokenRe.exec(trimmed)) !== null) {
        var amount = unitParseNumber(match[1]);
        if (amount === null) return null;
        var granularity = granularities.find(function (g) { return g.symbol === match[2]; });
        if (!granularity) return null;
        totalInSmallestUnit += amount * granularity.factor;
    }
    return totalInSmallestUnit / storage.factor;
}

/** Builds the DOM structure for a `.unit-value-field` (used by object-property-fields.js for nested OBJECT properties). */
function buildUnitValueField(field, name, value) {
    var container = document.createElement('div');
    container.className = 'unit-value-field';
    container.setAttribute('data-unit-family', field.unitFamily || '');
    container.setAttribute('data-unit-storage-granularity', field.unitStorageGranularity || '');
    container.setAttribute('data-unit-default-granularity', field.unitDefaultGranularity || '');

    var hidden = document.createElement('input');
    hidden.type = 'hidden';
    hidden.className = 'unit-hidden-value';
    hidden.name = name;
    if (value !== undefined && value !== null) hidden.value = value;
    container.appendChild(hidden);

    var dropdownMode = document.createElement('div');
    dropdownMode.className = 'unit-dropdown-mode';
    var dropdownGroup = document.createElement('div');
    dropdownGroup.className = 'input-group input-group-sm';
    var displayInput = document.createElement('input');
    displayInput.type = 'number';
    displayInput.step = 'any';
    displayInput.className = 'form-control app-form-control form-control-sm unit-display-input';
    if (!field.nullable) displayInput.required = true;
    var granularitySelect = document.createElement('select');
    granularitySelect.className = 'form-select app-form-control form-select-sm unit-granularity-select';
    granularitySelect.style.maxWidth = '6rem';
    var dropdownToggle = document.createElement('button');
    dropdownToggle.type = 'button';
    dropdownToggle.className = 'btn btn-app-secondary unit-mode-toggle';
    dropdownToggle.setAttribute('aria-label', unitFieldMessages.textModeButton || '');
    dropdownToggle.title = unitFieldMessages.textModeButton || '';
    dropdownToggle.innerHTML = '<i class="bi bi-fonts" aria-hidden="true"></i>';
    dropdownGroup.appendChild(displayInput);
    dropdownGroup.appendChild(granularitySelect);
    dropdownGroup.appendChild(dropdownToggle);
    dropdownMode.appendChild(dropdownGroup);

    var textMode = document.createElement('div');
    textMode.className = 'unit-text-mode d-none';
    var textGroup = document.createElement('div');
    textGroup.className = 'input-group input-group-sm';
    var textInput = document.createElement('input');
    textInput.type = 'text';
    textInput.className = 'form-control app-form-control form-control-sm unit-text-input';
    textInput.placeholder = field.unitFormatHint || '';
    var textToggle = document.createElement('button');
    textToggle.type = 'button';
    textToggle.className = 'btn btn-app-secondary unit-mode-toggle';
    textToggle.setAttribute('aria-label', unitFieldMessages.dropdownModeButton || '');
    textToggle.title = unitFieldMessages.dropdownModeButton || '';
    textToggle.innerHTML = '<i class="bi bi-hash" aria-hidden="true"></i>';
    textGroup.appendChild(textInput);
    textGroup.appendChild(textToggle);
    textMode.appendChild(textGroup);

    container.appendChild(dropdownMode);
    container.appendChild(textMode);
    return container;
}

var unitFieldMessages = {};

/**
 * Sets the messages (mode-toggle button labels) used by buildUnitValueField for nested OBJECT unit fields. Must be
 * called before renderObjectPropertyFields() (object-property-fields.js) so freshly built fields pick them up -
 * initUnitFields()'s own `messages` argument only reaches fields that already exist in the DOM at that point.
 */
function setUnitFieldMessages(messages) {
    unitFieldMessages = messages || {};
}

/** Wires up every not-yet-initialized `.unit-value-field` found under `root` (defaults to the whole document). */
function initUnitFields(root, messages) {
    if (messages) setUnitFieldMessages(messages);
    (root || document).querySelectorAll('.unit-value-field').forEach(function (container) {
        if (container._unitInitialized) return;
        container._unitInitialized = true;

        var family = container.getAttribute('data-unit-family');
        var storageGranularity = container.getAttribute('data-unit-storage-granularity');
        var defaultGranularity = container.getAttribute('data-unit-default-granularity');
        var hidden = container.querySelector('.unit-hidden-value');
        var displayInput = container.querySelector('.unit-display-input');
        var granularitySelect = container.querySelector('.unit-granularity-select');
        var textInput = container.querySelector('.unit-text-input');
        var dropdownMode = container.querySelector('.unit-dropdown-mode');
        var textMode = container.querySelector('.unit-text-mode');

        unitGranularitiesFor(family).forEach(function (g) {
            var option = document.createElement('option');
            option.value = g.value;
            option.textContent = g.symbol;
            granularitySelect.appendChild(option);
        });
        granularitySelect.value = defaultGranularity;

        function syncDisplayFromHidden() {
            var raw = hidden.value;
            if (!raw) {
                displayInput.value = '';
                return;
            }
            var parsedStorage = parseUnitText(raw, family, storageGranularity);
            displayInput.value = parsedStorage === null ? '' : convertUnitValue(parsedStorage, family, storageGranularity, granularitySelect.value);
        }

        function syncHiddenFromDropdown() {
            var raw = displayInput.value;
            hidden.value = raw === '' ? '' : raw + unitGranularitySymbol(family, granularitySelect.value);
        }

        syncDisplayFromHidden();
        textInput.value = hidden.value || '';

        displayInput.addEventListener('input', syncHiddenFromDropdown);
        granularitySelect.addEventListener('change', function () {
            syncDisplayFromHidden();
            syncHiddenFromDropdown();
        });
        textInput.addEventListener('input', function () {
            hidden.value = textInput.value;
        });

        container.querySelectorAll('.unit-mode-toggle').forEach(function (btn) {
            btn.addEventListener('click', function () {
                if (textMode.classList.contains('d-none')) {
                    textInput.value = hidden.value || '';
                    dropdownMode.classList.add('d-none');
                    textMode.classList.remove('d-none');
                } else {
                    syncDisplayFromHidden();
                    textMode.classList.add('d-none');
                    dropdownMode.classList.remove('d-none');
                }
            });
        });

        container._unitResync = function () {
            syncDisplayFromHidden();
            textInput.value = hidden.value || '';
        };
    });
}

/** Re-derives the visible dropdown/text controls of every `.unit-value-field` under `root` from its hidden value.
 * Needed after code elsewhere sets the hidden value directly (e.g. reapplying defaults or a captured snapshot).
 */
function resyncUnitFields(root) {
    (root || document).querySelectorAll('.unit-value-field').forEach(function (container) {
        if (container._unitResync) container._unitResync();
    });
}
