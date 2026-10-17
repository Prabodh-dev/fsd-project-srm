(function () {
    var storageKey = 'examedge-theme';
    var root = document.documentElement;

    function currentTheme() {
        return root.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    }

    function applyTheme(theme) {
        root.setAttribute('data-theme', theme);
        try {
            window.localStorage.setItem(storageKey, theme);
        } catch (error) {
            return;
        } finally {
            document.dispatchEvent(new CustomEvent('themechange', { detail: theme }));
        }
    }

    function syncThemeButtons() {
        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            var dark = currentTheme() === 'dark';
            button.setAttribute('aria-pressed', dark ? 'true' : 'false');
            button.textContent = dark ? 'Light theme' : 'Dark theme';
        });
    }

    document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
        button.addEventListener('click', function () {
            applyTheme(currentTheme() === 'dark' ? 'light' : 'dark');
            syncThemeButtons();
        });
    });
    syncThemeButtons();

    var menuToggle = document.getElementById('menuToggle');
    var backdrop = document.getElementById('sidebarBackdrop');

    function setSidebar(open) {
        document.body.classList.toggle('sidebar-open', open);
        if (menuToggle) {
            menuToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        }
    }

    if (menuToggle) {
        menuToggle.addEventListener('click', function () {
            setSidebar(!document.body.classList.contains('sidebar-open'));
        });
    }
    if (backdrop) {
        backdrop.addEventListener('click', function () {
            setSidebar(false);
        });
    }
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            setSidebar(false);
        }
    });

    var confirmDialog = document.getElementById('confirmDialog');
    var pendingForm = null;

    if (confirmDialog) {
        var confirmMessage = document.getElementById('confirmMessage');
        document.querySelectorAll('form[data-confirm]').forEach(function (form) {
            form.addEventListener('submit', function (event) {
                event.preventDefault();
                pendingForm = form;
                confirmMessage.textContent = form.getAttribute('data-confirm');
                confirmDialog.showModal();
            });
        });
        document.getElementById('confirmAccept').addEventListener('click', function () {
            confirmDialog.close();
            if (pendingForm) {
                pendingForm.submit();
            }
        });
        document.getElementById('confirmCancel').addEventListener('click', function () {
            pendingForm = null;
            confirmDialog.close();
        });
    }

    document.querySelectorAll('[data-autosubmit]').forEach(function (element) {
        element.addEventListener('change', function () {
            if (element.form) {
                element.form.submit();
            }
        });
    });

    document.querySelectorAll('[data-print]').forEach(function (button) {
        button.addEventListener('click', function () {
            window.print();
        });
    });

    document.querySelectorAll('[data-modal-open]').forEach(function (button) {
        button.addEventListener('click', function () {
            var dialog = document.getElementById(button.getAttribute('data-modal-open'));
            if (dialog) {
                dialog.showModal();
            }
        });
    });
    document.querySelectorAll('[data-modal-close]').forEach(function (button) {
        button.addEventListener('click', function () {
            var dialog = button.closest('dialog');
            if (dialog) {
                dialog.close();
            }
        });
    });
})();
