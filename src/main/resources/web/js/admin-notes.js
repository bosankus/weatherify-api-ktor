(function() {
    'use strict';

    const state = {
        loading: false,
        page: 1,
        pageSize: 20,
        totalPages: 1,
        totalCount: 0,
        search: '',
        searchTimer: null,
        notes: [],
        editor: null,
        editingNoteId: null,
        panelOpen: false
    };

    const API_BASE = '/api/notes';

    /**
     * Initialize TipTap editor in the panel
     */
    async function initializeEditor() {
        try {
            // Wait for TipTap to be loaded
            if (!window.TiptapEditor || !window.TiptapStarterKit) {
                console.warn('TipTap not yet loaded, retrying in 100ms');
                setTimeout(initializeEditor, 100);
                return;
            }

            const editorContainer = document.getElementById('notes-create-editor');
            if (editorContainer && !state.editor) {
                state.editor = new window.TiptapEditor({
                    element: editorContainer,
                    extensions: [window.TiptapStarterKit],
                    content: ''
                });
            }
        } catch (e) {
            console.error('Error initializing editor:', e);
        }
    }

    /**
     * Fetch notes from the API
     */
    async function fetchNotes(pageNum = 1, searchQuery = '') {
        try {
            state.loading = true;
            renderLoading(true);

            let url = `${API_BASE}?page=${pageNum}&limit=${state.pageSize}`;
            if (searchQuery && searchQuery.trim()) {
                url += `&search=${encodeURIComponent(searchQuery.trim())}`;
            }

            const token = localStorage.getItem('jwt_token');
            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Accept': 'application/json',
                    'Authorization': token ? `Bearer ${token}` : ''
                }
            });

            if (!response.ok) {
                if (response.status === 401) {
                    showMessage('error', 'Session expired. Please log in again.');
                    return;
                }
                throw new Error(`HTTP ${response.status}`);
            }

            const payload = await response.json();
            if (!payload.status) {
                showMessage('error', payload.message || 'Failed to load notes');
                return;
            }

            state.notes = payload.data.notes || [];
            state.totalCount = payload.data.totalCount || 0;
            state.page = payload.data.page || 1;
            state.totalPages = payload.data.totalPages || 1;

            renderNotesList();
            renderPagination();
        } catch (error) {
            console.error('Error fetching notes:', error);
            showMessage('error', 'Failed to load notes: ' + error.message);
        } finally {
            state.loading = false;
            renderLoading(false);
        }
    }

    /**
     * Render the notes list
     */
    function renderNotesList() {
        const listContainer = document.getElementById('notes-list');
        const emptyState = document.getElementById('notes-empty');

        if (!listContainer) return;

        if (state.notes.length === 0) {
            listContainer.innerHTML = '';
            if (emptyState) emptyState.classList.remove('hidden');
            return;
        }

        if (emptyState) emptyState.classList.add('hidden');

        listContainer.innerHTML = state.notes.map(note => {
            const contentPreview = renderNoteContent(note.content);
            const safePreview = DOMPurify.sanitize(contentPreview, {
                ALLOWED_TAGS: ['b', 'i', 'strong', 'em', 'u', 'h1', 'h2', 'h3', 'p', 'br', 'ul', 'ol', 'li', 'code', 'pre'],
                ALLOWED_ATTR: []
            });

            return `
                <div class="data-row" data-note-id="${escapeHtml(note.id)}">
                    <div style="flex: 1; min-width: 0;">
                        <div style="font-size: 14px; line-height: 1.5; color: var(--text-secondary); margin-bottom: 8px;">
                            ${safePreview.substring(0, 200)}${safePreview.length > 200 ? '...' : ''}
                        </div>
                        <div style="font-size: 12px; color: var(--text-tertiary);">
                            Updated: ${formatDate(note.updatedAt)}
                        </div>
                    </div>
                    <div style="flex-shrink: 0; padding-left: 16px;">
                        <button class="action-icon-btn" title="Edit note" onclick="window.NotesTab.editNote('${escapeHtml(note.id)}', ${escapeHtml(JSON.stringify(note.content))})">
                            <span class="material-icons" style="font-size: 18px;">edit</span>
                        </button>
                    </div>
                </div>
            `;
        }).join('');
    }

    /**
     * Render note content from TipTap JSON to HTML
     */
    function renderNoteContent(jsonContent) {
        try {
            const json = typeof jsonContent === 'string' ? JSON.parse(jsonContent) : jsonContent;
            if (window.TiptapGenerateHTML) {
                return window.TiptapGenerateHTML(json, [window.TiptapStarterKit]);
            }
        } catch (e) {
            console.error('Error rendering note content:', e);
        }
        return '<p>Unable to render note</p>';
    }

    /**
     * Render pagination controls
     */
    function renderPagination() {
        const container = document.getElementById('notes-pagination');
        if (!container) return;

        if (state.totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        let html = '<div style="display: flex; gap: 8px; justify-content: center; margin-top: 16px;">';

        // Previous button
        if (state.page > 1) {
            html += `<button class="btn btn-secondary" onclick="window.NotesTab.goToPage(${state.page - 1})">← Previous</button>`;
        } else {
            html += `<button class="btn btn-secondary" disabled style="opacity: 0.5; cursor: not-allowed;">← Previous</button>`;
        }

        // Page info
        html += `<div style="padding: 8px 16px; text-align: center;">Page ${state.page} of ${state.totalPages}</div>`;

        // Next button
        if (state.page < state.totalPages) {
            html += `<button class="btn btn-secondary" onclick="window.NotesTab.goToPage(${state.page + 1})">Next →</button>`;
        } else {
            html += `<button class="btn btn-secondary" disabled style="opacity: 0.5; cursor: not-allowed;">Next →</button>`;
        }

        html += '</div>';
        container.innerHTML = html;
    }

    /**
     * Show/hide loading indicator
     */
    function renderLoading(show) {
        const loader = document.getElementById('notes-loader');
        if (loader) {
            loader.style.display = show ? 'block' : 'none';
        }
    }

    /**
     * Open the notes panel
     */
    function openPanel(title = 'New Note', noteId = null, content = null) {
        const panel = document.getElementById('notes-panel');
        const titleEl = document.getElementById('notes-panel-title');
        const backdrop = document.getElementById('notes-panel-backdrop');

        if (panel && titleEl) {
            state.panelOpen = true;
            state.editingNoteId = noteId;
            titleEl.textContent = title;

            // Show backdrop and panel with transition
            if (backdrop) {
                setTimeout(() => backdrop.classList.add('active'), 10);
            }

            setTimeout(() => panel.classList.add('active'), 10);

            // Initialize or clear editor
            initializeEditor();
            if (state.editor) {
                if (content) {
                    try {
                        state.editor.commands.setContent(JSON.parse(content));
                    } catch (e) {
                        state.editor.commands.setContent('');
                    }
                } else {
                    state.editor.commands.setContent('');
                }
                // Focus on the editor
                setTimeout(() => {
                    if (state.editor && state.editor.view && state.editor.view.dom) {
                        state.editor.view.dom.focus();
                    }
                }, 100);
            }
        }
    }

    /**
     * Close the notes panel
     */
    function closePanel() {
        const panel = document.getElementById('notes-panel');
        const backdrop = document.getElementById('notes-panel-backdrop');

        if (panel) {
            state.panelOpen = false;
            state.editingNoteId = null;
            panel.classList.remove('active');

            // Hide backdrop
            if (backdrop) {
                backdrop.classList.remove('active');
            }
        }
    }

    /**
     * Save note (create or update)
     */
    async function saveNote() {
        if (!state.editor) {
            showMessage('error', 'Editor not ready');
            return;
        }

        const content = state.editor.getJSON();
        const contentStr = JSON.stringify(content);

        if (!contentStr || contentStr === '{}') {
            showMessage('error', 'Note cannot be empty');
            return;
        }

        try {
            state.loading = true;
            renderLoading(true);

            const token = localStorage.getItem('jwt_token');
            const authHeaders = {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            };

            if (state.editingNoteId) {
                // Update existing note
                const response = await fetch(`${API_BASE}/${state.editingNoteId}`, {
                    method: 'PATCH',
                    credentials: 'include',
                    headers: authHeaders,
                    body: JSON.stringify({
                        content: contentStr,
                        contentFormat: 'richtext-json'
                    })
                });

                if (!response.ok) {
                    if (response.status === 401) {
                        showMessage('error', 'Session expired. Please log in again.');
                        return;
                    }
                    if (response.status === 404) {
                        showMessage('error', 'Note not found');
                        closePanel();
                        await fetchNotes(state.page, state.search);
                        return;
                    }
                    throw new Error(`HTTP ${response.status}`);
                }

                const payload = await response.json();
                if (!payload.status) {
                    showMessage('error', payload.message || 'Failed to update note');
                    return;
                }

                showMessage('success', 'Note updated successfully');
            } else {
                // Create new note
                const response = await fetch(API_BASE, {
                    method: 'POST',
                    credentials: 'include',
                    headers: authHeaders,
                    body: JSON.stringify({
                        content: contentStr,
                        contentFormat: 'richtext-json'
                    })
                });

                if (!response.ok) {
                    if (response.status === 401) {
                        showMessage('error', 'Session expired. Please log in again.');
                        return;
                    }
                    throw new Error(`HTTP ${response.status}`);
                }

                const payload = await response.json();
                if (!payload.status) {
                    showMessage('error', payload.message || 'Failed to create note');
                    return;
                }

                showMessage('success', 'Note created successfully');
            }

            closePanel();
            state.page = 1;
            await fetchNotes(1, state.search);
        } catch (error) {
            console.error('Error saving note:', error);
            showMessage('error', 'Failed to save note: ' + error.message);
        } finally {
            state.loading = false;
            renderLoading(false);
        }
    }

    /**
     * Handle search input with debounce
     */
    function onSearchInput(value) {
        clearTimeout(state.searchTimer);
        state.search = value;
        state.page = 1;

        state.searchTimer = setTimeout(() => {
            fetchNotes(1, value);
        }, 300);
    }

    /**
     * Go to a specific page
     */
    function goToPage(pageNum) {
        state.page = pageNum;
        fetchNotes(pageNum, state.search);
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    /**
     * Initialize the notes tab
     */
    async function initialize(opts = {}) {
        const forceRefresh = opts.forceRefresh || false;

        // Bind event handlers
        const searchInput = document.getElementById('notes-search');
        const createToggle = document.getElementById('notes-create-toggle');
        const panelClose = document.getElementById('notes-panel-close');
        const panelCancel = document.getElementById('notes-panel-cancel');
        const panelSave = document.getElementById('notes-panel-save');
        const backdrop = document.getElementById('notes-panel-backdrop');

        if (searchInput && !searchInput.dataset.bound) {
            searchInput.addEventListener('input', (e) => onSearchInput(e.target.value));
            searchInput.dataset.bound = 'true';
        }

        if (createToggle && !createToggle.dataset.bound) {
            createToggle.addEventListener('click', () => openPanel('New Note'));
            createToggle.dataset.bound = 'true';
        }

        if (panelClose && !panelClose.dataset.bound) {
            panelClose.addEventListener('click', closePanel);
            panelClose.dataset.bound = 'true';
        }

        if (panelCancel && !panelCancel.dataset.bound) {
            panelCancel.addEventListener('click', closePanel);
            panelCancel.dataset.bound = 'true';
        }

        if (panelSave && !panelSave.dataset.bound) {
            panelSave.addEventListener('click', saveNote);
            panelSave.dataset.bound = 'true';
        }

        if (backdrop && !backdrop.dataset.bound) {
            backdrop.addEventListener('click', closePanel);
            backdrop.dataset.bound = 'true';
        }

        // Initialize editor
        initializeEditor();

        // Fetch notes
        if (forceRefresh || state.notes.length === 0) {
            await fetchNotes(1, '');
        }
    }

    // Export public API
    window.NotesTab = {
        initialize,
        editNote: (noteId, content) => openPanel('Edit Note', noteId, content),
        goToPage,
        saveNote,
        closePanel
    };
})();
