function fadeUpdate(elementId, url, callback) {
    var el = document.getElementById(elementId);
    if (!el) return;
    el.style.transition = 'opacity 0.5s ease';
    el.style.opacity = '0';
    fetch(url)
        .then(function (r) { return r.text(); })
        .then(function (html) {
            el.innerHTML = html;
            el.style.opacity = '1';
            if (callback) callback();
        })
        .catch(function () {
            el.style.opacity = '1';
        });
}

function connectSse(url, onMessage, onOpen) {
    var source;
    function connect() {
        source = new EventSource(url);
        source.onopen = function () {
            if (onOpen) onOpen();
        };
        source.onmessage = onMessage;
        source.onerror = function () {
            source.close();
        };
    }
    connect();
    setInterval(function () {
        if (!source || source.readyState === EventSource.CLOSED) {
            connect();
        }
    }, 60000);
}

var TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000;

function formatCountdown(ms) {
    if (ms <= 0) return 'now';
    var totalSeconds = Math.floor(ms / 1000);
    var hours = Math.floor(totalSeconds / 3600);
    var minutes = Math.floor((totalSeconds % 3600) / 60);
    var seconds = totalSeconds % 60;
    return String(hours).padStart(2, '0') + ':'
        + String(minutes).padStart(2, '0') + ':'
        + String(seconds).padStart(2, '0');
}

function formatBlockedUntil(epochMs) {
    var d = new Date(epochMs);
    var hours = String(d.getHours()).padStart(2, '0');
    var minutes = String(d.getMinutes()).padStart(2, '0');
    var remaining = epochMs - Date.now();
    if (remaining < TWENTY_FOUR_HOURS_MS) {
        return hours + ':' + minutes;
    }
    var day = String(d.getDate()).padStart(2, '0');
    var month = String(d.getMonth() + 1).padStart(2, '0');
    var year = d.getFullYear();
    return day + '.' + month + '.' + year + ' ' + hours + ':' + minutes;
}
