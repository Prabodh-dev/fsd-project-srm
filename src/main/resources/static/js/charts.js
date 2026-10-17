(function () {
    if (typeof Chart === 'undefined') {
        return;
    }

    var instances = [];

    function cssVariable(name) {
        return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    }

    function baseOptions() {
        var text = cssVariable('--text');
        var grid = cssVariable('--border');
        return {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { labels: { color: text } } },
            scales: {
                x: { ticks: { color: text }, grid: { color: grid } },
                y: { beginAtZero: true, ticks: { color: text, precision: 0 }, grid: { color: grid } }
            }
        };
    }

    function build(canvas) {
        var data = JSON.parse(canvas.getAttribute('data-chart'));
        var kind = canvas.getAttribute('data-chart-kind');
        var primary = cssVariable('--primary');
        var success = cssVariable('--success');
        var options = baseOptions();
        var config;

        if (kind === 'admin') {
            config = {
                type: 'line',
                data: {
                    labels: data.labels,
                    datasets: [
                        { label: 'Registrations', data: data.registrations, borderColor: primary, backgroundColor: primary, tension: 0.3 },
                        { label: 'Attempts', data: data.attempts, borderColor: success, backgroundColor: success, tension: 0.3 }
                    ]
                },
                options: options
            };
        } else if (kind === 'trend') {
            options.scales.y.max = 100;
            options.scales.y.title = { display: true, text: 'Score (%)', color: cssVariable('--text') };
            config = {
                type: 'line',
                data: {
                    labels: data.labels,
                    datasets: [{ label: 'Score percentage', data: data.values, borderColor: primary, backgroundColor: primary, tension: 0.25, fill: false }]
                },
                options: options
            };
        } else if (kind === 'histogram') {
            options.scales.x.title = { display: true, text: 'Score range', color: cssVariable('--text') };
            options.scales.y.title = { display: true, text: 'Students', color: cssVariable('--text') };
            config = {
                type: 'bar',
                data: {
                    labels: data.histogramLabels,
                    datasets: [{ label: 'Students', data: data.histogramCounts, backgroundColor: primary }]
                },
                options: options
            };
        } else if (kind === 'accuracy') {
            options.scales.y.max = 100;
            options.scales.y.title = { display: true, text: 'Correct answers (%)', color: cssVariable('--text') };
            config = {
                type: 'bar',
                data: {
                    labels: data.questionLabels,
                    datasets: [{ label: 'Accuracy', data: data.questionAccuracy, backgroundColor: success }]
                },
                options: options
            };
        }
        if (config) {
            instances.push(new Chart(canvas, config));
        }
    }

    function renderAll() {
        instances.forEach(function (instance) {
            instance.destroy();
        });
        instances = [];
        document.querySelectorAll('canvas[data-chart]').forEach(build);
    }

    renderAll();
    document.addEventListener('themechange', renderAll);
})();
