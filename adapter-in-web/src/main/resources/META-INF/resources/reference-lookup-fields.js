/**
 * Reference lookup fields (ADR 0026), shared by app-data-new.html and app-data-edit.html.
 *
 * A REF property with a reference lookup is rendered server-side as the regular select; this script replaces it with a
 * text input (`reflookup_<propertyId>`) offering the target entity's existing lookup values as proposals. The text is
 * resolved to the target object on save. With auto-create, an unknown text shows a hint plus the target entity's further
 * fields (rendered by object-property-fields.js into a `refnew_<propertyId>` container) - nothing is created before the
 * whole form is saved. Hidden new-object fields are disabled, so they are neither validated by the browser nor submitted.
 */
var referenceLookupMessages = {};
var referenceLookupData = {};

function referenceLookupIsKnown(lookup, text) {
    var lower = text.toLowerCase();
    return (lookup.proposals || []).some(function (p) { return p === text || p.toLowerCase() === lower; });
}

/** Shows/hides the "new" hint and the new-object fields of one lookup field for its current text. */
function syncReferenceLookupField(wrapper) {
    var lookup = referenceLookupData[wrapper.getAttribute('data-property-id')];
    if (!lookup) return;
    var input = wrapper.querySelector('.reference-lookup-input');
    var text = input.value.trim();
    var isNew = text !== '' && !referenceLookupIsKnown(lookup, text);
    var hint = wrapper.querySelector('.reference-lookup-hint');
    hint.classList.toggle('d-none', !isNew);
    hint.textContent = lookup.autoCreate ? referenceLookupMessages.newHint : referenceLookupMessages.unknownHint;
    var newFields = wrapper.querySelector('.reference-lookup-new');
    if (newFields) {
        var showNew = isNew && lookup.autoCreate;
        newFields.classList.toggle('d-none', !showNew);
        newFields.querySelectorAll('input, select, textarea').forEach(function (el) { el.disabled = !showNew; });
    }
}

/** Re-applies the visibility of all lookup fields, e.g. after object-property-fields.js re-rendered the new-object fields. */
function syncReferenceLookupFields(form) {
    form.querySelectorAll('.reference-lookup-field').forEach(syncReferenceLookupField);
}

/** Replaces the server-rendered selects of all lookup REF properties of `form` by lookup fields. Call before rendering OBJECT fields. */
function initReferenceLookupFields(form, lookups, messages) {
    referenceLookupData = lookups || {};
    referenceLookupMessages = messages || {};
    Object.keys(referenceLookupData).forEach(function (propertyId) {
        var lookup = referenceLookupData[propertyId];
        var select = form.querySelector('select[name="prop_' + propertyId + '"]');
        if (!select) return;

        var wrapper = document.createElement('div');
        wrapper.className = 'reference-lookup-field';
        wrapper.setAttribute('data-property-id', propertyId);

        var datalistId = 'reflookup_datalist_' + propertyId;
        var input = document.createElement('input');
        input.type = 'text';
        input.className = 'form-control app-form-control form-control-sm reference-lookup-input';
        input.name = 'reflookup_' + propertyId;
        input.id = 'reflookup_' + propertyId;
        input.autocomplete = 'off';
        input.required = select.required;
        input.value = lookup.value || '';
        input.setAttribute('list', datalistId);
        input.setAttribute('data-testid', 'reference-lookup-input-' + propertyId);
        wrapper.appendChild(input);

        var datalist = document.createElement('datalist');
        datalist.id = datalistId;
        (lookup.proposals || []).forEach(function (proposal) {
            var option = document.createElement('option');
            option.value = proposal;
            datalist.appendChild(option);
        });
        wrapper.appendChild(datalist);

        var hint = document.createElement('div');
        hint.className = 'form-text reference-lookup-hint d-none';
        hint.setAttribute('data-testid', 'reference-lookup-hint-' + propertyId);
        wrapper.appendChild(hint);

        if (lookup.autoCreate && lookup.hasNewFields) {
            var newFields = document.createElement('div');
            newFields.className = 'reference-lookup-new app-card p-3 mt-2 d-none';
            newFields.setAttribute('data-testid', 'reference-lookup-new-' + propertyId);
            var title = document.createElement('div');
            title.className = 'app-section-label mb-2';
            title.style.fontSize = '0.75rem';
            title.textContent = referenceLookupMessages.newFieldsTitle.replace('#', lookup.targetEntityName);
            newFields.appendChild(title);
            var container = document.createElement('div');
            container.className = 'object-property-fields';
            container.setAttribute('data-property-id', 'refnew_' + propertyId);
            container.setAttribute('data-name-prefix', 'refnew_' + propertyId);
            newFields.appendChild(container);
            wrapper.appendChild(newFields);
        }

        select.replaceWith(wrapper);
        input.addEventListener('input', function () { syncReferenceLookupField(wrapper); });
    });
}

/** Merges the target entity defaults of every auto-create lookup into `objectValues`, so new-object fields start prefilled. */
function referenceLookupObjectValues(objectValues) {
    var merged = Object.assign({}, objectValues || {});
    Object.keys(referenceLookupData).forEach(function (propertyId) {
        var key = 'refnew_' + propertyId;
        if (!merged[key] && referenceLookupData[propertyId].newDefaults) merged[key] = referenceLookupData[propertyId].newDefaults;
    });
    return merged;
}
