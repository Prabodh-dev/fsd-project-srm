(function () {
    var storageKey = 'examedge-theme';
    var stored = null;
    try {
        stored = window.localStorage.getItem(storageKey);
    } catch (error) {
        stored = null;
    }
    var preferred = stored;
    if (!preferred) {
        preferred = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    document.documentElement.setAttribute('data-theme', preferred);
})();
