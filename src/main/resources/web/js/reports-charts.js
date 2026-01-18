/**
 * Reports Charts Module
 * Handles rendering of sales chart showing new users and total sales using ECharts
 */

(function() {
    'use strict';

    // Chart instance
    let salesChartInstance = null;

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
            background: isDark ? '#1f2937' : '#ffffff',
            cardBg: isDark ? '#1f2937' : '#ffffff',
            cardBorder: isDark ? '#374151' : '#e5e7eb'
        };
    }

    /**
     * Parse date safely
     */
    function parseDateSafe(dateString) {
        if (!dateString) return null;
        try {
            return new Date(dateString);
        } catch (e) {
            return null;
        }
    }

    /**
     * Aggregate users by month for new user registrations
     */
    function aggregateUsersByMonth(users) {
        const monthlyData = new Map();
        
        users.forEach(user => {
            const createdAt = parseDateSafe(user.createdAt);
            if (!createdAt || isNaN(createdAt.getTime())) return;
            
            const monthKey = `${createdAt.getFullYear()}-${String(createdAt.getMonth() + 1).padStart(2, '0')}`;
            monthlyData.set(monthKey, (monthlyData.get(monthKey) || 0) + 1);
        });
        
        return monthlyData;
    }

    /**
     * Get last 12 months of data
     */
    function getLast12Months() {
        const months = [];
        const now = new Date();
        
        for (let i = 11; i >= 0; i--) {
            const date = new Date(now.getFullYear(), now.getMonth() - i, 1);
            const monthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
            months.push(monthKey);
        }
        
        return months;
    }

    /**
     * Format month label for display
     */
    function formatMonthLabel(monthKey) {
        try {
            const [year, month] = monthKey.split('-');
            const date = new Date(parseInt(year), parseInt(month) - 1, 1);
            return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
        } catch (e) {
            return monthKey;
        }
    }

    /**
     * Render Sales Chart (New Users + Total Sales)
     */
    function renderSalesChart(usersData, salesData) {
        const chartContainer = q('sales-chart');
        const emptyState = q('sales-chart-empty');
        
        if (!chartContainer) {
            console.warn('Sales chart container not found');
            return;
        }

        // Check if ECharts is available
        if (typeof echarts === 'undefined') {
            console.error('ECharts not loaded');
            if (emptyState) {
                emptyState.classList.remove('hidden');
                emptyState.textContent = 'Chart library not available';
            }
            return;
        }

        // Aggregate user data by month
        const usersMap = aggregateUsersByMonth(usersData || []);
        const allMonths = getLast12Months();
        
        // Prepare data arrays
        const newUsersData = allMonths.map(month => usersMap.get(month) || 0);
        const salesValues = [];
        const monthLabels = allMonths.map(formatMonthLabel);
        
        // Process sales data
        if (salesData && Array.isArray(salesData)) {
            const salesMap = new Map();
            salesData.forEach(item => {
                if (item.month && item.revenue !== undefined) {
                    salesMap.set(item.month, item.revenue);
                }
            });
            
            allMonths.forEach(month => {
                salesValues.push(salesMap.get(month) || 0);
            });
        } else {
            // If no sales data, fill with zeros
            allMonths.forEach(() => salesValues.push(0));
        }

        // Check if we have any data
        const hasUsersData = newUsersData.some(val => val > 0);
        const hasSalesData = salesValues.some(val => val > 0);

        if (!hasUsersData && !hasSalesData) {
            if (emptyState) {
                emptyState.classList.remove('hidden');
                emptyState.textContent = 'Not enough data to render chart';
            }
            if (salesChartInstance) {
                salesChartInstance.dispose();
                salesChartInstance = null;
            }
            return;
        }

        // Hide empty state
        if (emptyState) {
            emptyState.classList.add('hidden');
        }

        // Dispose existing chart
        if (salesChartInstance) {
            salesChartInstance.dispose();
            salesChartInstance = null;
        }

        // Get theme colors
        const colors = getThemeColors();
        const isDark = document.documentElement.getAttribute('data-theme') === 'dark';

        // Create chart instance
        salesChartInstance = echarts.init(chartContainer, isDark ? 'dark' : null);

        // Chart configuration
        const option = {
            backgroundColor: 'transparent',
            tooltip: {
                trigger: 'axis',
                axisPointer: {
                    type: 'cross',
                    crossStyle: {
                        color: '#999'
                    }
                },
                backgroundColor: isDark ? 'rgba(31, 41, 55, 0.95)' : 'rgba(0, 0, 0, 0.9)',
                borderColor: isDark ? 'rgba(99, 102, 241, 0.5)' : 'rgba(99, 102, 241, 0.3)',
                borderWidth: 1.5,
                textStyle: {
                    color: '#ffffff',
                    fontSize: 12
                },
                padding: [12, 16],
                formatter: function(params) {
                    let result = `<div style="font-weight: 600; margin-bottom: 8px;">${params[0].axisValue}</div>`;
                    params.forEach(param => {
                        if (param.seriesName === 'New Users') {
                            result += `<div style="margin: 4px 0;">
                                <span style="display: inline-block; width: 10px; height: 10px; background: ${param.color}; border-radius: 50%; margin-right: 8px;"></span>
                                ${param.seriesName}: <strong>${param.value}</strong>
                            </div>`;
                        } else if (param.seriesName === 'Total Sales') {
                            const formatted = '₹' + param.value.toLocaleString('en-IN', {
                                minimumFractionDigits: 2,
                                maximumFractionDigits: 2
                            });
                            result += `<div style="margin: 4px 0;">
                                <span style="display: inline-block; width: 10px; height: 10px; background: ${param.color}; border-radius: 50%; margin-right: 8px;"></span>
                                ${param.seriesName}: <strong>${formatted}</strong>
                            </div>`;
                        }
                    });
                    return result;
                }
            },
            legend: {
                data: ['New Users', 'Total Sales'],
                top: 10,
                right: 20,
                textStyle: {
                    color: colors.text,
                    fontSize: 12,
                    fontWeight: '600'
                },
                itemGap: 20,
                itemWidth: 10,
                itemHeight: 10
            },
            grid: {
                left: '3%',
                right: '4%',
                bottom: '3%',
                top: '15%',
                containLabel: true
            },
            xAxis: [
                {
                    type: 'category',
                    data: monthLabels,
                    axisPointer: {
                        type: 'shadow'
                    },
                    axisLabel: {
                            color: colors.text,
                        fontSize: 11,
                        rotate: 45,
                        margin: 10
                    },
                    axisLine: {
                        lineStyle: {
                            color: colors.grid
                        }
                    },
                    splitLine: {
                        show: false
                    }
                }
            ],
            yAxis: [
                {
                    type: 'value',
                    name: 'New Users',
                    position: 'left',
                    nameTextStyle: {
                        color: colors.text,
                        fontSize: 12,
                        padding: [0, 0, 0, 10]
                    },
                    axisLabel: {
                            color: colors.text,
                        fontSize: 11,
                        formatter: function(value) {
                            return Math.round(value);
                        }
                    },
                    axisLine: {
                        lineStyle: {
                            color: colors.grid
                        }
                    },
                    splitLine: {
                        lineStyle: {
                            color: colors.grid,
                            type: 'dashed',
                            opacity: 0.3
                        }
                    }
                },
                {
                    type: 'value',
                    name: 'Sales (₹)',
                    position: 'right',
                    nameTextStyle: {
                            color: colors.text,
                        fontSize: 12,
                        padding: [0, 10, 0, 0]
                            },
                    axisLabel: {
                        color: colors.text,
                        fontSize: 11,
                        formatter: function(value) {
                                if (value >= 100000) {
                                    return '₹' + (value / 100000).toFixed(1) + 'L';
                                } else if (value >= 1000) {
                                    return '₹' + (value / 1000).toFixed(1) + 'k';
                                }
                            return '₹' + Math.round(value);
                        }
                    },
                    axisLine: {
                        lineStyle: {
                            color: colors.grid
                        }
                    },
                    splitLine: {
                        show: false
                    }
                }
            ],
            series: [
                {
                    name: 'New Users',
                    type: 'bar',
                    yAxisIndex: 0,
                    data: newUsersData,
                    itemStyle: {
                        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                            { offset: 0, color: 'rgba(99, 102, 241, 0.8)' },
                            { offset: 1, color: 'rgba(99, 102, 241, 0.3)' }
                        ])
                    },
                    emphasis: {
                        itemStyle: {
                            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                                { offset: 0, color: 'rgba(99, 102, 241, 1)' },
                                { offset: 1, color: 'rgba(99, 102, 241, 0.5)' }
                            ])
                        }
                    },
                    barWidth: '40%',
                    animationDelay: function(idx) {
                        return idx * 50;
                    }
                },
                {
                    name: 'Total Sales',
                    type: 'line',
                    yAxisIndex: 1,
                    data: salesValues,
                    smooth: true,
                    lineStyle: {
                        width: 3,
                        color: '#10b981'
                    },
                    itemStyle: {
                        color: '#10b981'
                    },
                    areaStyle: {
                        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                            { offset: 0, color: isDark ? 'rgba(16, 185, 129, 0.25)' : 'rgba(16, 185, 129, 0.15)' },
                            { offset: 1, color: isDark ? 'rgba(16, 185, 129, 0.02)' : 'rgba(16, 185, 129, 0.01)' }
                        ])
                    },
                    symbol: 'circle',
                    symbolSize: 6,
                    emphasis: {
                        symbolSize: 8,
                        lineStyle: {
                            width: 4
                        }
                    },
                    animationDelay: function(idx) {
                        return idx * 50 + 100;
                    }
                }
            ],
            animation: true,
            animationDuration: 1000,
            animationEasing: 'cubicOut'
        };

        // Set chart option
        salesChartInstance.setOption(option);

        // Handle window resize
        window.addEventListener('resize', function() {
            if (salesChartInstance) {
                salesChartInstance.resize();
            }
        });

        console.log('Sales chart rendered successfully');
    }

    /**
     * Fetch all users for registration statistics
     */
    async function fetchAllUsers() {
        try {
        const token = localStorage.getItem('jwt_token');
            let allUsers = [];
            let page = 1;
            let hasMore = true;
            const pageSize = 100;
        
            while (hasMore) {
                try {
                    const response = await fetch(`/admin/users?page=${page}&pageSize=${pageSize}`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
                    });

                    if (!response.ok) {
                        // Show user-friendly error message
                        const errorMsg = response.status === 401 || response.status === 403
                            ? 'Your session has expired. Please refresh the page and login again.'
                            : response.status === 404
                            ? 'Users endpoint not found. Please contact support.'
                            : `Unable to load user data (Error ${response.status}). Please try again later.`;
                        
                        if (typeof window.showMessage === 'function') {
                            window.showMessage('error', errorMsg);
                        } else {
                            console.error('Failed to fetch users:', errorMsg);
                        }
                        return [];
                    }

                    const payload = await response.json();
                    if (payload.status === true && payload.data && Array.isArray(payload.data.users)) {
                        allUsers = allUsers.concat(payload.data.users);
                        
                        const pagination = payload.data.pagination;
                        if (pagination && pagination.totalPages) {
                            hasMore = page < pagination.totalPages;
                            page++;
                        } else {
                            hasMore = false;
                        }
                    } else {
                        hasMore = false;
                    }

                    // Safety check
                    if (page > 1000) {
                        console.warn('Reached maximum page limit');
                        hasMore = false;
                    }
                } catch (pageError) {
                    console.error(`Error fetching page ${page}:`, pageError);
                    hasMore = false;
                }
            }

            return allUsers;
        } catch (error) {
            console.error('Error fetching users:', error);
            return [];
        }
    }

    /**
     * Fetch financial metrics for sales data
     */
    async function fetchSalesData() {
        try {
            const token = localStorage.getItem('jwt_token');
            const response = await fetch('/finance/metrics', {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
            });

            if (!response.ok) {
                // Show user-friendly error message
                const errorMsg = response.status === 401 || response.status === 403
                    ? 'Your session has expired. Please refresh the page and login again.'
                    : response.status === 404
                    ? 'Financial metrics endpoint not found. Please contact support.'
                    : `Unable to load sales data (Error ${response.status}). Please try again later.`;
                
                if (typeof window.showMessage === 'function') {
                    window.showMessage('error', errorMsg);
                } else {
                    console.error('Failed to fetch financial metrics:', errorMsg);
                }
                return null;
            }

            const data = await response.json();
            if (data.status === true && data.data && data.data.monthlyRevenueChart) {
                return data.data.monthlyRevenueChart;
            }
            return null;
        } catch (error) {
            console.error('Error loading sales data:', error);
            return null;
        }
    }

    /**
     * Load and render sales chart
     */
    async function loadSalesChart() {
        const emptyState = q('sales-chart-empty');
        
        try {
            // Fetch both users and sales data in parallel
            const [usersData, salesData] = await Promise.all([
                fetchAllUsers(),
                fetchSalesData()
            ]);

            if ((!usersData || usersData.length === 0) && (!salesData || salesData.length === 0)) {
                if (emptyState) {
                    emptyState.classList.remove('hidden');
                    emptyState.textContent = 'Not enough data to render chart';
                }
                return;
            }

            renderSalesChart(usersData, salesData);
        } catch (error) {
            console.error('Error loading sales chart:', error);
            if (emptyState) {
                emptyState.classList.remove('hidden');
                emptyState.textContent = 'Failed to load chart data';
            }
        }
    }

    /**
     * Initialize charts when Reports tab is activated
     */
    function initializeReportsCharts() {
        console.log('Initializing Reports charts...');
        loadSalesChart();
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

        // Listen for theme changes to update chart
        const themeObserver = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
                if (mutation.type === 'attributes' && mutation.attributeName === 'data-theme') {
                    if (salesChartInstance) {
                        // Re-render chart with new theme
                        const chartContainer = q('sales-chart');
                        if (chartContainer) {
                            const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
                            salesChartInstance.dispose();
                            salesChartInstance = echarts.init(chartContainer, isDark ? 'dark' : null);
                            // Re-fetch and render
                            loadSalesChart();
                        }
                    }
                }
            });
        });

        themeObserver.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['data-theme']
        });
    });

    // Expose functions globally for manual triggering if needed
    window.ReportsChartsModule = {
        initialize: initializeReportsCharts,
        renderSalesChart: renderSalesChart,
        loadSalesChart: loadSalesChart
    };

    console.log('Reports Charts Module loaded');
})();
