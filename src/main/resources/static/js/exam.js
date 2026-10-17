(function () {
    var root = document.getElementById('examRoot');
    if (!root) {
        return;
    }

    var apiBase = root.getAttribute('data-api-base');
    var resultUrl = root.getAttribute('data-result-url');
    var maxViolations = parseInt(root.getAttribute('data-max-violations'), 10);
    var csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    var panels = Array.prototype.slice.call(document.querySelectorAll('.question-panel'));
    var navButtons = Array.prototype.slice.call(document.querySelectorAll('.nav-btn'));
    var timerElement = document.getElementById('timer');
    var saveStatus = document.getElementById('saveStatus');
    var violationCount = document.getElementById('violationCount');
    var violationCounter = document.getElementById('violationCounter');
    var violationBanner = document.getElementById('violationBanner');
    var progressText = document.getElementById('progressText');
    var submitDialog = document.getElementById('submitDialog');
    var submitForm = document.getElementById('submitForm');
    var automaticField = document.getElementById('automaticField');

    var currentIndex = 0;
    var deadline = Date.now() + parseInt(root.getAttribute('data-remaining'), 10) * 1000;
    var submitting = false;
    var pendingSaves = {};
    var lastViolationAt = 0;
    var lastBlockedReportAt = 0;

    function post(path, payload) {
        var headers = { 'Content-Type': 'application/json' };
        headers[csrfHeader] = csrfToken;
        return fetch(apiBase + path, {
            method: 'POST',
            headers: headers,
            credentials: 'same-origin',
            body: JSON.stringify(payload)
        });
    }

    function handleClosed(response) {
        if (response.status === 409) {
            return response.json().then(function (body) {
                submitting = true;
                window.location.href = body.resultUrl || resultUrl;
                return true;
            });
        }
        return Promise.resolve(false);
    }

    function setStatus(text) {
        saveStatus.textContent = text;
    }

    function panelFor(questionId) {
        return document.querySelector('.question-panel[data-question-id="' + questionId + '"]');
    }

    function navFor(questionId) {
        return document.querySelector('.nav-btn[data-question-id="' + questionId + '"]');
    }

    function isAnswered(panel) {
        return !!panel.querySelector('.option-input:checked');
    }

    function refreshNavigator() {
        var answered = 0;
        panels.forEach(function (panel, index) {
            var nav = navButtons[index];
            var flagButton = panel.querySelector('.flag-btn');
            var answeredNow = isAnswered(panel);
            if (answeredNow) {
                answered++;
            }
            nav.classList.toggle('answered', answeredNow);
            nav.classList.toggle('flagged', flagButton.getAttribute('aria-pressed') === 'true');
            nav.classList.toggle('current', index === currentIndex);
            if (index === currentIndex) {
                nav.setAttribute('aria-current', 'true');
            } else {
                nav.removeAttribute('aria-current');
            }
        });
        progressText.textContent = answered + ' of ' + panels.length + ' answered';
    }

    function showQuestion(index) {
        if (index < 0 || index >= panels.length) {
            return;
        }
        currentIndex = index;
        panels.forEach(function (panel, panelIndex) {
            panel.classList.toggle('current', panelIndex === index);
        });
        refreshNavigator();
        var heading = panels[index].querySelector('.question-text');
        heading.setAttribute('tabindex', '-1');
        heading.focus({ preventScroll: true });
    }

    function saveAnswer(questionId, optionId) {
        pendingSaves[questionId] = optionId;
        setStatus('Saving...');
        post('/answer', { questionId: Number(questionId), optionId: optionId === null ? null : Number(optionId) })
            .then(function (response) {
                return handleClosed(response).then(function (closed) {
                    if (closed) {
                        return;
                    }
                    if (!response.ok) {
                        throw new Error('save failed');
                    }
                    delete pendingSaves[questionId];
                    if (Object.keys(pendingSaves).length === 0) {
                        setStatus('All changes saved');
                    }
                });
            })
            .catch(function () {
                setStatus('Not saved. Will retry when the connection returns.');
            });
    }

    function retryPendingSaves() {
        Object.keys(pendingSaves).forEach(function (questionId) {
            saveAnswer(questionId, pendingSaves[questionId]);
        });
    }

    function toggleFlag(button) {
        var flagged = button.getAttribute('aria-pressed') !== 'true';
        button.setAttribute('aria-pressed', flagged ? 'true' : 'false');
        refreshNavigator();
        post('/flag', { questionId: Number(button.getAttribute('data-question-id')), flagged: flagged })
            .then(handleClosed)
            .catch(function () {
                setStatus('Flag not saved. Check your connection.');
            });
    }

    function formatTime(totalSeconds) {
        var seconds = Math.max(0, totalSeconds);
        var hours = Math.floor(seconds / 3600);
        var minutes = Math.floor((seconds % 3600) / 60);
        var rest = seconds % 60;
        var mm = String(minutes).padStart(2, '0');
        var ss = String(rest).padStart(2, '0');
        return hours > 0 ? hours + ':' + mm + ':' + ss : mm + ':' + ss;
    }

    function submitExam(automatic) {
        if (submitting) {
            return;
        }
        submitting = true;
        automaticField.value = automatic ? 'true' : 'false';
        submitForm.submit();
    }

    function tick() {
        var remaining = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
        timerElement.textContent = formatTime(remaining);
        timerElement.classList.toggle('low', remaining < 60);
        if (remaining === 0) {
            submitExam(true);
        }
    }

    function applyState(state) {
        deadline = Date.now() + state.remainingSeconds * 1000;
        Object.keys(state.answers).forEach(function (questionId) {
            var panel = panelFor(questionId);
            if (!panel) {
                return;
            }
            var input = panel.querySelector('.option-input[value="' + state.answers[questionId] + '"]');
            if (input && !pendingSaves[questionId]) {
                input.checked = true;
            }
        });
        panels.forEach(function (panel) {
            var questionId = panel.getAttribute('data-question-id');
            var flagged = state.flagged.indexOf(Number(questionId)) >= 0;
            panel.querySelector('.flag-btn').setAttribute('aria-pressed', flagged ? 'true' : 'false');
        });
        updateViolations(state.violations);
        refreshNavigator();
    }

    function syncState() {
        fetch(apiBase + '/state', { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function (response) {
                return handleClosed(response).then(function (closed) {
                    if (closed || !response.ok) {
                        return null;
                    }
                    return response.json();
                });
            })
            .then(function (state) {
                if (state) {
                    applyState(state);
                    retryPendingSaves();
                }
            })
            .catch(function () {
                setStatus('Offline. Your time keeps running on the server.');
            });
    }

    function updateViolations(count) {
        violationCount.textContent = count;
        violationCounter.classList.toggle('hot', count > 0);
        if (count > 0) {
            violationBanner.textContent = 'Warning: leaving the exam window was recorded. ' + count + ' of '
                + maxViolations + ' allowed violations used. The exam is submitted automatically at ' + maxViolations + '.';
            violationBanner.classList.add('visible');
        }
    }

    function reportEvent(type, countsAsViolation) {
        if (submitting) {
            return;
        }
        var now = Date.now();
        if (countsAsViolation) {
            if (now - lastViolationAt < 1500) {
                return;
            }
            lastViolationAt = now;
        } else {
            if (now - lastBlockedReportAt < 2000) {
                return;
            }
            lastBlockedReportAt = now;
        }
        post('/proctor', { eventType: type })
            .then(function (response) {
                return handleClosed(response).then(function (closed) {
                    if (closed || !response.ok) {
                        return null;
                    }
                    return response.json();
                });
            })
            .then(function (outcome) {
                if (!outcome) {
                    return;
                }
                updateViolations(outcome.violations);
                if (outcome.autoSubmitted) {
                    submitting = true;
                    window.location.href = resultUrl;
                }
            })
            .catch(function () {
                setStatus('Could not reach the server.');
            });
    }

    function openSubmitDialog() {
        var unanswered = panels.filter(function (panel) {
            return !isAnswered(panel);
        }).length;
        var flagged = panels.filter(function (panel) {
            return panel.querySelector('.flag-btn').getAttribute('aria-pressed') === 'true';
        }).length;
        var message = unanswered === 0
            ? 'You have answered every question.'
            : 'You have ' + unanswered + ' unanswered question' + (unanswered === 1 ? '' : 's') + '.';
        if (flagged > 0) {
            message += ' ' + flagged + ' question' + (flagged === 1 ? ' is' : 's are') + ' flagged for review.';
        }
        document.getElementById('submitMessage').textContent = message + ' Once submitted you cannot change your answers.';
        submitDialog.showModal();
    }

    document.querySelectorAll('.option-input').forEach(function (input) {
        input.addEventListener('change', function () {
            refreshNavigator();
            saveAnswer(input.getAttribute('data-question-id'), input.value);
        });
    });

    document.querySelectorAll('.clear-btn').forEach(function (button) {
        button.addEventListener('click', function () {
            var questionId = button.getAttribute('data-question-id');
            panelFor(questionId).querySelectorAll('.option-input').forEach(function (input) {
                input.checked = false;
            });
            refreshNavigator();
            saveAnswer(questionId, null);
        });
    });

    document.querySelectorAll('.flag-btn').forEach(function (button) {
        button.addEventListener('click', function () {
            toggleFlag(button);
        });
    });

    document.querySelectorAll('.prev-btn').forEach(function (button) {
        button.addEventListener('click', function () {
            showQuestion(currentIndex - 1);
        });
    });
    document.querySelectorAll('.next-btn').forEach(function (button) {
        button.addEventListener('click', function () {
            showQuestion(currentIndex + 1);
        });
    });
    navButtons.forEach(function (button, index) {
        button.addEventListener('click', function () {
            showQuestion(index);
        });
    });

    document.getElementById('openSubmit').addEventListener('click', openSubmitDialog);
    document.getElementById('cancelSubmit').addEventListener('click', function () {
        submitDialog.close();
    });
    document.getElementById('confirmSubmit').addEventListener('click', function () {
        submitDialog.close();
        submitExam(false);
    });

    document.addEventListener('keydown', function (event) {
        if (event.altKey && event.key === 'ArrowRight') {
            showQuestion(currentIndex + 1);
        } else if (event.altKey && event.key === 'ArrowLeft') {
            showQuestion(currentIndex - 1);
        }
    });

    document.addEventListener('visibilitychange', function () {
        if (document.hidden) {
            reportEvent('TAB_SWITCH', true);
        } else {
            syncState();
        }
    });
    window.addEventListener('blur', function () {
        if (!submitDialog.open) {
            reportEvent('WINDOW_BLUR', true);
        }
    });
    window.addEventListener('online', syncState);

    document.addEventListener('copy', function (event) {
        event.preventDefault();
        reportEvent('COPY_BLOCKED', false);
    });
    document.addEventListener('cut', function (event) {
        event.preventDefault();
    });
    document.addEventListener('paste', function (event) {
        event.preventDefault();
        reportEvent('PASTE_BLOCKED', false);
    });
    document.addEventListener('contextmenu', function (event) {
        event.preventDefault();
        reportEvent('CONTEXT_MENU_BLOCKED', false);
    });
    document.addEventListener('dragstart', function (event) {
        event.preventDefault();
    });

    refreshNavigator();
    tick();
    window.setInterval(tick, 250);
    window.setInterval(syncState, 30000);
    window.setInterval(retryPendingSaves, 10000);
})();
