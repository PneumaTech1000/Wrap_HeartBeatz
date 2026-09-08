package com.giga.tech1000.party_mode.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.media3.datasource.DataSpec;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PcmDataSourceStressTest {

    private PcmDataSource pcmDataSource;
    private LinkedBlockingQueue<ByteBuffer> buffer;

    @Before
    public void setUp() {
        buffer = new LinkedBlockingQueue<>(100);
        pcmDataSource = new PcmDataSource(buffer);
    }

    @Test
    public void testJitterBufferStability_HighLatency() throws IOException {
        pcmDataSource.open(new DataSpec(Uri.parse("party://host")));

        // Skip WAV header (44 bytes)
        byte[] skipBuffer = new byte[44];
        pcmDataSource.read(skipBuffer, 0, 44);

        // Simulate high latency: Buffer stays below INITIAL_BUFFER_PACKETS (15)
        for (int i = 0; i < 5; i++) {
            buffer.offer(ByteBuffer.allocate(1024));
        }

        byte[] readBuffer = new byte[1024];
        int bytesRead = pcmDataSource.read(readBuffer, 0, 1024);

        assertEquals(1024, bytesRead);
        // Should return silence (zeros) because it's still "buffering"
        for (byte b : readBuffer) {
            assertEquals(0, b);
        }
    }

    @Test
    public void testJitterBufferStability_BurstTraffic() throws IOException {
        pcmDataSource.open(new DataSpec(Uri.parse("party://host")));
        
        // Skip header
        pcmDataSource.read(new byte[44], 0, 44);

        // Fill buffer past threshold to trigger playback
        for (int i = 0; i < 20; i++) {
            buffer.offer(ByteBuffer.allocate(1024));
        }

        byte[] readBuffer = new byte[1024];
        int bytesRead = pcmDataSource.read(readBuffer, 0, 1024);
        assertEquals(1024, bytesRead);
        
        // Now burst many packets (over 30) to trigger dropping logic
        for (int i = 0; i < 50; i++) {
            buffer.offer(ByteBuffer.allocate(1024));
        }

        // The read call should have triggered the drop logic
        pcmDataSource.read(readBuffer, 0, 1024);
        
        // After dropping, buffer size should be <= 30
        assertTrue("Buffer should have dropped old packets", buffer.size() <= 30);
    }
}
