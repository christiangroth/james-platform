/**
 * Recursively renders form fields for OBJECT property values, mirroring the per-type input rendering
 * used for top-level properties in app-data-new.html / app-data-edit.html. Shared by both templates
 * since OBJECT properties may contain nested OBJECT properties to arbitrary depth.
 */
var LIST_VALUE_SEPARATOR = '';

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
        emptyOpt.textContent = '– Select –';
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
    if (field.type === 'DOUBLE') input.step = 'any';
    if (field.type === 'DURATION') input.placeholder = 'e.g. 1d 2h 30m 15s or 02:30:15';
    if (value !== undefined && value !== null) input.value = value;
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
    removeBtn.setAttribute('aria-label', 'Remove value');
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
    addBtn.textContent = '+ Add value';
    addBtn.addEventListener('click', function () {
        container.insertBefore(objectFieldListRow(field, name, ''), addBtn);
    });
    container.appendChild(addBtn);
    return container;
}

function objectFieldNested(field, namePrefix, value) {
    var container = document.createElement('div');
    container.className = 'border rounded p-2 ms-2';
    field.nestedProperties.forEach(function (nested) {
        container.appendChild(buildObjectPropertyField(nested, namePrefix + '.' + nested.id, value ? value[nested.id] : undefined));
    });
    return container;
}

function buildObjectPropertyField(field, name, value) {
    var wrapper = document.createElement('div');
    wrapper.className = 'mb-3';
    wrapper.setAttribute('data-testid', 'object-field-' + field.id);
    var label = document.createElement('label');
    label.className = 'form-label form-label-sm';
    label.style.color = 'var(--color-text-muted)';
    var typeLabel = field.type + (field.type === 'LIST' && field.listItemType ? '<' + field.listItemType + '>' : '') + (field.nullable ? '?' : '');
    label.textContent = field.name + (field.nullable ? '' : ' *') + ' (' + typeLabel + ')';
    wrapper.appendChild(label);
    if (field.type === 'LIST') {
        wrapper.appendChild(objectFieldList(field, name, value));
    } else if (field.type === 'OBJECT') {
        wrapper.appendChild(objectFieldNested(field, name, value));
    } else {
        wrapper.appendChild(objectFieldScalarInput(field, name, value));
    }
    return wrapper;
}

/** Renders the top-level fields for every `.object-property-fields` container found on the page. */
function renderObjectPropertyFields(objectFields, objectValues, referenceOptions) {
    objectFieldReferenceOptionsData = referenceOptions || {};
    document.querySelectorAll('.object-property-fields').forEach(function (container) {
        var propertyId = container.getAttribute('data-property-id');
        var field = objectFields[propertyId];
        if (!field) return;
        var value = (objectValues && objectValues[propertyId]) || {};
        field.nestedProperties.forEach(function (nested) {
            container.appendChild(buildObjectPropertyField(nested, container.getAttribute('data-name-prefix') + '.' + nested.id, value[nested.id]));
        });
    });
}

var objectFieldReferenceOptionsData = {};
function objectFieldReferenceOptions(propertyId) {
    return objectFieldReferenceOptionsData[propertyId];
}
