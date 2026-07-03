function breadcrumbItems(ol) {
    return Array.prototype.slice.call(ol.querySelectorAll(':scope > li.breadcrumb-item:not(.breadcrumb-ellipsis)'));
}

function breadcrumbWraps(items) {
    var visible = items.filter(function (item) { return !item.classList.contains('d-none'); });
    if (visible.length < 2) return false;
    var firstTop = visible[0].offsetTop;
    return visible.some(function (item) { return item.offsetTop !== firstTop; });
}

function applyBreadcrumbHiddenCount(ol, items, hiddenCount) {
    var existingEllipsis = ol.querySelector('.breadcrumb-ellipsis');
    if (existingEllipsis) existingEllipsis.remove();
    items.forEach(function (item) { item.classList.remove('d-none'); });

    if (!hiddenCount) return;

    items.slice(1, 1 + hiddenCount).forEach(function (item) { item.classList.add('d-none'); });

    var ellipsis = document.createElement('li');
    ellipsis.className = 'breadcrumb-item breadcrumb-ellipsis';
    ellipsis.setAttribute('aria-hidden', 'true');
    ellipsis.textContent = '…';
    items[0].insertAdjacentElement('afterend', ellipsis);
}

function truncateBreadcrumb(ol) {
    var items = breadcrumbItems(ol);
    applyBreadcrumbHiddenCount(ol, items, 0);

    // first and last two items are always kept visible, so hiding only ever helps once there are 4+ items
    var maxHiddenCount = items.length - 3;
    var hiddenCount = 0;
    while (hiddenCount < maxHiddenCount && breadcrumbWraps(items)) {
        hiddenCount++;
        applyBreadcrumbHiddenCount(ol, items, hiddenCount);
    }
}

function truncateAllBreadcrumbs() {
    document.querySelectorAll('nav.page-breadcrumb > ol.breadcrumb').forEach(truncateBreadcrumb);
}

document.addEventListener('DOMContentLoaded', truncateAllBreadcrumbs);

var breadcrumbResizeTimer;
window.addEventListener('resize', function () {
    clearTimeout(breadcrumbResizeTimer);
    breadcrumbResizeTimer = setTimeout(truncateAllBreadcrumbs, 150);
});
