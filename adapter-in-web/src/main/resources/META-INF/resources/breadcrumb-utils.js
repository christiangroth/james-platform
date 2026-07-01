var BREADCRUMB_SMALL_MAX_WIDTH = 768;
var BREADCRUMB_MEDIUM_MAX_WIDTH = 1200;
var BREADCRUMB_SMALL_THRESHOLD = 3;
var BREADCRUMB_MEDIUM_THRESHOLD = 5;
var BREADCRUMB_LARGE_THRESHOLD = 7;

function breadcrumbThreshold() {
    var width = window.innerWidth;
    if (width < BREADCRUMB_SMALL_MAX_WIDTH) return BREADCRUMB_SMALL_THRESHOLD;
    if (width < BREADCRUMB_MEDIUM_MAX_WIDTH) return BREADCRUMB_MEDIUM_THRESHOLD;
    return BREADCRUMB_LARGE_THRESHOLD;
}

function truncateBreadcrumb(ol) {
    var existingEllipsis = ol.querySelector('.breadcrumb-ellipsis');
    if (existingEllipsis) existingEllipsis.remove();

    var items = Array.prototype.slice.call(ol.querySelectorAll(':scope > li.breadcrumb-item'));
    items.forEach(function (item) { item.classList.remove('d-none'); });

    var threshold = breadcrumbThreshold();
    if (items.length <= threshold) return;

    var hiddenItems = items.slice(1, items.length - 2);
    hiddenItems.forEach(function (item) { item.classList.add('d-none'); });

    var ellipsis = document.createElement('li');
    ellipsis.className = 'breadcrumb-item breadcrumb-ellipsis';
    ellipsis.setAttribute('aria-hidden', 'true');
    ellipsis.textContent = '…';
    items[0].insertAdjacentElement('afterend', ellipsis);
}

function truncateAllBreadcrumbs() {
    document.querySelectorAll('nav[aria-label="breadcrumb"] > ol.breadcrumb').forEach(truncateBreadcrumb);
}

document.addEventListener('DOMContentLoaded', truncateAllBreadcrumbs);

var breadcrumbResizeTimer;
window.addEventListener('resize', function () {
    clearTimeout(breadcrumbResizeTimer);
    breadcrumbResizeTimer = setTimeout(truncateAllBreadcrumbs, 150);
});
