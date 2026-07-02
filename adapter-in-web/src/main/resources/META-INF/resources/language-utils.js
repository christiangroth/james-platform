function getLanguageCookie() {
    var match = document.cookie.match(/(?:^|; )lang=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}

function setLanguageCookie(code) {
    var maxAgeSeconds = 60 * 60 * 24 * 365;
    document.cookie = 'lang=' + encodeURIComponent(code) + '; path=/; max-age=' + maxAgeSeconds + '; samesite=lax';
}

function initLanguageToggle() {
    var toggle = document.getElementById('language-toggle');
    if (!toggle) {
        return;
    }
    toggle.addEventListener('click', function () {
        var current = getLanguageCookie() === 'xx' ? 'xx' : 'de';
        var next = current === 'de' ? 'xx' : 'de';
        setLanguageCookie(next);
        window.location.reload();
    });
}

document.addEventListener('DOMContentLoaded', initLanguageToggle);
