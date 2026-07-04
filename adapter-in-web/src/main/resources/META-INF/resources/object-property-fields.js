/**
 * Renders form fields for OBJECT property values, mirroring the per-type input rendering used for
 * top-level properties in app-data-new.html / app-data-edit.html. Shared by both templates since
 * OBJECT properties may contain nested OBJECT properties to arbitrary depth.
 *
 * Nested OBJECT properties are navigated breadcrumb-style: every nesting level is rendered into its own
 * "object-level" div up front (so the underlying inputs keep their values across navigation) and only the
 * active level is shown. Switching levels is pure DOM show/hide - no server requests - and the
 * surrounding entity form is still submitted/saved as a single whole, only from the top level.
 */
var LIST_VALUE_SEPARATOR = '';
var objectFieldMessages = {};

function objectFieldScalarInput(field, name, value) {
    if (field.htmlInputType === 'checkbox') {
        var wrapper = document.createElement('div');
        wrapper.className = 'form-check';
        var checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.className = 'form-check-input';
        checkbox.name = name;
        checkbox.value = 'true';
        checkbox.checked = value === 'true' || value === true;
        wrapper.appendChild(checkbox);
        return wrapper;
    }
    if (field.htmlInputType === 'select') {
        var select = document.createElement('select');
        select.className = 'form-select app-form-control form-select-sm';
        select.name = name;
        if (!field.nullable) select.required = true;
        var emptyOpt = document.createElement('option');
        emptyOpt.value = '';
        emptyOpt.textContent = objectFieldMessages.selectPlaceholder;
        select.appendChild(emptyOpt);
        (objectFieldReferenceOptions(field.id) || []).forEach(function (opt) {
            var option = document.createElement('option');
            option.value = opt.id;
            option.textContent = opt.displayText;
            if (value === opt.id) option.selected = true;
            select.appendChild(option);
        });
        return select;
    }
    var input = document.createElement('input');
    input.type = field.htmlInputType;
    input.className = 'form-control app-form-control form-control-sm';
    input.name = name;
    if (!field.nullable) input.required = true;
    if (field.type === 'DOUBLE') input.step = field.step || 'any';
    else if (field.type === 'LONG' && field.step) input.step = field.step;
    if (field.min) input.min = field.min;
    if (field.max) input.max = field.max;
    if (field.type === 'DURATION') input.placeholder = objectFieldMessages.durationPlaceholder;
    if (value !== undefined && value !== null) input.value = value;

    if ((field.type === 'LONG' || field.type === 'DOUBLE') && field.step) {
        var group = document.createElement('div');
        group.className = 'input-group input-group-sm';
        group.appendChild(input);
        var stepDown = document.createElement('button');
        stepDown.type = 'button';
        stepDown.className = 'btn btn-app-secondary number-step-down';
        stepDown.tabIndex = -1;
        stepDown.setAttribute('aria-label', objectFieldMessages.decreaseValueAriaLabel);
        stepDown.innerHTML = '&minus;';
        group.appendChild(stepDown);
        var stepUp = document.createElement('button');
        stepUp.type = 'button';
        stepUp.className = 'btn btn-app-secondary number-step-up';
        stepUp.tabIndex = -1;
        stepUp.setAttribute('aria-label', objectFieldMessages.increaseValueAriaLabel);
        stepUp.innerHTML = '&plus;';
        group.appendChild(stepUp);
        return group;
    }

    return input;
}

function objectFieldListRow(field, name, value) {
    var row = document.createElement('div');
    row.className = 'd-flex align-items-center gap-2 mb-2';
    var itemField = { id: field.id, htmlInputType: field.itemHtmlInputType, nullable: true, type: field.listItemType };
    row.appendChild(objectFieldScalarInput(itemField, name, value));
    var removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn btn-app-secondary btn-sm';
    removeBtn.innerHTML = '&times;';
    removeBtn.setAttribute('aria-label', objectFieldMessages.removeValueAriaLabel);
    removeBtn.addEventListener('click', function () { row.remove(); });
    row.appendChild(removeBtn);
    return row;
}

function objectFieldList(field, name, rawValue) {
    var container = document.createElement('div');
    var values = typeof rawValue === 'string' ? rawValue.split(LIST_VALUE_SEPARATOR).filter(Boolean) : [];
    values.forEach(function (value) {
        container.insertBefore(objectFieldListRow(field, name, value), container.lastChild);
    });
    var addBtn = document.createElement('button');
    addBtn.type = 'button';
    addBtn.className = 'btn btn-app-secondary btn-sm';
    addBtn.textContent = objectFieldMessages.addValueButton;
    addBtn.addEventListener('click', function () {
        container.insertBefore(objectFieldListRow(field, name, ''), addBtn);
    });
    container.appendChild(addBtn);
    return container;
}

