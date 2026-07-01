function getStoredTheme() {
    try {
        return localStorage.getItem('theme');
    } catch (e) {
        return null;
    }
}

function setStoredTheme(theme) {
    try {
        localStorage.setItem('theme', theme);
    } catch (e) {
        // localStorage unavailable (e.g. private browsing) - theme still applies for this page load
    }
}

function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
}

function currentTheme() {
    return document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
}

function initThemeToggle() {
    var toggle = document.getElementById('theme-toggle');
    if (toggle) {
        toggle.addEventListener('click', function () {
            var next = currentTheme() === 'dark' ? 'light' : 'dark';
            applyTheme(next);
            setStoredTheme(next);
        });
    }

    // Follow OS/browser theme changes live, but only as long as the user has not manually overridden it
    if (window.matchMedia) {
        window.matchMedia('(prefers-color-scheme: light)').addEventListener('change', function (event) {
            if (!getStoredTheme()) {
                applyTheme(event.matches ? 'light' : 'dark');
            }
        });
    }
}

document.addEventListener('DOMContentLoaded', initThemeToggle);
