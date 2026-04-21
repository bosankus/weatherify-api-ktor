import { useEffect } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { useGameStore } from './stores/gameStore';
import { GatePage } from './pages/GatePage';
import { ChapterPage } from './pages/ChapterPage';
import { FinalReveal } from './pages/FinalReveal';
import { ProgressDots } from './components/ProgressDots';

function AppContent() {
  const chapter = useGameStore(state => state.chapter);
  const totalChapters = useGameStore(state => state.totalChapters);
  const fetchState = useGameStore(state => state.fetchState);
  const fetchConfig = useGameStore(state => state.fetchConfig);
  const loading = useGameStore(state => state.loading);

  useEffect(() => {
    fetchConfig();
    fetchState();
  }, [fetchConfig, fetchState]);

  if (loading && chapter === 0) {
    return (
      <div className="min-h-screen bg-background-primary flex items-center justify-center">
        <div className="w-12 h-12 border-2 border-accent-gold border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="bg-background-primary min-h-screen">
      <AnimatePresence mode="wait">
        <Routes key={chapter <= 0 ? 'gate' : 'game'}>
          {chapter <= 0 ? (
            <Route path="*" element={<GatePage />} />
          ) : (
            <>
              {chapter <= 4 ? (
                <Route path="*" element={<ChapterPage />} />
              ) : (
                <Route path="*" element={<FinalReveal />} />
              )}
            </>
          )}
        </Routes>
      </AnimatePresence>

      {chapter > 0 && <ProgressDots current={chapter} total={totalChapters} />}
    </div>
  );
}

function App() {
  // Use a base name if needed, but since we are serving from /a-promise we handle it in Ktor
  return (
    <BrowserRouter basename="/a-promise">
      <AppContent />
    </BrowserRouter>
  );
}

export default App;
