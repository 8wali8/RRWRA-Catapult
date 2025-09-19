import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { ApolloProvider } from '@apollo/client';
import { client } from './apollo/client';

// Components
import Navbar from './components/Navbar';
import Dashboard from './pages/Dashboard';
import StreamView from './pages/StreamView';
import Analytics from './pages/Analytics';
import ChatRoom from './pages/ChatRoom';
import Settings from './pages/Settings';

// Theme configuration
const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: '#6366f1',
    },
    secondary: {
      main: '#8b5cf6',
    },
    background: {
      default: '#0f0f23',
      paper: '#1a1a2e',
    },
  },
  typography: {
    fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
  },
});

function App() {
  return (
    <ApolloProvider client={client}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Router>
          <div className="App">
            <Navbar />
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/stream/:streamId" element={<StreamView />} />
              <Route path="/analytics" element={<Analytics />} />
              <Route path="/chat/:roomId" element={<ChatRoom />} />
              <Route path="/settings" element={<Settings />} />
            </Routes>
          </div>
        </Router>
      </ThemeProvider>
    </ApolloProvider>
  );
}

export default App;