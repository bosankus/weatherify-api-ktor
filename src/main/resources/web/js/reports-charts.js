/**
 * Reports Charts Module
 * Handles rendering of revenue and refund charts in the Reports tab
 */

(function() {
    'use strict';

    // Chart instance
    let financialTrendsChartInstance = null;

    /**
     * Helper function to get element by ID
     */
    function q(id) {
        return document.getElementById(id);
    }

    /**
     * Helper function to get theme-aware colors
     */
    function getThemeColors() {
        const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        return {
            text: isDark ? '#e5e7eb' : '#374151',
            grid: isDark ? '#374151' : '#e5e7eb',
            background: isDark ? '#1f2937' : '#ffffff'
        };
    }

    /**
     * Render Combined Financial Trends Chart (Revenue + Refunds)
     */
    function renderFinancialTrendsChart(revenueData, refundData) {
        const canvas = q('financial-trends-chart');
        const empty = q('financial-trends-empty');
        
        if (!canvas) {
            console.warn('Financial trends chart canvas not found');
            return;
        }

        // Check if we have any data
        const hasRevenueData = revenueData && revenueData.length > 0;
        const hasRefundData = refundData && refundData.length > 0;

        if (!hasRevenueData && !hasRefundData) {
            if (empty) {
                empty.classList.remove('hidden');
                empty.classList.add('visible');
            }
            return;
        }

        // Hide empty state
        if (empty) {
            empty.classList.add('hidden');
            empty.classList.remove('visible');
        }

        // Check if Chart.js is available
        if (typeof Chart === 'undefined') {
            console.error('Chart.js not loaded');
            if (empty) {
                empty.classList.remove('hidden');
                empty.textContent = 'Chart library not available';
            }
            return;
        }

        // Destroy existing chart instance
        if (financialTrendsChartInstance) {
            try {
                financialTrendsChartInstance.destroy();
            } catch (e) {
                console.warn('Error destroying financial trends chart instance:', e);
            }
            financialTrendsChartInstance = null;
        }
        
        // Also check for Chart.js registry
        if (typeof Chart.getChart === 'function') {
            const existingChart = Chart.getChart(canvas);
            if (existingChart) {
                try {
                    existingChart.destroy();
                } catch (e) {
                    console.warn('Error destroying existing chart from registry:', e);
                }
            }
        }

        // Merge and prepare data - use all unique months from both datasets
        const allMonths = new Set();
        if (hasRevenueData) revenueData.forEach(item => allMonths.add(item.month));
        if (hasRefundData) refundData.forEach(item => allMonths.add(item.month));
        
        const sortedMonths = Array.from(allMonths).sort();
        
        // Create labels
        const labels = sortedMonths.map(month => {
            const date = new Date(month + '-01');
            return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
        });
        
        // Map data to sorted months
        const revenueMap = new Map(hasRevenueData ? revenueData.map(item => [item.month, item.revenue || 0]) : []);
        const refundMap = new Map(hasRefundData ? refundData.map(item => [item.month, item.refundAmount || 0]) : []);
        
        const revenueValues = sortedMonths.map(month => revenueMap.get(month) || 0);
        const refundValues = sortedMonths.map(month => refundMap.get(month) || 0);
        const netRevenueValues = revenueValues.map((rev, idx) => rev - refundValues[idx]);

        // Get theme colors
        const colors = getThemeColors();
        const isDark = document.documentElement.getAttribute('data-theme') === 'dark';

        // Create gradients for area fills
        const ctx = canvas.getContext('2d');
        
        const revenueGradient = ctx.createLinearGradient(0, 0, 0, 350);
        revenueGradient.addColorStop(0, isDark ? 'rgba(16, 185, 129, 0.25)' : 'rgba(16, 185, 129, 0.15)');
        revenueGradient.addColorStop(1, isDark ? 'rgba(16, 185, 129, 0.02)' : 'rgba(16, 185, 129, 0.01)');
        
        const refundGradient = ctx.createLinearGradient(0, 0, 0, 350);
        refundGradient.addColorStop(0, isDark ? 'rgba(239, 68, 68, 0.25)' : 'rgba(239, 68, 68, 0.15)');
        refundGradient.addColorStop(1, isDark ? 'rgba(239, 68, 68, 0.02)' : 'rgba(239, 68, 68, 0.01)');
        
        const netGradient = ctx.createLinearGradient(0, 0, 0, 350);
        netGradient.addColorStop(0, isDark ? 'rgba(99, 102, 241, 0.25)' : 'rgba(99, 102, 241, 0.15)');
        netGradient.addColorStop(1, isDark ? 'rgba(99, 102, 241, 0.02)' : 'rgba(99, 102, 241, 0.01)');
        
        const datasets = [];
        
        // Add revenue dataset if available
        if (hasRevenueData) {
            datasets.push({
                label: 'Revenue',
                data: revenueValues,
                borderColor: '#10b981',
                backgroundColor: revenueGradient,
                borderWidth: 2.5,
                fill: true,
                tension: 0.4,
                pointRadius: 4,
                pointHoverRadius: 6,
                pointBackgroundColor: '#10b981',
                pointBorderColor: '#ffffff',
                pointBorderWidth: 2,
                pointHoverBorderWidth: 2.5,
                order: 2
            });
        }
        
        // Add refund dataset if available
        if (hasRefundData) {
            datasets.push({
                label: 'Refunds',
                data: refundValues,
                borderColor: '#ef4444',
                backgroundColor: refundGradient,
                borderWidth: 2.5,
                fill: true,
                tension: 0.4,
                pointRadius: 4,
                pointHoverRadius: 6,
                pointBackgroundColor: '#ef4444',
                pointBorderColor: '#ffffff',
                pointBorderWidth: 2,
                pointHoverBorderWidth: 2.5,
                order: 3
            });
        }
        
        // Add net revenue dataset if both are available
        if (hasRevenueData && hasRefundData) {
            datasets.push({
                label: 'Net Revenue',
                data: netRevenueValues,
                borderColor: '#6366f1',
                backgroundColor: netGradient,
                borderWidth: 3,
                fill: true,
                tension: 0.4,
                pointRadius: 5,
                pointHoverRadius: 7,
                pointBackgroundColor: '#6366f1',
                pointBorderColor: '#ffffff',
                pointBorderWidth: 2,
                pointHoverBorderWidth: 2.5,
                borderDash: [0],
                order: 1
            });
        }

        // Create chart
        financialTrendsChartInstance = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: datasets
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    intersect: false,
                    mode: 'index'
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'top',
                        align: 'end',
                        labels: {
                            color: colors.text,
                            font: {
                                size: 12,
                                weight: '600'
                            },
                            padding: 15,
                            usePointStyle: true,
                            pointStyle: 'circle',
                            boxWidth: 8,
                            boxHeight: 8
                        }
                    },
                    tooltip: {
                        backgroundColor: isDark ? 'rgba(31, 41, 55, 0.95)' : 'rgba(0, 0, 0, 0.9)',
                        padding: 14,
                        titleColor: '#ffffff',
                        titleFont: {
                            size: 13,
                            weight: '700'
                        },
                        bodyColor: '#ffffff',
                        bodyFont: {
                            size: 12,
                            weight: '500'
                        },
                        bodySpacing: 6,
                        borderColor: isDark ? 'rgba(99, 102, 241, 0.5)' : 'rgba(99, 102, 241, 0.3)',
                        borderWidth: 1.5,
                        displayColors: true,
                        boxWidth: 10,
                        boxHeight: 10,
                        boxPadding: 5,
                        cornerRadius: 8,
                        callbacks: {
                            title: function(tooltipItems) {
                                return tooltipItems[0].label;
                            },
                            label: function(context) {
                                const label = context.dataset.label || '';
                                const value = context.parsed.y;
                                const formatted = '₹' + value.toLocaleString('en-IN', { 
                                    minimumFractionDigits: 2, 
                                    maximumFractionDigits: 2 
                                });
                                return label + ': ' + formatted;
                            },
                            afterBody: function(tooltipItems) {
                                if (tooltipItems.length >= 2) {
                                    const revenue = tooltipItems.find(t => t.dataset.label === 'Revenue')?.parsed.y || 0;
                                    const refunds = tooltipItems.find(t => t.dataset.label === 'Refunds')?.parsed.y || 0;
                                    
                                    if (revenue > 0) {
                                        const refundRate = ((refunds / revenue) * 100).toFixed(1);
                                        return [
                                            '',
                                            '━━━━━━━━━━━━━━━',
                                            'Refund Rate: ' + refundRate + '%'
                                        ];
                                    }
                                }
                                return [];
                            },
                            footer: function(tooltipItems) {
                                const netItem = tooltipItems.find(t => t.dataset.label === 'Net Revenue');
                                if (netItem) {
                                    const net = netItem.parsed.y;
                                    const formatted = '₹' + net.toLocaleString('en-IN', { 
                                        minimumFractionDigits: 2, 
                                        maximumFractionDigits: 2 
                                    });
                                    return 'Net: ' + formatted;
                                }
                                return '';
                            }
                        },
                        footerColor: '#a5b4fc',
                        footerFont: {
                            size: 12,
                            weight: '700'
                        },
                        footerMarginTop: 8
                    }
                },
                scales: {
                    x: {
                        grid: {
                            display: false,
                            drawBorder: false
                        },
                        ticks: {
                            color: colors.text,
                            font: {
                                size: 11,
                                weight: '500'
                            },
                            maxRotation: 45,
                            minRotation: 0,
                            autoSkip: true,
                            maxTicksLimit: 10
                        }
                    },
                    y: {
                        beginAtZero: true,
                        grid: {
                            color: colors.grid,
                            drawBorder: false,
                            lineWidth: 1
                        },
                        ticks: {
                            color: colors.text,
                            font: {
                                size: 11,
                                weight: '500'
                            },
                            callback: function(value) {
                                if (value >= 100000) {
                                    return '₹' + (value / 100000).toFixed(1) + 'L';
                                } else if (value >= 1000) {
                                    return '₹' + (value / 1000).toFixed(1) + 'k';
                                }
                                return '₹' + value.toFixed(0);
                            },
                            maxTicksLimit: 7,
                            padding: 8
                        }
                    }
                },
                animation: {
                    duration: 800,
                    easing: 'easeInOutCubic'
                }
            }
        });

        console.log('Financial trends chart rendered successfully');
    }

    /**
     * Fetch and render combined financial trends chart
     */
    function loadFinancialTrendsChart() {
        const token = localStorage.getItem('jwt_token');
        
        // Fetch both revenue and refund data in parallel
        const revenuePromise = fetch('/admin/finance/metrics', {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
        })
        .then(response => {
            if (!response.ok) throw new Error('Failed to fetch financial metrics');
            return response.json();
        })
        .then(data => {
            if (data.status === true && data.data && data.data.monthlyRevenueChart) {
                return data.data.monthlyRevenueChart;
            }
            return null;
        })
        .catch(error => {
            console.error('Error loading revenue data:', error);
            return null;
        });

        const refundPromise = fetch('/admin/refunds/metrics', {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
        })
        .then(response => {
            if (!response.ok) throw new Error('Failed to fetch refund metrics');
            return response.json();
        })
        .then(data => {
            if (data.status === true && data.data && data.data.monthlyRefundChart) {
                return data.data.monthlyRefundChart;
            }
            return null;
        })
        .catch(error => {
            console.error('Error loading refund data:', error);
            return null;
        });

        // Wait for both requests to complete
        Promise.all([revenuePromise, refundPromise])
            .then(([revenueData, refundData]) => {
                if (!revenueData && !refundData) {
                    console.warn('No financial data available');
                    const empty = q('financial-trends-empty');
                    if (empty) {
                        empty.classList.remove('hidden');
                        empty.classList.add('visible');
                    }
                } else {
                    renderFinancialTrendsChart(revenueData, refundData);
                }
            })
            .catch(error => {
                console.error('Error loading financial trends:', error);
                const empty = q('financial-trends-empty');
                if (empty) {
                    empty.classList.remove('hidden');
                    empty.classList.add('visible');
                    empty.textContent = 'Failed to load financial data';
                }
            });
    }

    /**
     * Initialize charts when Reports tab is activated
     */
    function initializeReportsCharts() {
        console.log('Initializing Reports charts...');
        loadFinancialTrendsChart();
    }

    // Listen for tab changes
    document.addEventListener('DOMContentLoaded', function() {
        // Find all tab buttons
        const tabButtons = document.querySelectorAll('[data-tab]');
        
        tabButtons.forEach(button => {
            button.addEventListener('click', function() {
                const tabName = this.getAttribute('data-tab');
                if (tabName === 'reports') {
                    // Small delay to ensure DOM is ready
                    setTimeout(initializeReportsCharts, 100);
                }
            });
        });

        // Also initialize if Reports tab is already active on page load
        const reportsTab = document.getElementById('reports');
        if (reportsTab && reportsTab.classList.contains('active')) {
            setTimeout(initializeReportsCharts, 100);
        }
    });

    // Expose functions globally for manual triggering if needed
    window.ReportsChartsModule = {
        initialize: initializeReportsCharts,
        renderFinancialTrendsChart: renderFinancialTrendsChart,
        loadFinancialTrendsChart: loadFinancialTrendsChart
    };

    console.log('Reports Charts Module loaded');
})();
