import React, { useState, useEffect } from 'react';
import {
  Box,
  Container,
  Grid,
  Card,
  CardContent,
  Typography,
  CircularProgress,
  Chip,
  Avatar,
  Button,
  TextField,
  InputAdornment,
  Paper,
} from '@mui/material';
import {
  Search as SearchIcon,
  PlayArrow as PlayIcon,
  Visibility as ViewersIcon,
  TrendingUp as TrendingIcon,
  Chat as ChatIcon,
} from '@mui/icons-material';
import { useQuery, useSubscription } from '@apollo/client';
import { GET_ALL_STREAMS, GET_TRENDING_STREAMS } from '../graphql/queries';
import { STREAM_STATUS_CHANGED } from '../graphql/subscriptions';
import { useNavigate } from 'react-router-dom';

const Dashboard = () => {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('all');

  const { data: streamsData, loading: streamsLoading, error: streamsError } = useQuery(GET_ALL_STREAMS);
  const { data: trendingData, loading: trendingLoading } = useQuery(GET_TRENDING_STREAMS, {
    variables: { limit: 10 }
  });

  // Subscribe to real-time stream status updates
  useSubscription(STREAM_STATUS_CHANGED, {
    onSubscriptionData: ({ subscriptionData }) => {
      console.log('Stream status updated:', subscriptionData.data);
    }
  });

  const streams = streamsData?.getAllStreams || [];
  const trendingStreams = trendingData?.getTrendingStreams || [];

  const filteredStreams = streams.filter(stream => {
    const matchesSearch = stream.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                         stream.streamerName.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory = selectedCategory === 'all' || stream.category === selectedCategory;
    return matchesSearch && matchesCategory;
  });

  const categories = ['all', 'gaming', 'music', 'tech', 'talk', 'art', 'sports'];

  const handleStreamClick = (streamId) => {
    navigate(`/stream/${streamId}`);
  };

  const formatViewerCount = (count) => {
    if (count > 1000) {
      return `${(count / 1000).toFixed(1)}K`;
    }
    return count.toString();
  };

  if (streamsLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="60vh">
        <CircularProgress size={60} />
      </Box>
    );
  }

  if (streamsError) {
    return (
      <Container>
        <Typography color="error" variant="h6" textAlign="center">
          Error loading streams: {streamsError.message}
        </Typography>
      </Container>
    );
  }

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      {/* Header */}
      <Box mb={4}>
        <Typography variant="h3" component="h1" gutterBottom fontWeight="bold">
          StreamSense Dashboard
        </Typography>
        <Typography variant="h6" color="text.secondary">
          Real-time streaming analytics and monitoring
        </Typography>
      </Box>

      {/* Search and Filters */}
      <Paper sx={{ p: 3, mb: 4 }}>
        <Grid container spacing={3} alignItems="center">
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              placeholder="Search streams or streamers..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
              }}
            />
          </Grid>
          <Grid item xs={12} md={6}>
            <Box display="flex" gap={1} flexWrap="wrap">
              {categories.map((category) => (
                <Chip
                  key={category}
                  label={category.charAt(0).toUpperCase() + category.slice(1)}
                  onClick={() => setSelectedCategory(category)}
                  color={selectedCategory === category ? 'primary' : 'default'}
                  variant={selectedCategory === category ? 'filled' : 'outlined'}
                />
              ))}
            </Box>
          </Grid>
        </Grid>
      </Paper>

      {/* Trending Streams */}
      {trendingStreams.length > 0 && (
        <Box mb={4}>
          <Typography variant="h5" gutterBottom display="flex" alignItems="center">
            <TrendingIcon sx={{ mr: 1 }} />
            Trending Now
          </Typography>
          <Grid container spacing={2}>
            {trendingStreams.slice(0, 5).map((stream) => (
              <Grid item xs={12} sm={6} md={2.4} key={stream.id}>
                <Card 
                  sx={{ 
                    cursor: 'pointer',
                    transition: 'transform 0.2s',
                    '&:hover': { transform: 'scale(1.02)' }
                  }}
                  onClick={() => handleStreamClick(stream.id)}
                >
                  <CardContent sx={{ p: 2 }}>
                    <Box display="flex" alignItems="center" mb={1}>
                      <Avatar src={stream.thumbnailUrl} sx={{ width: 24, height: 24, mr: 1 }}>
                        {stream.streamerName.charAt(0)}
                      </Avatar>
                      <Typography variant="caption" noWrap>
                        {stream.streamerName}
                      </Typography>
                    </Box>
                    <Typography variant="body2" fontWeight="bold" noWrap>
                      {stream.title}
                    </Typography>
                    <Box display="flex" justifyContent="space-between" alignItems="center" mt={1}>
                      <Chip size="small" label={stream.category} />
                      <Box display="flex" alignItems="center">
                        <ViewersIcon sx={{ fontSize: 14, mr: 0.5 }} />
                        <Typography variant="caption">
                          {formatViewerCount(stream.viewerCount)}
                        </Typography>
                      </Box>
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Box>
      )}

      {/* All Streams */}
      <Typography variant="h5" gutterBottom>
        All Streams ({filteredStreams.length})
      </Typography>
      
      <Grid container spacing={3}>
        {filteredStreams.map((stream) => (
          <Grid item xs={12} sm={6} md={4} lg={3} key={stream.id}>
            <Card 
              sx={{ 
                cursor: 'pointer',
                transition: 'all 0.3s ease',
                '&:hover': { 
                  transform: 'translateY(-4px)',
                  boxShadow: 4,
                }
              }}
              onClick={() => handleStreamClick(stream.id)}
            >
              <Box
                sx={{
                  height: 200,
                  backgroundImage: `url(${stream.thumbnailUrl || '/placeholder-stream.jpg'})`,
                  backgroundSize: 'cover',
                  backgroundPosition: 'center',
                  position: 'relative',
                  display: 'flex',
                  alignItems: 'flex-end',
                }}
              >
                {stream.isLive && (
                  <Chip
                    icon={<PlayIcon />}
                    label="LIVE"
                    color="error"
                    size="small"
                    sx={{ 
                      position: 'absolute',
                      top: 8,
                      left: 8,
                      fontWeight: 'bold'
                    }}
                  />
                )}
                <Box
                  sx={{
                    background: 'linear-gradient(transparent, rgba(0,0,0,0.8))',
                    color: 'white',
                    p: 2,
                    width: '100%',
                  }}
                >
                  <Typography variant="h6" fontWeight="bold" noWrap>
                    {stream.title}
                  </Typography>
                  <Typography variant="body2" sx={{ opacity: 0.9 }}>
                    {stream.streamerName}
                  </Typography>
                </Box>
              </Box>
              
              <CardContent>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                  <Chip 
                    label={stream.category} 
                    size="small" 
                    variant="outlined"
                  />
                  <Box display="flex" alignItems="center" gap={2}>
                    <Box display="flex" alignItems="center">
                      <ViewersIcon sx={{ fontSize: 16, mr: 0.5, color: 'text.secondary' }} />
                      <Typography variant="body2" color="text.secondary">
                        {formatViewerCount(stream.viewerCount)}
                      </Typography>
                    </Box>
                    <Button
                      size="small"
                      startIcon={<ChatIcon />}
                      onClick={(e) => {
                        e.stopPropagation();
                        navigate(`/chat/${stream.id}`);
                      }}
                    >
                      Chat
                    </Button>
                  </Box>
                </Box>
                
                <Typography variant="body2" color="text.secondary" noWrap>
                  {stream.description || 'No description available'}
                </Typography>
                
                {stream.tags && stream.tags.length > 0 && (
                  <Box mt={1} display="flex" gap={0.5} flexWrap="wrap">
                    {stream.tags.slice(0, 3).map((tag, index) => (
                      <Chip
                        key={index}
                        label={tag}
                        size="small"
                        variant="outlined"
                        sx={{ fontSize: '0.7rem', height: '20px' }}
                      />
                    ))}
                  </Box>
                )}
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {filteredStreams.length === 0 && (
        <Box textAlign="center" py={4}>
          <Typography variant="h6" color="text.secondary">
            No streams found matching your criteria
          </Typography>
        </Box>
      )}
    </Container>
  );
};

export default Dashboard;