/** Builds the red required-marker span appended to a field label, mirroring the server-rendered top-level fields. */
function buildRequiredAsterisk() {
    var span = document.createElement('span');
    span.className = 'text-danger ms-1';
    span.setAttribute('aria-label', objectFieldMessages.requiredAriaLabel);
    span.textContent = '*';
    return span;
}

/** Builds the muted constraint hint text shown under a field's input, mirroring the server-rendered top-level fields. */
function buildConstraintHintText(text) {
    var div = document.createElement('div');
    div.className = 'form-text';
    div.textContent = text;
    return div;
}

function buildObjectPropertyField(field, name, value) {
    var wrapper = document.createElement('div');
    wrapper.className = 'mb-3';
    wrapper.setAttribute('data-testid', 'object-field-' + field.id);
    var label = document.createElement('label');
    label.className = 'form-label form-label-sm';
    label.style.color = 'var(--color-text-muted)';
    label.appendChild(document.createTextNode(field.name));
    if (!field.nullable) label.appendChild(buildRequiredAsterisk());
    wrapper.appendChild(label);
    if (field.type === 'LIST') {
        wrapper.appendChild(objectFieldList(field, name, value));
        if (field.constraintHint) wrapper.appendChild(buildConstraintHintText(field.constraintHint));
        if (field.itemConstraintHint) wrapper.appendChild(buildConstraintHintText(field.itemConstraintHint));
    } else {
        wrapper.appendChild(objectFieldScalarInput(field, name, value));
        if (field.constraintHint) wrapper.appendChild(buildConstraintHintText(field.constraintHint));
    }
    return wrapper;
}

/** Renders the clickable row that drills into a nested OBJECT property's own level. */
function buildObjectDescendRow(field, targetPath) {
    var wrapper = document.createElement('div');
    wrapper.className = 'mb-3';
    wrapper.setAttribute('data-testid', 'object-field-' + field.id);
    var label = document.createElement('label');
    label.className = 'form-label form-label-sm d-block';
    label.style.color = 'var(--color-text-muted)';
    label.appendChild(document.createTextNode(field.name));
    if (!field.nullable) label.appendChild(buildRequiredAsterisk());
    wrapper.appendChild(label);
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn btn-app-secondary btn-sm descend-object-field';
    btn.setAttribute('data-target-path', targetPath);
    btn.setAttribute('data-testid', 'descend-object-field-' + field.id);
    var count = field.nestedProperties.length;
    var label2 = count === 0 ? objectFieldMessages.descendNoPropertiesLabel : objectFieldMessages.descendPropertyCountTemplate.replace('#', String(count));
    btn.textContent = label2 + ' ›';
    wrapper.appendChild(btn);
    return wrapper;
}

/**
 * Builds the level for `field` at `path` (its own nestedProperties), appending it to the container's
 * levels wrapper, and recurses into any nested OBJECT properties to build their levels too. `path` is the
 * dot-separated chain of nested property ids from the top-level OBJECT property down to this level; the
 * top-level property's own level uses the empty path.
 */
function buildObjectLevel(container, field, namePrefix, value, path) {
    var level = document.createElement('div');
    level.className = 'object-level' + (path === '' ? '' : ' d-none');
    level.dataset.path = path;
    if (path !== '') {
        var depth = path.split('.').length;
        level.dataset.depth = ((depth - 1) % 2) + 1;
    }
    container._pathNames[path] = field.name;

    if (field.nestedProperties.length === 0) {
        var empty = document.createElement('p');
        empty.className = 'app-section-label mb-0';
        empty.textContent = objectFieldMessages.noPropertiesDefinedMessage;
        level.appendChild(empty);
    } else {
        field.nestedProperties.forEach(function (nested) {
            var nestedName = namePrefix + '.' + nested.id;
            var nestedValue = value ? value[nested.id] : undefined;
            if (nested.type === 'OBJECT') {
                var childPath = path === '' ? nested.id : path + '.' + nested.id;
                level.appendChild(buildObjectDescendRow(nested, childPath));
                buildObjectLevel(container, nested, nestedName, nestedValue, childPath);
            } else {
                level.appendChild(buildObjectPropertyField(nested, nestedName, nestedValue));
            }
        });
    }
    container._levelsWrapper.appendChild(level);
}

function buildObjectBreadcrumbNav() {
    var nav = document.createElement('nav');
    nav.className = 'object-breadcrumb mb-2 d-none';
    nav.setAttribute('aria-label', objectFieldMessages.breadcrumbAriaLabel);
    var ol = document.createElement('ol');
    ol.className = 'breadcrumb mb-0';
    nav.appendChild(ol);
    return nav;
}

