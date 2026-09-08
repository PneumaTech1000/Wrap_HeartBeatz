package com.giga.tech1000.heartbeatz.view_models.extended_models;

import android.app.Application;
import android.arch.lifecycle.LiveData;

import com.giga.tech1000.heartbeatz.architecture.repositories.EnhancedFirebasePartyHostRepository;
import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;
import com.giga.tech1000.party_mode.core.PartyState;
import com.giga.tech1000.party_mode.model.PartyHost;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PartyViewModel with EnhancedFirebasePartyHostRepository
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28})
public class PartyViewModelTest {

    private PartyViewModel viewModel;
    private Application application;

    @Mock
    private PlaybackStateRepository mockPlaybackStateRepository;

    @Mock
    private EnhancedFirebasePartyHostRepository mockPartyHostRepository;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        application = RuntimeEnvironment.application;

        // Create ViewModel with mocked dependencies
        viewModel = new PartyViewModel(
                application,
                mockPlaybackStateRepository,
                mockPartyHostRepository
        );
    }

    @Test
    public void testConstructor_createsViewModelSuccessfully() {
        assertNotNull(viewModel);
        assertNotNull(viewModel.getPartyState());
        assertNotNull(viewModel.getCurrentSong());
        assertNotNull(viewModel.getCurrentPosition());
        assertNotNull(viewModel.getCurrentDuration());
        assertNotNull(viewModel.isPlaying());
        assertNotNull(viewModel.getQueue());
        assertNotNull(viewModel.getCurrentQueueIndex());
        assertNotNull(viewModel.getRepeatMode());
        assertNotNull(viewModel.isShuffleEnabled());
        assertNotNull(viewModel.getPlaybackSpeed());
        assertNotNull(viewModel.getPlaybackPitch());
        assertNotNull(viewModel.getDiscoveredParties());
        assertNotNull(viewModel.getHostedParty());
        assertNotNull(viewModel.getConnectedParty());
        assertNotNull(viewModel.getGuestList());
        assertNotNull(viewModel.getGuestCount());
        assertNotNull(viewModel.isGuestAuthenticated());
        assertNotNull(viewModel.getPartyError());
        assertNotNull(viewModel.isSetupRequired());
    }

    @Test
    public void testStartDiscovery_callsRepository() {
        // Act
        viewModel.startDiscovery();

        // Assert
        verify(mockPartyHostRepository).startDiscovery();
        // Verify that partyState was set to SEARCHING
        verify(mockPlaybackStateRepository, never()).play(); // Just verifying no unexpected calls
    }

    @Test
    public void testStopDiscovery_callsRepository() {
        // Act
        viewModel.stopDiscovery();

        // Assert
        verify(mockPartyHostRepository).stopDiscovery();
    }

    @Test
    public void testCreateParty_callsRepository() {
        // Arrange
        String partyName = "Test Party";
        String pin = "1234";

        // Act
        viewModel.createParty(partyName, pin);

        // Assert
        verify(mockPartyHostRepository).createParty(partyName, pin);
    }

    @Test
    public void testJoinParty_callsRepository() {
        // Arrange
        PartyHost host = new PartyHost();
        host.setPartyId("testPartyId");
        host.setPartyName("Test Party");
        String pin = "1234";

        // Act
        viewModel.joinParty(host, pin);

        // Assert
        verify(mockPartyHostRepository).joinParty(host, pin);
    }

    @Test
    public void testLeaveParty_callsRepository() {
        // Act
        viewModel.leaveParty();

        // Assert
        verify(mockPartyHostRepository).leaveParty();
    }

    @Test
    public void testGetPartyState_returnsLiveData() {
        // Act
        LiveData<PartyState> partyStateLiveData = viewModel.getPartyState();

        // Assert
        assertNotNull(partyStateLiveData);
        // Initial value should be IDLE
        assertEquals(PartyState.IDLE, partyStateLiveData.getValue());
    }

    @Test
    public void testGetNetworkConnectivity_returnsLiveData() {
        // Act
        LiveData<Boolean> networkLiveData = viewModel.getNetworkConnectivity();

        // Assert
        assertNotNull(networkLiveData);
    }

    @Test
    public void testIsNetworkConnected_returnsBoolean() {
        // Arrange
        when(mockPartyHostRepository.isNetworkConnected()).thenReturn(
                new androidx.lifecycle.MutableLiveData<>(true)
        );

        // Act
        boolean isConnected = viewModel.isNetworkConnected();

        // Assert
        assertTrue(isConnected);
    }

    @Test
    public void testIsHosting_delegatesToRepository() {
        // Arrange
        when(mockPartyHostRepository.isHosting()).thenReturn(true);

        // Act
        boolean isHosting = viewModel.isHosting();

        // Assert
        assertTrue(isHosting);
        verify(mockPartyHostRepository).isHosting();
    }

    @Test
    public void testIsGuest_delegatesToRepository() {
        // Arrange
        when(mockPartyHostRepository.isGuest()).thenReturn(true);

        // Act
        boolean isGuest = viewModel.isGuest();

        // Assert
        assertTrue(isGuest);
        verify(mockPartyHostRepository).isGuest();
    }

    @Test
    public void testIsInPartyMode_delegatesToRepository() {
        // Arrange
        when(mockPartyHostRepository.isInPartyMode()).thenReturn(true);

        // Act
        boolean inPartyMode = viewModel.isInPartyMode();

        // Assert
        assertTrue(inPartyMode);
        verify(mockPartyHostRepository).isInPartyMode();
    }

    @Test
    public void testIsDiscovering_delegatesToRepository() {
        // Arrange
        when(mockPartyHostRepository.isDiscoveringHosts()).thenReturn(true);

        // Act
        boolean isDiscovering = viewModel.isDiscovering();

        // Assert
        assertTrue(isDiscovering);
        verify(mockPartyHostRepository).isDiscoveringHosts();
    }

    @Test
    public void testPlay_delegatesToPlaybackState() {
        // Act
        viewModel.play();

        // Assert
        verify(mockPlaybackStateRepository).play();
    }

    @Test
    public void testPause_delegatesToPlaybackState() {
        // Act
        viewModel.pause();

        // Assert
        verify(mockPlaybackStateRepository).pause();
    }

    @Test
    public void testTogglePlayPause_delegatesToPlaybackState() {
        // Act
        viewModel.togglePlayPause();

        // Assert
        verify(mockPlaybackStateRepository).togglePlayPause();
    }

    @Test
    public void testSeekTo_delegatesToPlaybackState() {
        // Act
        viewModel.seekTo(1000L);

        // Assert
        verify(mockPlaybackStateRepository).seekTo(1000L);
    }

    @Test
    public void testNext_delegatesToPlaybackState() {
        // Act
        viewModel.next();

        // Assert
        verify(mockPlaybackStateRepository).next();
    }

    @Test
    public void testPrevious_delegatesToPlaybackState() {
        // Act
        viewModel.previous();

        // Assert
        verify(mockPlaybackStateRepository).previous();
    }

    @Test
    public void testSetRepeatMode_delegatesToPlaybackState() {
        // Act
        viewModel.setRepeatMode(1);

        // Assert
        verify(mockPlaybackStateRepository).setRepeatMode(1);
    }

    @Test
    public void testToggleShuffle_delegatesToPlaybackState() {
        // Act
        viewModel.toggleShuffle();

        // Assert
        verify(mockPlaybackStateRepository).toggleShuffle();
    }

    @Test
    public void testSetPlaybackSpeed_delegatesToPlaybackState() {
        // Act
        viewModel.setPlaybackSpeed(1.5f);

        // Assert
        verify(mockPlaybackStateRepository).setPlaybackSpeed(1.5f);
    }

    @Test
    public void testSetPlaybackPitch_delegatesToPlaybackState() {
        // Act
        viewModel.setPlaybackPitch(1.2f);

        // Assert
        verify(mockPlaybackStateRepository).setPlaybackPitch(1.2f);
    }

    @Test
    public void testGetHostIp_returnsDefaultWhenNoHost() {
        // Act
        String hostIp = viewModel.getHostIp();

        // Assert
        assertEquals("0.0.0.0", hostIp);
    }

    @Test
    public void testGetHostPort_returnsDefaultWhenNoHost() {
        // Act
        int hostPort = viewModel.getHostPort();

        // Assert
        assertEquals(8080, hostPort);
    }

    @Test
    public void testGetPartyName_returnsEmptyWhenNoHost() {
        // Act
        String partyName = viewModel.getPartyName();

        // Assert
        assertEquals("", partyName);
    }

    @Test
    public void testGetPartyPin_returnsEmptyWhenNoHost() {
        // Act
        String partyPin = viewModel.getPartyPin();

        // Assert
        assertEquals("", partyPin);
    }
}