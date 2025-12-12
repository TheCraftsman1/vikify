import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';

import MobileFullScreenPlayer from '../MobileFullScreenPlayer';

vi.mock('react-dom', async () => {
  const actual = await vi.importActual('react-dom');
  return {
    ...actual,
    createPortal: (node) => node,
  };
});

vi.mock('../../utils/haptics', () => ({
  hapticLight: vi.fn(),
  hapticMedium: vi.fn(),
  hapticSelection: vi.fn(),
}));

const seekMock = vi.fn();

vi.mock('../../context/PlayerContext', () => ({
  usePlayer: () => ({
    currentSong: {
      id: 'song-1',
      title: 'Test Song',
      artist: 'Test Artist',
      image: '/placeholder.svg',
      album: 'Test Album',
    },
    isPlaying: false,
    togglePlay: vi.fn(),
    progress: 0,
    duration: 200,
    seek: seekMock,
    playNext: vi.fn(),
    playPrevious: vi.fn(),
    playerRef: { current: null },
    isLoading: false,
    upNextQueue: [],
    queue: [],
    playSong: vi.fn(),
  }),
}));

vi.mock('../../context/LikedSongsContext', () => ({
  useLikedSongs: () => ({
    isLiked: () => false,
    toggleLike: vi.fn(),
  }),
}));

describe('MobileFullScreenPlayer scrubbing', () => {
  beforeEach(() => {
    seekMock.mockClear();
  });

  it('seeks on pointer release (not during drag)', () => {
    const { getByTestId } = render(
      <MobileFullScreenPlayer isOpen={true} onClose={() => {}} />
    );

    const scrubber = getByTestId('mobile-progress-scrubber');

    scrubber.getBoundingClientRect = () => ({
      left: 0,
      top: 0,
      width: 100,
      height: 4,
      right: 100,
      bottom: 4,
    });

    fireEvent.pointerDown(scrubber, { clientX: 10, pointerId: 1 });
    expect(seekMock).not.toHaveBeenCalled();

    fireEvent.pointerMove(scrubber, { clientX: 50, pointerId: 1 });
    expect(seekMock).not.toHaveBeenCalled();

    fireEvent.pointerUp(scrubber, { clientX: 50, pointerId: 1 });
    expect(seekMock).toHaveBeenCalledTimes(1);
    expect(seekMock).toHaveBeenCalledWith(100);
  });
});