function appendObjectBreadcrumbItem(ol, label, path, isActive) {
    var li = document.createElement('li');
    li.className = 'breadcrumb-item' + (isActive ? ' active' : '');
    if (isActive) {
        li.setAttribute('aria-current', 'page');
        li.textContent = label;
    } else {
        var a = document.createElement('a');
        a.href = '#';
        a.className = 'breadcrumb-link';
        a.textContent = label;
        a.setAttribute('data-target-path', path);
        li.appendChild(a);
    }
    ol.appendChild(li);
}

function renderObjectBreadcrumb(container, path) {
    var nav = container.querySelector('.object-breadcrumb');
    var ol = nav.querySelector('ol');
    ol.innerHTML = '';
    if (path === '') {
        nav.classList.add('d-none');
        return;
    }
    nav.classList.remove('d-none');
    var names = container._pathNames;
    appendObjectBreadcrumbItem(ol, names[''], '', false);
    var cumulative = '';
    var segments = path.split('.');
    segments.forEach(function (segmentId, index) {
        cumulative = cumulative === '' ? segmentId : cumulative + '.' + segmentId;
        appendObjectBreadcrumbItem(ol, names[cumulative], cumulative, index === segments.length - 1);
    });
    if (typeof truncateBreadcrumb === 'function') truncateBreadcrumb(ol);
}

/** Switches the visible nesting level of an OBJECT property container, purely client-side (no requests). */
function activateObjectLevel(container, path) {
    container.querySelectorAll(':scope > .object-levels > .object-level').forEach(function (level) {
        level.classList.toggle('d-none', level.dataset.path !== path);
    });
    renderObjectBreadcrumb(container, path);
}

/** Renders the top-level fields for every `.object-property-fields` container found on the page. */
function renderObjectPropertyFields(objectFields, objectValues, referenceOptions, messages) {
    objectFieldReferenceOptionsData = referenceOptions || {};
    objectFieldMessages = messages || {};
    document.querySelectorAll('.object-property-fields').forEach(function (container) {
        var propertyId = container.getAttribute('data-property-id');
        var field = objectFields[propertyId];
        if (!field) return;
        var value = (objectValues && objectValues[propertyId]) || {};

        container._pathNames = {};
        container._levelsWrapper = document.createElement('div');
        container._levelsWrapper.className = 'object-levels';
        container.appendChild(buildObjectBreadcrumbNav());
        container.appendChild(container._levelsWrapper);

        buildObjectLevel(container, field, container.getAttribute('data-name-prefix'), value, '');
    });
}

var objectFieldReferenceOptionsData = {};
function objectFieldReferenceOptions(propertyId) {
    return objectFieldReferenceOptionsData[propertyId];
}

document.addEventListener('click', function (e) {
    var target = e.target.nodeType === Node.TEXT_NODE ? e.target.parentElement : e.target;
    if (!target) return;

    var descendBtn = target.closest('.descend-object-field');
    if (descendBtn) {
        var container = descendBtn.closest('.object-property-fields');
        if (container) activateObjectLevel(container, descendBtn.getAttribute('data-target-path'));
        return;
    }

    var breadcrumbLink = target.closest('.object-breadcrumb a[data-target-path]');
    if (breadcrumbLink) {
        e.preventDefault();
        var breadcrumbContainer = breadcrumbLink.closest('.object-property-fields');
        if (breadcrumbContainer) activateObjectLevel(breadcrumbContainer, breadcrumbLink.getAttribute('data-target-path'));
    }
});

/**
 * Handles clicks on number-step-up / number-step-down buttons rendered next to numeric inputs that have a
 * Step constraint configured (both server-rendered top-level fields and JS-rendered OBJECT fields above).
 * The new value is computed manually (instead of via the native input.stepUp()/stepDown(), which throws an
 * InvalidStateError when the current value isn't aligned with the step) and clamped to the input's min/max
 * attributes, if set.
 */
document.addEventListener('click', function (e) {
    var target = e.target.nodeType === Node.TEXT_NODE ? e.target.parentElement : e.target;
    var button = target ? target.closest('.number-step-up, .number-step-down') : null;
    if (!button) return;
    var input = button.closest('.input-group').querySelector('input');
    if (!input) return;
    var step = parseFloat(input.step);
    if (!step) return;
    var decimals = (input.step.split('.')[1] || '').length;
    var current = parseFloat(input.value);
    if (isNaN(current)) current = 0;
    var next = button.classList.contains('number-step-up') ? current + step : current - step;
    if (input.min !== '' && next < parseFloat(input.min)) next = parseFloat(input.min);
    if (input.max !== '' && next > parseFloat(input.max)) next = parseFloat(input.max);
    input.value = next.toFixed(decimals);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
});
